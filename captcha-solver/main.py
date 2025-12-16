import base64
import io
import logging
import os
import re  # Added missing import
import time
from contextvars import ContextVar
from typing import Literal
from uuid import uuid4

import numpy as np
import tensorflow as tf
from fastapi import FastAPI, HTTPException, Request
from PIL import Image
from pydantic import BaseModel, Field

# Configure logging
request_id_ctx = ContextVar("request_id", default="-")
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(request_id)s] %(levelname)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger(__name__)
logger.addFilter(lambda record: setattr(record, "request_id", request_id_ctx.get()) or True)

EXAMPLE_BASE64 = """
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
""".strip()


# Models
class PredictionRequest(BaseModel):
    """
    Request model for CAPTCHA prediction
    """

    uuid: str = Field(
        ...,
        description="Unique identifier for the request",
        example="123e4567-e89b-12d3-a456-426614174000",
    )
    imageBase64: str = Field(
        ..., description="Base64 encoded PNG image data without any prefix", example=EXAMPLE_BASE64
    )

    class Config:
        json_schema_extra = {
            "example": {
                "uuid": "123e4567-e89b-12d3-a456-426614174000",
                "imageBase64": EXAMPLE_BASE64,
            }
        }


class PredictionResponse(BaseModel):
    """
    Response model for CAPTCHA prediction
    """

    uuid: str = Field(
        ...,
        description="Unique identifier matching the request",
        example="123e4567-e89b-12d3-a456-426614174000",
    )
    prediction: str = Field(..., description="Predicted CAPTCHA text", example="ABC123")
    confidence: float = Field(
        ..., description="Confidence score of the prediction (0-1)", ge=0, le=1, example=0.95
    )
    processingTimeMs: float = Field(
        ..., description="Processing time in milliseconds", example=123.45
    )


class HealthResponse(BaseModel):
    """
    Response model for health check
    """

    status: Literal["healthy", "unhealthy"] = Field(..., description="Current service status")
    model_loaded: bool = Field(..., description="Whether the ML model is loaded")


# Constants
CHARS = "2345789ABCDEFHKLMNPRTUVWXYZ"
IMAGE_SIZE = (65, 25)
MODEL_PATH = "model.keras"

# Initialize FastAPI with metadata
app = FastAPI(
    title="CAPTCHA Recognition API",
    description="""
    This API provides CAPTCHA recognition capabilities using deep learning.

    ## Features
    * CAPTCHA image recognition
    * Health check endpoint
    * Confidence scores for predictions

    ## Usage
    Send a base64 encoded image to the /predict endpoint to get the CAPTCHA text.
    """,
    version="1.0.0",
    contact={"name": "Konstantin Tenman"},
)

model = None


# Middleware
@app.middleware("http")
async def logging_middleware(request: Request, call_next):
    request_id_ctx.set(str(uuid4()))
    start_time = time.time()
    logger.info(f"Request {request.method} {request.url.path} started")

    try:
        response = await call_next(request)
        duration = (time.time() - start_time) * 1000
        logger.info(f"Request completed in {duration:.2f}ms")
        return response
    except Exception as e:
        logger.error(f"Request failed: {str(e)}", exc_info=True)
        raise
    finally:
        request_id_ctx.set("-")


def clean_base64(base64_string: str) -> str:
    """Remove data URI scheme if present and clean the base64 string"""
    # Remove data URI scheme if present
    match = re.match(r"data:image/[a-zA-Z]+;base64,(.+)", base64_string)
    if match:
        base64_string = match.group(1)
    # Remove any whitespace
    return base64_string.strip()


def process_image(image_bytes):
    try:
        # Open image from bytes
        img = Image.open(io.BytesIO(image_bytes))

        # Convert to grayscale if not already
        if img.mode != "L":
            img = img.convert("L")

        # Resize to expected dimensions
        img = img.resize(IMAGE_SIZE, Image.Resampling.LANCZOS)

        # Convert to numpy array and normalize
        img_array = np.array(img, dtype=np.float32) / 255.0

        # Add channel and batch dimensions
        img_array = np.expand_dims(np.expand_dims(img_array, axis=-1), axis=0)

        return img_array
    except Exception as e:
        logger.error(f"Image processing error: {str(e)}")
        raise HTTPException(status_code=400, detail=f"Image processing failed: {str(e)}")


def get_prediction(prediction):
    text = ""
    confidence = 1.0
    for i in range(4):
        char_pred = prediction[f"char_{i}"][0]
        prob = np.max(char_pred)
        idx = np.argmax(char_pred)
        if idx >= len(CHARS):
            logger.warning(f"Character index {idx} out of bounds for position {i}, using modulo")
            idx = idx % len(CHARS)
        text += CHARS[idx]
        confidence *= prob
    return text, confidence


# Endpoints
@app.on_event("startup")
async def startup():
    global model
    request_id_ctx.set("startup")
    logger.info("Starting application")

    try:
        if not os.path.exists(MODEL_PATH):
            raise RuntimeError(f"Model file not found: {MODEL_PATH}")
        model = tf.keras.models.load_model(MODEL_PATH)
        logger.info("Model loaded successfully")
    except Exception as e:
        logger.error(f"Failed to load model: {str(e)}")
        raise
    finally:
        request_id_ctx.set("-")


@app.get("/health")
async def health():
    return {"status": "healthy", "model_loaded": model is not None}


@app.post("/predict")
async def predict(request: PredictionRequest):
    if not model:
        raise HTTPException(status_code=503, detail="Model not loaded")

    start = time.time()
    logger.info("Prediction request received")

    try:
        # Clean and decode base64 image
        clean_b64 = clean_base64(request.imageBase64)
        try:
            image_bytes = base64.b64decode(clean_b64)
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Invalid base64 data: {str(e)}")

        # Process image
        processed_image = process_image(image_bytes)

        # Make prediction
        text, confidence = get_prediction(model.predict(processed_image, verbose=0))

        return {
            "uuid": request.uuid,
            "prediction": text,
            "confidence": float(confidence),
            "processingTimeMs": (time.time() - start) * 1000,
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Prediction failed: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))
