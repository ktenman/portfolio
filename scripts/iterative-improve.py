#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import logging
import subprocess
import sys
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime
from pathlib import Path

STATE_DIR = Path(".improve-loop")
STATE_FILE = STATE_DIR / "state.json"
LOG_FILE = STATE_DIR / "run.log"
MAX_CI_RETRIES = 3
CLAUDE_TIMEOUT = 600
CI_POLL_INTERVAL = 10
CI_APPEAR_TIMEOUT = 180
CI_RUN_TIMEOUT = 900
MAX_DIFF_CHARS = 12000

logger = logging.getLogger("improve")


def setup_logging():
    STATE_DIR.mkdir(exist_ok=True)
    logger.setLevel(logging.DEBUG)

    console = logging.StreamHandler()
    console.setLevel(logging.INFO)
    console.setFormatter(logging.Formatter("  %(asctime)s [%(message)s", datefmt="%H:%M:%S"))

    file_handler = logging.FileHandler(LOG_FILE, mode="a")
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(
        logging.Formatter("%(asctime)s %(levelname)-5s %(message)s", datefmt="%Y-%m-%d %H:%M:%S")
    )

    logger.addHandler(console)
    logger.addHandler(file_handler)


def run(
    cmd: list[str], timeout: int = 120, check: bool = False
) -> subprocess.CompletedProcess:
    logger.debug("cmd: %s", " ".join(cmd))
    result = subprocess.run(
        cmd, capture_output=True, text=True, timeout=timeout, check=check
    )
    if result.returncode != 0:
        logger.debug("exit=%d stderr=%s", result.returncode, result.stderr[:500])
    return result


def require_tools():
    missing = [tool for tool in ["git", "claude", "gh"] if run(["which", tool]).returncode != 0]
    if missing:
        logger.error("missing] Missing required tools: %s", ", ".join(missing))
        sys.exit(1)


def git_branch() -> str:
    return run(["git", "branch", "--show-current"]).stdout.strip()


def git_has_changes() -> bool:
    return bool(run(["git", "status", "--porcelain"]).stdout.strip())


def git_changed_files() -> list[str]:
    lines = run(["git", "status", "--porcelain"]).stdout.strip().split("\n")
    return [line[3:].strip() for line in lines if line.strip()]


def git_diff_vs_main() -> str:
    return run(["git", "diff", "--name-only", "main...HEAD"]).stdout.strip()


def git_has_conflicts() -> bool:
    result = run(["git", "diff", "--name-only", "--diff-filter=U"])
    return bool(result.stdout.strip())


def git_conflict_files() -> list[str]:
    result = run(["git", "diff", "--name-only", "--diff-filter=U"])
    return [f for f in result.stdout.strip().split("\n") if f]


def sync_with_main(branch: str) -> bool:
    logger.info("sync] Fetching origin/main...")
    fetch = run(["git", "fetch", "origin", "main"])
    if fetch.returncode != 0:
        logger.warning("sync] Fetch failed: %s", fetch.stderr.strip())
        return True

    behind = run(["git", "rev-list", "--count", "HEAD..origin/main"])
    count = behind.stdout.strip()
    if count == "0":
        logger.info("sync] Branch is up to date with main")
        return True

    logger.info("sync] Branch is %s commit(s) behind main, merging...", count)
    merge = run(["git", "merge", "origin/main", "--no-edit"])

    if merge.returncode == 0:
        logger.info("sync] Merged cleanly")
        run(["git", "push", "-u", "origin", branch])
        return True

    if not git_has_conflicts():
        logger.warning("sync] Merge failed but no conflicts detected")
        run(["git", "merge", "--abort"])
        return False

    return resolve_conflicts(branch)


def resolve_conflicts(branch: str) -> bool:
    conflicts = git_conflict_files()
    logger.warning("sync] Merge conflicts in %d file(s): %s", len(conflicts), ", ".join(conflicts[:5]))

    file_list = "\n".join(conflicts)
    prompt = (
        "There are git merge conflicts that need resolving.\n\n"
        f"Conflicted files:\n{file_list}\n\n"
        "Instructions:\n"
        "- Read each conflicted file\n"
        "- Resolve conflicts by keeping the correct code (merge both sides logically)\n"
        "- Remove all conflict markers (<<<<<<, ======, >>>>>>)\n"
        "- Make sure the resolved code compiles and is correct\n"
        "- Run lint/format/test commands if appropriate\n"
        '- Output one line starting with "SUMMARY:" describing what you resolved'
    )

    logger.info("sync] Asking Claude to resolve conflicts...")
    output, _ = run_claude(prompt)

    if git_has_conflicts():
        logger.error("sync] Conflicts remain after Claude attempted resolution")
        run(["git", "merge", "--abort"])
        return False

    run(["git", "add", "-A"], check=True)
    summary = extract_summary(output)
    commit = run(["git", "commit", "--no-edit"])
    if commit.returncode != 0:
        commit = run(["git", "commit", "-m", f"Resolve merge conflicts: {summary[:40]}"])
    if commit.returncode != 0:
        logger.error("sync] Failed to commit merge resolution")
        run(["git", "merge", "--abort"])
        return False

    push = run(["git", "push", "-u", "origin", branch])
    if push.returncode != 0:
        logger.warning("sync] Push failed after conflict resolution: %s", push.stderr.strip())
        return False

    logger.info("sync] Conflicts resolved and pushed")
    return True


