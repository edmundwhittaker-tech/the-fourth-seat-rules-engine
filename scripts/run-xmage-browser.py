"""Run an isolated real XMage/bridge/web/Chromium stack in the cloud."""
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parent.parent
out = root / 'build/nexus'
runtime = out / 'runtime'
runtime.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(out / 'mage-server.zip') as archive:
    assert archive.testzip() is None
    archive.extractall(runtime)
config = runtime / 'config/config.xml'
tree = ET.parse(config)
server = tree.getroot().find('server')
assert server is not None
for key, value in {'serverAddress': '127.0.0.1', 'port': '17171',
                   'authenticationActivated': 'false', 'maxAiOpponents': '3'}.items():
    server.set(key, value)
tree.write(config, encoding='utf-8', xml_declaration=True)
(out / 'browser-runtime-config.xml').write_bytes(config.read_bytes())
proxy_dir = out / 'proxy-runtime'
proxy_dir.mkdir(exist_ok=True)
opens = [f'--add-opens=java.base/{name}=ALL-UNNAMED' for name in
         ('java.io', 'java.util', 'java.lang', 'java.lang.reflect', 'java.text')]
processes, logs = [], []


def launch(name, command, cwd):
    handle = (out / f'{name}.log').open('w')
    logs.append(handle)
    child = subprocess.Popen(command, cwd=cwd, stdout=handle, stderr=subprocess.STDOUT)
    processes.append(child)
    return child


def wait_socket(child, port, seconds=480):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if child.poll() is not None:
            raise RuntimeError(f'Process exited with {child.returncode} before opening {port}')
        try:
            with socket.create_connection(('127.0.0.1', port), timeout=1):
                return
        except OSError:
            time.sleep(2)
    raise TimeoutError(f'Port {port} did not become ready')


result = {'integrationCommit': os.environ.get('GITHUB_SHA'),
          'pins': json.loads((root / 'integrations/nexus/upstream.json').read_text()),
          'publicServerUsed': False, 'simEnabled': False, 'cheatSetupUsed': False,
          'serverTestMode': False, 'passed': False}
try:
    own_server = launch('server', ['java', '-Xmx1800m', '-Djava.awt.headless=true', *opens,
        '-jar', str(runtime / 'lib/mage-server-1.4.62.jar')], runtime)
    wait_socket(own_server, 17171)
    proxy = launch('proxy', ['java', '-Xmx1200m', '-Djava.awt.headless=true', *opens,
        '-jar', str(out / 'mage-proxy-1.4.62.jar'), '--host', '127.0.0.1',
        '--port', '17171', '--bind', '127.0.0.1', '--wsPort', '8787',
        '--httpPort', '8788', '--sim', 'off', '--restoreIds', 'none'], proxy_dir)
    wait_socket(proxy, 8787)
    deadline = time.monotonic() + 480
    while time.monotonic() < deadline:
        if proxy.poll() is not None:
            raise RuntimeError(f'Bridge exited: {proxy.returncode}')
        try:
            with urllib.request.urlopen('http://127.0.0.1:8788/ready', timeout=3) as response:
                if json.load(response).get('ready') is True:
                    break
        except (OSError, urllib.error.HTTPError):
            pass
        time.sleep(2)
    else:
        raise TimeoutError('Bridge card database did not become ready')
    web = launch('web', ['npm', 'run', 'dev', '--', '--host', '127.0.0.1',
                        '--port', '5173', '--strictPort'], root / '.upstream/nexus/web')
    wait_socket(web, 5173, seconds=90)
    subprocess.run(['node', str(root / 'integrations/nexus/browser/verify-commander.mjs')],
                   cwd=root, check=True, timeout=780)
    result['passed'] = True
except BaseException as error:
    result['failure'] = str(error)
    for name in ('server', 'proxy', 'web'):
        path = out / f'{name}.log'
        if path.exists():
            print(f'{name} log tail:', flush=True)
            print('\n'.join(path.read_text(errors='replace').splitlines()[-45:]), flush=True)
    raise
finally:
    (out / 'browser-runtime-result.json').write_text(json.dumps(result, indent=2) + '\n')
    for child in reversed(processes):
        child.terminate()
        try:
            child.wait(timeout=10)
        except subprocess.TimeoutExpired:
            child.kill()
            child.wait(timeout=5)
    for handle in logs:
        handle.close()
    summary = ('## Stage 3: four-seat Commander browser proof\n\n'
               f'Passed: **{result["passed"]}**. See browser-result.json and browser/ evidence.\n\n'
               'Pinned client, bridge and our own server ran on loopback. SIM and server '
               'test mode were disabled. No public server or persistent endpoint was used.\n')
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as handle:
            handle.write(summary)
