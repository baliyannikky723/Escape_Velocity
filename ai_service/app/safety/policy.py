from typing import Set, Tuple, Optional

SENSITIVE_ACTIONS: Set[str] = {
    "ROLLBACK_PRODUCTION",
    "DEPLOY_PRODUCTION",
    "UPDATE_DATABASE",
    "MODIFY_CREDENTIALS",
    "DELETE_DATA",
    "RESTART_CLUSTER",
    "MODIFY_FIREWALL",
    "KILL_PODS",
    "SCALE_ZERO",
}

FORBIDDEN_KEYWORDS: Set[str] = {
    "rm -rf",
    "drop table",
    "truncate table",
    "delete from",
    "format c:",
}

class SafetyPolicy:
    @staticmethod
    def is_sensitive_action(action_name: str) -> bool:
        if not action_name:
            return False
        normalized = action_name.strip().upper()
        return normalized in SENSITIVE_ACTIONS or any(s in normalized for s in SENSITIVE_ACTIONS)

    @staticmethod
    def check_for_forbidden_operations(text: str) -> Tuple[bool, Optional[str]]:
        if not text:
            return False, None
        lower_text = text.lower()
        for forbidden in FORBIDDEN_KEYWORDS:
            if forbidden in lower_text:
                return True, f"Prohibited dangerous operation detected: '{forbidden}'"
        return False, None
