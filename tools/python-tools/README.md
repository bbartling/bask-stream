# Python baskStream test tools

Small read-only Python scripts for testing a Niagara station with the `baskStream` module installed.

These are intended for commissioning, diagnostics, and early Open-FDD connector experiments. They do **not** write to points, acknowledge alarms, or issue overrides.

## Files

```text
tools/python/
  baskstream_client.py   # reusable Niagara SCRAM + MessagePack WebSocket client
  baskstream_cli.py      # practical CLI: health, smoke, tree, values, read, schedules
  baskstream_smoke.py    # minimal smoke test
  requirements.txt
```

## Station prerequisites

1. Install `baskStream-rt.jar` on the Niagara station host.
2. Restart the station.
3. Add `BASkStreamService` under station services.
4. Verify the service is enabled and its servlet name is `stream`.
5. Use a Niagara user that can log into station web and browse/read the target station paths.
6. Confirm the station web port is reachable from the client machine.

For local/self-signed test benches, the scripts default to TLS verification off. Use `--verify-tls` only when the station certificate is trusted by the client machine.

## Quick network check

Unauthenticated `/stream/health` should usually redirect to Niagara login. That is a good sign because it proves the remote machine can reach the Niagara web server and the `stream` servlet path.

### Windows PowerShell

```powershell
curl.exe -k -i https://localhost/stream/health
curl.exe -k -i https://192.168.204.11/stream/health
```

Expected before login:

```text
HTTP/1.1 302 Found
Location: https://<station>/login
```

### Linux bash

```bash
curl -k -i https://192.168.204.11/stream/health
nc -vz 192.168.204.11 443
```

### macOS zsh/bash

```zsh
curl -k -i https://192.168.204.11/stream/health
nc -vz 192.168.204.11 443
```

On macOS, if `nc` behaves differently, use:

```zsh
nmap -Pn -p 443 192.168.204.11
```

## Install

### Windows PowerShell

```powershell
cd .\tools\python
py -m venv .venv
.\.venv\Scripts\Activate.ps1
py -m pip install -r requirements.txt
```

If script activation is blocked:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
.\.venv\Scripts\Activate.ps1
```

### Linux bash

```bash
cd tools/python
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
```

### macOS zsh/bash

```zsh
cd tools/python
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
```

If macOS does not have Python 3 installed:

```zsh
brew install python
```

## PowerShell ORD quoting

Niagara slot ORDs commonly contain `$20`, `$2d`, `$23`, etc.

In PowerShell, **always wrap Niagara ORDs in single quotes**. Double quotes will treat `$20BENCHTEST` like a PowerShell variable and corrupt the ORD.

Good:

```powershell
--base 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points'
```

Bad:

```powershell
--base "slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points"
```

Linux bash and macOS zsh should also use single quotes for Niagara ORDs. It is safer and avoids shell expansion surprises.

## Credentials

Prefer `--ask-pass` for interactive testing.

### Windows PowerShell

```powershell
py .\baskstream_cli.py --station https://localhost --user admin --ask-pass health
```

Or use environment variables:

```powershell
$env:BASKSTREAM_USER="admin"
$env:BASKSTREAM_PASS="<station-password>"
```

### Linux bash

```bash
python3 baskstream_cli.py --station https://192.168.204.11 --user admin --ask-pass health
```

Or use environment variables:

```bash
export BASKSTREAM_USER="admin"
export BASKSTREAM_PASS="<station-password>"
```

### macOS zsh/bash

```zsh
python3 baskstream_cli.py --station https://192.168.204.11 --user admin --ask-pass health
```

Or use environment variables:

```zsh
export BASKSTREAM_USER="admin"
export BASKSTREAM_PASS="<station-password>"
```

Do not commit real station credentials.

## Smoke test

### Windows PowerShell, local station

```powershell
py .\baskstream_smoke.py `
  --station https://localhost `
  --user admin `
  --ask-pass `
  --root 'slot:/Drivers'
```

### Linux bash, remote station

```bash
python3 baskstream_smoke.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  --root 'slot:/Drivers'
```

### macOS zsh/bash, remote station

```zsh
python3 baskstream_smoke.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  --root 'slot:/Drivers'
```

Expected result:

```text
[login] authenticatedUser=admin
[ws] connecting wss://<station>/stream
[capabilities] apiVersion=1.2
[browse] slot:/Drivers
- NiagaraNetwork | slot:/Drivers/NiagaraNetwork | niagaraDriver:NiagaraNetwork {ok}
- BacnetNetwork  | slot:/Drivers/BacnetNetwork  | bacnet:BacnetNetwork {ok}

OK: baskStream smoke test completed.
```

## Print a compact tree

### Windows PowerShell

```powershell
py .\baskstream_cli.py `
  --station https://localhost `
  --user admin `
  --ask-pass `
  tree `
  --base 'slot:/Drivers/BacnetNetwork' `
  --depth 4
```

### Linux bash

```bash
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  tree \
  --base 'slot:/Drivers/BacnetNetwork' \
  --depth 4
