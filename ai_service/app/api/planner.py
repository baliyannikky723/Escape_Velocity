from fastapi import APIRouter, Header, Request
from typing import Optional
from app.planner.models import PlannerRequest, PlannerResponse
from app.planner.service import PlannerService

router = APIRouter()
planner_service = PlannerService()

@router.post("/plan", response_model=PlannerResponse)
async def create_plan(
    request: PlannerRequest,
    x_correlation_id: Optional[str] = Header(None, alias="X-Correlation-ID")
):
    correlation_id = x_correlation_id or "none"
    return await planner_service.plan(request, correlation_id=correlation_id)
