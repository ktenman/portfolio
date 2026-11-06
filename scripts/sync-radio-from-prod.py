#!/usr/bin/env python3
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

LOCAL = Path('radio-data/recordings')
AUDIO = re.compile(r'episode\.m4a|raw(-part-[0-9]{3})?\.mp3')
MOUNT = '{{ range .Mounts }}{{ if eq .Destination "/recordings" }}{{ .Source }}{{ end }}{{ end }}'


def main():
    os.chdir(Path(__file__).resolve().parent.parent)
    settings = dict(line.split('=', 1) for line in Path('.env.sync').read_text().splitlines() if '=' in line)
    prod = f"root@{settings['SYNC_SSH'].split('@', 1)[-1]}"
    remote = subprocess.run(['ssh', prod, f"docker inspect backend -f '{MOUNT}'"],
                            capture_output=True, text=True, check=True).stdout.strip()
    if not remote:
        sys.exit('The backend container has no /recordings mount')
    LOCAL.mkdir(parents=True, exist_ok=True)
    print('Copying production radio recordings...')
    rsync('-rtz', '--delete', '--exclude=*.mp3', '--exclude=*.m4a', '--exclude=upload.json', f'{prod}:{remote}/', f'{LOCAL}/')
    for recording in LOCAL.iterdir():
        if recording.is_dir() and not recording.name.startswith('.') and not (recording / 'status.json').is_file():
            shutil.rmtree(recording)
    rsync('-rtz', '--include=*/', '--include=*.mp3', '--include=*.m4a', '--exclude=*', f'{prod}:{remote}/', f'{LOCAL}/')
    print('Fetching the audio production keeps only in its bucket...')
    with tempfile.TemporaryDirectory(prefix='portfolio-radio.') as uploads:
        rsync('-rt', '--include=*/', '--include=upload.json', '--exclude=*', f'{prod}:{remote}/', f'{uploads}/')
        for upload in sorted(Path(uploads).glob('*/upload.json')):
            fetch(prod, upload.parent.name, json.loads(upload.read_text())['files'])
    print('Done')


def rsync(*args):
    subprocess.run(['rsync', *args], check=True)


def fetch(prod, name, files):
    manifest = LOCAL / name / 'upload.json'
    stored = json.loads(manifest.read_text())['files'] if manifest.is_file() else {}
    for file in sorted(files):
        target = LOCAL / name / file
        if not AUDIO.fullmatch(file) or target.is_file() or file in stored:
            continue
        print(f'  {name}/{file}')
        part = target.with_name(f'{file}.part')
        with part.open('wb') as out:
            subprocess.run(['ssh', prod, f'docker exec backend curl -sf localhost:8080/api/radio/{name}/{file}'],
                           stdout=out, check=True)
        part.rename(target)


if __name__ == '__main__':
    main()
