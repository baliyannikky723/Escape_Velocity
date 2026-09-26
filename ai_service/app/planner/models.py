from typing import List, Optional, Dict, Any
from enum import Enum
from pydantic import BaseModel, Field

class PlanDecision(str, Enum):
    CONTINUE = "CONTINUE"
    REPLAN = "REPLAN"
    WAIT = "WAIT"
    STOP_SUCCESS = "STOP_SUCCESS"
    HUMAN_REVIEW_REQUIRED = "HUMAN_REVIEW_REQUIRED"
    STOP_LIMIT_REACHED = "STOP_LIMIT_REACHED"

class PlanActionType(str, Enum):
    CREATE_TASK = "CREATE_TASK"
    CANCEL_TASK = "CANCEL_TASK"
    REPRIORITIZE_TASK = "REPRIORITIZE_TASK"
    CONTINUE_TASK = "CONTINUE_TASK"
    WAIT = "WAIT"
    STOP = "STOP"

class TaskPriority(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"

class PlannedActionDto(BaseModel):
    action: PlanActionType
    taskType: str
    agentType: str
    priority: TaskPriority = TaskPriority.HIGH
    parentTaskId: Optional[str] = None
    toolRequirement: str = "NO_TOOL_REQUIRED"
    suggestedTool: Optional[str] = None
    reason: str
    expectedEvidence: List[str] = Field(default_factory=list)

class PlannerRequest(BaseModel):
    investigationId: str
    incident: Dict[str, Any] = Field(default_factory=dict)
    currentPlan: Dict[str, Any] = Field(default_factory=dict)
    tasks: List[Dict[str, Any]] = Field(default_factory=list)
    evidence: List[Dict[str, Any]] = Field(default_factory=list)
    agentResults: List[Dict[str, Any]] = Field(default_factory=list)
    criticResults: List[Dict[str, Any]] = Field(default_factory=list)
    recoveryResults: List[Dict[str, Any]] = Field(default_factory=list)
    availableAgents: List[str] = Field(default_factory=list)
    availableTools: List[str] = Field(default_factory=list)
    constraints: Dict[str, Any] = Field(default_factory=dict)

class PlannerResponse(BaseModel):
    investigationId: str
    decision: PlanDecision
    reason: str
    knownFacts: List[str] = Field(default_factory=list)
    unknowns: List[str] = Field(default_factory=list)
    actions: List[PlannedActionDto] = Field(default_factory=list)
    isEscalated: bool = False
    escalationReason: Optional[str] = None
    allowedHumanActions: List[str] = Field(default_factory=list)
    llmProvider: str = "mock"
    llmModel: str = "gpt-4o"
    durationMs: float = 0.0
    tokensUsed: int = 0
    isFallback: bool = False
