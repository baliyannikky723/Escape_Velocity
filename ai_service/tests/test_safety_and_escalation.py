import pytest
from app.safety.policy import SafetyPolicy
from app.safety.escalation import EscalationEvaluator

def test_sensitive_action_detection():
    assert SafetyPolicy.is_sensitive_action("ROLLBACK_PRODUCTION") is True
    assert SafetyPolicy.is_sensitive_action("DEPLOY_PRODUCTION") is True
    assert SafetyPolicy.is_sensitive_action("UPDATE_DATABASE") is True
    assert SafetyPolicy.is_sensitive_action("FETCH_LOGS") is False

def test_forbidden_operation_detection():
    is_forbidden, msg = SafetyPolicy.check_for_forbidden_operations("Please run rm -rf /var/log")
    assert is_forbidden is True
    assert "rm -rf" in msg

    is_forbidden, _ = SafetyPolicy.check_for_forbidden_operations("Analyze GitHub commits")
    assert is_forbidden is False

def test_escalation_on_three_consecutive_critic_rejections():
    decision = EscalationEvaluator.evaluate_escalation(
        critic_rejections=3,
        planning_failures=0,
        task_count=2,
        max_tasks=10
    )
    assert decision.is_escalated is True
    assert decision.status == "HUMAN_REVIEW_REQUIRED"
    assert "3 consecutive critic rejections" in decision.reason
    assert "MODIFY_PLAN" in decision.allowedHumanActions

def test_escalation_on_planning_failures():
    decision = EscalationEvaluator.evaluate_escalation(
        critic_rejections=0,
        planning_failures=2,
        task_count=1,
        max_tasks=10
    )
    assert decision.is_escalated is True
    assert decision.status == "HUMAN_REVIEW_REQUIRED"
