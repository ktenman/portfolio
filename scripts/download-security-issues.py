#!/usr/bin/env python3
import json
import os
import re
import shutil
import subprocess
import sys
import time
from collections import Counter
from pathlib import Path

DIRECTORY = Path(__file__).resolve().parent
REPORT = DIRECTORY / 'security-issues-report.md'
CSV = DIRECTORY / 'security-issues.csv'
PRIORITY = DIRECTORY / 'top-50-fixes.md'
ALERTS = DIRECTORY / 'alerts.json'
REPO = os.environ.get('GITHUB_REPO') or 'ktenman/portfolio'


def field(alert, path, default):
    for key in path.split('.'):
        alert = (alert or {}).get(key)
    return default if alert is None or alert is False else alert


def severity(alert):
    return field(alert, 'rule.severity', 'unknown')


def location(alert):
    return f"{field(alert, 'most_recent_instance.location.path', 'unknown')}:{field(alert, 'most_recent_instance.location.start_line', 0)}"


def message(alert, default):
    return field(alert, 'most_recent_instance.message.text', default)


def severities(alerts):
    return sorted(Counter(map(severity, alerts)).items(), reverse=True)


def rules(alerts):
    groups = {}
    for alert in sorted(alerts, key=lambda alert: field(alert, 'rule.id', 'unknown')):
        groups.setdefault(field(alert, 'rule.id', 'unknown'), []).append(alert)
    return sorted(groups.values(), key=len)[::-1]


def detail(alert):
    return '\n'.join([
        f"#### Issue #{alert['number']}: {field(alert, 'rule.name', 'Unknown')}",
        '',
        f"**Rule ID**: `{field(alert, 'rule.id', 'unknown')}`  ",
        f'**File**: `{location(alert)}`  ',
        f"**Created**: {field(alert, 'created_at', 'unknown')}  ",
        f"**State**: {field(alert, 'state', 'unknown')}  ",
        '',
        f"**Description**: {field(alert, 'rule.description', 'No description available')}",
        '',
        f"**Message**: {message(alert, 'No message available')}",
        '',
        '**Location**: ',
        f"- Path: `{field(alert, 'most_recent_instance.location.path', 'unknown')}`",
        f"- Lines: {field(alert, 'most_recent_instance.location.start_line', 0)}-{field(alert, 'most_recent_instance.location.end_line', 0)}",
        '',
        '---',
        '',
        '',
    ])


def entries(alerts, start, limit):
    chosen = sorted(alerts, key=lambda alert: field(alert, 'created_at', '9999'))[:limit]
    return ''.join('\n'.join([
        f"### {start + index}. {field(alert, 'rule.name', 'Unknown')} ({severity(alert).upper()})",
        f"- **Issue #**: {alert['number']}",
        f'- **File**: `{location(alert)}`',
        f"- **Rule**: `{field(alert, 'rule.id', 'unknown')}`",
        f"- **Issue**: {message(alert, 'No message available')}",
        f"- **Created**: {field(alert, 'created_at', 'unknown')}",
        '',
        '',
    ]) for index, alert in enumerate(chosen, 1))


def report(alerts, now):
    lines = ['# Security Issues Report', '', f'Generated: {now}  ', f'Repository: {REPO}  ',
             f'Total Open Issues: {len(alerts)}', '', '## Summary by Severity', '']
    lines += [f'- **{name.upper()}**: {count} issues' for name, count in severities(alerts)]
    lines += ['', '## Summary by Rule', '']
    lines += [f"- **{field(group[0], 'rule.id', 'unknown')}** ({severity(group[0])}): {len(group)} - "
              f"{field(group[0], 'rule.name', 'Unknown')}" for group in rules(alerts)]
    lines += ['', '## Detailed Issues', '']
    text = '\n'.join(lines) + '\n'
    for level in ('critical', 'high', 'medium', 'low', 'warning', 'note'):
        chosen = [alert for alert in alerts if severity(alert) == level]
        if chosen:
            text += f'### {level.upper()} Severity Issues ({len(chosen)})\n\n' + ''.join(map(detail, chosen))
    return text


def csv(alerts):
    rows = [[alert['number'], severity(alert), field(alert, 'rule.id', 'unknown'),
             field(alert, 'most_recent_instance.location.path', 'unknown'),
             field(alert, 'most_recent_instance.location.start_line', 0),
             re.sub('[,\n]', ' ', message(alert, 'No message'))] for alert in alerts]
    cells = [','.join(str(cell) if isinstance(cell, int) else '"' + cell.replace('"', '""') + '"' for cell in row) for row in rows]
    return '\n'.join(['Number,Severity,Rule,File,Line,Message'] + cells) + '\n'


