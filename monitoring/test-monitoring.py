import contextlib
import json
import os
import re
import secrets
import socket
import struct
import subprocess
import tempfile
import textwrap
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from runpy import run_path


ROOT = Path(__file__).resolve().parent
PROMETHEUS = 'prom/prometheus:v3.13.3'
ALERTMANAGER = 'prom/alertmanager:v0.34.1'
CADDY = 'caddy:2.10-alpine'


def run(*arguments, check=True):
    result = subprocess.run(arguments, capture_output=True, text=True, check=False)
    if check and result.returncode:
        raise AssertionError(f'{arguments}: {result.stdout}\n{result.stderr}')
    return result


def wait_for(predicate, timeout=15):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.2)
    raise AssertionError('Timed out waiting for monitoring delivery')


def request(url, body=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {'Content-Type': 'application/json'} if data is not None else {}
    with urllib.request.urlopen(urllib.request.Request(url, data, headers), timeout=2) as response:
        return response.read()


def wait_until_ready(base):
    def ready():
        try:
            request(base + '/-/ready')
            return True
        except (urllib.error.URLError, ConnectionError, TimeoutError):
            return False

    wait_for(ready)


class Receiver(BaseHTTPRequestHandler):
    events = []
    lock = threading.Lock()

    def do_POST(self):
        length = int(self.headers['Content-Length'])
        body = self.rfile.read(length)
        with self.lock:
            self.events.append((time.monotonic(), self.path, body.decode()))
        payload = b'{"ok":true,"result":{"message_id":1,"chat":{"id":12345,"type":"private"},"date":1,"text":"accepted"}}'
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *_arguments):
        pass


def events(path):
    with Receiver.lock:
        return [event for event in Receiver.events if path in event[1]]


def notifications(name, status, provider='', environment='production'):
    marker = f'{status} {name} {provider}'.rstrip()
    messages = (json.loads(event[2])['text'] for event in events('/sendMessage'))
    return [message for message in messages if message.startswith(environment + ' ') and marker in message]


def alert_state(base, name, provider='', environment='production'):
    alerts = json.loads(request(base + '/api/v2/alerts'))
    for current in alerts:
        labels = current['labels']
        if (labels['alertname'] == name and labels.get('provider', '') == provider
                and labels['environment'] == environment):
            return current['status']['state']
    return None


def alert(name, ends_after, provider=None, operation=None, environment='production'):
    now = datetime.now(timezone.utc)
    labels = {'alertname': name, 'environment': environment, 'severity': 'critical'}
    if provider:
        labels['provider'] = provider
    if operation:
        labels['operation'] = operation
    return {
        'labels': labels,
        'annotations': {'summary': '2 expected items', 'last_success': 'never'},
        'startsAt': (now - timedelta(seconds=1)).isoformat(),
        'endsAt': (now + timedelta(seconds=ends_after)).isoformat(),
    }


def check_readiness_recovery():
    listener = socket.socket()
    listener.bind(('127.0.0.1', 0))
    listener.listen(2)
    listener.settimeout(3)
    attempts = []

    def serve():
        for attempt in range(2):
            try:
                connection, _address = listener.accept()
            except OSError:
                return
            with connection:
                connection.recv(4096)
                attempts.append(attempt)
                if attempt == 0:
                    connection.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack('ii', 1, 0))
                    continue
                connection.sendall(b'HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nOK')

    thread = threading.Thread(target=serve, daemon=True)
    thread.start()
    try:
        wait_until_ready(f'http://127.0.0.1:{listener.getsockname()[1]}')
    finally:
        listener.close()
        thread.join(timeout=4)
    assert attempts == [0, 1]
    print('Readiness retries a TCP reset and then accepts a healthy response')


def start_alertmanager(directory, network):
    container = run(
        'docker', 'run', '-d', '--user', '0:0', '--read-only',
        '--tmpfs', '/tmp', '--network', network, '--network-alias', 'alertmanager',
        '--add-host', 'host.docker.internal:host-gateway',
        '-p', '127.0.0.1::9093', '-v', f'{directory}:/test',
        '--entrypoint', 'alertmanager', ALERTMANAGER,
        '--config.file=/test/alertmanager.yml', '--storage.path=/test/data',
    ).stdout.strip()
    try:
        mapping = run('docker', 'port', container, '9093/tcp').stdout.strip()
        port = int(re.search(r':(\d+)$', mapping).group(1))
        base = f'http://127.0.0.1:{port}'
        wait_until_ready(base)
        run('docker', 'exec', container, 'wget', '-q', '-O', '/dev/null',
            'http://127.0.0.1:9093/-/ready')
        return container, base
    except Exception as failure:
        logs = run('docker', 'logs', container, check=False)
        stop_alertmanager(container, check=False)
        raise AssertionError(f'Alertmanager startup failed: {failure}\n{logs.stdout}\n{logs.stderr}') from failure


def stop_alertmanager(container, check=True):
    try:
        run('docker', 'stop', container, check=check)
    finally:
        run('docker', 'rm', '-f', container, check=False)


def check_failed_startup_logs():
    with tempfile.TemporaryDirectory() as temporary:
        directory = Path(temporary)
        directory.chmod(0o755)
        (directory / 'alertmanager.yml').write_text('not_valid: true\n')
        (directory / 'data').mkdir()
        network = 'monitoring-failed-start-' + uuid.uuid4().hex[:12]
        run('docker', 'network', 'create', network)
        container = None
        try:
            try:
                container, _base = start_alertmanager(directory, network)
            except AssertionError as failure:
                assert 'not_valid' in str(failure), str(failure)
                assert not run('docker', 'ps', '-aq', '--filter', f'network={network}').stdout.strip()
            else:
                raise AssertionError('Invalid Alertmanager configuration started successfully')
        finally:
            if container:
                stop_alertmanager(container, check=False)
            run('docker', 'network', 'rm', network, check=False)
    print('Failed Alertmanager startup reports logs and removes its container')


def check_prometheus_loss(directory, network, heartbeat_count):
    rules = 'groups:\n  - name: watchdog\n    interval: 1s\n    rules:\n      - alert: MonitoringWatchdog\n        expr: vector(1)\n        labels:\n          severity: watchdog\n'
    config = 'global:\n  scrape_interval: 1s\n  evaluation_interval: 1s\n  external_labels:\n    environment: production\nrule_files:\n  - /test/watchdog.yml\nalerting:\n  alertmanagers:\n    - static_configs:\n        - targets: [alertmanager:9093]\n'
    (directory / 'watchdog.yml').write_text(rules)
    (directory / 'prometheus.yml').write_text(config)
    prometheus = run(
        'docker', 'run', '-d', '--rm', '--network', network,
        '--tmpfs', '/tmp', '-v', f'{directory}:/test:ro',
        '--entrypoint', 'prometheus', PROMETHEUS,
        '--config.file=/test/prometheus.yml', '--storage.tsdb.path=/tmp/prometheus',
        '--rules.alert.resend-delay=1s',
    ).stdout.strip()
    try:
        wait_for(lambda: len(events('/heartbeat')) > heartbeat_count, timeout=20)
        run('docker', 'exec', prometheus, 'wget', '-q', '-O', '/dev/null',
            'http://127.0.0.1:9090/-/ready')
    finally:
        run('docker', 'stop', '-t', '1', prometheus, check=False)
    time.sleep(6)
    count_after_expiry = len(events('/heartbeat'))
    time.sleep(3)
    assert len(events('/heartbeat')) == count_after_expiry, 'Heartbeat continued after Prometheus stopped'
    print('Real Prometheus watchdog stops after Prometheus exits')


