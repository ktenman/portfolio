#!/usr/bin/env python3
import fnmatch
import mimetypes
import re
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
  # Configuration and metadata files that add minimal architectural value
  'LICENSE', 'LICENSE*', 'COPYING', 'COPYING*',
  'NOTICE', 'NOTICE*', 'SECURITY.md',
  'CHANGELOG', 'CHANGELOG*', 'CODE_OF_CONDUCT.md',
  'CONTRIBUTING.md',
  '.gitattributes', '.mailmap', '.npmrc', '.yarnrc',
  '.prettierrc', '.eslintrc*', '.stylelintrc*',
  '.editorconfig', '.browserslistrc',

  # Documentation folders
  'docs/', 'documentation/', 'examples/', 'example/', 'samples/', 'sample/',

  # Local tooling, venvs, and caches
  '.venv/', 'venv/', '.ruff_cache/', '.pytest_cache/',
  '.coverage/', 'coverage/',
  # IDE/editor
  '.idea/', '*.iml', '.vscode/', '.DS_Store',

  # Build outputs & caches
  'build/', 'dist/', 'out/', 'target/', '.gradle/', '__pycache__/', '*.pyc',
  'node_modules/', '*.tsbuildinfo', '*.min.js', '*.min.css',
  '*.map', 'bundle.js',

  # Dependency and lock files
  'package-lock.json', 'yarn.lock', 'pnpm-lock.yaml',

  # Logs, temp & backup
  '*.log', '*.tmp', '*.bak', '*.backup', 'tmp/', 'temp/',
  '.log/', 'logs/',

  # Compiled binaries & archives
  '*.class', '*.jar', '*.exe', '*.dll', '*.so', '*.zip', '*.tar.gz', '*.tgz',

  # Images & media
  '*.png', '*.jpg', '*.jpeg', '*.gif', '*.svg', '*.ico', '*.mp3', '*.mp4',

  # Project-specific artifacts
  'combined_files.txt', 'healthcheck.sh', 'gpg-gen-key-script',

  # Environment files
  '*.env', '.env.*', '.env.local',

  # TypeScript build info and type definition files
  '*.tsbuildinfo', '**/*.d.ts',

  # Less important configuration files
  '.browserlistrc', '.editorconfig', '.prettierrc', '.prettierignore',
  '.eslintignore', '.jshintrc', '.stylelintrc', '.babelrc',
  'vite-env.d.ts', 'tsconfig*.json',

  # Mock files and fixtures
  '**/__mocks__/**', '**/__fixtures__/**', '**/fixtures/**',
  '**/mock-data/**', '**/mock/**', '**/__files/**',
]

# Source code file extensions - files with these extensions will use MAX_SOURCE_FILE_SIZE limit
SOURCE_CODE_EXTENSIONS = {
  '.kt',  # Kotlin code
  '.py',  # Python scripts
  '.ts',  # TypeScript
  '.tsx',  # TypeScript with JSX
  '.vue',  # Vue single‐file components
  '.js',  # JavaScript
  '.kts',  # Gradle Kotlin DSL
  '.sql',  # SQL scripts are now explicitly marked as source code
  '.yaml',
  '.yml',  # YAML files (e.g., for CI/CD)
}

# Whitelist only the file types and specific filenames that matter for understanding the project
ALLOWED_EXTENSIONS = {
  '.yml', '.yaml',  # CI/config
  '.kt',  # Kotlin code
  '.py',  # Python scripts
  '.ts', '.tsx',  # TypeScript
  '.vue',  # Vue single‐file components
  '.js',  # JS helpers
  '.json',  # configs & typings
  '.kts',  # Gradle Kotlin DSL
  '.sh',  # shell scripts
  '.md',  # markdown docs
  '.html', '.css',  # frontend assets
  '.conf',  # nginx/Caddy configs
  '.sql',  # SQL scripts will use source file limits
}

