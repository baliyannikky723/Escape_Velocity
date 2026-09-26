import time
import json
import httpx
from typing import Dict, Any, Tuple
from app.core.config import settings
from app.core.exceptions import LlmProviderError, LlmTimeoutError
from app.core.logging import get_logger
from app.llm.base_client import LLMClient
from app.safety.policy import SafetyPolicy

logger = get_logger("llm.provider")

class MockLLMClient(LLMClient):
    """High-fidelity local reasoning engine used when LLM_PROVIDER=mock or in tests."""
    
    async def generate_plan(self, prompt: str, schema: Dict[str, Any], correlation_id: str = "none") -> Tuple[Dict[str, Any], int, float]:
        start = time.time()
        
        # Analyze prompt contents
        prompt_lower = prompt.lower()
        has_critic_rejection = "critic_rejected" in prompt_lower or "unsupported_causal_claim" in prompt_lower or '"decision": "reject"' in prompt_lower
        has_triage_done = "triage" in prompt_lower and ('"status": "completed"' in prompt_lower or "status: completed" in prompt_lower)
        has_change_done = "change_analysis" in prompt_lower and ('"status": "completed"' in prompt_lower or "status: completed" in prompt_lower)
        
        if has_critic_rejection:
            result = {
                "decision": "REPLAN",
                "reason": "Critic rejected causal claim. Initiating follow-up PR diff and CI verification.",
                "knownFacts": ["Deployment temporal correlation observed", "Causal proof unverified"],
                "unknowns": ["Do pull request changes or CI artifacts directly touch the failing code path?"],
                "actions": [
                    {
                        "action": "CREATE_TASK",
                        "taskType": "CHANGE_ANALYSIS",
                        "agentType": "change-analysis-agent",
                        "priority": "HIGH",
                        "toolRequirement": "GITHUB_API",
                        "suggestedTool": "github.get_pull_requests",
                        "reason": "Verify detailed pull request commit diffs and reviews.",
                        "expectedEvidence": ["PR review approvals", "modified file paths"]
                    }
                ]
            }
        elif has_triage_done and not has_change_done:
            result = {
                "decision": "CONTINUE",
                "reason": "Incident triage verified anomaly. Inspecting recent code repository changes.",
                "knownFacts": ["Checkout service 5xx spike confirmed in production"],
                "unknowns": ["What commits or releases were deployed prior to the anomaly?"],
                "actions": [
                    {
                        "action": "CREATE_TASK",
                        "taskType": "CHANGE_ANALYSIS",
                        "agentType": "change-analysis-agent",
                        "priority": "HIGH",
                        "toolRequirement": "GITHUB_API",
                        "suggestedTool": "github.get_recent_commits",
                        "reason": "Identify commits deployed within the incident detection window.",
                        "expectedEvidence": ["Recent commit SHAs", "Author emails", "Deployment timestamps"]
                    },
                    {
                        "action": "CREATE_TASK",
                        "taskType": "DEPENDENCY_ANALYSIS",
                        "agentType": "dependency-analysis-agent",
                        "priority": "MEDIUM",
                        "toolRequirement": "NO_TOOL_REQUIRED",
                        "suggestedTool": None,
                        "reason": "Cross-check third-party payment gateway health to eliminate false positives.",
                        "expectedEvidence": ["Dependency health signals"]
                    }
                ]
            }
        elif has_change_done:
            result = {
                "decision": "STOP_SUCCESS",
                "reason": "All planned investigation tasks have concluded with verified evidence on Blackboard.",
                "knownFacts": ["Recent deployment is temporally correlated with incident timing"],
                "unknowns": ["Downstream application logs trace confirmation"],
                "actions": []
            }
        else:
            result = {
                "decision": "CONTINUE",
                "reason": "Initial incident classification is required before deeper investigation.",
                "knownFacts": ["Incident alert received"],
                "unknowns": ["Exact blast radius, environment, and severity level"],
                "actions": [
                    {
                        "action": "CREATE_TASK",
                        "taskType": "TRIAGE",
                        "agentType": "incident-triage-agent",
                        "priority": "HIGH",
                        "toolRequirement": "NO_TOOL_REQUIRED",
                        "suggestedTool": None,
                        "reason": "Establish baseline incident characteristics and anomaly metrics.",
                        "expectedEvidence": ["Service name", "Environment", "Severity level", "Anomaly metrics"]
                    }
                ]
            }
        
        duration_ms = (time.time() - start) * 1000.0
        return result, 240, duration_ms

    async def evaluate_finding(self, prompt: str, schema: Dict[str, Any], correlation_id: str = "none") -> Tuple[Dict[str, Any], int, float]:
        start = time.time()
        prompt_lower = prompt.lower()
        
        # Check for sensitive actions
        for action in ["rollback_production", "deploy_production", "update_database"]:
            if action in prompt_lower:
                result = {
                    "decision": "HUMAN_APPROVAL_REQUIRED",
                    "reason": f"Autonomous execution of sensitive action '{action.upper()}' is blocked by safety policy.",
                    "supportedClaims": [],
                    "unsupportedClaims": [],
                    "missingEvidence": [],
                    "recommendedFollowUp": ["Human engineer review and approve remediation action manually."],
                    "requiresHumanReview": True,
                    "proposedAction": action.upper(),
                    "allowedHumanActions": ["APPROVE_ACTION", "REJECT_ACTION", "MODIFY_PLAN", "STOP"]
                }
                duration_ms = (time.time() - start) * 1000.0
                return result, 180, duration_ms

        # Check for ungrounded causal leaps
        if "definitely caused" in prompt_lower or "proved root cause" in prompt_lower or "conclusively caused" in prompt_lower:
            result = {
                "decision": "REJECT",
                "reason": "UNSUPPORTED_CAUSAL_CLAIM: Temporal correlation alone does not establish causal proof without error traces.",
                "supportedClaims": ["Commit was deployed 8 minutes before incident"],
                "unsupportedClaims": ["Commit definitely caused the outage"],
                "missingEvidence": ["Runtime stack traces referencing modified code paths", "CI test failure reports"],
                "recommendedFollowUp": ["INVESTIGATE_PULL_REQUESTS", "FETCH_APPLICATION_LOGS"],
                "requiresHumanReview": False,
                "proposedAction": None,
                "allowedHumanActions": []
            }
        elif "no evidence" in prompt_lower or "empty" in prompt_lower:
            result = {
                "decision": "INCONCLUSIVE",
                "reason": "Insufficient evidence gathered to substantiate finding.",
                "supportedClaims": [],
                "unsupportedClaims": [],
                "missingEvidence": ["Telemetry metrics", "Repository commit metadata"],
                "recommendedFollowUp": ["RETRY_EVIDENCE_GATHERING"],
                "requiresHumanReview": False,
                "proposedAction": None,
                "allowedHumanActions": []
            }
        else:
            result = {
                "decision": "ACCEPT",
                "reason": "Findings are grounded in verified evidence and consistent with tool output.",
                "supportedClaims": ["Temporal correlation verified between deployment and error rate surge"],
                "unsupportedClaims": [],
                "missingEvidence": [],
                "recommendedFollowUp": [],
                "requiresHumanReview": False,
                "proposedAction": None,
                "allowedHumanActions": []
            }

        duration_ms = (time.time() - start) * 1000.0
        return result, 195, duration_ms

    async def interpret_evidence(self, prompt: str, schema: Dict[str, Any], correlation_id: str = "none") -> Tuple[Dict[str, Any], int, float]:
        start = time.time()
        
        result = {
            "claims": [
                {
                    "claim": "Recent commit deployed 8 minutes prior to error rate surge",
                    "type": "FACT",
                    "source": "github.get_recent_commits",
                    "sourceReference": "abc1234",
                    "confidence": 0.95,
                    "rawData": {"commitSha": "abc1234", "author": "dev@incidentmind.io"}
                }
            ],
            "unsupportedClaims": [],
            "missingInformation": []
        }
        
        duration_ms = (time.time() - start) * 1000.0
        return result, 160, duration_ms