def check_configurations():
    run('docker', 'run', '--rm', '-v', f'{ROOT}:/etc/prometheus:ro',
        '--entrypoint', 'promtool', PROMETHEUS,
        'check', 'config', '/etc/prometheus/prometheus.yml')
    run('docker', 'run', '--rm', '-v', f'{ROOT}:/etc/prometheus:ro',
        '--entrypoint', 'promtool', PROMETHEUS,
        'test', 'rules', '/etc/prometheus/rules.test.yml')
    run('docker', 'run', '--rm', '-v', f'{ROOT}:/etc/alertmanager:ro',
        '--entrypoint', 'amtool', ALERTMANAGER,
        'check-config', '/etc/alertmanager/alertmanager.yml')
    for name in ('HostSwapHigh', 'HostDiskHigh', 'HostMemoryLow', 'MonitoringTargetDown',
                 'OpenRouterBalanceLow', 'OpenRouterBalanceUnreadable', 'OpenRouterOtherKeysSpending'):
        route = run('docker', 'run', '--rm', '-v', f'{ROOT}:/etc/alertmanager:ro', '--entrypoint', 'amtool', ALERTMANAGER,
                    'config', 'routes', 'test', '--config.file=/etc/alertmanager/alertmanager.yml', f'alertname={name}').stdout.strip()
        assert route == 'host', f'{name} must reach the host receiver, which repeats daily and sends resolved, not {route}'
    print('Prometheus config, rule tests, and Alertmanager config passed')


def check_compose():
    compose = ROOT.parent / 'docker-compose.yml'
    configured = json.loads(run('docker', 'compose', '-f', str(compose),
                                '--profile', 'monitoring', '--profile', 'radio', '--profile', 'whisper',
                                'config', '--no-interpolate', '--format', 'json').stdout)
    services = configured['services']
    published = sorted(name for name, service in services.items() if 'ports' in service)
    assert published == ['app'], f'only Caddy may publish host ports, found {published}'
    for name in ('prometheus', 'alertmanager'):
        assert services[name]['profiles'] == ['monitoring']
        assert services[name]['healthcheck'], f'{name} has no healthcheck'
        command = services[name]['command']
        assert f'--web.external-url=https://fov.ee/{name}/' in command and '--web.route-prefix=/' in command, \
            f'{name} must link under its Caddy path and keep its own routes, healthcheck included, at /'
    monitoring = sorted(name for name, service in services.items() if service.get('profiles') == ['monitoring'])
    for name in ('node-exporter', 'cadvisor', 'redis-exporter', 'postgres-exporter'):
        assert name in monitoring, f'{name} must run in the monitoring profile, found {monitoring}'
    for name in monitoring:
        assert services[name].get('mem_limit'), f'{name} has no memory limit'
        assert 'backend' not in services[name].get('depends_on', {}), f'a deploy must not restart {name} with the backend'
    deploy = (ROOT.parent / '.github/workflows/deploy-pipeline.yml').read_text()
    listed = re.search(r"MONITORING='([^']+)'", deploy).group(1).split()
    assert sorted(listed) == monitoring, f'the deploy must stop and wait for every monitoring service, lists {listed}'
    proxy = services['socket-proxy']
    assert proxy['environment'] == {'CONTAINERS': '1', 'NETWORKS': '1', 'LOG_LEVEL': 'warning'}, proxy
    assert services['alloy']['user'] == '473:473', 'Alloy must not run as root'
    mounts = [(name, Path(volume.get('source', ''))) for name, service in services.items() for volume in service.get('volumes', [])]
    sockets = sorted(name for name, source in mounts if source.name == 'docker.sock')
    assert sockets == ['cadvisor', 'healthcheck', 'socket-proxy'], f'only the proxy, cAdvisor and the heartbeat may mount the Docker socket, found {sockets}'
    configs = sorted(source.name for _, source in mounts if source.parent == ROOT and source.is_file())
    copied = sorted(re.search(r'scp ((?:monitoring/\S+ )+)githubuser@fov.ee:~/monitoring/', deploy).group(1).replace('monitoring/', '').split())
    assert copied == configs, f'the deploy must copy every config file a service mounts from monitoring/, copies {copied}'
    grafana = services['grafana']
    assert grafana['read_only'] and grafana['cap_drop'] == ['ALL'] and 'ports' not in grafana, grafana
    assert {key: grafana['environment'][key] for key in ('GF_AUTH_DISABLE_LOGIN_FORM', 'GF_AUTH_BASIC_ENABLED', 'GF_AUTH_ANONYMOUS_ORG_ROLE')} == \
        {'GF_AUTH_DISABLE_LOGIN_FORM': 'true', 'GF_AUTH_BASIC_ENABLED': 'false', 'GF_AUTH_ANONYMOUS_ORG_ROLE': 'Viewer'}, \
        'logged-in users must stay anonymous Viewers, and the default admin must not log in with basic auth'
    jobs = sorted(re.findall(r'job_name: (\S+)', (ROOT / 'prometheus.yml').read_text()))
    assert jobs == ['alertmanager', 'cadvisor', 'node', 'portfolio-backend', 'postgres', 'prometheus', 'redis'], jobs
    assert services['backend']['environment']['MANAGEMENT_SERVER_PORT'] == 9090
    assert services['backend']['environment']['MONITORING_POSTGRES_PASSWORD'] == services['postgres-exporter']['environment']['DATA_SOURCE_PASS'], \
        'the backend must set the monitoring role password that postgres-exporter logs in with'
    worker = services['radio-worker']
    assert worker['oom_score_adj'] == 1000 and worker['mem_limit']
    assert worker['stop_grace_period'] == '5m' and list(worker['networks']) == ['radio']
    assert worker['restart'] == 'unless-stopped', 'a stopped radio worker must stay stopped when Docker restarts'
    caps = ('cpus', 'cpu_shares', 'mem_limit', 'memswap_limit', 'oom_score_adj', 'stop_grace_period', 'restart', 'networks')
    radio = sorted(name for name, service in services.items() if 'radio' in service.get('profiles', []))
    workers = [name for name in radio if name.startswith('radio-worker-')]
    assert all(services[name][cap] == worker[cap] for name in workers for cap in caps), workers
    stations = dict(entry.split(':') for entry in re.search(r"STATIONS='([^']+)'", deploy).group(1).split())
    deployed = sorted(name for names in stations.values() for name in names.split(','))
    assert deployed == radio, f'the deploy must start or stop every radio service by its station, lists {deployed}'
    for station, names in stations.items():
        for name in names.split(','):
            assert services[name]['profiles'] == ['radio', f'radio-{station.lower()}'], (name, services[name]['profiles'])
    backup = services['radio-whisper']
    assert backup['profiles'] == ['whisper'], 'the deploy must never start the Whisper backup'
    assert backup['restart'] == 'unless-stopped', 'a stopped Whisper backup must stay stopped when Docker restarts'
    assert ('radio_model', '/model') in {(volume['source'], volume['target']) for volume in backup['volumes']}, backup['volumes']
    assert '/model' not in {volume['target'] for volume in worker['volumes']}, worker['volumes']
    shared = [{(volume['source'], volume['target']) for volume in services[name]['volumes']} for name in ('backend', *radio)]
    assert all(('radio_recordings', '/recordings') in mounts for mounts in shared), shared
    assert '9090/actuator/health' in ' '.join(services['backend']['healthcheck']['test'])
    assert '--rules.alert.resend-delay=15s' in services['prometheus']['command']
    assert 'backend' not in services['prometheus']['depends_on'], 'a deploy must not restart Prometheus with the backend'
    assert '--data.retention=4320h' in services['alertmanager']['command'], \
        'Alertmanager forgets a sent alert after its data retention, so it must cover twice the 2160h split repeat interval'
    postgres = services['postgres']['command']
    for setting in ('log_lock_waits=on', 'log_temp_files=0'):
        assert setting in postgres, f'postgres is missing {setting}'
    assert 'start_interval' in services['postgres']['healthcheck']
    redis = services['redis']['command']
    for setting in ('--maxmemory ', '--save 3600 1', '--stop-writes-on-bgsave-error no'):
        assert setting in redis, f'redis is missing {setting}'
    unrotated = sorted(name for name, service in services.items()
                       if 'max-size' not in service.get('logging', {}).get('options', {}))
    assert not unrotated, f'container logs are not rotated for {unrotated}'
    assert 'evaluation_interval: 15s' in (ROOT / 'prometheus.yml').read_text()
    print('Optional Compose profile, internal ports, and healthchecks passed')


