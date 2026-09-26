from typing import Any, Dict, Optional

class IncidentMindAiException(Exception):
    def __init__(self, message: str, details: Optional[Dict[str, Any]] = None):
        super().__init__(message)
        self.message = message
        self.details = details or {}

class LlmProviderError(IncidentMindAiException):
    pass

class LlmTimeoutError(IncidentMindAiException):
    pass

class SchemaValidationError(IncidentMindAiException):
    pass

class SafetyPolicyViolationError(IncidentMindAiException):
    pass
