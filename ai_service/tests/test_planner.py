import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app

@pytest.mark.asyncio
async def test_planner_produces_valid_structured_plan():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "investigationId": "a1111111-1111-1111-1111-111111111111",
            "incident": {
                "title": "Checkout error rate surge",
                "serviceName": "checkout-service",
                "severity": "P1"
            },
            "tasks": [],
            "evidence": [],
            "criticResults": [],
            "constraints": {"maxTasks": 10}
        }
        response = await client.post("/ai/v1/plan", json=payload, headers={"X-Correlation-ID": "test-corr-123"})
        assert response.status_code == 200
        data = response.json()
        assert data["investigationId"] == "a1111111-1111-1111-1111-111111111111"
        assert data["decision"] == "CONTINUE"
        assert len(data["actions"]) >= 1
        assert data["actions"][0]["agentType"] == "incident-triage-agent"
        assert data["actions"][0]["toolRequirement"] == "NO_TOOL_REQUIRED"
        assert response.headers.get("X-Correlation-ID") == "test-corr-123"

@pytest.mark.asyncio
async def test_planner_chooses_valid_tool_when_triage_complete():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "investigationId": "a2222222-2222-2222-2222-222222222222",
            "incident": {"title": "Checkout error spike", "serviceName": "checkout-service"},
            "tasks": [
                {"taskType": "TRIAGE", "status": "COMPLETED", "assignedAgent": "incident-triage-agent"}
            ],
            "evidence": [{"claim": "Checkout 5xx rate jumped to 18%"}],
            "criticResults": [{"decision": "ACCEPT"}],
            "constraints": {"maxTasks": 10}
        }
        response = await client.post("/ai/v1/plan", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["decision"] == "CONTINUE"
        action_tools = [a["suggestedTool"] for a in data["actions"] if a.get("suggestedTool")]
        assert "github.get_recent_commits" in action_tools

@pytest.mark.asyncio
async def test_planner_reconciliation_on_critic_rejection():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "investigationId": "a3333333-3333-3333-3333-333333333333",
            "incident": {"title": "Checkout degradation"},
            "tasks": [
                {"taskType": "CHANGE_ANALYSIS", "status": "FAILED"}
            ],
            "criticResults": [
                {"decision": "REJECT", "reasons": ["UNSUPPORTED_CAUSAL_CLAIM"]}
            ],
            "constraints": {"maxTasks": 10}
        }
        response = await client.post("/ai/v1/plan", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["decision"] == "REPLAN"
        assert len(data["actions"]) >= 1
        assert data["actions"][0]["suggestedTool"] == "github.get_pull_requests"

@pytest.mark.asyncio
async def test_planner_stops_when_task_limit_reached():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "investigationId": "a4444444-4444-4444-4444-444444444444",
            "incident": {"title": "Checkout degradation"},
            "tasks": [{"id": f"task-{i}"} for i in range(5)],
            "constraints": {"maxTasks": 5}
        }
        response = await client.post("/ai/v1/plan", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["decision"] == "STOP_LIMIT_REACHED"
        assert data["isEscalated"] is True
