#!/usr/bin/env python3
"""
Captcha Training Data Collector using curl-impersonate

Downloads captchas from Auto24 using curl-impersonate (via Docker),
solves with Ollama vision model, and saves successful solutions.

Prerequisites:
    - Docker with curl-impersonate image running:
      docker run -d --name curl-proxy -p 3000:3000 cloudflare-bypass-proxy
    - Ollama with qwen3-vl:32b model

Usage:
    python3 captcha_collector.py [--count 100] [--output ./captcha_data]
"""

import argparse
import base64
import io
import re
import time
from pathlib import Path

import requests
from PIL import Image

PROXY_URL = "http://localhost:3000"
OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen3-vl:32b"
TEST_REG_NUMBER = "463BKH"

ALLOWED_CHARS = set("2345789ABCDEFHKLMNPRTUVWXYZ")
CAPTCHA_LENGTH = 4


def resize_image_for_ollama(image_base64: str, min_size: int = 256) -> str:
    image_data = base64.b64decode(image_base64)
    img = Image.open(io.BytesIO(image_data))
    if img.mode != "RGB":
        img = img.convert("RGB")
    width, height = img.size
    scale = max(min_size / width, min_size / height, 4.0)
    new_width = int(width * scale)
    new_height = int(height * scale)
    img = img.resize((new_width, new_height), Image.Resampling.LANCZOS)
    buffer = io.BytesIO()
    img.save(buffer, format="PNG")
    return base64.b64encode(buffer.getvalue()).decode("utf-8")


def extract_captcha(response_text: str) -> str | None:
    upper = response_text.upper()

    for pattern in [r'"([A-Z0-9]{4})"', r"\b[A-Z0-9]{4}\b"]:
        for match in re.findall(pattern, upper):
            cleaned = "".join(c for c in match if c in ALLOWED_CHARS)
            if len(cleaned) == CAPTCHA_LENGTH:
                return cleaned

    all_valid = "".join(c for c in upper if c in ALLOWED_CHARS)
    return all_valid[:CAPTCHA_LENGTH] if len(all_valid) >= CAPTCHA_LENGTH else None


def solve_with_ollama(image_base64: str) -> str | None:
    resized_image = resize_image_for_ollama(image_base64)
    payload = {
        "model": MODEL,
        "prompt": "Read the 4 characters in this CAPTCHA image. Reply with just the characters.",
        "images": [resized_image],
        "stream": False,
        "options": {"temperature": 0.1, "num_predict": 100},
    }
    try:
        resp = requests.post(OLLAMA_URL, json=payload, timeout=120)
        resp.raise_for_status()
        data = resp.json()
        raw = data.get("response", "").strip()
        thinking = data.get("thinking", "")
        combined = raw + " " + thinking
        print(f"  Ollama: '{raw[:50]}' thinking: '{thinking[:80]}...'")
        return extract_captcha(combined)
    except Exception as e:
        print(f"  Ollama error: {e}")
        return None


def get_captcha() -> tuple[str, str] | None:
    try:
        resp = requests.post(f"{PROXY_URL}/auto24/captcha", json={"regNr": TEST_REG_NUMBER}, timeout=60)
        if resp.status_code != 200:
            print(f"  Captcha request failed: {resp.status_code} - {resp.text[:100]}")
            return None
        data = resp.json()
        if data.get("status") != "captcha_required":
            print(f"  Unexpected status: {data.get('status', 'unknown')}")
            return None
        return data.get("sessionId"), data.get("captchaImage")
    except Exception as e:
        print(f"  Error: {e}")
        return None


def test_solution(session_id: str, solution: str) -> bool:
    try:
        resp = requests.post(f"{PROXY_URL}/auto24/submit", json={"sessionId": session_id, "solution": solution}, timeout=60)
        if resp.status_code != 200:
            return False
        return resp.json().get("status") in ["success", "not_found"]
    except:
        return False


def save_captcha(image_base64: str, solution: str, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    filepath = output_dir / f"{solution}.png"
    counter = 1
    while filepath.exists():
        filepath = output_dir / f"{solution}_{counter}.png"
        counter += 1
    filepath.write_bytes(base64.b64decode(image_base64))
    return filepath


def check_services():
    print("Checking services...")

    try:
        resp = requests.get(f"{PROXY_URL}/health", timeout=5)
        if resp.status_code == 200:
            print(f"  Proxy: OK ({PROXY_URL})")
        else:
            print(f"  Proxy: FAILED - {resp.status_code}")
            return False
    except Exception as e:
        print(f"  Proxy: NOT RUNNING - {e}")
        print("\n  Start with: docker run -d --platform linux/amd64 -p 3000:3000 cloudflare-bypass-proxy")
        return False

    try:
        resp = requests.get("http://localhost:11434/api/tags", timeout=5)
        models = [m["name"] for m in resp.json().get("models", [])]
        if MODEL in models or any(MODEL.split(":")[0] in m for m in models):
            print(f"  Ollama: OK (model: {MODEL})")
        else:
            print(f"  Ollama: Model {MODEL} not found. Available: {models}")
            return False
    except Exception as e:
        print(f"  Ollama: NOT RUNNING - {e}")
        return False

    return True


def collect(target_count: int, output_dir: Path):
    print(f"\nCollecting {target_count} captchas -> {output_dir}")
    print("-" * 50)

    success = failed = attempts = 0

    while success < target_count:
        attempts += 1
        print(f"\n[{attempts}] {success}/{target_count} collected, {failed} failed")

        result = get_captcha()
        if not result:
            time.sleep(1)
            continue

        session_id, captcha_image = result
        solution = solve_with_ollama(captcha_image)

        if not solution:
            failed += 1
            continue

        print(f"  Solution: {solution}")

        if test_solution(session_id, solution):
            path = save_captcha(captcha_image, solution, output_dir)
            print(f"  SUCCESS -> {path.name}")
            success += 1
        else:
            print(f"  FAILED: incorrect")
            failed += 1

        time.sleep(0.5)

    print(f"\n{'='*50}")
    print(f"Done! {success} collected, {failed} failed, {attempts} total")
    print(f"Success rate: {success/attempts*100:.1f}%" if attempts else "N/A")


def main():
    parser = argparse.ArgumentParser(description="Collect captcha training data")
    parser.add_argument("--count", type=int, default=100)
    parser.add_argument("--output", type=str, default="./captcha_data")
    args = parser.parse_args()

    if not check_services():
        return

    collect(args.count, Path(args.output).resolve())


if __name__ == "__main__":
    main()
