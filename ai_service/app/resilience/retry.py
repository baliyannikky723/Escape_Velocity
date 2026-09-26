import asyncio
import time
from typing import Callable, TypeVar, Any
from app.core.exceptions import LlmProviderError, LlmTimeoutError
from app.core.logging import get_logger

logger = get_logger("resilience.retry")
T = TypeVar("T")

async def execute_with_retry(
    func: Callable[[], Any],
    max_retries: int = 2,
    base_delay_seconds: float = 0.5,
    max_delay_seconds: float = 5.0,
    correlation_id: str = "none"
) -> T:
    last_exception = None
    for attempt in range(1, max_retries + 2):
        try:
            return await func()
        except (LlmTimeoutError, LlmProviderError, Exception) as ex:
            last_exception = ex
            if attempt > max_retries:
                logger.warning(
                    f"LLM retry budget exhausted after {attempt-1} retries. Raising error: {ex}",
                    extra={"correlation_id": correlation_id}
                )
                raise ex

            delay = min(base_delay_seconds * (2 ** (attempt - 1)), max_delay_seconds)
            logger.info(
                f"LLM call failed (attempt {attempt}/{max_retries+1}): {ex}. Retrying in {delay:.2f}s...",
                extra={"correlation_id": correlation_id}
            )
            await asyncio.sleep(delay)

    raise last_exception if last_exception else RuntimeError("Retry failed without exception")
