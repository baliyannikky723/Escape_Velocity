from fastapi import APIRouter, Header
from typing import Optional
from app.evidence.models import EvidenceAnalyzeRequest, EvidenceAnalyzeResponse
from app.evidence.service import EvidenceService

router = APIRouter()
evidence_service = EvidenceService()

@router.post("/evidence/analyze", response_model=EvidenceAnalyzeResponse)
async def analyze_evidence(
    request: EvidenceAnalyzeRequest,
    x_correlation_id: Optional[str] = Header(None, alias="X-Correlation-ID")
):
    if not request.correlationId and x_correlation_id:
        request.correlationId = x_correlation_id
    return await evidence_service.analyze(request)