@contextlib.contextmanager
def compose_project(*services):
    project = 'monitoring-' + uuid.uuid4().hex[:12]
    compose = ['docker', 'compose', '-f', str(ROOT.parent / 'docker-compose.yml'), '-p', project, '--profile', 'monitoring']
    environment = {**os.environ, 'MINIO_ROOT_USER': secrets.token_hex(8), 'MINIO_ROOT_PASSWORD': secrets.token_hex(16)}
    try:
        subprocess.run([*compose, 'up', '-d', '--no-deps', *services], env=environment, capture_output=True, text=True, check=True)
        yield project
    finally:
        subprocess.run([*compose, 'down', '-v'], env=environment, capture_output=True, check=False)


def check_exporters():
    desktop = run('docker', 'info', '--format', '{{.OperatingSystem}}').stdout.strip() == 'Docker Desktop'
    with compose_project('node-exporter', 'cadvisor') as project:
        probe = project + '-probe'

        def metrics(service, port):
            return run('docker', 'exec', f'{project}-{service}-1', 'wget', '-qO-', f'http://127.0.0.1:{port}/metrics', check=False).stdout

        try:
            run('docker', 'run', '-d', '--name', probe, 'alpine:3.22', 'sleep', '120')
            wait_for(lambda: f'name="{probe}"' in metrics('cadvisor', 8080), timeout=90)
            node = metrics('node-exporter', 9100)
        finally:
            run('docker', 'rm', '-f', probe, check=False)
    assert 'node_memory_SwapTotal_bytes' in node and 'node_memory_MemAvailable_bytes' in node, node[:500]
    assert desktop or 'mountpoint="/"' in node, 'node-exporter must report the root filesystem the disk alert reads'
    print('cAdvisor names containers and node-exporter reports memory, swap and the root filesystem')


def check_postgres():
    services = json.loads(run('docker', 'compose', '-f', str(ROOT.parent / 'docker-compose.yml'), '--profile', 'monitoring',
                              'config', '--no-interpolate', '--format', 'json').stdout)['services']
    migrations = ROOT.parent / 'src/main/resources/db/migration'
    password = secrets.token_hex(24)
    statements = [(migrations / 'V202609231610__add_pg_stat_statements_extension.sql').read_text(),
                  (migrations / 'V202611031538__create_monitoring_role.sql').read_text(),
                  (migrations / 'afterMigrate.sql').read_text().replace('${monitoring_password}', password),
                  'SELECT pg_sleep(0.3)']
    board = json.loads((ROOT / 'grafana/dashboards/postgres.json').read_text())
    wanted = {name for panel in board['panels'] for target in panel.get('targets', []) for name in re.findall(r'\bpg_\w+', target['expr'])}
    slowest = [panel for panel in board['panels'] if panel['title'] == 'Slowest queries']
    network = 'monitoring-postgres-' + uuid.uuid4().hex[:12]
    database, exporter, prometheus = (f'{network}-{name}' for name in ('postgres', 'exporter', 'prometheus'))
    environment = {**services['postgres-exporter']['environment'], 'DATA_SOURCE_PASS': password}
    psql = ['docker', 'exec', database, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'portfolio', '-d', 'portfolio', '-c']

    def query(expr):
        values = {'db': '.*', 'instance': '.*', '__range': '1m'}
        url = 'http://127.0.0.1:9090/api/v1/query?' + urllib.parse.urlencode({'query': re.sub(r'\$(\w+)', lambda match: values[match.group(1)], expr)})
        found = run('docker', 'exec', prometheus, 'wget', '-qO-', url, check=False).stdout
        return json.loads(found)['data']['result'] if found else []

    def slept(expr):
        return [float(series['value'][1]) for series in query(expr) if 'pg_sleep' in series['metric'].get('query', '')]

    run('docker', 'network', 'create', network)
    try:
        run('docker', 'run', '-d', '--name', database, '--network', network, '--network-alias', 'postgres', '-e', 'POSTGRES_DB=portfolio',
            '-e', 'POSTGRES_USER=portfolio', '-e', f'POSTGRES_PASSWORD={secrets.token_hex(8)}', services['postgres']['image'], *services['postgres']['command'].split())
        wait_for(lambda: run(*psql, 'SELECT 1', check=False).returncode == 0, timeout=60)
        for statement in statements:
            run(*psql, statement)
        run('docker', 'run', '-d', '--name', exporter, '--network', network, '--network-alias', 'postgres-exporter',
            *[flag for name, value in environment.items() for flag in ('-e', f'{name}={value}')],
            services['postgres-exporter']['image'], *services['postgres-exporter'].get('command', []))
        run('docker', 'run', '-d', '--name', prometheus, '--network', network, '-v', f'{ROOT}:/etc/prometheus:ro', PROMETHEUS)
        wait_for(lambda: slept('pg_stat_statements_query_id'), timeout=60)
        run(*psql, 'SELECT pg_sleep(0.3)')
        wait_for(lambda: slowest and all(slept(target['expr']) for target in slowest[0]['targets']), timeout=60)
        mean, calls, total = (slept(target['expr'])[0] for target in slowest[0]['targets'])
        scraped = run('docker', 'exec', exporter, 'wget', '-qO-', 'http://127.0.0.1:9187/metrics').stdout
        stored = json.dumps(query('{job="postgres"}'))
    finally:
        run('docker', 'rm', '-f', prometheus, exporter, database, check=False)
        run('docker', 'network', 'rm', network, check=False)
    exported = {line.split('{')[0].split(' ')[0] for line in scraped.splitlines() if line and not line.startswith('#')}
    assert wanted <= exported, f'the Postgres dashboard reads metrics the exporter does not expose: {sorted(wanted - exported)}'
    assert 0.29 < mean < 5 and calls > 0 and total > 0.29, f'Slowest queries must show seconds per call, calls and total time, got {mean}, {calls}, {total}'
    assert password in scraped and password not in stored, 'Prometheus must drop the statement that sets the monitoring password'
    print('The Postgres dashboard lists the slowest queries and reads only exported metrics, and Prometheus drops passwords')


