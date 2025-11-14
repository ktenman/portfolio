#!/bin/bash

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Get the project root (parent of scripts directory)
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Change to project root so relative paths work
cd "$PROJECT_ROOT"

PLANTUML_VERSION="1.2024.8"
PLANTUML_JAR="plantuml-${PLANTUML_VERSION}.jar"
PLANTUML_URL="https://github.com/plantuml/plantuml/releases/download/v${PLANTUML_VERSION}/plantuml-${PLANTUML_VERSION}.jar"

download_plantuml() {
  if [ ! -f "$PLANTUML_JAR" ]; then
    echo "Downloading PlantUML ${PLANTUML_VERSION}..."
    curl -L -o "$PLANTUML_JAR" "$PLANTUML_URL"
    echo "PlantUML downloaded successfully"
  else
    echo "PlantUML ${PLANTUML_VERSION} already exists"
  fi
}

generate_svg() {
  local puml_file=$1
  local output_dir=$(cd "$(dirname "$puml_file")" && pwd)
  local filename=$(basename "$puml_file" .puml)

  echo "Generating SVG for: $puml_file"
  java -jar "$PLANTUML_JAR" -tsvg "$puml_file"
  echo "Generated: ${output_dir}/${filename}.svg"
}

main() {
  download_plantuml

  if [ $# -eq 0 ]; then
    echo "Generating all diagrams..."

    for puml_file in docs/architecture/*.puml screenshots/*.puml; do
      if [ -f "$puml_file" ]; then
        generate_svg "$puml_file"
      fi
    done

    echo "All diagrams generated successfully!"
  else
    for puml_file in "$@"; do
      if [ -f "$puml_file" ]; then
        generate_svg "$puml_file"
      else
        echo "File not found: $puml_file"
        exit 1
      fi
    done
  fi
}

main "$@"