# Specific, important files to always include
ALLOWED_FILENAMES = {
  'Dockerfile', 'Caddyfile',
  'docker-compose.yml', 'compose.yaml', 'docker-compose.e2e-minimal.yml', 'docker-compose.local.yml',
  'build.gradle.kts', 'settings.gradle.kts',
  'README.md', 'package.json',
  'nginx.conf', 'eslint.config.js',
  # GitHub specific files
  'codeql-config.yml',
  'check-readme-links.sh',
  'ci.yml',
  'codeql.yml',
  'deploy-pipeline.yml',
  'dependabot.yml',
}

# Folders to include in a limited capacity (not skipped entirely, but we'll be selective)
LIMITED_INCLUDE_FOLDERS = [
  'src/test/',  # Test folders - now limited, not skipped entirely
  'ui/test/',
  'market-price-tracker/tests/',
]

# Skip certain folders entirely - more comprehensive and now explicitly includes .git
# We've removed test folders from here, moved them to LIMITED_INCLUDE_FOLDERS
SKIP_FOLDERS = [
  'docs', 'documentation',  # project hand-written docs
  'examples', 'example',  # demo code or snippets
  'samples', 'sample',  # likewise
  '.venv',  # Python virtual-envs
  '.git',  # Git metadata - NOTE: This should NOT include .github
  '.gradle',  # Gradle cache
  'generated',  # Generated code
  'coverage',  # Test coverage reports
  'dist',  # Distribution builds
  'build',  # Build artifacts
  'node_modules',  # Node dependencies
  'out',  # Output folders
  'target',  # Build target for JVM
  '__pycache__',  # Python cache
  '.pytest_cache',  # Pytest cache
  '.coverage',  # Code coverage data
  'src/test/resources/__files',  # Test files specific to this project
]

# Selected test files that provide valuable architectural insights
IMPORTANT_TEST_FILES = [
  # Integration Tests
  'src/test/kotlin/ee/tenman/portfolio/controller/InstrumentControllerIT.kt',
  'src/test/kotlin/ee/tenman/portfolio/controller/PortfolioSummaryControllerIT.kt',
  'src/test/kotlin/ee/tenman/portfolio/controller/PortfolioTransactionControllerIT.kt',
  'src/test/kotlin/ee/tenman/portfolio/job/DailyPortfolioXirrJobIT.kt',
  'src/test/kotlin/ee/tenman/portfolio/service/JobExecutionServiceIT.kt',

  # Core Unit Tests
  'src/test/kotlin/ee/tenman/portfolio/service/xirr/XirrTest.kt',

  # E2E Tests
  'src/test/kotlin/e2e/TransactionManagementE2ETests.kt',
  'src/test/kotlin/e2e/InstrumentManagementE2ETests.kt',

  # Configuration Tests
  'src/test/kotlin/ee/tenman/portfolio/configuration/exception/GlobalExceptionHandlerTest.kt',
]

