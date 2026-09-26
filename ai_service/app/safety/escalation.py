from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field

class EscalationDecision(BaseModel):
    is_escalated: bool
    status: str = "AUTONOMOUS"  # AUTONOMOUS, HUMAN_APPROVAL_REQUIRED, HUMAN_REVIEW_REQUIRED, STOP_LIMIT_REACHED
    reason: Optional[str] = None
    proposedAction: Optional[str] = None
    context: Dict[str, Any] = Field(default_factory=dict)
    allowedHumanActions: List[str] = Field(default_factory=list)

class EscalationEvaluator:
    @staticmethod
    def evaluate_escalation(
        critic_rejections: int,
        planning_failures: int,
        task_count: int,
        max_tasks: int,
        proposed_sensitive_action: Optional[str] = None,
        tools_exhausted: bool = False,
        max_consecutive_rejections: int = 3,
        max_planning_failures: int = 2
    ) -> EscalationDecision:
        # 1. Immediate Sensitive Action Gate
        if proposed_sensitive_action:
            return EscalationDecision(
                is_escalated=True,
                status="HUMAN_APPROVAL_REQUIRED",
                reason=f"Proposed sensitive action '{proposed_sensitive_action}' requires explicit human engineer authorization.",
                proposedAction=proposed_sensitive_action,
                context={"action": proposed_sensitive_action},
                allowedHumanActions=["APPROVE_ACTION", "REJECT_ACTION", "MODIFY_PLAN", "STOP"]
            )

        # 2. Repeated Critic Rejections
        if critic_rejections >= max_consecutive_rejections:
            return EscalationDecision(
                is_escalated=True,
                status="HUMAN_REVIEW_REQUIRED",
                reason=f"{critic_rejections} consecutive critic rejections occurred without grounded evidence. Autonomous progress blocked.",
                context={"criticRejections": critic_rejections},
                allowedHumanActions=["CONTINUE", "MODIFY_PLAN", "STOP"]
            )

        # 3. Repeated Planning Failures
        if planning_failures >= max_planning_failures:
            return EscalationDecision(
                is_escalated=True,
                status="HUMAN_REVIEW_REQUIRED",
                reason=f"{planning_failures} planning or validation failures encountered.",
                context={"planningFailures": planning_failures},
                allowedHumanActions=["CONTINUE", "MODIFY_PLAN", "STOP"]
            )

        # 4. Budget / Task Limit
        if max_tasks > 0 and task_count >= max_tasks:
            return EscalationDecision(
                is_escalated=True,
                status="STOP_LIMIT_REACHED",
                reason=f"Task budget limit ({max_tasks}) reached.",
                context={"taskCount": task_count, "maxTasks": max_tasks},
                allowedHumanActions=["CONTINUE", "STOP"]
            )

        # 5. Tool Exhaustion / No Next Task
        if tools_exhausted:
            return EscalationDecision(
                is_escalated=True,
                status="HUMAN_REVIEW_REQUIRED",
                reason="Available investigative tools and retries exhausted without determining root cause.",
                context={"toolsExhausted": True},
                allowedHumanActions=["CONTINUE", "MODIFY_PLAN", "STOP"]
            )

        return EscalationDecision(is_escalated=False, status="AUTONOMOUS")
