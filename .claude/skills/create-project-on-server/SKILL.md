---
name: create-project-on-server
description: Provision a Jakon project on a server with the Ansible playbook in ansible/ — creates the system user, /srv/<project> directory, systemd service and nginx site. Use when the user wants to create, set up, provision or prepare a project/app on a server, or asks to run the provisioning playbook.
---

# Create project on server

Runs `ansible/provision.yml` (role `ansible/roles/jakon_app`) for this project. Read `ansible/README.md` first if anything below is unclear — it is the source of truth for variables.

## 1. Collect inputs

| input          | how to get it                                                                                          |
|----------------|--------------------------------------------------------------------------------------------------------|
| `project_name` | ask; suggest `projectName` from `build.sbt` (lowercase, `[a-z][a-z0-9_-]*`)                            |
| `server_name`  | ask — the domain nginx serves (e.g. `myapp.example.com`). No default.                                  |
| target server  | hosts in `ansible/inventory.ini` group `[jakon]`; if there are several, ask which one (`--limit <host>`) |
| `jakon_port`   | only needed when the server has no `/srv/<project>/jakon_config.properties` yet — see step 3          |

Don't guess the domain or port; ask with one question listing everything missing.

## 2. Inventory

`ansible/inventory.ini` is git-ignored and per-user. If it is missing:
- copy `ansible/inventory.example.ini` to `ansible/inventory.ini`
- ask for the server host (an alias from `~/.ssh/config` works) and `deploy_user` — the existing server user that should get access to `/srv/<project>` (uploads jars and config). Write it as `<host> deploy_user=<user>` under `[jakon]`.

Never commit `inventory.ini`.

## 3. Pre-flight checks (read-only)

Run these and stop with a clear explanation if one fails:

1. **Ansible available.** Ansible does not run natively on Windows — on Windows use WSL:
   - Windows: `wsl -e ansible-playbook --version`
   - Linux/macOS: `ansible-playbook --version`

   If missing, tell the user to install it (`sudo apt install ansible` inside WSL). Do not install it yourself.
2. **SSH works:** `ssh -o BatchMode=yes <host> true`. On Windows the keys usually live in Windows, not WSL — if SSH works from Windows but not from WSL, point the user to `ansible/README.md` / copying keys into WSL.
3. **Port.** `ssh <host> "grep -h '^port=' /srv/<project>/jakon_config.properties 2>/dev/null"`
   - found → the playbook uses it; don't pass `jakon_port` (if the user gave a different one, warn — the playbook fails on mismatch)
   - not found → `jakon_port` is required; ask for it. To help pick a free one, show `ssh <host> "ss -ltn"` listening ports.
4. **Existing project.** `ssh <host> "ls -ld /srv/<project>; systemctl is-active <project>"`. If the project already exists, tell the user re-running is safe (idempotent) but it re-applies directory permissions (`2770`) and restarts the service when the unit file changed. Confirm before continuing.
5. **Syntax:** from `ansible/`: `ANSIBLE_CONFIG=./ansible.cfg ansible-playbook provision.yml --syntax-check -e project_name=<project>` (via `wsl -e sh -c "cd <wsl path to ansible> && ..."` on Windows; convert the path with `wslpath`).

`ANSIBLE_CONFIG=./ansible.cfg` is needed when the repo is on `/mnt/c` — Ansible ignores `ansible.cfg` in world-writable directories.

## 4. Run the playbook

The playbook needs sudo on the server (`--ask-become-pass`). **Never ask for, type, or pass the sudo password yourself** (no `-e ansible_become_password`, no `echo pw | ...`). The user runs the command in their own terminal:

```bash
cd ansible
ANSIBLE_CONFIG=./ansible.cfg ansible-playbook provision.yml --limit <host> -e project_name=<project> -e server_name=<domain> [-e jakon_port=<port>] --ask-become-pass
```

On Windows prefix with `wsl` and use the WSL path (`/mnt/c/...`). Give the user the exact, fully filled-in command in a `bash` code block. If a terminal tool is available, offer to open a terminal tab with the command so they only type the password.

Wait for the user to report back (or read their terminal output if possible).

## 5. Verify (read-only)

```bash
ssh <host> "ls -ld /srv/<project> /srv/<project>/*; systemctl is-enabled <project>; systemctl is-active <project>; ls -l /etc/nginx/sites-enabled/<project>; id <project>"
```

Expected:
- `/srv/<project>` and subdirs `jar out static templates upload` owned by `<project>:<project>`, mode `drwxrws---`
- service `enabled`; `active` only once `app.jar` is deployed (otherwise `inactive` is fine)
- nginx site symlink exists
- `id <deploy_user>` lists group `<project>` (takes effect on the user's next SSH login)

## 6. Report

Summarize what was created (user, directory, service, nginx site, port, domain) and the next steps:
- CI must upload `jakon_config.properties` (with the same `port=`) and the jar to `/srv/<project>/`
- point `/srv/<project>/app.jar` at the jar and `sudo systemctl restart <project>` (see "Deploy aplikace" in `ansible/README.md`)
- the app needs a Jakon version with the `host` setting, otherwise it ignores `host=127.0.0.1` and listens on all interfaces
- DNS for `<domain>` must point to the server; TLS is not handled by the playbook