# Core architectural files we definitely want to include - specific to this portfolio project
CORE_ARCHITECTURAL_PATTERNS = [
  # Backend architecture
  'src/main/kotlin/ee/tenman/portfolio/controller/',  # API endpoints
  'src/main/kotlin/ee/tenman/portfolio/domain/',  # Domain models
  'src/main/kotlin/ee/tenman/portfolio/service/',  # Business logic
  'src/main/kotlin/ee/tenman/portfolio/repository/',  # Data access
  'src/main/kotlin/ee/tenman/portfolio/configuration/',  # App configuration
  'src/main/kotlin/ee/tenman/portfolio/job/',  # Background jobs
  'src/main/kotlin/ee/tenman/portfolio/PortfolioApplication.kt',  # Main app entry point
  'src/main/kotlin/ee/tenman/portfolio/common/',  # Common utilities
  'src/main/kotlin/ee/tenman/portfolio/binance/',  # Binance integration
  'src/main/kotlin/ee/tenman/portfolio/alphavantage/',  # Alpha Vantage integration
  'src/main/kotlin/ee/tenman/portfolio/ft/',  # FT integration
  'src/main/kotlin/ee/tenman/portfolio/telegram/',  # Telegram integration

  # Frontend architecture
  'ui/components/',  # UI components
  'ui/models/',  # Frontend models
  'ui/services/',  # Frontend services
  'ui/router/',  # Frontend routing
  'ui/app.vue',  # Main app component
  'ui/main.ts',  # App entry point
  'ui/index.html',  # HTML entry point
  'ui/decorators/',  # Decorators
  'ui/constants/',  # Constants

  # Price tracker microservice
  'market-price-tracker/main.py',  # Market price tracker component
  'market-price-tracker/models.py',  # Models
  'market-price-tracker/services.py',  # Services
  'market-price-tracker/price_fetcher.py',  # Core business logic
  'market-price-tracker/scheduler.py',  # Scheduler

  # Infrastructure & deployment
  'docker-compose.yml',  # Main docker setup
  'docker-compose.e2e.yml',  # E2E testing setup
  'docker-compose.local.yml',  # Local dev setup
  'Dockerfile',  # Base docker config
  'src/Dockerfile',  # Backend service docker
  'ui/Dockerfile',  # Frontend docker
  'Caddyfile',  # Caddy server config
  'nginx.conf',  # Nginx config
  'vite.config.ts',  # Vite config

  # Database schema & migrations
  'src/main/resources/db/migration/V202406160849__create_initial.sql',  # Initial schema
  'src/main/resources/application.yml',  # App configuration

  # GitHub workflows and configuration - explicitly listed by exact path
  '.github/codeql/codeql-config.yml',  # CodeQL configuration
  '.github/workflows/check-readme-links.sh',  # README links checker script
  '.github/workflows/ci.yml',  # CI workflow
  '.github/workflows/codeql.yml',  # CodeQL workflow
  '.github/workflows/deploy-pipeline.yml',  # Deployment pipeline
  '.github/workflows/dependabot.yml',  # Dependabot configuration
]

# GitHub-specific files to include
GITHUB_FILES = [
  '.github/codeql/codeql-config.yml',
  '.github/workflows/check-readme-links.sh',
  '.github/workflows/ci.yml',
  '.github/workflows/codeql.yml',
  '.github/workflows/deploy-pipeline.yml',
  '.github/workflows/dependabot.yml',
]

# Add GitHub patterns to core architectural patterns
for github_file in GITHUB_FILES:
  if github_file not in CORE_ARCHITECTURAL_PATTERNS:
    CORE_ARCHITECTURAL_PATTERNS.append(github_file)

# Patterns to identify duplicate or non-architectural code
# We've removed general test patterns from here to be more selective
DUPLICATE_PATTERNS = [
  # Documentation-related
  '/docs/', '/documentation/',  # Duplicated docs under src/
  '/examples/', '/samples/',  # Duplicated demo data

  # Public resources
  'ui/public/',  # Public assets
]

# Portfolio-specific data migration files to exclude (keeping only structural ones)
# These add little architectural value - mostly just test data
SKIP_DATA_MIGRATIONS = [
  'V202501271802__data_insertion.sql',
  'V202501061457__data_insertion.sql',
  'V202502281429__data_insertion.sql',
  'V202501291256__update_data.sql',
  'V202504031430__data_insertion.sql',
  'V202412171259__data_deletion.sql',
  'V202406160851__sample_data_insertion.sql',
  'V202409021024__sample_data_insertion.sql',
  'V202410191612__sample_data_insertion.sql',
  'V202407210850__sample_data_insertion.sql',
  'V202501021311__data_deletion.sql',
  'V202412021103__data_deletion.sql',
  'V202410311635__sample_data_insertion.sql',
  'V202408010710__sample_data_insertion.sql',
  'V202411151238__data_insertion.sql',
  'V202410191614__fix_data_insertion.sql',
  'V202410311503__sample_data_insertion.sql',
  'V202503101195__data_insertion.sql',
  'V202406160851__sample_data_insertion_daily_price.sql',
  'V202503041457__data_insertion.sql',
]

