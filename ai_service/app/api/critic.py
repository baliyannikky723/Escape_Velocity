from fastapi import APIRouter, Header
from typing import Optional
from app.critic.models import CriticRequest, CriticResponse
from app.critic.service import CriticService

router = APIRouter()
critic_service = CriticService()

@router.post("/critic", response_model=CriticResponse)
async def evaluate_finding(
    request: CriticRequest,
    x_correlation_id: Optional[str] = Header(None, alias="X-Correlation-ID")
):
    correlation_id = x_correlation_id or "none"
    return await critic_service.evaluate(request, correlation_id=correlation_id)
