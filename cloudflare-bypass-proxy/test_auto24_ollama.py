#!/usr/bin/env python3
import requests
import json
import sys
import os
import base64
from pathlib import Path

PROXY_URL = "http://localhost:3000"
OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen3-vl:32b"
REG_NR = "463BKH"
MAX_ATTEMPTS = 10
OUTPUT_DIR = Path(__file__).parent / "captcha_training_data"

ALLOWED_CHARS = set('2345789ABCDEFHKLMNPRTUVWXYZ')
CAPTCHA_LENGTH = 4


def extract_captcha_from_response(response_text):
    import re
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


def solve_captcha_with_ollama(base64_image):
    prompt = "Read the text in this CAPTCHA image. What are the 4 characters shown?"

    payload = {
        "model": MODEL,
        "prompt": prompt,
        "images": [base64_image],
        "stream": False,
        "options": {
            "temperature": 0.1,
            "num_predict": 100
        }
    }

    try:
        response = requests.post(OLLAMA_URL, json=payload, timeout=120)
        response.raise_for_status()
        result = response.json()
        raw_response = result.get("response", "").strip()
        cleaned = extract_captcha_from_response(raw_response)

        print(f"Ollama response: '{raw_response}' -> extracted: '{cleaned}'")

        if cleaned and len(cleaned) == CAPTCHA_LENGTH:
            return cleaned

        print(f"Invalid extraction from response")
        return None
    except requests.exceptions.Timeout:
        print("Ollama timeout - model may be loading")
        return None
    except Exception as e:
        print(f"Ollama error: {e}")
        return None


def save_successful_captcha(captcha_image, solution):
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    filepath = OUTPUT_DIR / f"{solution}.png"
    counter = 1
    while filepath.exists():
        filepath = OUTPUT_DIR / f"{solution}_{counter}.png"
        counter += 1

    image_data = base64.b64decode(captcha_image)
    filepath.write_bytes(image_data)
    print(f"Saved training data: {filepath}")


def test_auto24_flow():
    print(f"\n{'='*60}")
    print(f"Testing Auto24 with registration: {REG_NR}")
    print(f"Using Ollama model: {MODEL}")
    print(f"{'='*60}\n")

    for attempt in range(MAX_ATTEMPTS):
        print(f"\n--- Attempt {attempt + 1} of {MAX_ATTEMPTS} ---\n")

        print("Step 1: Get CAPTCHA...")
        try:
            response = requests.post(
                f"{PROXY_URL}/auto24/captcha",
                json={"regNr": REG_NR},
                timeout=60
            )
        except requests.exceptions.ConnectionError:
            print(f"ERROR: Cannot connect to proxy at {PROXY_URL}")
            print("Make sure cloudflare-bypass-proxy is running")
            return False

        if response.status_code != 200:
            print(f"ERROR: Failed to get CAPTCHA: {response.status_code}")
            print(response.text)
            continue

        data = response.json()

        if data.get("status") == "success":
            print(f"No CAPTCHA needed! Price: {data.get('price')}")
            print(f"Car info: {data.get('carInfo')}")
            return True

        if data.get("status") == "error":
            print(f"ERROR: {data.get('error')}")
            continue

        session_id = data.get("sessionId")
        captcha_image = data.get("captchaImage")

        print(f"Session ID: {session_id}")
        print(f"CAPTCHA URL: {data.get('captchaUrl')}")

        temp_file = "/tmp/captcha_ollama.png"
        with open(temp_file, "wb") as f:
            f.write(base64.b64decode(captcha_image))
        print(f"Saved CAPTCHA to {temp_file}")

        print("\nStep 2: Solve CAPTCHA with Ollama...")
        solution = solve_captcha_with_ollama(captcha_image)

        if not solution:
            print("ERROR: Could not solve CAPTCHA")
            continue

        print(f"\nStep 3: Submit CAPTCHA solution: '{solution}'...")
        response = requests.post(
            f"{PROXY_URL}/auto24/submit",
            json={"sessionId": session_id, "solution": solution},
            timeout=60
        )

        if response.status_code != 200:
            print(f"ERROR: Failed to submit CAPTCHA: {response.status_code}")
            print(response.text)
            continue

        result = response.json()
        print(f"\nResult: {json.dumps(result, indent=2)}")

        if result.get("status") == "success":
            print(f"\n SUCCESS! Price: {result.get('price')}")
            print(f"Car info: {result.get('carInfo')}")
            save_successful_captcha(captcha_image, solution)
            return True
        elif result.get("status") == "captcha_failed":
            print(f"\n CAPTCHA incorrect: '{solution}'")
            continue
        elif result.get("status") == "not_found":
            print("\n Vehicle not found in registry")
            return True
        else:
            print(f"\n Unexpected response: {result.get('status')}")
            continue

    print(f"\nFailed after {MAX_ATTEMPTS} attempts")
    return False


def collect_training_data(target_count=100):
    print(f"\n{'='*60}")
    print(f"Collecting training data (target: {target_count})")
    print(f"Using Ollama model: {MODEL}")
    print(f"Output directory: {OUTPUT_DIR}")
    print(f"{'='*60}\n")

    success_count = 0
    attempt_count = 0

    while success_count < target_count:
        attempt_count += 1
        print(f"\n--- Attempt {attempt_count} (successes: {success_count}/{target_count}) ---")

        try:
            response = requests.post(
                f"{PROXY_URL}/auto24/captcha",
                json={"regNr": REG_NR},
                timeout=60
            )
        except requests.exceptions.ConnectionError:
            print(f"ERROR: Cannot connect to proxy at {PROXY_URL}")
            return success_count

        if response.status_code != 200:
            print(f"Failed to get CAPTCHA: {response.status_code}")
            continue

        data = response.json()
        if data.get("status") != "captcha_required":
            continue

        session_id = data.get("sessionId")
        captcha_image = data.get("captchaImage")

        solution = solve_captcha_with_ollama(captcha_image)
        if not solution:
            continue

        response = requests.post(
            f"{PROXY_URL}/auto24/submit",
            json={"sessionId": session_id, "solution": solution},
            timeout=60
        )

        if response.status_code != 200:
            continue

        result = response.json()

        if result.get("status") in ["success", "not_found"]:
            print(f"SUCCESS: '{solution}' is correct!")
            save_successful_captcha(captcha_image, solution)
            success_count += 1
        else:
            print(f"FAILED: '{solution}' was incorrect")

    print(f"\n\nCollection complete: {success_count} successful CAPTCHAs")
    return success_count


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "test"

    if mode == "collect":
        count = int(sys.argv[2]) if len(sys.argv) > 2 else 100
        collect_training_data(count)
    else:
        success = test_auto24_flow()
        sys.exit(0 if success else 1)
