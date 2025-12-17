#!/usr/bin/env python3
"""
Convert Keras captcha model to ONNX format for Node.js inference.
No training required - just format conversion.

Usage:
    python convert_to_onnx.py

Output:
    model.onnx - ONNX model for onnxruntime-node
"""

import tf2onnx
import tensorflow as tf
import numpy as np

MODEL_PATH = "model.keras"
OUTPUT_PATH = "model.onnx"
IMAGE_SIZE = (25, 65, 1)  # height, width, channels

def convert_model():
    print(f"Loading Keras model from {MODEL_PATH}...")
    model = tf.keras.models.load_model(MODEL_PATH)

    print("Model summary:")
    model.summary()

    print(f"\nInput shape: {model.input_shape}")
    print(f"Output names: {[output.name for output in model.outputs]}")

    input_signature = [tf.TensorSpec(shape=(1, *IMAGE_SIZE), dtype=tf.float32, name="input")]

    print(f"\nConverting to ONNX with input signature: {input_signature}...")

    onnx_model, _ = tf2onnx.convert.from_keras(
        model,
        input_signature=input_signature,
        opset=13,
        output_path=OUTPUT_PATH
    )

    print(f"\nONNX model saved to {OUTPUT_PATH}")

    print("\nVerifying ONNX model...")
    import onnxruntime as ort

    session = ort.InferenceSession(OUTPUT_PATH)

    print(f"Input name: {session.get_inputs()[0].name}")
    print(f"Input shape: {session.get_inputs()[0].shape}")
    print(f"Output names: {[o.name for o in session.get_outputs()]}")

    test_input = np.random.rand(1, *IMAGE_SIZE).astype(np.float32)
    outputs = session.run(None, {session.get_inputs()[0].name: test_input})

    print(f"\nTest inference successful!")
    print(f"Number of outputs: {len(outputs)}")
    for i, output in enumerate(outputs):
        print(f"  Output {i}: shape={output.shape}")

    print(f"\n✅ Conversion complete! Copy {OUTPUT_PATH} to cloudflare-bypass-proxy/")

if __name__ == "__main__":
    convert_model()
