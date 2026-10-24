"""On-demand cloud engine. Never prints the broker secret or publishes ports itself."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
STATE = ROOT / '.codespace-engine'
ENV_FILE = STATE / 'engine.env'
IMAGE = 'fourth-seat-engine:codespace'
CONTAINER = 'fourth-seat-engine'


def run(*args, **kwargs):
    return subprocess.run(list(args), cwd=ROOT, check=True, **kwargs)


def prepare():
    STATE.mkdir(mode=0o700, exist_ok=True)
    os.chmod(STATE, 0o700)
    if not ENV_FILE.exists():
        with ENV_FILE.open('x') as handle:
            os.chmod(ENV_FILE, 0o600)
            handle.write('ENGINE_APP_SECRET=' + secrets.token_hex(32) + '\n')
            handle.write('SITE_ORIGIN=https://thefourthseat.net\n')


def inspect():
    result = subprocess.run(['docker', 'container', 'inspect', CONTAINER],
                            cwd=ROOT, capture_output=True, text=True)
    return json.loads(result.stdout)[0] if result.returncode == 0 else None


def connection():
    name = os.environ.get('CODESPACE_NAME')
    domain = os.environ.get('GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN', 'app.github.dev')
    if name:
        print('FOURTH_SEAT_ENGINE_URL=https://' + name + '-8080.' + domain)
    else:
        print('Use the HTTPS forwarded address for port 8080 in the Codespaces Ports panel.')
    print('Secret location: .codespace-engine/engine.env (ignored, owner-readable).')
    print('Copy ENGINE_APP_SECRET into the Site server setting FOURTH_SEAT_ENGINE_SECRET.')
    print('Keep the secret out of chat, screenshots, Git commits and browser code.')
    print('Port 8080 must be Public for the Site broker and other players to reach it.')
    print('The gateway still requires broker authentication and signed browser tickets.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['build', 'start', 'stop', 'status', 'connection'])
    args = parser.parse_args()
    if args.command == 'build':
        prepare()
        run('docker', 'build', '--file', 'integrations/fourth-seat/hosting/Dockerfile',
            '--tag', IMAGE, '.')
    elif args.command == 'start':
        prepare()
        current = inspect()
        if current and current['State']['Running']:
            print('Engine container is already running.')
        else:
            if current:
                run('docker', 'rm', CONTAINER, stdout=subprocess.DEVNULL)
            run('docker', 'run', '--detach', '--name', CONTAINER, '--memory', '4g',
                '--cpus', '2', '--env-file', str(ENV_FILE),
                '--publish', '127.0.0.1:8080:8080',
                '--volume', 'fourth-seat-engine-data:/data', IMAGE,
                stdout=subprocess.DEVNULL)
            print('Engine is starting. First card-database initialization can take several minutes.')
        connection()
    elif args.command == 'stop':
        if inspect():
            run('docker', 'stop', '--time', '30', CONTAINER, stdout=subprocess.DEVNULL)
        print('Engine stopped. Also stop the Codespace in GitHub to stop compute usage.')
    elif args.command == 'connection':
        connection()
    else:
        current = inspect()
        if not current or not current['State']['Running']:
            raise SystemExit('Engine container is stopped.')
        try:
            with urllib.request.urlopen('http://127.0.0.1:8080/health', timeout=5) as response:
                ready = json.load(response).get('ready') is True
        except (OSError, ValueError):
            ready = False
        print('Engine ready.' if ready else 'Engine is running; database is still starting or unhealthy.')
        if not ready:
            raise SystemExit(1)


if __name__ == '__main__':
    main()
