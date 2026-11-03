import http.client
import json
import os
import socket
import time
import urllib.request
from pathlib import Path

DISK_LIMIT = 85
SWAP_LIMIT = 75
SERVICES = ('postgres', 'redis', 'backend', 'frontend', 'auth', 'app')


class DockerSocket(http.client.HTTPConnection):
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX)
        self.sock.connect('/var/run/docker.sock')


def log(message):
    print(f"{time.strftime('%a %b %e %H:%M:%S %Z %Y')}: {message}", flush=True)


def health(service):
    connection = DockerSocket('localhost')
    try:
        connection.request('GET', f'/containers/{service}/json')
        container = json.load(connection.getresponse())
    except (OSError, ValueError, http.client.HTTPException):
        return ''
    return container.get('State', {}).get('Health', {}).get('Status') or 'null'


def disk_percent():
    disk = os.statvfs('/')
    used = disk.f_blocks - disk.f_bfree
    total = used + disk.f_bavail
    return (used * 100 + total // 2) // total


def swap_percent(meminfo):
    values = dict(line.split()[:2] for line in meminfo.splitlines())
    total = int(values.get('SwapTotal:', 0))
    free = int(values.get('SwapFree:', 0))
    return (total - free) * 100 // total if total else 0


def redis_saves():
    try:
        with socket.create_connection(('redis', 6379), timeout=5) as connection:
            connection.sendall(b'INFO persistence\r\nQUIT\r\n')
            reply = connection.makefile('rb').read()
    except OSError:
        return False
    return b'rdb_last_bgsave_status:ok' in reply


def host_problem(disk, swap, saves):
    if disk >= DISK_LIMIT:
        return f'disk {disk}% used'
    if swap >= SWAP_LIMIT:
        return f'swap {swap}% used'
    if not saves():
        return 'Redis cannot save'
    return ''


def ping(url):
    for delay in (1, 2, 4, 8, 16, 0):
        try:
            with urllib.request.urlopen(url, timeout=10):
                return True
        except (OSError, ValueError, http.client.HTTPException):
            time.sleep(delay)
    return False


def main():
    url = os.environ.get('HEALTHCHECK_URL', '')
    if not url:
        raise SystemExit('Error: HEALTHCHECK_URL is not set')
    while True:
        healthy = True
        for service in SERVICES:
            status = health(service)
            log(f'{service} status: {status}')
            healthy = healthy and status == 'healthy'
        problem = host_problem(disk_percent(), swap_percent(Path('/proc/meminfo').read_text()), redis_saves)
        if problem:
            log(f'host problem: {problem}')
            healthy = False
        if healthy:
            log('All services are healthy. Sending heartbeat')
            log('Health check ping sent successfully' if ping(url) else 'Failed to send health check ping')
        else:
            log('Not all services are healthy. Skipping health check ping.')
        time.sleep(60)


if __name__ == '__main__':
    main()
