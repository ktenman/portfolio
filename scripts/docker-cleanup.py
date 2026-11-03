#!/usr/bin/env python3
import subprocess
import time

LOG = '/var/log/docker-cleanup.log'
VACUUM = 'lock\nvolume.vacuum -garbageThreshold 0.1\nunlock\n'


def available():
    return int(subprocess.run(['df', '/', '--output=avail'], capture_output=True, text=True).stdout.split()[-1])


def stamp():
    return time.strftime('%Y-%m-%d %H:%M:%S')


def run(log, command, data=None):
    subprocess.run(command.split(), input=data, stdout=log, stderr=log, text=True)


def main():
    with open(LOG, 'a', buffering=1) as log:
        print(f'[{stamp()}] Starting cleanup...', file=log)
        before = available()
        run(log, 'journalctl --vacuum-size=100M')
        run(log, 'find /var/lib/docker/volumes/githubuser_redis_data/_data -maxdepth 1 -name temp-*.rdb -mmin +60 -delete')
        run(log, 'docker container prune -f --filter until=1h')
        builders = subprocess.run(['docker', 'ps', '-a', '--filter', 'name=buildx_buildkit', '--filter', 'status=exited',
                                   '--format', '{{.Names}}'], capture_output=True, text=True).stdout.split()
        for builder in builders:
            print(f'Removing stale builder: {builder}', file=log)
            run(log, f'docker rm {builder}')
        run(log, 'docker volume prune -f')
        run(log, 'docker builder prune -af')
        run(log, 'docker image prune -af')
        run(log, 'docker exec -i seaweedfs weed shell', VACUUM)
        print(f'[{stamp()}] Cleanup complete. Freed: {int((available() - before) / 1024)}MB', file=log)
        print('---', file=log)


if __name__ == '__main__':
    main()
