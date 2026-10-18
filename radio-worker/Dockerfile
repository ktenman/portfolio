FROM ghcr.io/astral-sh/uv:0.13.1 AS uv

FROM python:3.12-slim-trixie AS base

ENV UV_NO_CACHE=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 1000 --create-home worker \
    && mkdir /recordings /model \
    && chown worker:worker /recordings /model

WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN --mount=from=uv,source=/uv,target=/bin/uv uv sync --locked --no-dev --no-install-project
COPY src ./src
RUN --mount=from=uv,source=/uv,target=/bin/uv uv sync --locked --no-dev && .venv/bin/python -c "import faster_whisper"

FROM base AS test
RUN --mount=from=uv,source=/uv,target=/bin/uv apt-get update \
    && apt-get install -y --no-install-recommends procps \
    && rm -rf /var/lib/apt/lists/* \
    && uv sync --locked
COPY tests ./tests
USER worker
CMD [".venv/bin/pytest", "-p", "no:cacheprovider"]

FROM base
USER worker
ENTRYPOINT ["/app/.venv/bin/radio-worker"]