class HttpOpenAILLMClient(LLMClient):
    """Real HTTP client supporting OpenAI, Gemini OpenAI-compatible endpoint, or Ollama."""
    
    def __init__(self):
        self.api_key = settings.LLM_API_KEY
        self.base_url = settings.LLM_BASE_URL.rstrip('/')
        self.model = settings.LLM_MODEL
        self.timeout = settings.LLM_TIMEOUT_SECONDS

    async def _call_chat_completion(self, system_prompt: str, user_prompt: str, correlation_id: str) -> Tuple[Dict[str, Any], int, float]:
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}" if self.api_key else ""
        }
        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt}
            ],
            "response_format": {"type": "json_object"},
            "max_tokens": settings.LLM_MAX_OUTPUT_TOKENS,
            "temperature": 0.1
        }
        
        start = time.time()
        async with httpx.AsyncClient(timeout=self.timeout) as client:
            try:
                response = await client.post(
                    f"{self.base_url}/chat/completions",
                    headers=headers,
                    json=payload
                )
                duration_ms = (time.time() - start) * 1000.0
                
                if response.status_code == 429:
                    raise LlmProviderError(f"LLM Provider Rate Limited (HTTP 429): {response.text}")
                if response.status_code >= 500:
                    raise LlmProviderError(f"LLM Provider Server Error (HTTP {response.status_code}): {response.text}")
                if response.status_code != 200:
                    raise LlmProviderError(f"LLM Provider Error (HTTP {response.status_code}): {response.text}")

                data = response.json()
                content = data["choices"][0]["message"]["content"]
                tokens_used = data.get("usage", {}).get("total_tokens", 0)
                parsed_json = json.loads(content)
                return parsed_json, tokens_used, duration_ms
            except httpx.TimeoutException as te:
                raise LlmTimeoutError(f"LLM request timed out after {self.timeout}s: {te}")
            except json.JSONDecodeError as jde:
                raise LlmProviderError(f"LLM returned invalid JSON: {jde}")

    async def generate_plan(self, prompt: str, schema: Dict[str, Any], correlation_id: str = "none") -> Tuple[Dict[str, Any], int, float]:
        from app.llm.prompts import PLANNER_SYSTEM_PROMPT
        return await self._call_chat_completion(PLANNER_SYSTEM_PROMPT, prompt, correlation_id)

    async def evaluate_finding(self, prompt: str, schema: Dict[str, Any], correlation_id: str = "none") -> Tuple[Dict[str, Any], int, float]:
        from app.llm.prompts import CRITIC_SYSTEM_PROMPT
        return await self._call_chat_completion(CRITIC_SYSTEM_PROMPT, prompt, correlation_id)

    async def interpret_evidence(self, prompt: str, schema: Dict[str, Any], correlation_id: str = "none") -> Tuple[Dict[str, Any], int, float]:
        from app.llm.prompts import EVIDENCE_SYSTEM_PROMPT
        return await self._call_chat_completion(EVIDENCE_SYSTEM_PROMPT, prompt, correlation_id)


def get_llm_client() -> LLMClient:
    if settings.LLM_PROVIDER.lower() in ("openai", "gemini", "anthropic", "ollama") and settings.LLM_API_KEY:
        return HttpOpenAILLMClient()
    return MockLLMClient()
