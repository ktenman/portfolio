import base64
import os
import pytest
import httpx
from pathlib import Path

CAPTCHA_SOLVER_URL = os.getenv("CAPTCHA_SOLVER_URL", "http://localhost:8000")
TEST_IMAGES_DIR = Path(__file__).parent / "test_images"


def get_test_images():
    images = []
    for img_path in TEST_IMAGES_DIR.glob("*.png"):
        expected_text = img_path.stem
        images.append((img_path, expected_text))
    return images


@pytest.fixture
def captcha_images():
    return get_test_images()


class TestCaptchaSolverIntegration:
    @pytest.mark.integration
    def test_health_endpoint(self):
        response = httpx.get(f"{CAPTCHA_SOLVER_URL}/health", timeout=10)
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert data["model_loaded"] is True

    @pytest.mark.integration
    @pytest.mark.parametrize("img_path,expected_text", get_test_images())
    def test_captcha_prediction(self, img_path: Path, expected_text: str):
        with open(img_path, "rb") as f:
            image_base64 = base64.b64encode(f.read()).decode("utf-8")

        payload = {
            "uuid": f"test-{expected_text}",
            "imageBase64": image_base64
        }

        response = httpx.post(
            f"{CAPTCHA_SOLVER_URL}/predict",
            json=payload,
            timeout=30
        )

        assert response.status_code == 200
        data = response.json()
        assert data["uuid"] == f"test-{expected_text}"
        assert "prediction" in data
        assert "confidence" in data
        assert "processingTimeMs" in data
        assert data["prediction"] == expected_text, (
            f"Expected '{expected_text}', got '{data['prediction']}' "
            f"(confidence: {data['confidence']:.2%})"
        )

    @pytest.mark.integration
    def test_all_captchas_accuracy(self, captcha_images):
        results = []
        for img_path, expected_text in captcha_images:
            with open(img_path, "rb") as f:
                image_base64 = base64.b64encode(f.read()).decode("utf-8")

            payload = {
                "uuid": f"test-{expected_text}",
                "imageBase64": image_base64
            }

            response = httpx.post(
                f"{CAPTCHA_SOLVER_URL}/predict",
                json=payload,
                timeout=30
            )

            data = response.json()
            is_correct = data["prediction"] == expected_text
            results.append({
                "expected": expected_text,
                "predicted": data["prediction"],
                "confidence": data["confidence"],
                "correct": is_correct
            })

        correct_count = sum(1 for r in results if r["correct"])
        total_count = len(results)
        accuracy = correct_count / total_count if total_count > 0 else 0

        print(f"\n{'='*60}")
        print(f"Captcha Solver Accuracy Report")
        print(f"{'='*60}")
        for r in results:
            status = "PASS" if r["correct"] else "FAIL"
            print(f"[{status}] Expected: {r['expected']}, "
                  f"Predicted: {r['predicted']}, "
                  f"Confidence: {r['confidence']:.2%}")
        print(f"{'='*60}")
        print(f"Accuracy: {correct_count}/{total_count} ({accuracy:.0%})")
        print(f"{'='*60}")

        assert accuracy >= 0.8, f"Accuracy {accuracy:.0%} is below 80% threshold"


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-m", "integration", "-s"])
