"""Supervise the private Java services and the public fixed-target gateway."""
import os
from pathlib import Path
import signal
import socket
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

if len(os.environ.get('ENGINE_APP_SECRET', '')) < 32:
    raise RuntimeError('Set ENGINE_APP_SECRET to a random value of at least 32 characters.')
data = Path(os.environ.get('DATA_DIR', '/data'))
runtime = data / 'server'
runtime.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile('/opt/engine/mage-server.zip') as archive:
    archive.extractall(runtime)
config = runtime / 'config/config.xml'
tree = ET.parse(config)
server = tree.getroot().find('server')
for key, value in {'serverAddress': '127.0.0.1', 'port': '17171', 'authenticationActivated': 'false', 'maxAiOpponents': '3'}.items():
    server.set(key, value)
tree.write(config, encoding='utf-8', xml_declaration=True)
opens = [f'--add-opens=java.base/{name}=ALL-UNNAMED' for name in ('java.io', 'java.util', 'java.lang', 'java.lang.reflect', 'java.text')]
children = []
stopping = False

def stop(signum=None, frame=None):
    global stopping
    stopping = True
    for child in reversed(children):
        if child.poll() is None:
            child.terminate()

def launch(command, cwd):
    child = subprocess.Popen(command, cwd=cwd)
    children.append(child)
    return child

def wait_port(child, port):
    deadline = time.monotonic() + 480
    while time.monotonic() < deadline and not stopping:
        if child.poll() is not None:
            raise RuntimeError(f'Service failed before opening port {port}')
        try:
            with socket.create_connection(('127.0.0.1', port), timeout=1):
                return
        except OSError:
            time.sleep(1)
    raise RuntimeError('Service startup timed out or was interrupted.')

for sig in (signal.SIGTERM, signal.SIGINT):
    signal.signal(sig, stop)
try:
    java = launch(['java', '-Xmx1800m', '-Djava.awt.headless=true', *opens, '-jar', str(runtime / 'lib/mage-server-1.4.62.jar')], runtime)
    wait_port(java, 17171)
    proxy_dir = data / 'proxy'
    proxy_dir.mkdir(exist_ok=True)
    proxy = launch(['java', '-Xmx1200m', '-Djava.awt.headless=true', *opens, '-jar', '/opt/engine/mage-proxy-1.4.62.jar', '--host', '127.0.0.1', '--port', '17171', '--bind', '127.0.0.1', '--wsPort', '8787', '--httpPort', '8788', '--sim', 'off', '--restoreIds', str(data / 'restore-ids.json')], proxy_dir)
    wait_port(proxy, 8787)
    deadline = time.monotonic() + 480
    while time.monotonic() < deadline and not stopping:
        try:
            with urllib.request.urlopen('http://127.0.0.1:8788/ready', timeout=3) as response:
                if response.status == 200:
                    break
        except OSError:
            time.sleep(2)
    else:
        raise RuntimeError('Bridge card database startup failed.')
    launch(['node', '--max-old-space-size=192', '/opt/gateway/server.mjs'], data)
    while not stopping:
        if any(child.poll() is not None for child in children):
            raise RuntimeError('A required engine service exited.')
        time.sleep(1)
finally:
    stop()
    for child in children:
        try:
            child.wait(timeout=10)
        except subprocess.TimeoutExpired:
            child.kill()
            child.wait()