# Specific important files to prioritize (will be placed at the top)
PRIORITY_FILES = [
  'README.md',
  'docker-compose.yml',
  'docker-compose.e2e.yml',
  'compose.yaml',
  'docker-compose.local.yml',
  'Caddyfile',
  'nginx.conf',
  'build.gradle.kts',
  'src/main/kotlin/ee/tenman/portfolio/PortfolioApplication.kt',
  'src/main/resources/application.yml',
  'ui/app.vue',
  'ui/main.ts',
  'ui/index.html',
  'market-price-tracker/main.py',
  # Add important SQL files to priority list if they exist
  'src/main/resources/db/migration/V202406160849__create_initial.sql',
]

# GitHub workflow files to prioritize
GITHUB_PRIORITY_FILES = [
  '.github/workflows/ci.yml',
  '.github/workflows/deploy-pipeline.yml',
  '.github/workflows/codeql.yml',
  '.github/codeql/codeql-config.yml',
]

# Add GitHub files to priority list
PRIORITY_FILES.extend(GITHUB_PRIORITY_FILES)

# Maximum file sizes
MAX_FILE_SIZE = 5000  # For most files
MAX_SOURCE_FILE_SIZE = 10000  # Higher limit for source code files (including SQL)
MAX_TEST_FILE_SIZE = 8000  # Specific limit for test files

# Binary file extensions to always skip
BINARY_EXTENSIONS = {
  '.gz', '.zip', '.tar', '.jar', '.war', '.ear', '.class', '.exe', '.dll', '.so',
  '.bin', '.dat', '.pack', '.idx', '.obj', '.o'
}

# Initialize mimetypes
mimetypes.init()


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


def is_safe_path(path: Path, base_dir: Path) -> bool:
  """
  Verify that a file path is safe to access and within the project directory.
  Protects against path traversal attacks and symlink exploits.
  """
  try:
    resolved_path = path.resolve(strict=False)
    resolved_base = base_dir.resolve(strict=True)

    if not str(resolved_path).startswith(str(resolved_base)):
      print(f"Security: Path escapes project directory: {path}")
      return False

    if path.is_symlink():
      symlink_target = resolved_path
      if not str(symlink_target).startswith(str(resolved_base)):
        print(f"Security: Symlink points outside project directory: {path} -> {symlink_target}")
        return False

    parts = path.parts
    if '..' in parts or '.' in parts[1:]:
      print(f"Security: Path contains suspicious components: {path}")
      return False

    return True
  except (OSError, ValueError, RuntimeError) as e:
    print(f"Security: Error validating path {path}: {e}")
    return False


def is_binary_file(path: Path) -> bool:
  """Determine if a file is likely binary."""
  if path.suffix.lower() in BINARY_EXTENSIONS:
    return True

  mimetype, _ = mimetypes.guess_type(str(path))
  if mimetype and not mimetype.startswith('text/') and not mimetype.startswith('application/json'):
    return True

  try:
    with open(path, 'r', encoding='utf-8', errors='strict') as f:
      chunk = f.read(1024)
      if '\0' in chunk:
        return True
    return False
  except (UnicodeDecodeError, IOError, PermissionError):
    return True


def is_important_architectural_file(rel_path: str) -> bool:
  """Checks if a file is part of the core architecture we want to include."""
  # Include GitHub workflow files
  if rel_path.startswith('.github/workflows/') or rel_path.startswith('.github/codeql/'):
    return True

  # Check against the explicit list of architectural file patterns
  return any(pattern in str(rel_path) for pattern in CORE_ARCHITECTURAL_PATTERNS)


def is_important_test_file(rel_path: str) -> bool:
  """Checks if a file is an important test file we want to include."""
  return rel_path in IMPORTANT_TEST_FILES


def is_duplicate_or_unimportant(rel_path: str) -> bool:
  """Checks if a file is likely a duplicate or unimportant for understanding the system."""
  return any(pattern in str(rel_path) for pattern in DUPLICATE_PATTERNS)


def is_data_migration(rel_path: str) -> bool:
  """Check if this is a data migration file that adds little architectural value."""
  for migration in SKIP_DATA_MIGRATIONS:
    if migration in rel_path:
      return True
  return False


def is_in_skip_folder(rel_path: str) -> bool:
  """Check if the path is in a folder that should be completely skipped."""
  # Special handling for .github vs .git
  if rel_path.startswith('.github/'):
    return False

  for folder in SKIP_FOLDERS:
    if rel_path.startswith(folder) or f"/{folder}/" in f"/{rel_path}/":
      return True
  return False


