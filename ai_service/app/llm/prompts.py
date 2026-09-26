import json
from typing import Dict, Any, List

PLANNER_SYSTEM_PROMPT = """You are the AI Planner for IncidentMind, an autonomous SRE incident response system.
Your responsibility is to analyze production incidents and formulate a minimal, structured, non-hallucinating investigation plan.

Rules:
1. Decompose the incident into logical steps: TRIAGE -> CHANGE_ANALYSIS -> DEPENDENCY_ANALYSIS -> VERIFICATION.
2. Select appropriate specialized agents: 'incident-triage-agent', 'change-analysis-agent', 'dependency-analysis-agent'.
3. Do NOT make ungrounded assumptions.
4. If previous Critic rejected a finding, replan an alternate evidence-gathering path.
5. If max tasks or constraints reached, return STOP_SUCCESS or STOP_LIMIT_REACHED.
6. Return strictly valid JSON adhering to the specified schema.
"""

CRITIC_SYSTEM_PROMPT = """You are the AI Critic & Safety Gatekeeper for IncidentMind.
Your responsibility is to evaluate findings from specialized agents with strict epistemic rigor and safety policy compliance.

Rules:
1. ACCEPT only claims directly backed by persisted evidence or real tool output.
2. REJECT unsupported causal leaps (e.g. claiming a commit definitely caused an outage solely because it happened before the incident).
3. If proposed action modifies production (e.g. ROLLBACK_PRODUCTION, DEPLOY_PRODUCTION, UPDATE_DATABASE), return HUMAN_APPROVAL_REQUIRED immediately.
4. Return INCONCLUSIVE if data is missing or ambiguous.
5. Return strictly valid JSON adhering to the specified schema.
"""

EVIDENCE_SYSTEM_PROMPT = """You are the Evidence Extraction Engine for IncidentMind.
Your responsibility is to extract strictly factual claims from raw tool API responses without hallucinating or fabricating details.

Rules:
1. Classify verified facts as FACT.
2. Classify temporal associations as SUPPORTED_FINDING.
3. Every claim MUST cite the source and sourceReference (commit SHA, timestamp, etc.).
4. Do not invent timestamps, authors, metrics, or error codes.
5. Return strictly valid JSON adhering to the specified schema.
"""

def format_planner_prompt(request_dict: Dict[str, Any]) -> str:
    return f"""Incident:
{json.dumps(request_dict.get('incident', {}), indent=2)}

Current Tasks:
{json.dumps(request_dict.get('tasks', []), indent=2)}

Evidence Gathered:
{json.dumps(request_dict.get('evidence', []), indent=2)}

Critic Results:
{json.dumps(request_dict.get('criticResults', []), indent=2)}

Available Agents: {json.dumps(request_dict.get('availableAgents', []))}
Available Tools: {json.dumps(request_dict.get('availableTools', []))}
Constraints: {json.dumps(request_dict.get('constraints', {}))}

Analyze current state and formulate the next investigation plan."""

def format_critic_prompt(request_dict: Dict[str, Any]) -> str:
    return f"""Incident:
{json.dumps(request_dict.get('incident', {}), indent=2)}

Task:
{json.dumps(request_dict.get('task', {}), indent=2)}

Agent Output & Claim:
{json.dumps(request_dict.get('agentOutput', {}), indent=2)}

Evidence Context:
{json.dumps(request_dict.get('evidence', []), indent=2)}

Tool Results:
{json.dumps(request_dict.get('toolResults', []), indent=2)}

Evaluate agent finding rigorously."""

def format_evidence_prompt(request_dict: Dict[str, Any]) -> str:
    return f"""Tool Invoked: {request_dict.get('toolName')}
Task Context: {json.dumps(request_dict.get('task', {}), indent=2)}

Raw Tool API Response:
{json.dumps(request_dict.get('toolResponse', {}), indent=2)}

Extract all factual, grounded claims."""
