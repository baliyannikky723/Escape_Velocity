import time
from typing import Dict, Any
from pydantic import BaseModel, Field

class LlmMetricsData(BaseModel):
    plannerLlmCalls: int = 0
    criticLlmCalls: int = 0
    evidenceLlmCalls: int = 0
    successfulCalls: int = 0
    failedCalls: int = 0
    fallbackCalls: int = 0
    totalLatencyMs: float = 0.0
    averageLatencyMs: float = 0.0
    totalTokens: int = 0
    planningFailures: int = 0
    criticRejections: int = 0

class MetricsTracker:
    def __init__(self):
        self._data = LlmMetricsData()

    def record_call(
        self,
        operation_type: str,
        success: bool,
        duration_ms: float,
        tokens_used: int = 0,
        is_fallback: bool = False,
        is_rejection: bool = False
    ):
        if operation_type == "plan":
            self._data.plannerLlmCalls += 1
            if not success:
                self._data.planningFailures += 1
        elif operation_type == "critic":
            self._data.criticLlmCalls += 1
            if is_rejection:
                self._data.criticRejections += 1
        elif operation_type == "evidence":
            self._data.evidenceLlmCalls += 1

        if success:
            self._data.successfulCalls += 1
        else:
            self._data.failedCalls += 1

        if is_fallback:
            self._data.fallbackCalls += 1

        self._data.totalTokens += tokens_used
        self._data.totalLatencyMs += duration_ms
        total_calls = self._data.successfulCalls + self._data.failedCalls
        if total_calls > 0:
            self._data.averageLatencyMs = self._data.totalLatencyMs / total_calls

    def get_metrics(self) -> Dict[str, Any]:
        return self._data.model_dump()

    def reset(self):
        self._data = LlmMetricsData()

metrics_tracker = MetricsTracker()
