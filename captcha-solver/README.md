# CAPTCHA Solver

Deep learning-based CAPTCHA recognition service built with TensorFlow and FastAPI. Originally developed for auto24.ee (Estonian car marketplace) CAPTCHAs.

## Overview

This service provides a REST API for solving 4-character alphanumeric CAPTCHAs using a convolutional neural network (CNN). The model achieves **99% accuracy** on live production data.

## Quick Start

### Using uv (Recommended)

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh

uv sync

uv run uvicorn main:app --host 0.0.0.0 --port 8000
```

### Using Docker

```bash
docker build -t captcha-solver .
docker run -p 8000:8000 captcha-solver
```

## API Endpoints

### Health Check

```bash
curl http://localhost:8000/health
```

### Predict CAPTCHA

```bash
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "uuid": "test-123",
    "imageBase64": "<base64-encoded-png>"
  }'
```

Response:

```json
{
  "uuid": "test-123",
  "prediction": "ABC7",
  "confidence": 0.95,
  "processingTimeMs": 45.2
}
```

## Model Architecture

The CNN model processes 65x25 grayscale images and outputs predictions for 4 character positions simultaneously using a multi-output architecture.

**Character Set:** `2345789ABCDEFHKLMNPRTUVWXYZ` (27 characters)

The character set excludes visually ambiguous characters:
- `0` excluded (confused with `O`)
- `1` excluded (confused with `I` and `L`)
- `6` excluded (confused with `G`)
- `I` excluded (confused with `1`)
- `O` excluded (confused with `0`)
- `S` excluded (confused with `5`)

**Input:** 65x25 grayscale PNG images normalized to [0, 1]

**Output:** 4 parallel softmax classifiers (one per character position)

## Training Methodology

### Phase 1: Initial Data Collection (~2,000 images)

1. **Image Acquisition**: Used a Cloudflare bypass proxy (curl-impersonate) to capture CAPTCHA images from auto24.ee

2. **AI-Assisted Labeling**: Sent base64-encoded images to OpenRouter API with Claude Opus 4.5 vision model using the prompt:
   > "Read this CAPTCHA. Valid chars: 2345789ABCDEFHKLMNPRTUVWXYZ only. Common confusions: 8/B, 3/E, 5/S, 0/O - pick the valid one. Output ONLY the 4 characters."

3. **Verification**: Each AI prediction was tested against the actual auto24.ee website to confirm correctness. Only verified correct predictions were saved to the training dataset.

4. **Label Quality**: The AI-generated labels achieved approximately **22% accuracy** on verification, meaning ~440 correctly labeled images from 2,000 attempts.

### Phase 2: Initial Model Training

1. **Data Split**: 80% training / 10% validation / 10% test
2. **Architecture**: Multi-output CNN with shared convolutional layers
3. **Result**: Model achieved **~87% accuracy** on live production data

### Phase 3: Iterative Improvement (~16,000 images)

1. **Production Deployment**: Deployed the 87%-accurate model to production

2. **Data Collection**: Collected 16,000+ additional images with model-assisted labeling, verified against the live website

3. **Retraining**: Retrained the model on the expanded dataset

4. **Final Result**: **99% accuracy** on live production data

### Key Insights

- **AI labeling bootstrap**: Even with 22% label accuracy, the model learned enough patterns to bootstrap itself to 87% accuracy
- **Verification is critical**: Testing each prediction against the actual website ensured high-quality training data
- **Iterative improvement**: Using the model in production to collect more accurately-labeled data created a positive feedback loop
- **Character set optimization**: Excluding ambiguous characters (0/O, 1/I/L, S/5) simplified the classification task

## Development

### Running Tests

```bash
uv sync --all-extras

uv run pytest

uv run pytest -m integration
```

### Linting

```bash
uv run ruff check .
uv run ruff format .
```

### Local Development

```bash
uv run uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

## Project Structure

```
captcha-solver/
├── main.py              # FastAPI application
├── model.keras          # Trained TensorFlow model
├── pyproject.toml       # Project configuration
├── Dockerfile           # Multi-stage Docker build with uv
├── test_images/         # Test CAPTCHA images
└── test_captcha_integration.py
```

## Requirements

- Python 3.11+
- TensorFlow 2.18+
- ~500MB disk space (model + dependencies)

## License

MIT
