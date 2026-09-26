import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app

@pytest.mark.asyncio
async def test_critic_accepts_grounded_claim():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "investigationId": "b1111111-1111-1111-1111-111111111111",
            "task": {"taskType": "CHANGE_ANALYSIS"},
            "agentOutput": {
                "claim": "Temporal correlation verified between deployment and error rate surge",
                "confidence": 0.90
            },
            "evidence": [
                {"claim": "Commit abc1234 deployed 8m before error spike"}
            ]
        }
        response = await client.post("/ai/v1/critic", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["decision"] == "ACCEPT"
        assert data["requiresHumanReview"] is False

@pytest.mark.asyncio
async def test_critic_rejects_unsupported_causal_claim():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "investigationId": "b2222222-2222-2222-2222-222222222222",
            "task": {"taskType": "CHANGE_ANALYSIS"},
            "agentOutput": {
                "claim": "Commit abc1234 definitely caused the incident without any stack traces."
            },
            "evidence": []
        }
        response = await client.post("/ai/v1/critic", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["decision"] == "REJECT"
        assert "UNSUPPORTED_CAUSAL_CLAIM" in data["reason"]
        assert len(data["missingEvidence"]) >= 1

@pytest.mark.asyncio
async def test_critic_returns_inconclusive_when_evidence_empty():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "investigationId": "b3333333-3333-3333-3333-333333333333",
            "task": {"taskType": "CHANGE_ANALYSIS"},
            "agentOutput": {"claim": "No evidence was found and response was empty."},
            "evidence": []
        }
        response = await client.post("/ai/v1/critic", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["decision"] == "INCONCLUSIVE"

@pytest.mark.asyncio
async def test_critic_sensitive_action_requires_human_approval():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "investigationId": "b4444444-4444-4444-4444-444444444444",
            "task": {"taskType": "PRODUCTION_REMEDIATION"},
            "agentOutput": {
                "claim": "Attempting automatic ROLLBACK_PRODUCTION of payment service.",
                "proposedAction": "ROLLBACK_PRODUCTION"
            }
        }
        response = await client.post("/ai/v1/critic", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert data["decision"] == "HUMAN_APPROVAL_REQUIRED"
        assert data["requiresHumanReview"] is True
        assert data["proposedAction"] == "ROLLBACK_PRODUCTION"
        assert "APPROVE_ACTION" in data["allowedHumanActions"]
