import json
import re
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


ROOT = Path(__file__).resolve().parent
PROMETHEUS = 'prom/prometheus:v3.13.3'
ALERTMANAGER = 'prom/alertmanager:v0.34.1'


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


def start_alertmanager(directory, network):
    container = run(
        'docker', 'run', '-d', '--rm', '--user', '0:0', '--read-only',
        '--tmpfs', '/tmp', '--network', network, '--network-alias', 'alertmanager',
        '--add-host', 'host.docker.internal:host-gateway',
        '-p', '127.0.0.1::9093', '-v', f'{directory}:/test',
        '--entrypoint', 'alertmanager', ALERTMANAGER,
        '--config.file=/test/alertmanager.yml', '--storage.path=/test/data',
    ).stdout.strip()
    mapping = run('docker', 'port', container, '9093/tcp').stdout.strip()
    port = int(re.search(r':(\d+)$', mapping).group(1))
    base = f'http://127.0.0.1:{port}'

    def ready():
        try:
            request(base + '/-/ready')
            return True
        except (urllib.error.URLError, TimeoutError):
            return False

    wait_for(ready)
    run('docker', 'exec', container, 'wget', '-q', '-O', '/dev/null',
        'http://127.0.0.1:9093/-/ready')
    return container, base


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
    print('Prometheus config, rule tests, and Alertmanager config passed')


def check_compose():
    compose = ROOT.parent / 'docker-compose.yml'
    configured = json.loads(run('docker', 'compose', '-f', str(compose),
                                '--profile', 'monitoring', 'config',
                                '--no-interpolate', '--format', 'json').stdout)
    services = configured['services']
    for name in ('prometheus', 'alertmanager'):
        assert services[name]['profiles'] == ['monitoring']
        assert 'ports' not in services[name], f'{name} exposes a host port'
        assert services[name]['healthcheck'], f'{name} has no healthcheck'
    assert services['backend']['environment']['MANAGEMENT_SERVER_PORT'] == 9090
    assert '9090/actuator/health' in ' '.join(services['backend']['healthcheck']['test'])
    assert '--rules.alert.resend-delay=15s' in services['prometheus']['command']
    assert 'evaluation_interval: 15s' in (ROOT / 'prometheus.yml').read_text()
    print('Optional Compose profile, internal ports, and healthchecks passed')


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
                      'test -r /run/monitoring-secrets/bot_token && '
                      'printf durable > /alertmanager/permission-check']
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
        (Path(temporary) / 'coverage.test.yml').write_text(document)
        run('docker', 'run', '--rm', '-v', f'{ROOT}:/etc/prometheus:ro',
            '-v', f'{temporary}:/test:ro', '--entrypoint', 'promtool', PROMETHEUS,
            'test', 'rules', '/test/coverage.test.yml')
    print('Nine-operation and per-item metric coverage passed')


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
            run('docker', 'stop', container)
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
            run('docker', 'stop', container)
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
            run('docker', 'stop', container, check=False)
        if 'network' in locals():
            run('docker', 'network', 'rm', network, check=False)
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    check_configurations()
    check_compose()
    check_production_permissions()
    check_metric_coverage()
    check_delivery()
