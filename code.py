#!/usr/bin/env python3
import fnmatch
from pathlib import Path

# Optional dependency: true Git‐ignore parsing
try:
  import pathspec

  HAS_PATHSPEC = True
except ImportError:
  print("⚠️ pathspec not installed; falling back to simple fnmatch ignores")
  HAS_PATHSPEC = False

# Built-in ignore patterns for IDEs, build outputs, binaries, media, temp files, etc.
DEFAULT_IGNORE = [
  # IDE/editor
  '.idea/', '*.iml', '.vscode/', '.DS_Store',
  # Git metadata
  '.git/',
  # Build outputs & caches
  'build/', 'dist/', 'out/', 'target/', '.gradle/', '__pycache__/', '*.pyc',
  # Dependencies
  'node_modules/',
  # Logs, temp & backup
  '*.log', '*.tmp', '*.bak', '*.backup',
  # Compiled binaries & archives
  '*.class', '*.jar', '*.exe', '*.dll', '*.so',
  # Images & media
  '*.png', '*.jpg', '*.jpeg', '*.gif', '*.svg', '*.ico',
  # Project-specific artifacts
  'combined_files.txt', 'healthcheck.sh', 'gpg-gen-key-script',
  # Environment files
  '*.env', '.env.*',
  # Screenshots / misc directories
  'screenshots/',
  # TypeScript build info (not needed)
  '*.tsbuildinfo',
]

# Whitelist only the file types and specific filenames that matter for understanding the project
ALLOWED_EXTENSIONS = {
  '.yml', '.yaml',  # CI/config
  '.kt',  # Kotlin code
  '.py',  # Python scripts
  '.ts', '.tsx',  # TypeScript
  '.vue',  # Vue single‐file components
  '.js',  # JS helpers
  '.json', '.d.ts',  # configs & typings
  '.kts',  # Gradle Kotlin DSL
  '.sh',  # shell scripts
  '.md',  # markdown docs
  '.html', '.css',  # frontend assets
  '.conf',  # nginx/Caddy configs
}

ALLOWED_FILENAMES = {
  'Dockerfile', 'Caddyfile',
  'docker-compose.yml', 'compose.yaml',
  'build.gradle.kts', 'settings.gradle.kts',
}

# Skip certain folders entirely
SKIP_FOLDERS = [
  'market-price-tracker',  # Ignore this whole folder
]


def load_patterns(*filenames):
  """Read lines (ignoring blanks/comments) from given ignore files."""
  patterns = []
  for fname in filenames:
    p = Path(fname)
    if p.is_file():
      for line in p.read_text(encoding='utf-8').splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
          continue
        patterns.append(line)
  return patterns


def build_matcher(base_dir: Path):
  """
  Load .gitignore and .dockerignore, append DEFAULT_IGNORE,
  then build either a pathspec matcher or a simple list.
  """
  patterns = load_patterns(base_dir / '.gitignore', base_dir / '.dockerignore')
  patterns += DEFAULT_IGNORE
  if HAS_PATHSPEC:
    return pathspec.PathSpec.from_lines('gitwildmatch', patterns)
  else:
    return patterns


def is_ignored(path: Path, matcher, base_dir: Path) -> bool:
  """Return True if path should be skipped."""
  rel = path.relative_to(base_dir)

  # Skip ignored folders entirely
  for folder in SKIP_FOLDERS:
    if str(rel).startswith(folder):
      return True

  if HAS_PATHSPEC:
    return matcher.match_file(str(rel))
  else:
    for pat in matcher:
      if fnmatch.fnmatch(str(rel), pat):
        return True
    return False


def combine_files(base_dir: str = '.', output_file: str = 'combined_files.txt'):
  base = Path(base_dir).resolve()
  matcher = build_matcher(base)

  with open(output_file, 'w', encoding='utf-8') as out:
    for path in base.rglob('*'):
      if not path.is_file():
        continue
      if is_ignored(path, matcher, base):
        continue

      # Skip anything not in our whitelist
      if path.name not in ALLOWED_FILENAMES and path.suffix.lower() not in ALLOWED_EXTENSIONS:
        continue

      try:
        out.write(f"File: {path.relative_to(base)}\n")
        out.write(path.read_text(encoding='utf-8'))
        out.write("\n\n")
      except Exception as e:
        print(f"Skipping {path}: {e}")


if __name__ == '__main__':
  combine_files()
