import os
from pydantic import BaseModel, Field

class Settings(BaseModel):
    PROJECT_NAME: str = "IncidentMind AI Intelligence Service"
    API_V1_STR: str = "/ai/v1"
    
    # Provider configuration
    LLM_PROVIDER: str = os.getenv("LLM_PROVIDER", "mock")  # "openai", "anthropic", "gemini", "ollama", "mock"
    LLM_API_KEY: str = os.getenv("LLM_API_KEY", "")
    LLM_MODEL: str = os.getenv("LLM_MODEL", "gpt-4o")
    LLM_BASE_URL: str = os.getenv("LLM_BASE_URL", "https://api.openai.com/v1")
    LLM_TIMEOUT_SECONDS: float = float(os.getenv("LLM_TIMEOUT_SECONDS", "20.0"))
    LLM_MAX_OUTPUT_TOKENS: int = int(os.getenv("LLM_MAX_OUTPUT_TOKENS", "2048"))
    LLM_MAX_RETRIES: int = int(os.getenv("LLM_MAX_RETRIES", "2"))
    
    # Human in the loop thresholds
    MAX_CONSECUTIVE_CRITIC_REJECTIONS: int = int(os.getenv("MAX_CONSECUTIVE_CRITIC_REJECTIONS", "3"))
    MAX_PLANNING_FAILURES: int = int(os.getenv("MAX_PLANNING_FAILURES", "2"))
    
    # Server settings
    HOST: str = os.getenv("AI_SERVICE_HOST", "0.0.0.0")
    PORT: int = int(os.getenv("AI_SERVICE_PORT", "8000"))

settings = Settings()