def check_logs():
    line, other = f'hello-ä (x) {uuid.uuid4().hex}', f'ERROR {uuid.uuid4().hex}'
    board = json.loads((ROOT / 'grafana/dashboards/logs.json').read_text())
    every = {variable['name']: variable.get('allValue', '') for variable in board['templating']['list']} | {'__auto': '1m'}

    def expand(expr, **values):
        return re.sub(r'\$\{(\w+)(?::\w+)?\}|\$(\w+)', lambda match: (every | values)[match.group(1) or match.group(2)], expr)

    with compose_project('socket-proxy', 'loki', 'alloy') as project:
        shipped, hidden, loki = project + '-shipped', project + '-hidden', project + '-loki'

        def query(expr, check=False):
            url = 'http://loki:3100/loki/api/v1/query_range?' + urllib.parse.urlencode({'query': expr, 'limit': 10})
            return run('docker', 'exec', f'{project}-socket-proxy-1', 'wget', '-qO-', url, check=check).stdout

        def stored():
            found = query('{service_name=~".+"} |= "' + line + '"')
            streams = json.loads(found)['data']['result'] if found else []
            return sorted((stream['stream'].get('container', ''), stream['stream'].get('level', ''), entry[1]) for stream in streams for entry in stream['values'])

        expected = [(loki, '', f'{line} loki'), (shipped, '', line), (shipped, 'error', f'ERROR {line}')]
        try:
            run('docker', 'run', '-d', '--name', hidden, '--label', 'com.docker.compose.service=auth', 'alpine:3.22', 'sh', '-c', f'echo "{line}"; sleep 15')
            noise = f'echo "level=error msg=error notifying scheduler err=EOF {line}"; echo "level=error err=context canceled {line}"; echo "level=error msg=Request Completed path=/apis/quotas.grafana.app/v0alpha1/namespaces/default/usage status=501 {line}"'
            run('docker', 'run', '-d', '--name', loki, '--label', 'com.docker.compose.service=loki', 'alpine:3.22', 'sh', '-c', f'{noise}; echo "{line} loki"; sleep 15')
            script = f'echo "{line}"; echo "clientSecret={line}"; echo "level=error msg=error calling scheduler err=no jobs found {line}"; echo "ERROR {line}"; echo "{other}"; sleep 15'
            run('docker', 'run', '-d', '--name', shipped, 'alpine:3.22', 'sh', '-c', script)
            wait_for(lambda: set(expected) <= set(stored()), timeout=60)
            entries = stored()
            for panel in board['panels']:
                query(expand(panel['targets'][0]['expr']), check=True)
            search = next(panel['targets'][0]['expr'] for panel in board['panels'] if panel['type'] == 'logs')
            wait_for(lambda: other in query('{container="' + shipped + '"}'), timeout=30)
            found = json.loads(query(expand(search, level='error', search=re.escape(line.upper())), check=True))
        finally:
            run('docker', 'rm', '-f', shipped, hidden, loki, check=False)
    assert entries == expected, f'Alloy must ship container logs with their level, and nothing from auth, #2012 secrets or known noise, stored {entries}'
    searched = [entry[1] for stream in found['data']['result'] for entry in stream['values']]
    assert searched == [f'ERROR {line}'], f'the Logs search must match case-insensitively within the chosen level, found {searched}'
    print('Alloy ships container logs to Loki, leaves auth out, and the Logs dashboard searches them')


def datasource_uids(node):
    if isinstance(node, list):
        return {uid for item in node for uid in datasource_uids(item)}
    if not isinstance(node, dict):
        return set()
    own = node.get('datasource')
    uids = {own if isinstance(own, str) else own.get('uid')} if isinstance(own, (str, dict)) else set()
    return uids | {uid for value in node.values() for uid in datasource_uids(value)}


def check_grafana():
    directory = ROOT / 'grafana'
    dashboards = [json.loads(path.read_text()) for path in sorted((directory / 'dashboards').glob('*.json'))]
    unknown = datasource_uids(dashboards) - {'prometheus', 'loki', 'tempo', 'grafana', '-- Grafana --'}
    assert not unknown, f'dashboards must use the provisioned datasources, found {unknown}'
    with compose_project('grafana') as project:
        container = f'{project}-grafana-1'

        def api(path):
            return run('docker', 'exec', container, 'wget', '-qO-', f'http://127.0.0.1:3000/grafana{path}', check=False).stdout

        wait_for(lambda: len(json.loads(api('/api/search?type=dash-db') or '[]')) >= len(dashboards), timeout=90)
        titles = sorted(item['title'] for item in json.loads(api('/api/search?type=dash-db')))
        sources = {source['uid'] for source in json.loads(api('/api/datasources'))}
        permissions = json.loads(api('/api/access-control/user/permissions') or '{}')
        logs = run('docker', 'logs', container)
    assert titles == sorted(dashboard['title'] for dashboard in dashboards), titles
    assert 'datasources:explore' in permissions, f'the logged-in viewer must be able to search the logs in Explore, has {sorted(permissions)}'
    assert sources == {'prometheus', 'loki', 'tempo'}, sources
    errors = [line for line in (logs.stderr + logs.stdout).splitlines() if 'level=error' in line and 'provisioning' in line]
    assert not errors, errors
    print('Grafana provisions every dashboard against the provisioned datasources and opens Explore to the viewer')


