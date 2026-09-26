import time
from typing import Dict, Any
from app.core.config import settings
from app.core.logging import get_logger
from app.critic.models import CriticRequest, CriticResponse, CriticDecisionEnum
from app.llm.prompts import format_critic_prompt
from app.llm.provider_client import get_llm_client
from app.observability.metrics import metrics_tracker
from app.resilience.retry import execute_with_retry
from app.safety.policy import SafetyPolicy

logger = get_logger("critic.service")

class CriticService:
    def __init__(self):
        self.llm_client = get_llm_client()

    async def evaluate(self, request: CriticRequest, correlation_id: str = "none") -> CriticResponse:
        start_time = time.time()
        
        # 1. Pre-Check for Sensitive Actions in Agent Output
        agent_claim = str(request.agentOutput.get("claim", ""))
        proposed_action = request.agentOutput.get("proposedAction") or request.task.get("taskType")
        
        if SafetyPolicy.is_sensitive_action(str(proposed_action)) or SafetyPolicy.is_sensitive_action(agent_claim):
            action_name = str(proposed_action).upper() if proposed_action else "SENSITIVE_ACTION"
            duration_ms = (time.time() - start_time) * 1000.0
            metrics_tracker.record_call("critic", success=True, duration_ms=duration_ms, is_rejection=False)
            
            return CriticResponse(
                investigationId=request.investigationId,
                taskId=request.task.get("id"),
                decision=CriticDecisionEnum.HUMAN_APPROVAL_REQUIRED,
                reason=f"Action '{action_name}' modifies production or state. Explicit human engineer approval is required.",
                requiresHumanReview=True,
                proposedAction=action_name,
                allowedHumanActions=["APPROVE_ACTION", "REJECT_ACTION", "MODIFY_PLAN", "STOP"],
                llmProvider=settings.LLM_PROVIDER,
                llmModel=settings.LLM_MODEL,
                durationMs=duration_ms,
                tokensUsed=0
            )

        # 2. Invoke LLM with Bounded Retry
        prompt = format_critic_prompt(request.model_dump())
        
        async def _call_llm():
            return await self.llm_client.evaluate_finding(prompt, {}, correlation_id=correlation_id)

        try:
            raw_eval, tokens_used, duration_ms = await execute_with_retry(
                _call_llm,
                max_retries=settings.LLM_MAX_RETRIES,
                correlation_id=correlation_id
            )
            
            decision_str = str(raw_eval.get("decision", "ACCEPT")).upper()
            decision = CriticDecisionEnum(decision_str) if decision_str in CriticDecisionEnum.__members__ else CriticDecisionEnum.ACCEPT
            is_rejection = (decision == CriticDecisionEnum.REJECT)
            
            metrics_tracker.record_call("critic", success=True, duration_ms=duration_ms, tokens_used=tokens_used, is_rejection=is_rejection)
            
            return CriticResponse(
                investigationId=request.investigationId,
                taskId=request.task.get("id"),
                decision=decision,
                reason=raw_eval.get("reason", "Critic evaluation completed."),
                supportedClaims=raw_eval.get("supportedClaims", []),
                unsupportedClaims=raw_eval.get("unsupportedClaims", []),
                missingEvidence=raw_eval.get("missingEvidence", []),
                recommendedFollowUp=raw_eval.get("recommendedFollowUp", []),
                requiresHumanReview=raw_eval.get("requiresHumanReview", False),
                proposedAction=raw_eval.get("proposedAction"),
                allowedHumanActions=raw_eval.get("allowedHumanActions", []),
                llmProvider=settings.LLM_PROVIDER,
                llmModel=settings.LLM_MODEL,
                durationMs=duration_ms,
                tokensUsed=tokens_used
            )
            
        except Exception as ex:
            duration_ms = (time.time() - start_time) * 1000.0
            metrics_tracker.record_call("critic", success=False, duration_ms=duration_ms, tokens_used=0)
            logger.error(f"Critic LLM failed after retries: {ex}", extra={"correlation_id": correlation_id})
            
            # Safe Fallback: Mark Inconclusive so human or deterministic system can resolve
            return CriticResponse(
                investigationId=request.investigationId,
                taskId=request.task.get("id"),
                decision=CriticDecisionEnum.INCONCLUSIVE,
                reason=f"Critic evaluation unavailable ({ex}). Recommending manual or deterministic verification.",
                requiresHumanReview=False,
                durationMs=duration_ms,
                tokensUsed=0
            )
