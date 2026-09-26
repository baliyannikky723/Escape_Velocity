from fastapi import APIRouter
from typing import Dict, Any
from app.core.config import settings

router = APIRouter()

@router.get("/health")
async def get_health() -> Dict[str, Any]:
    return {
        "status": "UP",
        "service": settings.PROJECT_NAME,
        "provider": settings.LLM_PROVIDER,
        "model": settings.LLM_MODEL
    }