def is_in_limited_include_folder(rel_path: str) -> bool:
  """Check if the path is in a folder with limited inclusion."""
  return any(rel_path.startswith(folder) or f"/{folder}/" in f"/{rel_path}/"
             for folder in LIMITED_INCLUDE_FOLDERS)


def is_source_code_file(path: Path) -> bool:
  """Determine if a file is a source code file that should get the higher size limit."""
  return path.suffix.lower() in SOURCE_CODE_EXTENSIONS


def is_test_file(rel_path: str) -> bool:
  """Determine if a file is a test file."""
  # Patterns for identifying test files
  test_patterns = [
    r'.*Test\.kt$',  # Kotlin test classes
    r'.*Tests\.kt$',  # Kotlin test classes (plural)
    r'.*IT\.kt$',  # Integration tests
    r'.*E2ETests\.kt$',  # End-to-end tests
    r'test_.*\.py$',  # Python test files
    r'.*\.spec\.ts$',  # TypeScript test specs
    r'.*\.test\.ts$',  # TypeScript test files
    r'.*\.spec\.js$',  # JavaScript test specs
    r'.*\.test\.js$',  # JavaScript test files
  ]
  return any(re.match(pattern, rel_path) for pattern in test_patterns)


def should_include_file(path: Path, rel_path: str) -> bool:
  """Determine if a file should be included based on name, extension, or location."""
  # Always include GitHub files
  if rel_path.startswith('.github/'):
    return True

  # Otherwise use standard filters
  return (path.name in ALLOWED_FILENAMES or
          path.suffix.lower() in ALLOWED_EXTENSIONS or
          is_important_test_file(rel_path))


