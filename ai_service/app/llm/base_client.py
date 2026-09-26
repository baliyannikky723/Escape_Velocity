from abc import ABC, abstractmethod
from typing import Dict, Any, Tuple

class LLMClient(ABC):
    @abstractmethod
    async def generate_plan(self, prompt: str, schema: Dict[str, Any], correlation_id: str = "none") -> Tuple[Dict[str, Any], int, float]:
        """Returns (structured_dict, tokens_used, duration_ms)"""
        pass

    @abstractmethod
    async def evaluate_finding(self, prompt: str, schema: Dict[str, Any], correlation_id: str = "none") -> Tuple[Dict[str, Any], int, float]:
        """Returns (structured_dict, tokens_used, duration_ms)"""
        pass

    @abstractmethod
    async def interpret_evidence(self, prompt: str, schema: Dict[str, Any], correlation_id: str = "none") -> Tuple[Dict[str, Any], int, float]:
        """Returns (structured_dict, tokens_used, duration_ms)"""
        pass