def git_commit_and_push(message: str, branch: str) -> bool:
    run(["git", "add", "-A"], check=True)
    commit = run(["git", "commit", "-m", message])
    if commit.returncode != 0:
        logger.warning("git] Commit failed: %s", commit.stderr.strip())
        return False
    push = run(["git", "push", "-u", "origin", branch])
    if push.returncode != 0:
        logger.warning("git] Push failed: %s", push.stderr.strip())
        return False
    logger.info("git] Pushed: %s", message)
    return True


def get_latest_run_id(branch: str) -> int | None:
    result = run(
        ["gh", "run", "list", "--branch", branch, "--limit", "1", "--json", "databaseId"]
    )
    if result.returncode != 0:
        return None
    runs = json.loads(result.stdout)
    return runs[0]["databaseId"] if runs else None


def wait_for_new_run(branch: str, previous_id: int | None) -> int | None:
    deadline = time.time() + CI_APPEAR_TIMEOUT
    while time.time() < deadline:
        current_id = get_latest_run_id(branch)
        if current_id and current_id != previous_id:
            return current_id
        time.sleep(CI_POLL_INTERVAL)
    return None


def wait_for_ci(branch: str) -> tuple[bool, str, float]:
    start = time.monotonic()
    previous_id = get_latest_run_id(branch)
    logger.info("ci] Waiting for CI run...")

    run_id = wait_for_new_run(branch, previous_id)
    if not run_id:
        logger.info("ci] No CI run detected, skipping")
        return True, "", time.monotonic() - start

    logger.info("ci] Watching run #%d...", run_id)
    result = run(
        ["gh", "run", "watch", str(run_id), "--exit-status"],
        timeout=CI_RUN_TIMEOUT,
    )

    elapsed = time.monotonic() - start
    if result.returncode == 0:
        logger.info("ci] Passed in %s", format_duration(elapsed))
        return True, "", elapsed

    logger.warning("ci] Failed after %s — fetching error logs...", format_duration(elapsed))
    logs = run(["gh", "run", "view", str(run_id), "--log-failed"], timeout=60)
    return False, (logs.stdout[-4000:] if logs.stdout else "No logs available"), elapsed


TOOL_SUMMARY_KEYS = {
    "Bash": "command",
    "Read": "file_path",
    "Edit": "file_path",
    "Write": "file_path",
    "Glob": "pattern",
    "Grep": "pattern",
    "Agent": "description",
    "Skill": "skill",
}


def _summarize_tool_input(tool: str, raw_json: str) -> str:
    key = TOOL_SUMMARY_KEYS.get(tool)
    if not key or not raw_json:
        return tool
    try:
        data = json.loads(raw_json)
        value = data.get(key, "")
        if not value:
            return tool
        truncated = (value[:80] + "...") if len(value) > 80 else value
        return f"{tool} > {truncated}"
    except json.JSONDecodeError:
        return tool


def git_diff_content() -> str:
    result = run(["git", "diff", "main...HEAD"], timeout=30)
    diff = result.stdout.strip()
    if len(diff) > MAX_DIFF_CHARS:
        return diff[:MAX_DIFF_CHARS] + f"\n\n... (truncated, {len(diff)} total chars)"
    return diff


