"""Run the pinned server and bridge on loopback for the cloud protocol check."""
import hashlib
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
pin = json.loads((root / 'integrations/nexus/upstream.json').read_text())
with zipfile.ZipFile(out / 'mage-server.zip') as archive:
    assert archive.testzip() is None
    archive.extractall(runtime)
config = runtime / 'config/config.xml'
tree = ET.parse(config)
server = tree.getroot().find('server')
assert server is not None
server.set('serverAddress', '127.0.0.1')
server.set('port', '17171')
server.set('authenticationActivated', 'false')
server.set('maxAiOpponents', '3')
tree.write(config, encoding='utf-8', xml_declaration=True)
(out / 'runtime-config.xml').write_bytes(config.read_bytes())
proxy_dir = out / 'proxy-runtime'
proxy_dir.mkdir(exist_ok=True)
opens = [f'--add-opens=java.base/{name}=ALL-UNNAMED' for name in
         ('java.io', 'java.util', 'java.lang', 'java.lang.reflect', 'java.text')]
processes = []
logs = []

def launch(name, command, cwd):
    handle = (out / f'{name}.log').open('w')
    logs.append(handle)
    child = subprocess.Popen(command, cwd=cwd, stdout=handle, stderr=subprocess.STDOUT)
    processes.append(child)
    return child

def wait_socket(child, port, seconds=480):
    deadline = time.monotonic()+seconds
    while time.monotonic()<deadline:
        if child.poll() is not None:
            raise RuntimeError(f'Process exited with {child.returncode} before opening {port}')
        try:
            with socket.create_connection(('127.0.0.1', port), timeout=1):
                return
        except OSError:
            time.sleep(2)
    raise TimeoutError(f'Port {port} did not become ready')

result = {'pins': pin, 'integrationCommit': os.environ.get('GITHUB_SHA'),
          'serverHost': '127.0.0.1', 'proxyHost': '127.0.0.1',
          'publicServerUsed': False, 'passed': False}
try:
    own_server = launch('server', ['java', '-Xmx2g', '-Djava.awt.headless=true', *opens,
        '-jar', str(runtime / 'lib/mage-server-1.4.62.jar'), '-testMode'], runtime)
    wait_socket(own_server, 17171)
    proxy = launch('proxy', ['java', '-Xmx1500m', '-Djava.awt.headless=true', *opens,
        '-jar', str(out / 'mage-proxy-1.4.62.jar'), '--host', '127.0.0.1',
        '--port', '17171', '--bind', '127.0.0.1', '--wsPort', '8787',
        '--httpPort', '8788', '--sim', 'off', '--restoreIds', 'none'], proxy_dir)
    wait_socket(proxy, 8787)
    deadline = time.monotonic()+480
    while time.monotonic()<deadline:
        if proxy.poll() is not None:
            raise RuntimeError(f'Bridge exited: {proxy.returncode}')
        try:
            with urllib.request.urlopen('http://127.0.0.1:8788/ready', timeout=3) as response:
                readiness = json.load(response)
            if readiness.get('ready') is True:
                result['readiness'] = readiness
                break
        except (OSError, urllib.error.HTTPError):
            pass
        time.sleep(2)
    else:
        raise TimeoutError('Bridge card database did not become ready')
    subprocess.run(['node', str(root / 'scripts/verify-xmage-bridge.mjs')],
                   cwd=root, check=True, timeout=240)
    result['passed'] = True
finally:
    (out / 'runtime-result.json').write_text(json.dumps(result, indent=2)+'\n')
    for child in reversed(processes):
        child.terminate()
        try:
            child.wait(timeout=10)
        except subprocess.TimeoutExpired:
            child.kill()
            child.wait(timeout=5)
    for handle in logs:
        handle.close()
    checksums = [f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}'
                 for path in sorted(out.iterdir()) if path.is_file() and path.name!='SHA256SUMS']
    (out / 'SHA256SUMS').write_text('\n'.join(checksums)+'\n')
if result['passed']:
    summary = ('## Stage 2: private XMage bridge verified\n\n'
               '- Pinned compatibility fork and pinned bridge built.\n'
               '- Bridge Java tests passed.\n'
               '- Own XMage server and bridge ran on loopback in this Actions job.\n'
               '- Protocol checks passed; see protocol-result.json for assertions.\n'
               '- No public XMage server or persistent hosted endpoint was used.\n'
               '- Four-seat Commander browser gameplay remains Stage 3.\n')
    print(summary)
    if os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as handle:
            handle.write(summary)
