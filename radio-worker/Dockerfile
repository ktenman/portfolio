FROM python:3.12-slim-trixie

COPY --from=ghcr.io/astral-sh/uv:0.13.1 /uv /usr/local/bin/uv

ENV UV_NO_CACHE=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg procps \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --uid 1000 --create-home worker \
    && mkdir /recordings \
    && chown worker:worker /recordings

WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --locked --no-dev --no-install-project
COPY src ./src
RUN uv sync --locked --no-dev && .venv/bin/python -c "import faster_whisper"

USER worker
ENTRYPOINT ["/app/.venv/bin/radio-worker"]
