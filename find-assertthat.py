#!/usr/bin/env python3
"""
Script to find all assertThat usages and report them.
Does NOT make any changes - just reports findings.
"""

import re
from pathlib import Path
from typing import List, Dict
from collections import defaultdict

def find_test_files(base_path: Path) -> List[Path]:
    """Find all Kotlin test files."""
    return list(base_path.rglob("*Test*.kt")) + list(base_path.rglob("*IT.kt"))

def find_assertions(file_path: Path) -> List[Dict[str, any]]:
    """Find all assertThat usages in a file."""
    try:
        content = file_path.read_text(encoding='utf-8')
        lines = content.split('\n')

        findings = []
        for line_num, line in enumerate(lines, 1):
            if 'assertThat' in line:
                findings.append({
                    'line': line_num,
                    'content': line.strip(),
                    'file': file_path
                })

        return findings
    except Exception as e:
        print(f"❌ Error reading {file_path}: {e}")
        return []

def main():
    """Main function to find and report assertThat usages."""
    base_path = Path(__file__).parent / "src" / "test"

    if not base_path.exists():
        print(f"Test directory not found: {base_path}")
        return

    print("🔍 Searching for assertThat usages...\n")

    test_files = find_test_files(base_path)
    all_findings = defaultdict(list)

    for file_path in test_files:
        findings = find_assertions(file_path)
        if findings:
            all_findings[file_path] = findings

    if not all_findings:
        print("✨ No assertThat found - migration already complete!")
        return

    total_count = sum(len(findings) for findings in all_findings.values())

    print(f"📊 Found {total_count} assertThat usages in {len(all_findings)} files:\n")
    print("="  * 80)

    for file_path in sorted(all_findings.keys()):
        findings = all_findings[file_path]
        relative_path = file_path.relative_to(base_path.parent.parent)

        print(f"\n📁 {relative_path} ({len(findings)} usages)")
        print("-" * 80)

        for finding in findings:
            print(f"  Line {finding['line']:4d}: {finding['content']}")

    print("\n" + "=" * 80)
    print(f"\n📈 Summary:")
    print(f"   Total files: {len(all_findings)}")
    print(f"   Total assertThat usages: {total_count}")

    print(f"\n💡 Files to migrate:")
    for file_path in sorted(all_findings.keys()):
        relative_path = file_path.relative_to(base_path.parent.parent)
        count = len(all_findings[file_path])
        print(f"   - {relative_path} ({count})")

if __name__ == "__main__":
    main()