def priority(alerts, now):
    critical = [alert for alert in alerts if severity(alert) in ('critical', 'high')]
    medium = [alert for alert in alerts if severity(alert) == 'medium']
    low = [alert for alert in alerts if severity(alert) == 'low']
    text = (f'# Top 50 Security Issues to Fix\n\nGenerated: {now}\n\n## Priority Issues by Severity\n\n'
            'These are the top 50 most critical security issues that should be addressed first, '
            'sorted by severity and creation date.\n\n')
    if critical:
        text += '### Critical and High Severity Issues\n\n' + entries(critical, 0, 50)
    shown = len(critical)
    if shown < 50 and medium:
        text += '\n### Additional Medium Severity Issues\n\n' if critical else '### Medium Severity Issues\n\n'
        text += entries(medium, shown, 50 - shown)
        shown = min(shown + len(medium), 50)
    if shown < 50 and low:
        text += '\n### Additional Low Severity Issues\n\n' + entries(low, shown, 50 - shown)
    text += '\n## Summary Statistics\n\n'
    for group in rules(alerts)[:10]:
        text += (f"- **{field(group[0], 'rule.name', 'Unknown')}** ({field(group[0], 'rule.id', 'unknown')}): "
                 f'{len(group)} occurrences - {severity(group[0]).upper()}\n')
    return text


def merge(output):
    decoder = json.JSONDecoder()
    alerts = []
    index = 0
    while index < len(output):
        page, index = decoder.raw_decode(output, index)
        alerts += page
        while output[index:index + 1].isspace():
            index += 1
    return alerts


def fetch():
    if not re.fullmatch(r'[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+', REPO):
        sys.exit('Error: Invalid repository format. Expected: owner/repo')
    if not shutil.which('gh'):
        sys.exit('Error: Missing required dependencies: gh (GitHub CLI)\n\n'
                 'Installation:\n  macOS: brew install gh\n  Linux: apt install gh or yum install gh')
    print('Checking GitHub CLI authentication...')
    if subprocess.run(['gh', 'auth', 'status'], capture_output=True).returncode:
        sys.exit('Error: Not authenticated with GitHub CLI\n\n'
                 'Please run: gh auth login\nAnd follow the prompts to authenticate.')
    print('✅ GitHub CLI is authenticated')
    print(f'Fetching code scanning alerts for {REPO}...')
    result = subprocess.run(['gh', 'api', '-H', 'Accept: application/vnd.github+json', '-H', 'X-GitHub-Api-Version: 2022-11-28',
                             f'/repos/{REPO}/code-scanning/alerts?state=open&per_page=100', '--paginate'],
                            capture_output=True, text=True)
    if result.returncode:
        sys.exit(f'Error: Failed to fetch alerts from GitHub API\nResponse: {(result.stdout + result.stderr).strip()}')
    return result.stdout.rstrip('\n')


def main():
    output = fetch()
    if output in ('', '[]'):
        print('No code scanning alerts found.\n\nPossible reasons:\n'
              '1. Code scanning might not be enabled for this repository\n2. There are no open security alerts\n'
              '3. You might not have the required permissions\n\nTo check manually, visit:\n'
              f'https://github.com/{REPO}/security/code-scanning')
        return
    ALERTS.write_text(output + '\n')
    alerts = merge(output)
    print(f'Found {len(alerts)} open security alerts')
    now = time.strftime('%Y-%m-%d %H:%M:%S UTC', time.gmtime())
    REPORT.write_text(report(alerts, now))
    print('Creating CSV summary...')
    CSV.write_text(csv(alerts))
    PRIORITY.write_text(priority(alerts, now))
    print(f'\n✅ Security report generated successfully!\n\n📄 Files created:\n  - {REPORT} (Full markdown report)\n'
          f'  - {CSV} (CSV summary)\n  - {PRIORITY} (Top 50 priority fixes)\n  - {ALERTS} (Raw JSON data)\n\n📊 Quick Summary:')
    for name, count in severities(alerts):
        print(f'  - {name.upper()}: {count}')
    print('\n🔍 Preview of top issues:')
    for alert in [alert for alert in alerts if severity(alert) in ('critical', 'high', 'medium')][:3]:
        path = field(alert, 'most_recent_instance.location.path', 'unknown')
        print(f"  - {field(alert, 'rule.name', 'Unknown')} in {path.split('/')[-1]}:{field(alert, 'most_recent_instance.location.start_line', 0)}")


if __name__ == '__main__':
    main()
