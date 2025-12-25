#!/usr/bin/env python3
import requests
import base64
import sys
import re
from pathlib import Path

OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen3-vl:32b"

ALLOWED_CHARS = set('2345789ABCDEFHKLMNPRTUVWXYZ')
CAPTCHA_LENGTH = 4


def create_test_image():
    from PIL import Image, ImageDraw, ImageFont
    import io

    img = Image.new('RGB', (200, 80), color='white')
    draw = ImageDraw.Draw(img)

    try:
        font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 40)
    except:
        font = ImageFont.load_default()

    draw.text((30, 15), "RC22", fill='black', font=font)

    buffer = io.BytesIO()
    img.save(buffer, format='PNG')
    return buffer.getvalue()


def extract_captcha_from_response(response_text):
    response_upper = response_text.upper()

    quoted = re.findall(r'"([A-Z0-9]{4})"', response_upper)
    if quoted:
        for q in quoted:
            cleaned = ''.join(c for c in q if c in ALLOWED_CHARS)
            if len(cleaned) == CAPTCHA_LENGTH:
                return cleaned

    words = re.findall(r'\b[A-Z0-9]{4}\b', response_upper)
    for word in words:
        cleaned = ''.join(c for c in word if c in ALLOWED_CHARS)
        if len(cleaned) == CAPTCHA_LENGTH:
            return cleaned

    all_valid = ''.join(c for c in response_upper if c in ALLOWED_CHARS)
    if len(all_valid) >= CAPTCHA_LENGTH:
        return all_valid[:CAPTCHA_LENGTH]

    return None


def solve_captcha(image_base64):
    prompt = "Read the text in this CAPTCHA image. What are the 4 characters shown?"

    payload = {
        "model": MODEL,
        "prompt": prompt,
        "images": [image_base64],
        "stream": False,
        "options": {
            "temperature": 0.1,
            "num_predict": 100
        }
    }

    response = requests.post(OLLAMA_URL, json=payload, timeout=120)
    response.raise_for_status()
    result = response.json()

    raw_response = result.get("response", "").strip()
    return raw_response, extract_captcha_from_response(raw_response)


def test_ollama_vision():
    print(f"Testing Ollama Vision with model: {MODEL}")
    print("=" * 50)

    print("\nChecking if Ollama is running...")
    try:
        response = requests.get("http://localhost:11434/api/tags", timeout=5)
        models = [m['name'] for m in response.json().get('models', [])]
        print(f"Available models: {', '.join(models)}")
    except Exception as e:
        print(f"Error connecting to Ollama: {e}")
        return False

    print("\nCreating test image with text 'RC22'...")
    try:
        image_bytes = create_test_image()
        image_base64 = base64.b64encode(image_bytes).decode('utf-8')
        print(f"Image created: {len(image_bytes)} bytes")

        Path("/tmp/test_captcha.png").write_bytes(image_bytes)
        print("Saved to /tmp/test_captcha.png")
    except ImportError:
        print("PIL not installed. Run: pip3 install Pillow")
        return False

    print("\nSending to Ollama...")
    try:
        raw_response, cleaned = solve_captcha(image_base64)
        print(f"Raw response: '{raw_response}'")
        print(f"Extracted: '{cleaned}'")

        if cleaned == "RC22":
            print("\n SUCCESS! Model correctly identified 'RC22'")
            return True
        else:
            print(f"\n MISMATCH: Expected 'RC22', got '{cleaned}'")
            return False

    except requests.exceptions.Timeout:
        print("Timeout - model may be loading")
        return False
    except Exception as e:
        print(f"Error: {e}")
        return False


def test_with_image(image_path):
    print(f"Testing with image: {image_path}")
    print("=" * 50)

    try:
        image_bytes = Path(image_path).read_bytes()
        image_base64 = base64.b64encode(image_bytes).decode('utf-8')
        print(f"Image loaded: {len(image_bytes)} bytes")
    except Exception as e:
        print(f"Error loading image: {e}")
        return None

    print("\nSending to Ollama...")
    try:
        raw_response, cleaned = solve_captcha(image_base64)
        print(f"Raw response: '{raw_response}'")
        print(f"Predicted: '{cleaned}'")
        return cleaned
    except Exception as e:
        print(f"Error: {e}")
        return None


if __name__ == "__main__":
    if len(sys.argv) > 1:
        test_with_image(sys.argv[1])
    else:
        success = test_ollama_vision()
        sys.exit(0 if success else 1)
