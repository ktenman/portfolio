#!/usr/bin/env python3
"""
Test Auto24 full flow with Google Cloud Vision for CAPTCHA solving.
Uses the cloudflare-bypass-proxy running in Docker.
"""

import requests
import base64
import os
import json
import sys
from google.cloud import vision
from google.oauth2 import service_account

PROXY_URL = "http://localhost:3000"
REG_NR = "463BKH"


def get_vision_client():
    """Create Google Vision client from base64-encoded credentials."""
    base64_key = os.environ.get("VISION_BASE64_KEY")
    if not base64_key:
        key_file = os.path.expanduser("~/google-vision-key.json")
        if os.path.exists(key_file):
            print(f"Using credentials from {key_file}")
            return vision.ImageAnnotatorClient.from_service_account_file(key_file)
        print("ERROR: Set VISION_BASE64_KEY env var or create ~/google-vision-key.json")
        sys.exit(1)

    creds_json = base64.b64decode(base64_key)
    creds_dict = json.loads(creds_json)
    credentials = service_account.Credentials.from_service_account_info(creds_dict)
    return vision.ImageAnnotatorClient(credentials=credentials)


def solve_captcha_with_vision(client, base64_image):
    """Use Google Vision API to extract text from CAPTCHA image."""
    image_bytes = base64.b64decode(base64_image)
    image = vision.Image(content=image_bytes)
    response = client.text_detection(image=image)

    if response.error.message:
        raise Exception(f"Vision API error: {response.error.message}")

    texts = response.text_annotations
    if texts:
        full_text = texts[0].description.strip()
        solution = ''.join(c for c in full_text if c.isalnum())
        print(f"Vision detected text: '{full_text}' -> solution: '{solution}'")
        return solution

    print("No text detected in CAPTCHA")
    return None


def test_auto24_flow():
    """Test the full Auto24 flow."""
    print(f"\n{'='*60}")
    print(f"Testing Auto24 with registration: {REG_NR}")
    print(f"{'='*60}\n")

    vision_client = get_vision_client()
    print("Google Vision client initialized\n")

    print("Step 1: Get CAPTCHA...")
    response = requests.post(
        f"{PROXY_URL}/auto24/captcha",
        json={"regNr": REG_NR},
        timeout=60
    )

    if response.status_code != 200:
        print(f"ERROR: Failed to get CAPTCHA: {response.status_code}")
        print(response.text)
        return False

    data = response.json()

    if data.get("status") == "success":
        print(f"No CAPTCHA needed! Price found directly: {data.get('price')}")
        print(f"Car info: {data.get('carInfo')}")
        return True

    session_id = data.get("sessionId")
    captcha_image = data.get("captchaImage")

    print(f"Session ID: {session_id}")
    print(f"CAPTCHA URL: {data.get('captchaUrl')}")

    with open("/tmp/captcha_vision.png", "wb") as f:
        f.write(base64.b64decode(captcha_image))
    print("Saved CAPTCHA to /tmp/captcha_vision.png\n")

    print("Step 2: Solve CAPTCHA with Google Vision...")
    solution = solve_captcha_with_vision(vision_client, captcha_image)

    if not solution:
        print("ERROR: Could not solve CAPTCHA")
        return False

    print(f"\nStep 3: Submit CAPTCHA solution: '{solution}'...")
    response = requests.post(
        f"{PROXY_URL}/auto24/submit",
        json={"sessionId": session_id, "solution": solution},
        timeout=60
    )

    if response.status_code != 200:
        print(f"ERROR: Failed to submit CAPTCHA: {response.status_code}")
        print(response.text)
        return False

    result = response.json()
    print(f"\nResult: {json.dumps(result, indent=2)}")

    if result.get("status") == "success":
        print(f"\n✓ SUCCESS! Price: {result.get('price')}")
        print(f"Car info: {result.get('carInfo')}")
        return True
    elif result.get("status") == "captcha_failed":
        print("\n✗ CAPTCHA solution was incorrect")
        return False
    else:
        print("\n✗ Unexpected response")
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
