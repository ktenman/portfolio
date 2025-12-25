#!/usr/bin/env python3
"""
Captcha Training Data Collector using Selenium

Uses browser automation to bypass Cloudflare, then Ollama vision for solving.
Works on any platform including Apple Silicon.

Requirements:
    pip install selenium webdriver-manager requests

Usage:
    python3 captcha_collector_selenium.py [--count 100] [--output ./captcha_data] [--headless]
"""

import argparse
import base64
import re
import time
from pathlib import Path

import requests
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait
from webdriver_manager.chrome import ChromeDriverManager

OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL = "qwen3-vl:32b"
AUTO24_URL = "https://www.auto24.ee/ostuabi/?t=soiduki-turuhinna-paring"
TEST_REG_NUMBER = "463BKH"

ALLOWED_CHARS = set("2345789ABCDEFHKLMNPRTUVWXYZ")
CAPTCHA_LENGTH = 4


def extract_captcha_from_response(response_text: str) -> str | None:
    upper = response_text.upper()

    quoted = re.findall(r'"([A-Z0-9]{4})"', upper)
    for q in quoted:
        cleaned = "".join(c for c in q if c in ALLOWED_CHARS)
        if len(cleaned) == CAPTCHA_LENGTH:
            return cleaned

    words = re.findall(r"\b[A-Z0-9]{4}\b", upper)
    for word in words:
        cleaned = "".join(c for c in word if c in ALLOWED_CHARS)
        if len(cleaned) == CAPTCHA_LENGTH:
            return cleaned

    all_valid = "".join(c for c in upper if c in ALLOWED_CHARS)
    if len(all_valid) >= CAPTCHA_LENGTH:
        return all_valid[:CAPTCHA_LENGTH]

    return None


def solve_with_ollama(image_base64: str) -> str | None:
    payload = {
        "model": MODEL,
        "prompt": "Read the text in this CAPTCHA image. What are the 4 characters shown?",
        "images": [image_base64],
        "stream": False,
        "options": {"temperature": 0.1, "num_predict": 100},
    }

    try:
        response = requests.post(OLLAMA_URL, json=payload, timeout=120)
        response.raise_for_status()
        result = response.json()
        raw = result.get("response", "").strip()
        print(f"  Ollama: '{raw}'")
        return extract_captcha_from_response(raw)
    except Exception as e:
        print(f"  Ollama error: {e}")
        return None


def create_driver(headless: bool = True) -> webdriver.Chrome:
    options = Options()
    if headless:
        options.add_argument("--headless=new")
    options.add_argument("--window-size=1920,1080")
    options.add_argument("--disable-blink-features=AutomationControlled")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

    service = Service(ChromeDriverManager().install())
    return webdriver.Chrome(service=service, options=options)


def dismiss_cookies(driver: webdriver.Chrome):
    selectors = [
        "#onetrust-accept-btn-handler",
        ".onetrust-close-btn-handler",
        "button[title='Nõustun']",
    ]
    for selector in selectors:
        try:
            btn = driver.find_element(By.CSS_SELECTOR, selector)
            if btn.is_displayed():
                btn.click()
                time.sleep(0.5)
                return
        except:
            pass


def get_captcha_image(driver: webdriver.Chrome) -> str | None:
    try:
        captcha_elem = driver.find_element(By.ID, "vpc_captcha")
        screenshot = captcha_elem.screenshot_as_png
        return base64.b64encode(screenshot).decode("utf-8")
    except Exception as e:
        print(f"  Error capturing captcha: {e}")
        return None


def submit_solution(driver: webdriver.Chrome, solution: str):
    try:
        input_field = driver.find_element(By.NAME, "checksec1")
        input_field.clear()
        input_field.send_keys(solution)

        submit_btn = driver.find_element(By.CLASS_NAME, "sbmt")
        submit_btn.click()
        time.sleep(2)
    except Exception as e:
        print(f"  Error submitting: {e}")


def check_success(driver: webdriver.Chrome) -> bool:
    try:
        if driver.find_elements(By.CSS_SELECTOR, ".vpc_error_msg"):
            error = driver.find_element(By.CSS_SELECTOR, ".vpc_error_msg").text
            if "Vale kontrollkood" in error or "kontroll" in error.lower():
                return False

        captcha_exists = len(driver.find_elements(By.ID, "vpc_captcha")) > 0
        return not captcha_exists
    except:
        return False


def save_captcha(image_base64: str, solution: str, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    filepath = output_dir / f"{solution}.png"

    counter = 1
    while filepath.exists():
        filepath = output_dir / f"{solution}_{counter}.png"
        counter += 1

    image_data = base64.b64decode(image_base64)
    filepath.write_bytes(image_data)
    return filepath


def collect_captchas(target_count: int, output_dir: Path, headless: bool):
    print(f"Collecting {target_count} captchas")
    print(f"Output: {output_dir}")
    print(f"Model: {MODEL}")
    print(f"Headless: {headless}")
    print("-" * 50)

    driver = create_driver(headless)
    success_count = 0
    attempt_count = 0
    failed_count = 0

    try:
        while success_count < target_count:
            attempt_count += 1
            print(f"\n[{attempt_count}] Success: {success_count}/{target_count} | Failed: {failed_count}")

            driver.get(AUTO24_URL)
            time.sleep(2)
            dismiss_cookies(driver)

            try:
                reg_input = driver.find_element(By.NAME, "vpc_reg_nr")
                reg_input.clear()
                reg_input.send_keys(TEST_REG_NUMBER)
                driver.find_element(By.CLASS_NAME, "sbmt").click()
                time.sleep(2)
            except Exception as e:
                print(f"  Error filling form: {e}")
                continue

            captcha_base64 = get_captcha_image(driver)
            if not captcha_base64:
                continue

            print(f"  Got captcha image")

            solution = solve_with_ollama(captcha_base64)
            if not solution:
                print("  Could not solve")
                failed_count += 1
                continue

            print(f"  Solution: {solution}")
            submit_solution(driver, solution)

            if check_success(driver):
                filepath = save_captcha(captcha_base64, solution, output_dir)
                print(f"  SUCCESS! Saved: {filepath.name}")
                success_count += 1
            else:
                print(f"  FAILED: incorrect solution")
                failed_count += 1

            time.sleep(1)

    finally:
        driver.quit()

    print(f"\n{'='*50}")
    print(f"Collection complete!")
    print(f"Successful: {success_count}")
    print(f"Failed: {failed_count}")
    print(f"Total attempts: {attempt_count}")
    if attempt_count > 0:
        print(f"Success rate: {success_count / attempt_count * 100:.1f}%")


def main():
    parser = argparse.ArgumentParser(description="Collect captcha training data")
    parser.add_argument("--count", type=int, default=100, help="Number of captchas to collect")
    parser.add_argument("--output", type=str, default="./captcha_data", help="Output directory")
    parser.add_argument("--headless", action="store_true", default=True, help="Run headless")
    parser.add_argument("--no-headless", dest="headless", action="store_false", help="Show browser")
    args = parser.parse_args()

    output_dir = Path(args.output).resolve()
    collect_captchas(args.count, output_dir, args.headless)


if __name__ == "__main__":
    main()