def run_claude(prompt: str, model: str = "", fast: bool = False) -> tuple[str, float]:
    logger.info("claude] Running...")
    logger.debug("claude] prompt length: %d chars", len(prompt))
    start = time.monotonic()
    cmd = [
        "claude", "-p",
        "--output-format", "stream-json",
        "--include-partial-messages",
        "--dangerously-skip-permissions",
        "--effort", "max",
    ]
    if model:
        cmd.extend(["--model", model])
    if fast:
        cmd.append("--fast")
    process = subprocess.Popen(
        cmd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    process.stdin.write(prompt)
    process.stdin.close()

    result_text = ""
    has_streamed = False
    current_tool = ""
    tool_input_chunks: list[str] = []

    for line in process.stdout:
        line = line.strip()
        if not line:
            continue
        try:
            event = json.loads(line)
            event_type = event.get("type", "")

            if event_type == "stream_event":
                inner = event.get("event", {})
                delta = inner.get("delta", {})
                inner_type = inner.get("type", "")

                if delta.get("type") == "text_delta":
                    sys.stdout.write(delta["text"])
                    sys.stdout.flush()
                    has_streamed = True

                elif inner_type == "content_block_start":
                    block = inner.get("content_block", {})
                    if block.get("type") == "tool_use":
                        if has_streamed:
                            sys.stdout.write("\n")
                            has_streamed = False
                        current_tool = block.get("name", "?")
                        tool_input_chunks = []

                elif delta.get("type") == "input_json_delta":
                    tool_input_chunks.append(delta.get("partial_json", ""))

                elif inner_type == "content_block_stop" and current_tool:
                    detail = _summarize_tool_input(current_tool, "".join(tool_input_chunks))
                    logger.info("claude] %s", detail)
                    current_tool = ""

            elif event_type == "result":
                result_text = event.get("result", "")

        except json.JSONDecodeError:
            logger.debug("claude] unparseable line")

    if has_streamed:
        sys.stdout.write("\n")
    stderr = process.stderr.read()
    process.wait()
    elapsed = time.monotonic() - start

    if process.returncode != 0 and stderr:
        logger.warning("claude] stderr: %s", stderr[:300])

    logger.info("claude] Done in %s", format_duration(elapsed))
    logger.debug("claude] output length: %d chars", len(result_text))
    return result_text, elapsed


def build_phase_prompt(phase: str, branch_diff: str, context: str, diff_content: str = "") -> str:
    focus = {
        "simplify": (
            "simplification opportunities:\n"
            "- Duplicated code that can be extracted\n"
            "- Overly complex logic that can be simplified\n"
            "- Inefficient patterns\n"
            "- Dead or unreachable code"
        ),
        "review": (
            "quality issues:\n"
            "- Bugs and correctness problems\n"
            "- Security vulnerabilities\n"
            "- Performance issues\n"
            "- Missing edge case handling"
        ),
        "batch": (
            "BOTH simplification AND quality issues in a single pass:\n"
            "Simplification:\n"
            "- Duplicated code that can be extracted\n"
            "- Overly complex logic that can be simplified\n"
            "- Inefficient patterns\n"
            "- Dead or unreachable code\n"
            "Quality:\n"
            "- Bugs and correctness problems\n"
            "- Security vulnerabilities\n"
            "- Performance issues\n"
            "- Missing edge case handling"
        ),
    }[phase]

    diff_section = ""
    if diff_content:
        diff_section = f"\nDiff content (for reference, read full files before editing):\n```\n{diff_content}\n```\n"

    return (
        f"Review the code changed on this branch (vs main) for {focus}\n\n"
        f"Files changed on this branch:\n{branch_diff}\n"
        f"{diff_section}\n"
        f"Previous iterations already addressed:\n{context}\n\n"
        "Instructions:\n"
        "- Focus on NEW issues not already fixed\n"
        "- Make changes directly to the files\n"
        "- After editing, run the project's lint/format/test commands if appropriate\n"
        '- If nothing needs changing, say "NO_CHANGES_NEEDED"\n'
        '- Output exactly one line starting with "SUMMARY:" describing what you changed'
    )


def build_ci_fix_prompt(errors: str) -> str:
    return (
        "CI/CD pipeline failed. Fix the errors with minimal changes.\n\n"
        f"Error logs:\n{errors}\n\n"
        "Instructions:\n"
        "- Fix only what's needed to pass CI\n"
        "- Run lint/format/test commands after fixing\n"
        '- Output one line starting with "SUMMARY:" describing the fix'
    )


def extract_summary(output: str) -> str:
    for line in output.split("\n"):
        stripped = line.strip()
        if stripped.upper().startswith("SUMMARY:"):
            return stripped[8:].strip()
    for line in output.split("\n"):
        stripped = line.strip()
        if len(stripped) > 15:
            return stripped
    return "Code improvements"


ACTION_VERBS = {"add", "fix", "update", "remove", "extract", "simplify", "refactor", "replace", "move", "rename", "clean"}


def build_commit_message(phase: str, summary: str) -> str:
    clean = summary.replace("`", "").replace("*", "").strip()
    first_word = clean.split()[0].lower() if clean else ""
    if first_word in ACTION_VERBS:
        message = clean[0].upper() + clean[1:]
    elif phase == "simplify":
        message = f"Simplify {clean[0].lower() + clean[1:]}" if clean else "Simplify code"
    else:
        message = f"Fix {clean[0].lower() + clean[1:]}" if clean else "Fix code issues"
    if len(message) <= 50:
        return message
    truncated = message[:47]
    last_space = truncated.rfind(" ")
    if last_space > 20:
        return truncated[:last_space] + "..."
    return truncated + "..."


def format_duration(seconds: float) -> str:
    if seconds < 60:
        return f"{seconds:.1f}s"
    minutes = int(seconds // 60)
    secs = int(seconds % 60)
    if minutes < 60:
        return f"{minutes}m {secs}s"
    hours = int(minutes // 60)
    return f"{hours}h {minutes % 60}m {secs}s"


@dataclass
class PhaseResult:
    iteration: int
    phase: str
    changes_made: bool
    files: list[str]
    summary: str
    ci_passed: bool
    ci_retries: int
    duration_seconds: float = 0.0
    claude_seconds: float = 0.0
    ci_seconds: float = 0.0


@dataclass
class LoopState:
    branch: str
    started_at: str
    results: list[dict] = field(default_factory=list)

    def add(self, result: PhaseResult):
        self.results.append(asdict(result))

    def context(self) -> str:
        changed = [r for r in self.results if r["changes_made"]]
        if not changed:
            return "None (first iteration)"
        return "\n".join(f"- [{r['phase']}] {r['summary']}" for r in changed)

    def save(self):
        STATE_DIR.mkdir(exist_ok=True)
        STATE_FILE.write_text(json.dumps(asdict(self), indent=2))


def run_phase(
    phase: str, iteration: int, state: LoopState, skip_ci: bool,
    model: str = "", fast: bool = False,
) -> PhaseResult:
    phase_start = time.monotonic()
    total_claude = 0.0
    total_ci = 0.0
    diff_content = git_diff_content()
    prompt = build_phase_prompt(phase, git_diff_vs_main(), state.context(), diff_content)

    logger.info("%s] Running %s...", phase, phase)
    output, claude_time = run_claude(prompt, model=model, fast=fast)
    total_claude += claude_time

    if not git_has_changes():
        logger.info("%s] No changes", phase)
        elapsed = time.monotonic() - phase_start
        return PhaseResult(iteration, phase, False, [], "No changes needed", True, 0, elapsed, total_claude, 0.0)

    files = git_changed_files()
    summary = extract_summary(output)
    logger.info("%s] Changed %d file(s): %s", phase, len(files), ", ".join(files[:5]))

    commit_msg = build_commit_message(phase, summary)
    if not git_commit_and_push(commit_msg, state.branch):
        elapsed = time.monotonic() - phase_start
        return PhaseResult(iteration, phase, True, files, summary, False, 0, elapsed, total_claude, 0.0)

    if skip_ci:
        elapsed = time.monotonic() - phase_start
        return PhaseResult(iteration, phase, True, files, summary, True, 0, elapsed, total_claude, 0.0)

    ci_passed, ci_errors, ci_time = wait_for_ci(state.branch)
    total_ci += ci_time
    retries = 0

    while not ci_passed and retries < MAX_CI_RETRIES:
        retries += 1
        logger.info("ci-fix] Attempt %d/%d...", retries, MAX_CI_RETRIES)
        _, fix_claude_time = run_claude(build_ci_fix_prompt(ci_errors), model=model, fast=fast)
        total_claude += fix_claude_time

        if not git_has_changes():
            logger.info("ci-fix] No fix produced")
            break

        git_commit_and_push(f"Fix CI after {phase} (attempt {retries})", state.branch)
        ci_passed, ci_errors, ci_time = wait_for_ci(state.branch)
        total_ci += ci_time

    elapsed = time.monotonic() - phase_start
    logger.info(
        "%s] Phase done in %s (claude: %s, ci: %s)",
        phase, format_duration(elapsed), format_duration(total_claude), format_duration(total_ci),
    )
    return PhaseResult(iteration, phase, True, files, summary, ci_passed, retries, elapsed, total_claude, total_ci)


def print_summary(state: LoopState, total_elapsed: float):
    total_changed = sum(1 for r in state.results if r["changes_made"])
    total_ci_fixes = sum(r["ci_retries"] for r in state.results)
    total_claude = sum(r.get("claude_seconds", 0) for r in state.results)
    total_ci = sum(r.get("ci_seconds", 0) for r in state.results)

    lines = [
        f"\n{'=' * 60}",
        "RESULTS",
        f"{'=' * 60}",
        f"  Phases run:     {len(state.results)}",
        f"  With changes:   {total_changed}",
        f"  CI fixes:       {total_ci_fixes}",
        f"  Total time:     {format_duration(total_elapsed)}",
        f"  Claude time:    {format_duration(total_claude)}",
        f"  CI time:        {format_duration(total_ci)}",
        f"  Overhead:       {format_duration(total_elapsed - total_claude - total_ci)}",
        "",
    ]
    for r in state.results:
        marker = "+" if r["changes_made"] else " "
        ci = "PASS" if r["ci_passed"] else "FAIL"
        duration = format_duration(r.get("duration_seconds", 0))
        lines.append(f"  [{marker}] {r['phase']:10s} | CI:{ci} | {duration:>9s} | {r['summary']}")
    lines.append(f"\n  State: {STATE_FILE}")
    lines.append(f"  Log:   {LOG_FILE}")

    summary = "\n".join(lines)
    print(summary)
    logger.debug(summary)


def main():
    parser = argparse.ArgumentParser(
        description="Iterative code improvement with CI monitoring"
    )
    parser.add_argument("-n", "--iterations", type=int, default=10)
    parser.add_argument("--ci-timeout", type=int, default=15, help="CI timeout in minutes")
    parser.add_argument("--skip-ci", action="store_true")
    parser.add_argument("--batch", action="store_true", help="Combine simplify+review into one Claude call per iteration (faster)")
    parser.add_argument("--model", type=str, default="", help="Claude model override (e.g., sonnet for speed)")
    parser.add_argument("--fast", action="store_true", help="Use Claude fast output mode")
    args = parser.parse_args()

    setup_logging()
    require_tools()

    global CI_RUN_TIMEOUT
    CI_RUN_TIMEOUT = args.ci_timeout * 60

    branch = git_branch()
    if branch in ("main", "master"):
        logger.error("loop] Cannot run on main/master, switch to a feature branch")
        sys.exit(1)

    state = LoopState(branch=branch, started_at=datetime.now().isoformat())

    mode = "batch" if args.batch else "simplify+review"
    header = (
        f"\n{'=' * 50}\n"
        f"  Iterative Improvement Loop\n"
        f"  Branch:     {branch}\n"
        f"  Iterations: {args.iterations}\n"
        f"  Mode:       {mode}\n"
        f"  CI:         {'skip' if args.skip_ci else f'{args.ci_timeout}m timeout'}\n"
        f"  Model:      {args.model or 'default'}\n"
        f"  Fast:       {args.fast}\n"
        f"{'=' * 50}"
    )
    print(header)
    logger.info(
        "loop] Started: branch=%s iterations=%d skip_ci=%s batch=%s model=%s fast=%s",
        branch, args.iterations, args.skip_ci, args.batch, args.model or "default", args.fast,
    )

    if not sync_with_main(branch):
        logger.error("loop] Cannot sync with main, aborting")
        sys.exit(1)

    loop_start = time.monotonic()
    claude_opts = {"model": args.model, "fast": args.fast}

    for i in range(1, args.iterations + 1):
        print(f"\n--- Iteration {i}/{args.iterations} ---")
        logger.info("loop] === Iteration %d/%d ===", i, args.iterations)

        if not sync_with_main(branch):
            logger.error("loop] Merge conflict could not be resolved, stopping")
            break

        if args.batch:
            result = run_phase("batch", i, state, args.skip_ci, **claude_opts)
            state.add(result)
            state.save()

            if not result.ci_passed:
                logger.warning("loop] Stopping: CI failed after batch")
                break

            if not result.changes_made:
                logger.info("loop] Converged: no changes needed")
                break
        else:
            simplify = run_phase("simplify", i, state, args.skip_ci, **claude_opts)
            state.add(simplify)
            state.save()

            if not simplify.ci_passed:
                logger.warning("loop] Stopping: CI failed after simplify")
                break

            review = run_phase("review", i, state, args.skip_ci, **claude_opts)
            state.add(review)
            state.save()

            if not review.ci_passed:
                logger.warning("loop] Stopping: CI failed after review")
                break

            if not simplify.changes_made and not review.changes_made:
                logger.info("loop] Converged: no changes in either phase")
                break

    total = time.monotonic() - loop_start
    logger.info("loop] Finished in %s", format_duration(total))
    print_summary(state, total)


if __name__ == "__main__":
    main()
