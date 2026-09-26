from fastapi import APIRouter
from typing import Dict, Any
from app.observability.metrics import metrics_tracker

router = APIRouter()

@router.get("/metrics")
async def get_metrics() -> Dict[str, Any]:
    return metrics_tracker.get_metrics()
