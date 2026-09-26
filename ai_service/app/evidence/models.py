from typing import List, Optional, Dict, Any
from enum import Enum
from pydantic import BaseModel, Field

class ClaimType(str, Enum):
    FACT = "FACT"
    SUPPORTED_FINDING = "SUPPORTED_FINDING"
    HYPOTHESIS = "HYPOTHESIS"
    UNKNOWN = "UNKNOWN"
    UNRESOLVED = "UNRESOLVED"

class ExtractedClaim(BaseModel):
    claim: str
    type: ClaimType
    source: str
    sourceReference: Optional[str] = None
    confidence: float = 0.90
    rawData: Dict[str, Any] = Field(default_factory=dict)

class EvidenceAnalyzeRequest(BaseModel):
    toolName: str
    task: Dict[str, Any] = Field(default_factory=dict)
    toolResponse: Dict[str, Any] = Field(default_factory=dict)
    correlationId: Optional[str] = None

class EvidenceAnalyzeResponse(BaseModel):
    claims: List[ExtractedClaim] = Field(default_factory=list)
    unsupportedClaims: List[str] = Field(default_factory=list)
    missingInformation: List[str] = Field(default_factory=list)
    durationMs: float = 0.0
    tokensUsed: int = 0
    llmProvider: str = "mock"
    llmModel: str = "gpt-4o"
