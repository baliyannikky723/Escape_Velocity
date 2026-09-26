import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app

@pytest.mark.asyncio
async def test_evidence_extraction_grounded():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        payload = {
            "toolName": "github.get_recent_commits",
            "task": {"taskType": "CHANGE_ANALYSIS"},
            "toolResponse": {
                "commits": [
                    {
                        "sha": "abc1234",
                        "commit": {
                            "author": {"name": "SRE Dev", "email": "dev@incidentmind.io"},
                            "message": "fix: update payment gateway timeout configuration"
                        }
                    }
                ]
            }
        }
        response = await client.post("/ai/v1/evidence/analyze", json=payload)
        assert response.status_code == 200
        data = response.json()
        assert len(data["claims"]) >= 1
        claim = data["claims"][0]
        assert claim["type"] == "FACT"
        assert claim["source"] == "github.get_recent_commits"
        assert claim["sourceReference"] == "abc1234"
        assert claim["confidence"] >= 0.80
