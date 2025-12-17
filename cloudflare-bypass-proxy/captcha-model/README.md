# CAPTCHA Model

Deep learning model for solving auto24.ee CAPTCHAs. Achieves **99% accuracy** on production data.

## Files

- `model.keras` - TensorFlow/Keras trained model (source of truth)
- `model.onnx` - ONNX format for Node.js inference (generated from keras)
- `convert_to_onnx.py` - Script to convert keras model to ONNX

## Model Architecture

Multi-output CNN processing 65x25 grayscale images, predicting 4 characters simultaneously.

**Character Set:** `2345789ABCDEFHKLMNPRTUVWXYZ` (27 characters)

Excluded ambiguous characters: `0` (vs `O`), `1` (vs `I`/`L`), `6` (vs `G`), `I`, `O`, `S` (vs `5`)

## Training Methodology

### Phase 1: Initial Data Collection (2,011 images)

1. **Image Acquisition**: Cloudflare bypass proxy (curl-impersonate) captured CAPTCHAs from auto24.ee

2. **AI-Assisted Labeling**: Claude Opus 4.5 vision model via OpenRouter API:

   > "Read this CAPTCHA. Valid chars: 2345789ABCDEFHKLMNPRTUVWXYZ only. Common confusions: 8/B, 3/E, 5/S, 0/O - pick the valid one. Output ONLY the 4 characters."

3. **Verification**: Each AI prediction tested against auto24.ee website. Only verified correct predictions saved.

4. **Label Quality**: ~22% AI accuracy yielded 2,011 correct labels from 9,140 attempts (~$8 OpenRouter cost)

### Phase 2: Initial Model Training

- Data Split: 80% training / 10% validation / 10% test
- Result: ~87% accuracy on live production data

### Phase 3: Iterative Improvement (~16,000 images)

1. Deployed 87%-accurate model to production
2. Collected 16,000+ images with model-assisted labeling, verified against live website
3. Retrained on expanded dataset
4. **Final Result: 99% accuracy**

## Why ONNX Instead of Keras?

We use ONNX format for production inference instead of the original Keras model:

| Aspect                | Keras (.keras)             | ONNX (.onnx)                   |
| --------------------- | -------------------------- | ------------------------------ |
| **Runtime**           | TensorFlow (Python)        | onnxruntime-node (Node.js)     |
| **Docker Image Size** | ~2GB (TensorFlow + Python) | ~150MB (Node.js + onnxruntime) |
| **Cold Start**        | ~5-10 seconds              | ~500ms                         |
| **Inference Speed**   | ~50ms                      | ~5ms                           |
| **Memory Usage**      | ~500MB                     | ~100MB                         |
| **Integration**       | Requires Python service    | Native Node.js                 |

**Key Benefits:**

- **No Python dependency**: Runs directly in the Node.js cloudflare-bypass-proxy
- **10x smaller Docker image**: 150MB vs 2GB
- **10x faster inference**: 5ms vs 50ms per prediction
- **Single service**: No separate captcha-solver container needed
- **Simpler deployment**: One less service to manage in production

The Keras model remains the source of truth for training/retraining. ONNX is purely for optimized inference.

## Converting to ONNX

Requirements:

```bash
pip install tensorflow tf2onnx onnxruntime
```

Convert:

```bash
cd captcha-model/
python convert_to_onnx.py
cp model.onnx ../  # Copy to project root for Docker
```

## Key Insights

- **AI labeling bootstrap**: Even 22% label accuracy bootstrapped model to 87%
- **Verification is critical**: Testing against actual website ensured data quality
- **Iterative improvement**: Production model collecting verified data created positive feedback loop
- **Character set optimization**: Excluding ambiguous characters simplified classification
