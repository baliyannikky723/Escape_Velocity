import time
from typing import Dict, Any, List
from app.core.config import settings
from app.core.exceptions import SchemaValidationError, SafetyPolicyViolationError
from app.core.logging import get_logger
from app.llm.prompts import format_planner_prompt
from app.llm.provider_client import get_llm_client
from app.observability.metrics import metrics_tracker
from app.planner.models import PlannerRequest, PlannerResponse, PlannedActionDto, PlanDecision, PlanActionType, TaskPriority
from app.resilience.retry import execute_with_retry
from app.safety.escalation import EscalationEvaluator
from app.safety.policy import SafetyPolicy

logger = get_logger("planner.service")

KNOWN_AGENTS = {"incident-triage-agent", "change-analysis-agent", "dependency-analysis-agent"}
KNOWN_TASK_TYPES = {"TRIAGE", "CHANGE_ANALYSIS", "DEPENDENCY_ANALYSIS", "VERIFICATION"}

class PlannerService:
    def __init__(self):
        self.llm_client = get_llm_client()

    async def plan(self, request: PlannerRequest, correlation_id: str = "none") -> PlannerResponse:
        start_time = time.time()
        
        # 1. Check Pre-Conditions & Escalation Limits
        critic_rejections = sum(1 for c in request.criticResults if str(c.get("decision", "")).upper() == "REJECT")
        task_count = len(request.tasks)
        max_tasks = request.constraints.get("maxTasks", 10)
        
        escalation = EscalationEvaluator.evaluate_escalation(
            critic_rejections=critic_rejections,
            planning_failures=0,
            task_count=task_count,
            max_tasks=max_tasks,
            max_consecutive_rejections=settings.MAX_CONSECUTIVE_CRITIC_REJECTIONS
        )
        
        if escalation.is_escalated:
            logger.warning(f"Planner triggered escalation: {escalation.status} - {escalation.reason}", extra={"correlation_id": correlation_id})
            decision = PlanDecision(escalation.status) if escalation.status in PlanDecision.__members__ else PlanDecision.HUMAN_REVIEW_REQUIRED
            return PlannerResponse(
                investigationId=request.investigationId,
                decision=decision,
                reason=escalation.reason or "Investigation escalation limit reached.",
                isEscalated=True,
                escalationReason=escalation.reason,
                allowedHumanActions=escalation.allowedHumanActions,
                llmProvider=settings.LLM_PROVIDER,
                llmModel=settings.LLM_MODEL,
                durationMs=(time.time() - start_time) * 1000.0,
                tokensUsed=0
            )

        # 2. Invoke LLM with Bounded Retry
        prompt = format_planner_prompt(request.model_dump())
        
        async def _call_llm():
            return await self.llm_client.generate_plan(prompt, {}, correlation_id=correlation_id)

        try:
            raw_plan, tokens_used, duration_ms = await execute_with_retry(
                _call_llm,
                max_retries=settings.LLM_MAX_RETRIES,
                correlation_id=correlation_id
            )
            
            # 3. Semantic & Safety Validation
            validated_actions = self._validate_and_sanitize_actions(
                raw_plan.get("actions", []),
                request.availableAgents or list(KNOWN_AGENTS),
                request.tasks
            )
            
            decision_str = str(raw_plan.get("decision", "CONTINUE")).upper()
            decision = PlanDecision(decision_str) if decision_str in PlanDecision.__members__ else PlanDecision.CONTINUE
            
            metrics_tracker.record_call("plan", success=True, duration_ms=duration_ms, tokens_used=tokens_used)
            
            return PlannerResponse(
                investigationId=request.investigationId,
                decision=decision,
                reason=raw_plan.get("reason", "Autonomous plan created."),
                knownFacts=raw_plan.get("knownFacts", []),
                unknowns=raw_plan.get("unknowns", []),
                actions=validated_actions,
                isEscalated=False,
                llmProvider=settings.LLM_PROVIDER,
                llmModel=settings.LLM_MODEL,
                durationMs=duration_ms,
                tokensUsed=tokens_used,
                isFallback=False
            )
            
        except Exception as ex:
            duration_ms = (time.time() - start_time) * 1000.0
            metrics_tracker.record_call("plan", success=False, duration_ms=duration_ms, tokens_used=0)
            logger.error(f"Planner LLM failed after retries: {ex}", extra={"correlation_id": correlation_id})
            
            # Signal deterministic fallback recommendation to Spring Boot
            return PlannerResponse(
                investigationId=request.investigationId,
                decision=PlanDecision.CONTINUE,
                reason=f"LLM Planner unavailable ({ex}). Recommending fallback to deterministic engine.",
                actions=[],
                isEscalated=False,
                isFallback=True,
                durationMs=duration_ms,
                tokensUsed=0
            )

    def _validate_and_sanitize_actions(
        self,
        raw_actions: List[Dict[str, Any]],
        allowed_agents: List[str],
        existing_tasks: List[Dict[str, Any]]
    ) -> List[PlannedActionDto]:
        validated = []
        allowed_agents_set = set(allowed_agents) | KNOWN_AGENTS
        
        for a in raw_actions:
            agent = a.get("agentType", "")
            task_type = a.get("taskType", "")
            reason = a.get("reason", "")
            
            # Check safety
            is_forbidden, forbidden_msg = SafetyPolicy.check_for_forbidden_operations(reason + " " + task_type)
            if is_forbidden:
                raise SafetyPolicyViolationError(forbidden_msg)
            
            if agent not in allowed_agents_set:
                logger.warning(f"Unknown agent '{agent}' in plan. Filtering out.")
                continue

            action_type_str = str(a.get("action", "CREATE_TASK")).upper()
            action_type = PlanActionType(action_type_str) if action_type_str in PlanActionType.__members__ else PlanActionType.CREATE_TASK
            
            priority_str = str(a.get("priority", "HIGH")).upper()
            priority = TaskPriority(priority_str) if priority_str in TaskPriority.__members__ else TaskPriority.HIGH

            dto = PlannedActionDto(
                action=action_type,
                taskType=task_type,
                agentType=agent,
                priority=priority,
                parentTaskId=a.get("parentTaskId"),
                toolRequirement=a.get("toolRequirement", "NO_TOOL_REQUIRED"),
                suggestedTool=a.get("suggestedTool"),
                reason=reason,
                expectedEvidence=a.get("expectedEvidence", [])
            )
            validated.append(dto)
            
        return validated
