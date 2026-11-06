#!/usr/bin/env python3
import os
import subprocess
import tempfile
from pathlib import Path

COMPOSE = ['docker', 'compose', '-f', 'compose.yaml']


def main():
    os.chdir(Path(__file__).resolve().parent.parent)
    settings = dict(line.split('=', 1) for line in Path('.env.sync').read_text().splitlines() if '=' in line)
    with tempfile.NamedTemporaryFile(prefix='portfolio-prod.', suffix='.dump') as dump:
        print('Dumping production database...')
        remote = f"docker exec {settings['SYNC_CONTAINER']} sh -c 'pg_dump -U $POSTGRES_USER -Fc {settings['SYNC_DB']}'"
        subprocess.run(['ssh', settings['SYNC_SSH'], remote], stdout=dump, check=True)
        print(f"Dump size: {subprocess.run(['du', '-h', dump.name], capture_output=True, text=True).stdout.split()[0]}")
        print('Restoring into local postgres-dev...')
        subprocess.run(COMPOSE + ['up', '-d', '--wait', 'postgres-dev', 'redis-dev'], stdout=subprocess.DEVNULL, check=True)
        subprocess.run(COMPOSE + ['exec', '-T', 'postgres-dev', 'psql', '-U', 'postgres', '-d', 'postgres', '-q',
                                  '-c', 'DROP DATABASE IF EXISTS portfolio WITH (FORCE)', '-c', 'CREATE DATABASE portfolio'],
                       check=True)
        dump.seek(0)
        subprocess.run(COMPOSE + ['exec', '-T', 'postgres-dev', 'pg_restore', '-U', 'postgres', '-d', 'portfolio',
                                  '--no-owner', '--no-privileges'], stdin=dump, check=True)
    print('Flushing local Redis cache...')
    subprocess.run(COMPOSE + ['exec', '-T', 'redis-dev', 'redis-cli', 'FLUSHALL'], stdout=subprocess.DEVNULL)
    print('Done')


if __name__ == '__main__':
    main()
