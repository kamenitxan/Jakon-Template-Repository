# Ansible – provisioning Jakon aplikace

Připraví na serveru vše potřebné pro novou Jakon aplikaci:

- systémového uživatele `<projekt>` (deploy uživatel je přidán do jeho skupiny)
- `/srv/<projekt>/` se složkami `jar`, `out`, `static`, `templates/<projekt>`, `upload`
- systemd službu `/etc/systemd/system/<projekt>.service` (spustí se, až existuje `app.jar`)
- nginx site `/etc/nginx/sites-available/<projekt>` + symlink do `sites-enabled`

`jakon_config.properties` playbook nevytváří – nahrává ho CI.

## Izolace aplikací

- každá aplikace běží pod vlastním systémovým uživatelem bez shellu
- `/srv/<projekt>` má práva `2770` – přístup má jen uživatel aplikace, deploy uživatel a nginx (`www-data`, kvůli servírování `out/`)
- systemd služba je sandboxovaná: v `/srv` vidí jen svou vlastní složku (`TemporaryFileSystem` + `BindPaths`),
  zbytek systému je jen pro čtení (`ProtectSystem=strict`), `/home` je skrytý, vlastní `/tmp`, žádné nové privilegia
- Jakon poslouchá jen na `127.0.0.1` (služba se spouští s argumentem `host=`, vyžaduje Jakon s podporou nastavení `host`),
  ven je dostupný jen přes nginx

## Spuštění

Ansible neběží nativně na Windows – spouští se z WSL.

```bash
cd ansible
cp inventory.example.ini inventory.ini   # doplň svůj server a deploy_user, inventory.ini je v .gitignore
ansible-playbook provision.yml -e project_name=myapp -e server_name=myapp.example.com --ask-become-pass
```

Povinné parametry:

| proměnná       | popis                                                                                   |
|----------------|-----------------------------------------------------------------------------------------|
| `project_name` | název projektu (uživatel, složka v `/srv`, název služby a nginx site)                   |
| `server_name`  | doména pro nginx                                                                        |
| `jakon_port`   | port aplikace – povinný jen pokud na serveru ještě není `/srv/<projekt>/jakon_config.properties` s `port=` |

Pokud config na serveru existuje, port se bere z něj. Když je zadaný i `jakon_port` a liší se, playbook skončí chybou.

Volitelné parametry:

| proměnná            | default                          | popis                                                                 |
|---------------------|----------------------------------|-----------------------------------------------------------------------|
| `deploy_user`       | uživatel, pod kterým se Ansible připojuje | existující uživatel na serveru, který dostane přístup do `/srv/<projekt>` (přidá se do skupiny aplikace, projeví se po novém přihlášení). Nastavuje se per server v `inventory.ini` |
| `jakon_host`        | `127.0.0.1`                      | adresa, na které Jakon poslouchá                                      |
| `nginx_listen_port` | `8080`                           |                                                                       |

## Deploy aplikace

```bash
scp target/<projekt>-assembly-X.Y.Z.jar <server>:/srv/<projekt>/jar/
ssh <server> 'ln -sfn /srv/<projekt>/jar/<projekt>-assembly-X.Y.Z.jar /srv/<projekt>/app.jar && sudo systemctl restart <projekt>'
```