```

### macOS zsh/bash

```zsh
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  tree \
  --base 'slot:/Drivers/BacnetNetwork' \
  --depth 4
```

The tree command skips child links that jump outside the requested base by default. This prevents a BACnet device browse from unexpectedly expanding into the entire station through a `slot:/` child. Use `--follow-external` only when intentionally exploring cross-links.

## Print BACnet point names and values

### Windows PowerShell

```powershell
py .\baskstream_cli.py `
  --station https://localhost `
  --user admin `
  --ask-pass `
  values `
  --base 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points'
```

### Linux bash

```bash
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  values \
  --base 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points'
```

### macOS zsh/bash

```zsh
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  values \
  --base 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points'
```

Example output:

```text
Point          Value   Status       Type                  ORD
-------------------------------------------------------------
OA-T           75.07   {ok}         control:NumericPoint  slot:/...
DUCT-T         52.94   {ok}         control:NumericPoint  slot:/...
CURRENT-S      false   {ok}         control:BooleanPoint  slot:/...
```

Filter by name/ORD/type:

```bash
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  values \
  --base 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points' \
  --query temp
```

List point ORDs without reading values:

```bash
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  values \
  --base 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points' \
  --list-only
```

Read one explicit point:

```bash
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  read 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points/OA$2dT'
```

## Export values

### CSV

Windows PowerShell:

```powershell
py .\baskstream_cli.py `
  --station https://localhost `
  --user admin `
  --ask-pass `
  values `
  --base 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points' `
  --format csv `
  --output bacnet_values.csv
```

Linux/macOS:

```bash
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  values \
  --base 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points' \
  --format csv \
  --output bacnet_values.csv
```

### JSON

```bash
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  values \
  --base 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points' \
  --format json \
  --output bacnet_values.json
```

### Markdown table

```bash
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  values \
  --base 'slot:/Drivers/BacnetNetwork/BENS$20BENCHTEST$20BOX/points' \
  --format markdown
```

## Schedules

List schedules from the whole station, depth-limited.

Windows PowerShell:

```powershell
py .\baskstream_cli.py `
  --station https://localhost `
  --user admin `
  --ask-pass `
  schedules `
  --base 'slot:/' `
  --depth 5
```

Linux/macOS:

```bash
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  schedules \
  --base 'slot:/' \
  --depth 5
```

Read schedule summaries where supported:

```bash
python3 baskstream_cli.py \
  --station https://192.168.204.11 \
  --user admin \
  --ask-pass \
  schedules \
  --base 'slot:/' \
  --depth 5 \
  --read
```

## Notes for remote Open-FDD servers

For a backend Python connector running on Linux or macOS, `Allowed Origins` usually does not need to include the Open-FDD server. Origin allowlists are mainly for browser JavaScript clients. The preferred architecture is:

```text
Browser/UI
  -> Open-FDD backend
    -> Python baskStream connector
      -> Niagara https://<station>/stream
```

Only add remote origins if a browser page served from the Open-FDD host connects directly to Niagara's `/stream` WebSocket.

## Notes for Open-FDD

A safe first Open-FDD Niagara connector should remain read-only:

```text
Niagara station
  -> baskStream /stream WebSocket
  -> cached discovery of devices/points/schedules
  -> bounded batch reads
  -> Arrow table
  -> Open-FDD rules
```

Recommended connector behavior:

- Cache discovery results.
- Use shallow browse and depth limits.
- Read values in batches.
- Avoid subscribing to every point forever.
- Do not implement writes/overrides until a separate safety model exists.
- Use dedicated read-only Niagara users for production testing.

## Troubleshooting

`/stream/health` returns `302`:

- The client is not logged in yet.
- Confirm the username can log into Niagara Web.
- If this is from `curl`, it may still be a good network test.

`/stream/health` returns `403`:

- The user is authenticated but not allowed to access the service/path.
- Test with a known-good admin user.
- Confirm `BASkStreamService` is enabled.

Tree or values returns nothing for a BACnet device:

- In PowerShell, check that the ORD is in single quotes.
- Try browsing the parent first:
  ```powershell
  py .\baskstream_cli.py --station https://localhost --user admin --ask-pass tree --base 'slot:/Drivers/BacnetNetwork' --depth 2
  ```
- Use the exact ORD printed by the tree command.

The output explodes into the entire station:

- The CLI skips external slot jumps by default.
- Keep `--follow-external` off unless intentionally exploring cross-links.
- Lower `--depth` or `--max-nodes`.

Remote Linux or macOS cannot connect:

- Test the port:
  ```bash
  nc -vz <station-ip> 443
  ```
- Test the health endpoint:
  ```bash
  curl -k -i https://<station-ip>/stream/health
  ```
- Check Windows firewall on the Niagara host.
- Use the same station URL in the scripts that you tested with `curl`.

## Suggested contribution location

For a PR to `bask-stream`, a low-friction location would be:

```text
tools/python/
  README.md
  requirements.txt
  baskstream_client.py
  baskstream_cli.py
  baskstream_smoke.py
```

These scripts are intentionally read-only and dependency-light so they are easy to review.
