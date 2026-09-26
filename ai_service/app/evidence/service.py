import time
from typing import Dict, Any, List
from app.core.config import settings
from app.core.logging import get_logger
from app.evidence.models import EvidenceAnalyzeRequest, EvidenceAnalyzeResponse, ExtractedClaim, ClaimType
from app.llm.prompts import format_evidence_prompt
from app.llm.provider_client import get_llm_client
from app.observability.metrics import metrics_tracker
from app.resilience.retry import execute_with_retry

logger = get_logger("evidence.service")

class EvidenceService:
    def __init__(self):
        self.llm_client = get_llm_client()

    async def analyze(self, request: EvidenceAnalyzeRequest) -> EvidenceAnalyzeResponse:
        start_time = time.time()
        correlation_id = request.correlationId or "none"
        prompt = format_evidence_prompt(request.model_dump())
        
        async def _call_llm():
            return await self.llm_client.interpret_evidence(prompt, {}, correlation_id=correlation_id)

        try:
            raw_data, tokens_used, duration_ms = await execute_with_retry(
                _call_llm,
                max_retries=settings.LLM_MAX_RETRIES,
                correlation_id=correlation_id
            )
            
            claims: List[ExtractedClaim] = []
            for c in raw_data.get("claims", []):
                type_str = str(c.get("type", "FACT")).upper()
                claim_type = ClaimType(type_str) if type_str in ClaimType.__members__ else ClaimType.FACT
                
                claims.append(ExtractedClaim(
                    claim=c.get("claim", ""),
                    type=claim_type,
                    source=c.get("source", request.toolName),
                    sourceReference=c.get("sourceReference"),
                    confidence=float(c.get("confidence", 0.90)),
                    rawData=c.get("rawData", {})
                ))
                
            metrics_tracker.record_call("evidence", success=True, duration_ms=duration_ms, tokens_used=tokens_used)
            
            return EvidenceAnalyzeResponse(
                claims=claims,
                unsupportedClaims=raw_data.get("unsupportedClaims", []),
                missingInformation=raw_data.get("missingInformation", []),
                durationMs=duration_ms,
                tokensUsed=tokens_used,
                llmProvider=settings.LLM_PROVIDER,
                llmModel=settings.LLM_MODEL
            )
            
        except Exception as ex:
            duration_ms = (time.time() - start_time) * 1000.0
            metrics_tracker.record_call("evidence", success=False, duration_ms=duration_ms, tokens_used=0)
            logger.error(f"Evidence LLM failed after retries: {ex}", extra={"correlation_id": correlation_id})
            
            return EvidenceAnalyzeResponse(
                claims=[],
                unsupportedClaims=[],
                missingInformation=[f"Tool output interpretation failed: {ex}"],
                durationMs=duration_ms,
                tokensUsed=0
            )
