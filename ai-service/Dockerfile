# Docker 构建
FROM python:3.11-slim

WORKDIR /app

RUN pip install uv

COPY pyproject.toml uv.lock* ./
RUN uv sync --frozen --no-dev

COPY src/ ./src/
COPY prompts/ ./prompts/
COPY config/ ./config/

EXPOSE 8001

CMD ["uv", "run", "uvicorn", "src.ai_service.main:app", "--host", "0.0.0.0", "--port", "8001"]
