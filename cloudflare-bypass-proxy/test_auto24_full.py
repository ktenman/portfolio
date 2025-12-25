#!/usr/bin/env python3
"""
Test Auto24 full flow with the existing captcha-solver service.
Uses the cloudflare-bypass-proxy running in Docker.
"""

import requests
import json
import sys
import uuid

PROXY_URL = "http://localhost:3000"
CAPTCHA_SOLVER_URL = "http://localhost:8000"
REG_NR = "463BKH"
MAX_ATTEMPTS = 10


def solve_captcha(base64_image):
    """Call the captcha-solver service to predict the CAPTCHA."""
    request = {
        "uuid": str(uuid.uuid4()),
        "imageBase64": base64_image
    }

    response = requests.post(
        f"{CAPTCHA_SOLVER_URL}/predict",
        json=request,
        timeout=30
    )

    if response.status_code != 200:
        print(f"Captcha solver error: {response.status_code}")
        print(response.text)
        return None

    result = response.json()
    print(f"Captcha prediction: {result.get('prediction')}")
    print(f"Confidence: {result.get('confidence')}")
    print(f"Processing time: {result.get('processingTimeMs')}ms")

    return result.get("prediction")


def test_auto24_flow():
    """Test the full Auto24 flow."""
    print(f"\n{'='*60}")
    print(f"Testing Auto24 with registration: {REG_NR}")
    print(f"{'='*60}\n")

    for attempt in range(MAX_ATTEMPTS):
        print(f"\n--- Attempt {attempt + 1} of {MAX_ATTEMPTS} ---\n")

        print("Step 1: Get CAPTCHA...")
        response = requests.post(
            f"{PROXY_URL}/auto24/captcha",
            json={"regNr": REG_NR},
            timeout=60
        )

        if response.status_code != 200:
            print(f"ERROR: Failed to get CAPTCHA: {response.status_code}")
            print(response.text)
            continue

        data = response.json()

        if data.get("status") == "success":
            print(f"No CAPTCHA needed! Price found directly: {data.get('price')}")
            print(f"Car info: {data.get('carInfo')}")
            return True

        session_id = data.get("sessionId")
        captcha_image = data.get("captchaImage")

        print(f"Session ID: {session_id}")
        print(f"CAPTCHA URL: {data.get('captchaUrl')}")

        print("\nStep 2: Solve CAPTCHA with captcha-solver...")
        solution = solve_captcha(captcha_image)

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
            print(f"\n✓ SUCCESS! Price: {result.get('price')}")
            print(f"Car info: {result.get('carInfo')}")
            return True
        elif result.get("status") == "captcha_failed":
            print("\n✗ CAPTCHA solution was incorrect, retrying...")
            continue
        else:
            print("\n✗ Unexpected response, retrying...")
            continue

    print(f"\nFailed after {MAX_ATTEMPTS} attempts")
    return False


if __name__ == "__main__":
    try:
        success = test_auto24_flow()
        sys.exit(0 if success else 1)
    except Exception as e:
        print(f"ERROR: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
