from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.core.exceptions import IncidentMindAiException, SafetyPolicyViolationError
from app.core.logging import setup_logging, get_logger
from app.api import planner, critic, evidence, health, metrics

setup_logging()
logger = get_logger("main")

app = FastAPI(
    title=settings.PROJECT_NAME,
    openapi_url=f"{settings.API_V1_STR}/openapi.json",
    docs_url=f"{settings.API_V1_STR}/docs"
)

# CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global Correlation ID Middleware
@app.middleware("http")
async def correlation_id_middleware(request: Request, call_next):
    correlation_id = request.headers.get("X-Correlation-ID", "none")
    response = await call_next(request)
    if correlation_id != "none":
        response.headers["X-Correlation-ID"] = correlation_id
    return response

# Global Exception Handlers
@app.exception_handler(SafetyPolicyViolationError)
async def safety_exception_handler(request: Request, exc: SafetyPolicyViolationError):
    logger.error(f"Safety policy violation: {exc.message}")
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content={"error": "SAFETY_VIOLATION", "message": exc.message, "details": exc.details}
    )

@app.exception_handler(IncidentMindAiException)
async def ai_exception_handler(request: Request, exc: IncidentMindAiException):
    logger.error(f"AI Service Exception: {exc.message}")
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"error": "AI_SERVICE_ERROR", "message": exc.message, "details": exc.details}
    )

# Include Routers
app.include_router(health.router, prefix=settings.API_V1_STR, tags=["Health"])
app.include_router(metrics.router, prefix=settings.API_V1_STR, tags=["Metrics"])
app.include_router(planner.router, prefix=settings.API_V1_STR, tags=["Planner"])
app.include_router(critic.router, prefix=settings.API_V1_STR, tags=["Critic"])
app.include_router(evidence.router, prefix=settings.API_V1_STR, tags=["Evidence"])
