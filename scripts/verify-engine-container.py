import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.error
import urllib.request

out = Path('build/nexus/fourth-seat')
out.mkdir(parents=True, exist_ok=True)
result = {'passed': False, 'memoryLimitGiB': 4, 'publicServerUsed': False}
secret = secrets.token_hex(32)
try:
    subprocess.run(['docker', 'build', '--build-arg', 'BINARIES=artifact', '-f', 'integrations/fourth-seat/hosting/Dockerfile', '-t', 'fourth-seat-engine:ci', '.'], check=True)
    subprocess.run(['docker', 'run', '-d', '--name', 'fourth-seat-engine-ci', '--memory=4g', '--cpus=2', '-p', '127.0.0.1:18080:8080', '-e', 'ENGINE_APP_SECRET', 'fourth-seat-engine:ci'], env={**os.environ, 'ENGINE_APP_SECRET': secret}, check=True)
    deadline = time.monotonic() + 480
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen('http://127.0.0.1:18080/health', timeout=4) as response:
                if json.load(response).get('ready'):
                    break
        except OSError:
            time.sleep(2)
    else:
        raise RuntimeError('Container did not become ready within eight minutes.')
    request = urllib.request.Request('http://127.0.0.1:18080/api', data=json.dumps({'operation': 'session', 'browserKey': secrets.token_hex(32)}).encode(), headers={'Authorization': 'Bearer ' + secret, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=10) as response:
        session = json.load(response)
        assert session.get('ticket') and session.get('user')
    result.update(passed=True, imageId=subprocess.check_output(['docker', 'image', 'inspect', '--format={{.Id}}', 'fourth-seat-engine:ci'], text=True).strip())
finally:
    logs = subprocess.run(['docker', 'logs', 'fourth-seat-engine-ci'], text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT).stdout
    (out / 'container.log').write_text(logs.replace(secret, '[redacted]'))
    (out / 'container-result.json').write_text(json.dumps(result, indent=2) + '\n')
    subprocess.run(['docker', 'rm', '-f', 'fourth-seat-engine-ci'], check=False)
print(json.dumps(result))
