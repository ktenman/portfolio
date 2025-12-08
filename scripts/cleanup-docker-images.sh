#!/bin/bash

set -e

DOCKERHUB_USERNAME="${DOCKERHUB_USERNAME:-ktenman}"
GITHUB_USERNAME="${GITHUB_USERNAME:-ktenman}"

DOCKERHUB_REPOS=(
  "portfolio-be"
  "portfolio-fe"
  "cloudflare-bypass-proxy"
)

GITHUB_PACKAGES=(
  "backend"
  "frontend"
  "cloudflare-bypass-proxy"
)

echo "============================================"
echo "Docker Image Cleanup Script"
echo "============================================"
echo ""

cleanup_dockerhub() {
  echo "=== Docker Hub Cleanup ==="
  echo ""

  if [ -z "$DOCKERHUB_TOKEN" ]; then
    echo "Getting Docker Hub token..."
    echo "Please enter your Docker Hub password or access token:"
    read -s DOCKERHUB_PASSWORD

    DOCKERHUB_TOKEN=$(curl -s -X POST \
      "https://hub.docker.com/v2/users/login" \
      -H "Content-Type: application/json" \
      -d "{\"username\": \"$DOCKERHUB_USERNAME\", \"password\": \"$DOCKERHUB_PASSWORD\"}" \
      | jq -r '.token')

    if [ "$DOCKERHUB_TOKEN" == "null" ] || [ -z "$DOCKERHUB_TOKEN" ]; then
      echo "Failed to authenticate with Docker Hub"
      return 1
    fi
    echo "Successfully authenticated with Docker Hub"
  fi

  for repo in "${DOCKERHUB_REPOS[@]}"; do
    echo ""
    echo "Processing $DOCKERHUB_USERNAME/$repo..."

    tags=$(curl -s "https://hub.docker.com/v2/repositories/$DOCKERHUB_USERNAME/$repo/tags?page_size=100" \
      | jq -r '.results[].name' | grep "^sha-" || true)

    if [ -z "$tags" ]; then
      echo "  No SHA tags found to delete"
      continue
    fi

    tag_count=$(echo "$tags" | wc -l | tr -d ' ')
    echo "  Found $tag_count SHA tags to delete"

    for tag in $tags; do
      echo "  Deleting tag: $tag"
      response=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
        "https://hub.docker.com/v2/repositories/$DOCKERHUB_USERNAME/$repo/tags/$tag" \
        -H "Authorization: Bearer $DOCKERHUB_TOKEN")

      if [ "$response" == "204" ]; then
        echo "    ✓ Deleted"
      else
        echo "    ✗ Failed (HTTP $response)"
      fi
    done
  done

  echo ""
  echo "Docker Hub cleanup complete!"
}

cleanup_github() {
  echo ""
  echo "=== GitHub Container Registry Cleanup ==="
  echo ""
  echo "To clean up GitHub Container Registry, you need to:"
  echo ""
  echo "1. Go to your GitHub profile → Packages"
  echo "   https://github.com/$GITHUB_USERNAME?tab=packages"
  echo ""
  echo "2. For each package, click on it and delete old versions"
  echo ""
  echo "Or use the GitHub CLI with proper scopes:"
  echo ""
  echo "  # First, re-authenticate with read:packages and delete:packages scopes"
  echo "  gh auth login --scopes read:packages,delete:packages"
  echo ""
  echo "  # Then run these commands to delete old versions:"
  for pkg in "${GITHUB_PACKAGES[@]}"; do
    echo ""
    echo "  # For $pkg:"
    echo "  gh api -X GET /user/packages/container/$pkg/versions --jq '.[].id' | while read id; do"
    echo "    gh api -X DELETE /user/packages/container/$pkg/versions/\$id"
    echo "  done"
  done
  echo ""
  echo "Note: Keep at least one version (latest) for each package."
}

show_current_state() {
  echo "=== Current State ==="
  echo ""

  echo "Docker Hub images:"
  for repo in "${DOCKERHUB_REPOS[@]}"; do
    echo ""
    echo "  $DOCKERHUB_USERNAME/$repo:"
    tags=$(curl -s "https://hub.docker.com/v2/repositories/$DOCKERHUB_USERNAME/$repo/tags?page_size=100" \
      | jq -r '.results[] | "    - \(.name) (pushed: \(.last_updated | split("T")[0]))"' 2>/dev/null || echo "    (not found)")
    echo "$tags"
  done
}

case "${1:-}" in
  "dockerhub")
    cleanup_dockerhub
    ;;
  "github")
    cleanup_github
    ;;
  "status")
    show_current_state
    ;;
  "all")
    cleanup_dockerhub
    cleanup_github
    ;;
  *)
    echo "Usage: $0 {dockerhub|github|status|all}"
    echo ""
    echo "  dockerhub - Clean up Docker Hub (delete SHA tags, keep latest)"
    echo "  github    - Show instructions for GitHub Container Registry cleanup"
    echo "  status    - Show current state of all repositories"
    echo "  all       - Run both dockerhub and github cleanup"
    echo ""
    echo "Environment variables:"
    echo "  DOCKERHUB_USERNAME - Docker Hub username (default: ktenman)"
    echo "  DOCKERHUB_TOKEN    - Docker Hub JWT token (will prompt for password if not set)"
    echo "  GITHUB_USERNAME    - GitHub username (default: ktenman)"
    ;;
esac
