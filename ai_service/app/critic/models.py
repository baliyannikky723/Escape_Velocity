from typing import List, Optional, Dict, Any
from enum import Enum
from pydantic import BaseModel, Field

class CriticDecisionEnum(str, Enum):
    ACCEPT = "ACCEPT"
    REJECT = "REJECT"
    INCONCLUSIVE = "INCONCLUSIVE"
    HUMAN_APPROVAL_REQUIRED = "HUMAN_APPROVAL_REQUIRED"

class CriticRequest(BaseModel):
    investigationId: str
    incident: Dict[str, Any] = Field(default_factory=dict)
    task: Dict[str, Any] = Field(default_factory=dict)
    agentOutput: Dict[str, Any] = Field(default_factory=dict)
    evidence: List[Dict[str, Any]] = Field(default_factory=list)
    toolResults: List[Dict[str, Any]] = Field(default_factory=list)
    previousFindings: List[Dict[str, Any]] = Field(default_factory=list)

class CriticResponse(BaseModel):
    investigationId: str
    taskId: Optional[str] = None
    decision: CriticDecisionEnum
    reason: str
    supportedClaims: List[str] = Field(default_factory=list)
    unsupportedClaims: List[str] = Field(default_factory=list)
    missingEvidence: List[str] = Field(default_factory=list)
    recommendedFollowUp: List[str] = Field(default_factory=list)
    requiresHumanReview: bool = False
    proposedAction: Optional[str] = None
    allowedHumanActions: List[str] = Field(default_factory=list)
    llmProvider: str = "mock"
    llmModel: str = "gpt-4o"
    durationMs: float = 0.0
    tokensUsed: int = 0
