FROM python:3.12-slim

WORKDIR /app

# Create non-root system user
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN chown -R appuser:appgroup /app
USER appuser

ENV AI_SERVICE_HOST=0.0.0.0
ENV AI_SERVICE_PORT=8000

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