def is_ignored(path: Path, matcher, base_dir: Path) -> bool:
  """Return True if path should be skipped."""
  rel = path.relative_to(base_dir)
  rel_str = str(rel)

  # Special handling for .github directory
  if rel_str.startswith('.github/'):
    return False  # Never ignore .github files

  # First check: Skip folders entirely - this is more efficient
  if is_in_skip_folder(rel_str):
    # Don't even print messages for .git files
    if not rel_str.startswith('.git'):
      print(f"Skipping file in excluded directory: {rel}")
    return True

  # Always include core architectural files
  if is_important_architectural_file(rel_str):
    return False

  # Include important test files
  if is_important_test_file(rel_str):
    return False

  # For limited inclusion folders, only include specific important test files
  if is_in_limited_include_folder(rel_str) and not is_important_test_file(rel_str):
    print(f"Skipping non-essential test file: {rel}")
    return True

  # Skip duplicate or unimportant files
  if is_duplicate_or_unimportant(rel_str):
    print(f"Skipping duplicate or unimportant file: {rel}")
    return True

  # Skip data migration files
  if is_data_migration(rel_str):
    print(f"Skipping data migration file: {rel}")
    return True

  # Handle binary files
  if path.is_file() and is_binary_file(path):
    if path.stat().st_size > MAX_FILE_SIZE:
      print(f"Skipping binary file: {rel} ({path.stat().st_size / 1024:.1f}KB)")
      return True

  # Handle large text files with different limits
  elif path.is_file():
    # Test files get their own size limit
    if is_test_file(rel_str):
      if path.stat().st_size > MAX_TEST_FILE_SIZE:
        print(f"Skipping large test file: {rel} ({path.stat().st_size / 1024:.1f}KB)")
        return True
    # Use a separate function to clearly identify source code files
    elif is_source_code_file(path):
      # More permissive for source code files (including SQL)
      if path.stat().st_size > MAX_SOURCE_FILE_SIZE:
        print(f"Skipping large source file: {rel} ({path.stat().st_size / 1024:.1f}KB)")
        return True
    elif path.suffix.lower() in ALLOWED_EXTENSIONS:
      # Other allowed but non-source files use regular limit
      if path.stat().st_size > MAX_FILE_SIZE:
        print(f"Skipping large file: {rel} ({path.stat().st_size / 1024:.1f}KB)")
        return True
    elif path.stat().st_size > MAX_FILE_SIZE:
      print(f"Skipping large file: {rel} ({path.stat().st_size / 1024:.1f}KB)")
      return True

  # Skip firefox-profile archives specifically
  if 'firefox-profile' in rel_str and path.suffix.lower() in ('.tar', '.gz', '.tar.gz'):
    print(f"Skipping firefox profile: {rel}")
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

  # First collect all files
  all_files = []
  test_files = []
  github_files = []  # Add dedicated list for GitHub files

  # Before processing, print which directories we're skipping entirely
  print("\nSkipping these directories completely:")
  for skip_dir in SKIP_FOLDERS:
    print(f"  - {skip_dir}/")
  print()

  # Get all files, but use custom filtering to completely avoid processing .git directory
  for path in base.rglob('*'):
    # Skip directories themselves
    if not path.is_file():
      continue

    # Security: Validate path is safe before processing
    if not is_safe_path(path, base):
      continue

    rel_path = str(path.relative_to(base))

    # Special case for .github directory - always include
    if rel_path.startswith('.github/'):
      try:
        content = path.read_text(encoding='utf-8', errors='strict')
        # Store GitHub files separately for inclusion and sorting
        github_files.append((path, content))
        print(f"Including GitHub file: {rel_path}")
        continue
      except Exception as e:
        print(f"Error reading GitHub file {path}: {e}")
        continue

    # Skip .git files (but not .github!)
    if rel_path.startswith('.git') and not rel_path.startswith('.github'):
      continue

    if is_ignored(path, matcher, base):
      continue

    # Use our improved function to determine if we should include this file
    if not should_include_file(path, rel_path):
      continue

    try:
      # Security: Use strict error handling to detect encoding issues
      content = path.read_text(encoding='utf-8', errors='strict')

      # Separate test files from main code for better organization
      if is_test_file(rel_path) or is_important_test_file(rel_path):
        test_files.append((path, content))
      else:
        all_files.append((path, content))
    except Exception as e:
      print(f"Skipping {path}: {e}")

  # Now prioritize and write in a specific order
  with open(output_file, 'w', encoding='utf-8') as out:
    print(f"\nWriting {len(all_files) + len(test_files) + len(github_files)} files to {output_file}")

    # First write priority files in order
    priority_written = 0
    for priority_file in PRIORITY_FILES:
      priority_path = base / priority_file
      if priority_path.exists():
        # Check in all collections: regular files, GitHub files, and test files
        for file_list in [all_files, github_files, test_files]:
          for i, (path, content) in enumerate(file_list):
            if path == priority_path:
              out.write(f"File: {path.relative_to(base)}\n")
              out.write(content)
              out.write("\n\n")
              # Remove from the original list to avoid duplicates
              file_list.pop(i)
              priority_written += 1
              break

    print(f"Wrote {priority_written} priority files at the top")

    # Write GitHub files next (those that weren't already written as priority files)
    github_written = 0
    for path, content in github_files:
      out.write(f"File: {path.relative_to(base)}\n")
      out.write(content)
      out.write("\n\n")
      github_written += 1

    print(f"Wrote {github_written} GitHub workflow files")

    # Then write remaining regular files
    for path, content in all_files:
      out.write(f"File: {path.relative_to(base)}\n")
      out.write(content)
      out.write("\n\n")

    # Write a divider for test files section
    if test_files:
      out.write("\n\n")
      out.write("=" * 80 + "\n")
      out.write("TEST FILES - Demonstrating Expected Behavior\n")
      out.write("=" * 80 + "\n\n")

      # Write test files last in their own section
      for path, content in test_files:
        out.write(f"File: {path.relative_to(base)}\n")
        out.write(content)
        out.write("\n\n")

    print(f"Wrote {len(all_files)} additional source files")
    print(f"Wrote {len(test_files)} test files")
    print(f"Total files included: {priority_written + github_written + len(all_files) + len(test_files)}")


if __name__ == '__main__':
  combine_files()
