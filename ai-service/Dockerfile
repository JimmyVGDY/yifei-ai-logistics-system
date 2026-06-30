# Docker 构建
FROM python:3.11-slim AS builder

WORKDIR /app

RUN pip install uv

COPY pyproject.toml uv.lock* ./
COPY src/ ./src/
RUN uv sync --frozen --no-dev

FROM python:3.11-slim

WORKDIR /app

RUN groupadd -r appuser \
    && useradd -r -g appuser -d /app -s /usr/sbin/nologin appuser

COPY --from=builder /app/.venv ./.venv
COPY src/ ./src/
COPY prompts/ ./prompts/
COPY config/ ./config/
RUN chown -R appuser:appuser /app

EXPOSE 8001
USER appuser

ENV PATH="/app/.venv/bin:${PATH}" \
    PYTHONPATH="/app/src"

HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=30s \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8001/health', timeout=3).read()" || exit 1

CMD ["uvicorn", "ai_service.main:app", "--host", "0.0.0.0", "--port", "8001"]
