import logging
import sys

def setup_logging():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s (correlationId=%(correlation_id)s): %(message)s",
        handlers=[logging.StreamHandler(sys.stdout)]
    )

class CorrelationAdapter(logging.LoggerAdapter):
    def process(self, msg, kwargs):
        correlation_id = kwargs.pop("correlation_id", self.extra.get("correlation_id", "none") if self.extra else "none")
        extra = kwargs.get("extra", {})
        extra["correlation_id"] = correlation_id
        kwargs["extra"] = extra
        return msg, kwargs

def get_logger(name: str) -> logging.LoggerAdapter:
    logger = logging.getLogger(name)
    return CorrelationAdapter(logger, {"correlation_id": "none"})
