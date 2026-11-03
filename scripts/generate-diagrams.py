#!/usr/bin/env python3
import hashlib
import os
import subprocess
import sys
import urllib.request
from glob import glob
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION = '1.2024.8'
JAR = f'plantuml-{VERSION}.jar'
URL = f'https://github.com/plantuml/plantuml/releases/download/v{VERSION}/{JAR}'
SHA256 = '2bf9f26f03ad597eb37c4264da2bd3c88c9b5d20f92bc69c96cfb65d83a38a11'


def download():
    if Path(JAR).is_file():
        print(f'PlantUML {VERSION} already exists')
        return
    print(f'Downloading PlantUML {VERSION}...')
    try:
        with urllib.request.urlopen(URL) as response:
            jar = response.read()
    except OSError:
        sys.exit('Error: Failed to download PlantUML')
    print('Verifying checksum...')
    if hashlib.sha256(jar).hexdigest() != SHA256:
        sys.exit('Error: Checksum verification failed!')
    Path(JAR).write_bytes(jar)
    print('PlantUML downloaded and verified successfully')


def invalid(path):
    if not Path(path).is_file():
        return f'Error: File not found: {path}'
    if not path.endswith('.puml'):
        return f'Error: File must have .puml extension: {path}'
    if ROOT not in Path(path).resolve().parents:
        return f'Error: File must be within project directory: {path}'
    return ''


def generate(path):
    error = invalid(path)
    if error:
        print(error, file=sys.stderr)
        return False
    print(f'Generating SVG for: {path}', flush=True)
    if subprocess.run(['java', '-jar', JAR, '-tsvg', '--', path]).returncode:
        print(f'Error: Failed to generate SVG for {path}', file=sys.stderr)
        return False
    print(f'Generated: {Path(path).resolve().with_suffix(".svg")}')
    return True


def main():
    os.chdir(ROOT)
    download()
    if len(sys.argv) == 1:
        print('Generating all diagrams...')
        for path in sorted(glob('docs/architecture/*.puml')) + sorted(glob('screenshots/*.puml')):
            if not generate(path):
                print(f'Warning: Failed to generate diagram for {path}', file=sys.stderr)
        print('All diagrams generated successfully!')
        return
    failed = sum(not generate(path) for path in sys.argv[1:])
    if failed:
        sys.exit(f'Error: Failed to generate {failed} diagram(s)')


if __name__ == '__main__':
    main()
