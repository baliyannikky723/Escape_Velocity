import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.observability.metrics import metrics_tracker
from app.resilience.retry import execute_with_retry
from app.core.exceptions import LlmTimeoutError

@pytest.mark.asyncio
async def test_resilience_retry_exhaustion():
    call_count = 0
    async def _failing_call():
        nonlocal call_count
        call_count += 1
        raise LlmTimeoutError("Simulated LLM Gateway Timeout")

    with pytest.raises(LlmTimeoutError):
        await execute_with_retry(_failing_call, max_retries=2, base_delay_seconds=0.01)

    assert call_count == 3  # 1 initial + 2 retries

@pytest.mark.asyncio
async def test_metrics_endpoint_tracking():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        # Perform a health check
        health_resp = await client.get("/ai/v1/health")
        assert health_resp.status_code == 200
        assert health_resp.json()["status"] == "UP"

        # Get metrics
        metrics_resp = await client.get("/ai/v1/metrics")
        assert metrics_resp.status_code == 200
        data = metrics_resp.json()
        assert "successfulCalls" in data
        assert "totalLatencyMs" in data
        assert "plannerLlmCalls" in data