def check_traces():
    trace, poll, start = secrets.token_hex(16), secrets.token_hex(16), time.time_ns()

    def span(trace_id, name, kind, nanoseconds):
        return {'traceId': trace_id, 'spanId': secrets.token_hex(8), 'name': name, 'kind': kind,
                'startTimeUnixNano': str(start), 'endTimeUnixNano': str(start + nanoseconds)}

    spans = {'resourceSpans': [{'resource': {'attributes': [{'key': 'service.name', 'value': {'stringValue': 'backend'}}]},
                                'scopeSpans': [{'spans': [span(trace, 'GET /api/instruments', 2, 6 * 10 ** 8), span(poll, 'POST', 3, 50 * 10 ** 9)]}]}]}
    pattern = re.search(r"console: '(.+)'", (ROOT.parent / 'src/main/resources/application.yml').read_text()).group(1)
    board = json.loads((ROOT / 'grafana/dashboards/overview.json').read_text())
    panel = next(panel for panel in board['panels'] if panel['title'] == 'Slow requests')
    listing = {'queries': panel['targets'], 'from': str(start // 10 ** 6 - 60000), 'to': str(start // 10 ** 6 + 60000)}
    fields = {'d': '2026-11-03 21:20:00.000', 'thread': 'scheduled-1', 'level': 'INFO', 'logger': 'e.t.p.j.FtDataRetrievalJob',
              'trace_id': trace, 'msg': f'Hinnad uuendatud ä {uuid.uuid4().hex}'}
    line = re.sub(r'%(-?\d+)?(\w+)(?:\{([^}]*)\})?',
                  lambda match: fields.get(match.group(3) if match.group(2) == 'X' else match.group(2), '').ljust(abs(int(match.group(1) or 0))), pattern)
    with compose_project('tempo', 'loki', 'grafana') as project:
        container = f'{project}-grafana-1'

        def fetch(url, body=None):
            post = ['--header', 'Content-Type: application/json', '--post-data', json.dumps(body)] if body else []
            return run('docker', 'exec', container, 'wget', '-qO-', *post, url, check=False).stdout

        def grafana(path):
            return json.loads(fetch(f'http://127.0.0.1:3000/grafana{path}') or '{}')

        def logged(search):
            fetch('http://loki:3100/loki/api/v1/push', {'streams': [{'stream': {'container': 'backend'}, 'values': [[str(start), line]]}]})
            found = fetch('http://loki:3100/loki/api/v1/query_range?' + urllib.parse.urlencode({'query': search}))
            return [entry[1] for stream in json.loads(found or '{"data":{"result":[]}}')['data']['result'] for entry in stream['values']]

        wait_for(lambda: grafana('/api/datasources/uid/tempo/health').get('status') == 'OK', timeout=90)
        wait_for(lambda: 'partialSuccess' in fetch('http://tempo:4318/v1/traces', spans), timeout=60)
        wait_for(lambda: all(name in fetch(f'http://tempo:3200/api/v2/traces/{trace_id}') for trace_id, name in ((trace, 'GET /api/instruments'), (poll, 'POST'))), timeout=60)
        wait_for(lambda: trace.lstrip('0') in fetch('http://127.0.0.1:3000/grafana/api/ds/query', listing), timeout=60)
        listed = fetch('http://127.0.0.1:3000/grafana/api/ds/query', listing)
        links = {uid: grafana(f'/api/datasources/uid/{uid}')['jsonData'] for uid in ('tempo', 'loki')}
        search = links['tempo']['tracesToLogsV2']['query'].replace('${__trace.traceId}', trace)
        wait_for(lambda: logged(search), timeout=90)
        found = logged(search)
    field = links['loki']['derivedFields'][0]
    matched = re.search(field['matcherRegex'], line)
    assert found == [line], f'a trace must open the backend log lines that carry its id, found {found} for {search}'
    assert field['datasourceUid'] == 'tempo' and matched and matched.group(1) == trace, f'Loki must link the trace id in {line!r} to Tempo'
    assert poll.lstrip('0') not in listed, f'Slow requests must leave out the Telegram bot long polling, found {poll} in {listed}'
    print('Tempo stores OTLP spans, the overview lists slow requests, and Grafana links a trace to its backend log lines and back')


def check_caddy_routes():
    stubs = (':80 {\n\trespond "frontend {uri}"\n}\n'
             ':8083 {\n\t@guest not header Cookie user=1\n\tredir @guest /login?redirect={header.X-Forwarded-Uri}\n\trespond 200\n}\n'
             ':9090 {\n\trespond "prometheus {uri}"\n}\n'
             ':9093 {\n\trespond "alertmanager {uri}"\n}\n'
             ':3000 {\n\trespond "grafana {uri}"\n}\n')
    with tempfile.TemporaryDirectory() as temporary:
        directory = Path(temporary)
        directory.chmod(0o755)
        (directory / 'stubs').write_text(stubs)
        (directory / 'Caddyfile').write_text('{\n\tlocal_certs\n\tskip_install_trust\n}\n'
                                             + (ROOT.parent / 'Caddyfile').read_text())
        network = 'monitoring-caddy-' + uuid.uuid4().hex[:12]
        run('docker', 'network', 'create', network)
        containers = []
        try:
            for aliases, config in ((('frontend', 'auth', 'prometheus', 'alertmanager', 'grafana'), 'stubs'), ((), 'Caddyfile')):
                flags = [flag for alias in aliases for flag in ('--network-alias', alias)]
                containers.append(run('docker', 'run', '-d', '--network', network, *flags, '-v', f'{directory}:/test:ro',
                                      CADDY, 'caddy', 'run', '--config', f'/test/{config}', '--adapter', 'caddyfile')
                                  .stdout.strip())
            caddy = containers[-1]

            def fetch(path, cookie='user=0'):
                return run('docker', 'exec', caddy, 'curl', '-s', '--cacert', '/data/caddy/pki/authorities/local/root.crt',
                           '--resolve', 'fov.ee:443:127.0.0.1', '-H', f'Cookie: {cookie}',
                           '-w', '|%{http_code}|%{redirect_url}', f'https://fov.ee{path}', check=False).stdout

            try:
                wait_for(lambda: fetch('/') == '|302|https://fov.ee/login?redirect=/', timeout=30)
            except AssertionError as failure:
                raise AssertionError(run('docker', 'logs', caddy, check=False).stderr) from failure
            assert fetch('/prometheus/query') == '|302|https://fov.ee/login?redirect=/prometheus/query', \
                'the login must run before the prefix is stripped, or it returns to the app instead of Prometheus'
            assert fetch('/prometheus/assets/index.js', 'user=1') == 'prometheus /assets/index.js|200|', \
                'the monitoring routes must stay above the calculator routes, which send every *.js to the frontend'
            assert fetch('/alertmanager/api/v2/silences', 'user=1') == 'alertmanager /api/v2/silences|200|'
            assert fetch('/alertmanager') == '|302|https://fov.ee/alertmanager/'
            assert fetch('/grafana/') == '|302|https://fov.ee/login?redirect=/grafana/'
            assert fetch('/grafana/public/build/app.js', 'user=1') == 'grafana /grafana/public/build/app.js|200|', \
                'Grafana serves from its sub-path, so Caddy must keep the prefix and stay above the calculator routes'
            assert fetch('/grafana') == '|302|https://fov.ee/grafana/'
            assert fetch('/', 'user=1') == 'frontend /|200|'
        finally:
            for container in containers:
                run('docker', 'rm', '-f', container, check=False)
            run('docker', 'network', 'rm', network, check=False)
    print('Caddy serves Prometheus, Alertmanager and Grafana behind the app login')


def workflow_step(workflow, name):
    step = workflow.split(f'      - name: {name}\n', 1)[1].split('\n      - ', 1)[0]
    return textwrap.dedent(step.split('run: |\n', 1)[1])


def check_host_gates():
    root = ROOT.parent
    healthcheck = run_path(str(root / 'healthcheck.py'))
    assert healthcheck['swap_percent']('SwapTotal: 4000 kB\nSwapFree: 1000 kB\n') == 75
    cases = {
        (10, 75, True): 'swap 75% used',
        (91, 75, True): 'disk 91% used',
        (10, 74, True): '',
        (10, 3, False): 'Redis cannot save',
        (10, 3, True): '',
    }
    for (disk, swap, saves), expected in cases.items():
        assert healthcheck['host_problem'](disk, swap, lambda: saves) == expected, (disk, swap, saves)
    deploy = (root / '.github/workflows/deploy-pipeline.yml').read_text()
    with tempfile.TemporaryDirectory() as directory:
        fakes = {
            'ssh': 'shift\nexec sh -c "$*"\n',
            'df': 'echo Filesystem\necho "/dev/root 100 $DISK 0 $DISK% /"\n',
            'docker': 'echo "$COMPOSE_PROFILES|$*" >> "$HOME/docker.log"\ncase "$*" in *Health.Status*) echo healthy ;; *) echo "$HEARTBEAT" ;; esac\n',
            'sleep': '',
        }
        for name, body in fakes.items():
            fake = Path(directory) / name
            fake.write_text('#!/bin/sh\n' + body)
            fake.chmod(0o755)
        environment = f'export PATH="{directory}:$PATH" HOME="{directory}"\n'
        meminfo = Path(directory) / 'meminfo'
        preflight = workflow_step(deploy, 'Check host before deploy').replace('/proc/meminfo', str(meminfo))
        for disk, swap, code in ((90, 74, 1), (89, 75, 1), (89, 74, 0)):
            meminfo.write_text(f'SwapTotal: 100 kB\nSwapFree: {100 - swap} kB\n')
            result = run('bash', '-eo', 'pipefail', '-c', f'{environment}export DISK={disk}\n{preflight}', check=False)
            assert result.returncode == code and f'Disk {disk}% used, swap {swap}% used' in result.stdout, result
            assert ('Refusing to deploy: limits are disk 90% and swap 75%' in result.stdout) == bool(code), result
        services = ('postgres', 'redis', 'seaweedfs', 'backend', 'frontend', 'auth', 'app', 'cloudflare-bypass-proxy')
        logs = sorted(path.name for path in (Path(directory) / 'deploy-logs').iterdir())
        assert logs == sorted(f'{service}.log' for service in services), logs
        step = workflow_step(deploy, 'Deploy with Docker Compose')
        for heartbeat, code in {'running 0': 0, 'restarting 1': 1, 'running 1': 1}.items():
            command = f'{environment}export HEARTBEAT="{heartbeat}" RADIO_STATIONS=KUKU,ARIPAEV,ELMAR,SKYPLUS\n{step}'
            assert run('bash', '-eo', 'pipefail', '-c', command, check=False).returncode == code, heartbeat
        log = Path(directory) / 'docker.log'

        def radio(enabled, stations):
            log.unlink(missing_ok=True)
            settings = f'export HEARTBEAT="running 0" RADIO_ENABLED={enabled} RADIO_STATIONS=\'{stations}\'\n'
            result = run('bash', '-eo', 'pipefail', '-c', f'{environment}{settings}{step}', check=False)
            calls = log.read_text().splitlines() if log.exists() else []
            stopped = [call.split('|', 1)[1].split()[4:] for call in calls if '|compose --profile radio stop ' in call]
            profiles = {call.split('|', 1)[0] for call in calls if call.endswith('|compose up -d')}
            return result, stopped, profiles

        for stations in ('KUKU,ELMR', 'KUKU, SKYPLUS', ','):
            result, stopped, _ = radio('true', stations)
            assert result.returncode != 0 and stations in result.stdout and not log.exists(), (stations, result, stopped)
        _, stopped, profiles = radio('true', 'KUKU,ARIPAEV,SKYPLUS')
        assert stopped == [['radio-bridge-elmar', 'radio-worker-elmar']], stopped
        assert profiles == {'radio-kuku,radio-aripaev,radio-skyplus'}, profiles
        _, stopped, _ = radio('false', 'KUKU,ARIPAEV,ELMAR,SKYPLUS')
        assert len(stopped) == 1 and sorted(stopped[0]) == sorted(['radio-worker', 'radio-bridge', 'radio-worker-aripaev',
            'radio-bridge-elmar', 'radio-worker-elmar', 'radio-bridge-skyplus', 'radio-worker-skyplus']), stopped
    cleanup = (root / 'scripts/docker-cleanup.py').read_text()
    compile(cleanup, 'docker-cleanup.py', 'exec')
    assert 'container prune -f --filter until=1h' in cleanup
    assert deploy.index('- name: Check host before deploy') < deploy.index('scp .env'), 'deploy copies files before the preflight'
    assert 'docker compose --profile monitoring stop \\$MONITORING' in deploy, 'a deploy with monitoring off must stop every monitoring service'
    assert deploy.index('docker rm -f netdata') < deploy.index('docker compose up -d'), 'Netdata must go before the new stack starts'
    assert deploy.index('docker compose restart grafana loki') < deploy.index('docker compose kill -s HUP prometheus alertmanager alloy'), \
        'Grafana and Loki read their configuration only at startup'
    assert 'scp -r monitoring/grafana' in deploy, 'the deploy must copy the Grafana provisioning and dashboards'
    assert 'rm -rf ~/monitoring/grafana' not in deploy, 'a running Grafana keeps its bind mount on the deleted directory'
    guard = "if [ '$RADIO_ENABLED' = true ]; then docker compose --profile whisper stop radio-whisper; fi"
    assert deploy.index(guard) < deploy.index('docker compose up -d'), 'the deploy must stop the Whisper backup before it starts the radio worker'
    print('Heartbeat and deploy host gates passed')


def check_production_permissions():
    compose = ROOT.parent / 'docker-compose.yml'
    configured = json.loads(run('docker', 'compose', '-f', str(compose),
                                '--profile', 'monitoring', 'config',
                                '--no-interpolate', '--format', 'json').stdout)
    service = configured['services']['alertmanager']
    secret_volume = 'monitoring-secret-test-' + uuid.uuid4().hex[:12]
    data_volume = 'monitoring-data-test-' + uuid.uuid4().hex[:12]
    for volume in (secret_volume, data_volume):
        run('docker', 'volume', 'create', volume)
    try:
        run('docker', 'run', '--rm', '--user', '0:0',
            '-v', f'{secret_volume}:/secrets', '-v', f'{data_volume}:/state',
            '--entrypoint', 'sh', ALERTMANAGER, '-c',
            'printf dummy > /secrets/bot_token; chmod 600 /secrets/bot_token; '
            'chown 1000:1000 /secrets /secrets/bot_token /state; '
            'chmod 700 /secrets /state')
        arguments = ['docker', 'run', '--rm', '--read-only', '--user', service['user']]
        for capability in service['cap_drop']:
            arguments += ['--cap-drop', capability]
        for capability in service.get('cap_add', []):
            arguments += ['--cap-add', capability]
        arguments += ['-v', f'{secret_volume}:/run/monitoring-secrets:ro',
                      '-v', f'{data_volume}:/alertmanager', '--entrypoint', 'sh',
                      ALERTMANAGER, '-c',
                      ('test -r /run/monitoring-secrets/bot_token && '
                       'printf durable > /alertmanager/permission-check')]
        run(*arguments)
    finally:
        for volume in (secret_volume, data_volume):
            run('docker', 'volume', 'rm', volume, check=False)
    print('Production UID1000 secret and data volume permissions passed')


def check_metric_coverage():
    operations = (
        ('lightyear', 'prices'), ('trading212', 'prices'), ('binance', 'prices'),
        ('ft', 'history'), ('lightyear', 'history'), ('lightyear', 'holdings'),
        ('trading212', 'holdings'), ('blackrock', 'holdings'), ('vanguard', 'holdings'),
        ('tuleva', 'history'), ('tuleva', 'holdings'), ('imdb', 'ratings'),
    )
    required = (
        'enabled', 'expected_now', 'expected_items', 'window_start_timestamp_seconds',
        'deadline_timestamp_seconds', 'last_attempt_timestamp_seconds',
        'last_completion_timestamp_seconds', 'last_persisted_timestamp_seconds',
        'last_full_success_timestamp_seconds', 'last_run_attempted_items',
        'last_run_fetched_items', 'last_run_persisted_items', 'last_run_failed_items',
        'consecutive_empty_runs', 'run_duration_seconds', 'circuit_breaker_state',
    )

    def series(metric, labels='', value=0):
        return f'      - series: \'{metric}{{{labels}}}\'\n        values: \'{value}+0x20\'\n'

    def fixture(missing=None, expected_item=False, item_metrics=False, missing_item_initialization=False):
        lines = series('up', 'job="portfolio-backend"', 1)
        lines += series('portfolio_collection_inventory_ready', value=1)
        for provider, operation in operations:
            labels = f'provider="{provider}",operation="{operation}"'
            lines += series('portfolio_collection_operation_info', labels, 1)
            for metric in required:
                if missing == (provider, operation, metric):
                    continue
                value = 1 if expected_item and provider == 'lightyear' and operation == 'prices' and metric == 'expected_items' else 0
                lines += series('portfolio_collection_' + metric, labels, value)
        if item_metrics:
            labels = 'provider="lightyear",operation="prices",instrument="A"'
            lines += series('portfolio_collection_item_last_success_timestamp_seconds', labels)
            if not missing_item_initialization:
                lines += series('portfolio_collection_item_initialized_timestamp_seconds', labels)
        expected = ('          - exp_labels:\n              severity: critical\n'
                    '            exp_annotations:\n              summary: \'Collection metrics are incomplete\'\n')
        alerts = expected if missing or expected_item and (not item_metrics or missing_item_initialization) else '[]'
        suffix = f'      - eval_time: 3m\n        alertname: CollectionMetricsIncomplete\n        exp_alerts: {alerts if alerts == "[]" else ""}\n'
        if alerts != '[]':
            suffix += alerts
        return '  - interval: 15s\n    input_series:\n' + lines + '    alert_rule_test:\n' + suffix

    document = ('rule_files:\n  - /etc/prometheus/alerts.yml\n'
                'evaluation_interval: 15s\ntests:\n'
                + fixture()
                + fixture(('lightyear', 'prices', 'expected_now'))
                + fixture(('lightyear', 'prices', 'last_persisted_timestamp_seconds'))
                + fixture(expected_item=True)
                + fixture(expected_item=True, item_metrics=True, missing_item_initialization=True)
                + fixture(expected_item=True, item_metrics=True))
    with tempfile.TemporaryDirectory() as temporary:
        Path(temporary).chmod(0o755)
        (Path(temporary) / 'coverage.test.yml').write_text(document)
        run('docker', 'run', '--rm', '-v', f'{ROOT}:/etc/prometheus:ro',
            '-v', f'{temporary}:/test:ro', '--entrypoint', 'promtool', PROMETHEUS,
            'test', 'rules', '/test/coverage.test.yml')
    print(f'{len(operations)}-operation and per-item metric coverage passed')


def evaluate_openrouter(expressions, values, tests, **overrides):
    labels = 'month="2026-11",purpose="sector",model="google/gemini-4-argon",type="prompt",outcome="success"'
    metrics = sorted(set(re.findall(r'portfolio_openrouter_\w+', ' '.join(expressions))))
    document = json.dumps({'evaluation_interval': '15s', 'tests': [{
        'interval': '15s',
        'input_series': [{'series': f'{metric}{{{labels}}}', 'values': overrides.get(metric, values)} for metric in metrics],
        'promql_expr_test': tests,
    }]})
    with tempfile.TemporaryDirectory() as temporary:
        Path(temporary).chmod(0o755)
        (Path(temporary) / 'openrouter.test.yml').write_text(document)
        run('docker', 'run', '--rm', '-v', f'{temporary}:/test:ro', '--entrypoint', 'promtool', PROMETHEUS,
            'test', 'rules', '/test/openrouter.test.yml')


def check_openrouter_days():
    board = json.loads((ROOT / 'grafana/dashboards/openrouter.json').read_text())
    daily = [target['expr'] for panel in board['panels'] if panel.get('interval') == '1d' for target in panel['targets']]
    assert daily, 'the OpenRouter dashboard has no daily panel'
    flat_day_then_six_hours_of_spend = '7+0x5760 8+1x1439'
    account_spends_twice_the_key = '7+0x5760 9+2x1439'
    spent_since_midnight = [{'expr': f'count(0 < ({expr}) <= 1441)', 'eval_time': '1d', 'exp_samples': [{'labels': '{}', 'value': 1}]}
                            for expr in daily]
    loads_the_samples_after_midnight = {'expr': 'vector(1)', 'eval_time': '2d', 'exp_samples': [{'labels': '{}', 'value': 1}]}
    evaluate_openrouter(daily, flat_day_then_six_hours_of_spend, spent_since_midnight + [loads_the_samples_after_midnight],
                        portfolio_openrouter_account_usage_dollars=account_spends_twice_the_key)
    print('OpenRouter daily bars show the day that starts at them, the running one up to now, and split the account between the keys')


def check_openrouter_latest():
    board = json.loads((ROOT / 'grafana/dashboards/openrouter.json').read_text())
    latest = [target for panel in board['panels'] if panel['type'] != 'timeseries' for target in panel['targets']]
    assert latest and all(target['instant'] and not target['range'] for target in latest), \
        'a panel of the latest value must read it now, a range query shows N/A until a step point falls after the first sample'
    expressions = [target['expr'].replace('${__to:date:YYYY-MM}', '2026-11') for target in latest]
    read_for_ten_minutes_then_restarting = '62+1x40 stale'
    shown_a_minute_into_the_restart = [{'expr': f'count(({expr}) > 0)', 'eval_time': '11m', 'exp_samples': [{'labels': '{}', 'value': 1}]}
                                       for expr in expressions]
    evaluate_openrouter(expressions, read_for_ten_minutes_then_restarting, shown_a_minute_into_the_restart)
    print('The OpenRouter tiles and monthly bars show the latest reading and keep it while the backend restarts')


def check_openrouter_lone_days():
    board = json.loads((ROOT / 'grafana/dashboards/openrouter.json').read_text())
    styles = {panel['title']: panel['fieldConfig']['defaults']['custom'] for panel in board['panels'] if panel.get('interval') == '1d'}
    blank = [title for title, style in styles.items() if style.get('drawStyle') != 'bars' and style['showPoints'] != 'always']
    assert not blank, f'{blank} draw a line with hidden points, so a day with no value next to it shows nothing'
    print('OpenRouter daily panels show a day that has no neighbour')


def check_once_delivery(base, name, labels, summary):
    def incident(ends_after):
        firing = alert(name, ends_after)
        firing['labels'].update(severity='warning', **labels)
        firing['annotations'] = {'summary': summary}
        return firing

    def sent():
        return [json.loads(event[2])['text'] for event in events('/sendMessage') if name in event[2]]

    request(base + '/api/v2/alerts', [incident(30)])
    wait_for(lambda: len(sent()) == 1)
    assert sent() == [f'production {name}: {summary}']
    request(base + '/api/v2/alerts', [incident(30)])
    time.sleep(3)
    request(base + '/api/v2/alerts', [incident(-1)])
    time.sleep(2)
    assert len(sent()) == 1, f'{name} reached Telegram more than once: {sent()}'


def check_alert_separation(base):
    def sent(text):
        return [event for event in events('/sendMessage') if text in json.loads(event[2])['text']]

    request(base + '/api/v2/alerts', [alert('CollectionDeadlineMissed', 30, 'tuleva', 'history', 'grouped'),
                                      alert('CollectionItemsStale', 30, 'tuleva', 'history', 'grouped'),
                                      alert('RadioScheduleFailed', 30, 'kuku', environment='grouped'),
                                      alert('RadioScheduleFailed', 30, 'aripaev', environment='grouped')])
    wait_for(lambda: sent('issues/1936\n\ngrouped ') and sent('items\n\ngrouped RadioScheduleFailed'))


def check_delivery():
    Receiver.events = []
    server = ThreadingHTTPServer(('0.0.0.0', 0), Receiver)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    port = server.server_address[1]
    container = None
    try:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            directory.chmod(0o755)
            production_config = (ROOT / 'alertmanager.yml').read_text()
            production_config = production_config.replace('        chat_id_file:',
                                                          f'        api_url: http://host.docker.internal:{port}\n        chat_id_file:')
            production_config = production_config.replace('/run/monitoring-secrets/', '/test/secrets/')
            config = production_config.replace('group_wait: 30s', 'group_wait: 1s')
            config = config.replace('group_interval: 1m', 'group_interval: 1s')
            config = config.replace('repeat_interval: 1h', 'repeat_interval: 30s')
            config = config.replace('repeat_interval: 1m', 'repeat_interval: 1s')
            secrets = directory / 'secrets'
            secrets.mkdir()
            (secrets / 'bot_token').write_text('test-token\n')
            (secrets / 'chat_id').write_text('12345\n')
            (secrets / 'heartbeat_url').write_text(f'http://host.docker.internal:{port}/heartbeat\n')
            (directory / 'alertmanager.yml').write_text(config)
            (directory / 'data').mkdir()
            network = 'monitoring-test-' + uuid.uuid4().hex[:12]
            run('docker', 'network', 'create', network)
            container, base = start_alertmanager(directory, network)
            incident = alert('CollectionPricesUnavailable', 30, 'lightyear', 'prices')
            heartbeat = alert('MonitoringWatchdog', 3)
            request(base + '/api/v2/alerts', [incident, heartbeat])
            wait_for(lambda: len(notifications('CollectionPricesUnavailable', 'firing', 'lightyear')) == 1
                     and len(events('/heartbeat')) >= 1)
            message = notifications('CollectionPricesUnavailable', 'firing', 'lightyear')[0]
            assert 'CollectionPricesUnavailable' in message
            assert 'github.com/ktenman/portfolio/issues/1936' in message
            assert 'instrument' not in message and 'account' not in message
            request(base + '/api/v2/alerts', [incident])
            time.sleep(1)
            assert len(notifications('CollectionPricesUnavailable', 'firing', 'lightyear')) == 1, 'Duplicate incident notification'
            request(base + '/api/v2/alerts', [alert('CollectionPricesUnavailable', -1, 'lightyear', 'prices')])
            wait_for(lambda: len(notifications('CollectionPricesUnavailable', 'resolved', 'lightyear')) == 1)
            time.sleep(4)
            heartbeat_count = len(events('/heartbeat'))
            time.sleep(2)
            assert len(events('/heartbeat')) == heartbeat_count, 'Expired watchdog kept sending'
            check_prometheus_loss(directory, network, heartbeat_count)
            restarted_incident = alert('CollectionDeadlineMissed', 60, 'trading212', 'prices')
            request(base + '/api/v2/alerts', [restarted_incident])
            wait_for(lambda: len(notifications('CollectionDeadlineMissed', 'firing', 'trading212')) == 1)
            stop_alertmanager(container)
            container, base = start_alertmanager(directory, network)
            request(base + '/api/v2/alerts', [restarted_incident])
            time.sleep(2)
            assert len(notifications('CollectionDeadlineMissed', 'firing', 'trading212')) == 1, 'Restart duplicated the active incident'
            request(base + '/api/v2/alerts', [alert('CollectionDeadlineMissed', -1, 'trading212', 'prices')])
            wait_for(lambda: len(notifications('CollectionDeadlineMissed', 'resolved', 'trading212')) == 1)
            outage = alert('CollectionPricesUnavailable', 30, 'binance', 'prices')
            partial = alert('CollectionItemsStale', 30, 'binance', 'prices')
            request(base + '/api/v2/alerts', [outage])
            wait_for(lambda: alert_state(base, 'CollectionPricesUnavailable', 'binance') == 'active')
            wait_for(lambda: len(notifications('CollectionPricesUnavailable', 'firing', 'binance')) == 1)
            request(base + '/api/v2/alerts', [partial])
            wait_for(lambda: alert_state(base, 'CollectionItemsStale', 'binance') == 'suppressed')
            time.sleep(2)
            assert not notifications('CollectionItemsStale', 'firing', 'binance')
            backend = alert('PortfolioBackendDown', 30)
            inventory = alert('CollectionInventoryUnavailable', 30)
            coverage = alert('CollectionMetricsIncomplete', 30)
            request(base + '/api/v2/alerts', [backend])
            wait_for(lambda: alert_state(base, 'PortfolioBackendDown') == 'active')
            wait_for(lambda: len(notifications('PortfolioBackendDown', 'firing')) == 1)
            request(base + '/api/v2/alerts', [inventory, coverage])
            wait_for(lambda: alert_state(base, 'CollectionInventoryUnavailable') == 'suppressed'
                     and alert_state(base, 'CollectionMetricsIncomplete') == 'suppressed')
            time.sleep(2)
            assert not notifications('CollectionInventoryUnavailable', 'firing')
            assert not notifications('CollectionMetricsIncomplete', 'firing')
            check_once_delivery(base, 'RadioCaptureMissed',
                                {'station': 'Kuku', 'capture': '2026-10-24-1155-keskpaevatund', 'programmes': 'Keskpäevatund'},
                                'Kuku recording 2026-10-24-1155-keskpaevatund ended without a recording')
            check_once_delivery(base, 'SplitAnnounced',
                                {'instrument': 'BNKE:PAR:EUR', 'ratio': '1:80', 'effective': '2026-10-27'},
                                'BNKE:PAR:EUR splits 1:80 on 2026-10-27; record it with the recording-etf-splits skill')
            check_alert_separation(base)
            stop_alertmanager(container)
            (directory / 'alertmanager.yml').write_text(production_config)
            container, base = start_alertmanager(directory, network)
            batch_outage = alert('CollectionPricesUnavailable', 90, 'binance', 'prices', 'simultaneous')
            batch_partial = alert('CollectionItemsStale', 90, 'binance', 'prices', 'simultaneous')
            request(base + '/api/v2/alerts', [batch_outage, batch_partial])
            wait_for(lambda: alert_state(base, 'CollectionItemsStale', 'binance', 'simultaneous') == 'suppressed')
            wait_for(lambda: len(notifications('CollectionPricesUnavailable', 'firing', 'binance', 'simultaneous')) == 1,
                     timeout=50)
            time.sleep(2)
            assert not notifications('CollectionItemsStale', 'firing', 'binance', 'simultaneous')
            print('Mock Telegram and watchdog delivery, deduplication, and recovery passed')
    finally:
        if container:
            stop_alertmanager(container, check=False)
        if 'network' in locals():
            run('docker', 'network', 'rm', network, check=False)
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    check_configurations()
    check_compose()
    check_exporters()
    check_postgres()
    check_grafana()
    check_logs()
    check_traces()
    check_caddy_routes()
    check_host_gates()
    check_production_permissions()
    check_metric_coverage()
    check_openrouter_days()
    check_openrouter_latest()
    check_openrouter_lone_days()
    check_readiness_recovery()
    check_failed_startup_logs()
    check_delivery()
