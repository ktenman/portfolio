# Container Security Scan Report

Last updated: $(date -u +'%Y-%m-%d %H:%M:%S UTC')

## Scan Status

| Image                | Status     | Last Scan              |
| -------------------- | ---------- | ---------------------- |
| portfolio-be         | ✅ Scanned | $(date -u +'%Y-%m-%d') |
| portfolio-fe         | ✅ Scanned | $(date -u +'%Y-%m-%d') |
| market-price-tracker | ✅ Scanned | $(date -u +'%Y-%m-%d') |

## Actions

- View detailed results in the [Security tab](../../security/code-scanning)
- Check [workflow runs](../../actions/workflows/trivy-scan.yml)
- Download [SBOM artifacts](../../actions/workflows/trivy-scan.yml) from the latest run

## Automated Scanning

- **Trigger:** After each successful CI build
- **Schedule:** Daily at 2 AM UTC
- **Scope:** All production Docker images
- **Severity Levels:** CRITICAL, HIGH, MEDIUM
