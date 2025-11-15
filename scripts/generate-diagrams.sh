#!/bin/bash

set -euo pipefail
IFS=$'\n\t'

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Get the project root (parent of scripts directory)
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Change to project root so relative paths work
cd "$PROJECT_ROOT"

PLANTUML_VERSION="1.2024.8"
PLANTUML_JAR="plantuml-${PLANTUML_VERSION}.jar"
PLANTUML_URL="https://github.com/plantuml/plantuml/releases/download/v${PLANTUML_VERSION}/plantuml-${PLANTUML_VERSION}.jar"
PLANTUML_SHA256="2bf9f26f03ad597eb37c4264da2bd3c88c9b5d20f92bc69c96cfb65d83a38a11"

validate_file_path() {
  local file_path="$1"
  local resolved_path

  if [[ ! -f "$file_path" ]]; then
    echo "Error: File not found: $file_path" >&2
    return 1
  fi

  if [[ ! "$file_path" =~ \.puml$ ]]; then
    echo "Error: File must have .puml extension: $file_path" >&2
    return 1
  fi

  resolved_path="$(cd "$(dirname "$file_path")" && pwd)/$(basename "$file_path")"

  if [[ ! "$resolved_path" =~ ^"$PROJECT_ROOT" ]]; then
    echo "Error: File must be within project directory: $file_path" >&2
    return 1
  fi

  return 0
}

download_plantuml() {
  if [ ! -f "$PLANTUML_JAR" ]; then
    echo "Downloading PlantUML ${PLANTUML_VERSION}..."

    if ! curl -fsSL -o "$PLANTUML_JAR" "$PLANTUML_URL"; then
      echo "Error: Failed to download PlantUML" >&2
      rm -f "$PLANTUML_JAR"
      exit 1
    fi

    echo "Verifying checksum..."
    if command -v sha256sum >/dev/null 2>&1; then
      echo "$PLANTUML_SHA256  $PLANTUML_JAR" | sha256sum -c - || {
        echo "Error: Checksum verification failed!" >&2
        rm -f "$PLANTUML_JAR"
        exit 1
      }
    elif command -v shasum >/dev/null 2>&1; then
      echo "$PLANTUML_SHA256  $PLANTUML_JAR" | shasum -a 256 -c - || {
        echo "Error: Checksum verification failed!" >&2
        rm -f "$PLANTUML_JAR"
        exit 1
      }
    else
      echo "Warning: Neither sha256sum nor shasum found, skipping checksum verification" >&2
      echo "This is a security risk. Please install sha256sum or shasum." >&2
    fi

    echo "PlantUML downloaded and verified successfully"
  else
    echo "PlantUML ${PLANTUML_VERSION} already exists"
  fi
}

generate_svg() {
  local puml_file="$1"
  local output_dir
  local filename

  if ! validate_file_path "$puml_file"; then
    return 1
  fi

  output_dir="$(cd "$(dirname "$puml_file")" && pwd)"
  filename="$(basename "$puml_file" .puml)"

  echo "Generating SVG for: $puml_file"

  if ! java -jar "$PLANTUML_JAR" -tsvg -- "$puml_file"; then
    echo "Error: Failed to generate SVG for $puml_file" >&2
    return 1
  fi

  echo "Generated: ${output_dir}/${filename}.svg"
}

main() {
  download_plantuml

  if [ $# -eq 0 ]; then
    echo "Generating all diagrams..."

    for puml_file in docs/architecture/*.puml screenshots/*.puml; do
      if [ -f "$puml_file" ]; then
        if ! generate_svg "$puml_file"; then
          echo "Warning: Failed to generate diagram for $puml_file" >&2
        fi
      fi
    done

    echo "All diagrams generated successfully!"
  else
    local failed=0
    for puml_file in "$@"; do
      if ! generate_svg "$puml_file"; then
        failed=$((failed + 1))
      fi
    done

    if [ $failed -gt 0 ]; then
      echo "Error: Failed to generate $failed diagram(s)" >&2
      exit 1
    fi
  fi
}

main "$@"
