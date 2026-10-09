# TP DevOps – Docker, GitHub Actions, Ansible

A student/department app: PostgreSQL database, Spring Boot API, Vue front, and an
Apache httpd reverse proxy. CI builds and publishes the images to Docker Hub,
Ansible deploys them on the server.

```
browser ──▶ httpd :80 ──┬─ /api/ ─▶ simple-api :8080 ──▶ database :5432
                        └─ /     ─▶ front :80
```

## Layout

| Path | Content |
| --- | --- |
| `database/` | Postgres image with init SQL scripts |
| `simple-api/` | Spring Boot API |
| `front/` | Vue front (served by nginx) |
| `http-server/` | Apache reverse proxy config |
| `docker-compose.yaml` | Run everything locally |
| `.github/workflows/` | CI/CD |
| `inventories/`, `roles/`, `playbook.yaml` | Ansible deployment |

## Run locally

```sh
docker compose up --build
```

## CI/CD

On push or PR to `main`/`develop`:

1. `test-backend` runs `mvn clean verify` (unit + integration tests).
2. `test-frontend` builds the front, checks the Apache config, and checks that
   `/api/` and `/` are proxied to the right container.
3. On `main` only, once tests pass, the four images are pushed to Docker Hub
   with tags `latest` and the commit SHA.
4. On `main` only, once all images are pushed, `deploy` runs the Ansible
   playbook against the server (continuous deployment).

`rollback.yml` (manual) points `latest` back to the images of a given commit SHA.

Required repository secrets:

| Secret | Value |
| --- | --- |
| `DOCKERHUB_USERNAME` | Docker Hub username |
| `DOCKERHUB_TOKEN` | Docker Hub access token (read/write) |
| `SSH_PRIVATE_KEY` | Private key used to SSH to the server as `admin` |
| `ANSIBLE_VAULT_PASSWORD` | Password that decrypts the vaulted variables |

## Deploy with Ansible

```sh
ansible all -m ping                # test the connection
ansible-playbook playbook.yaml     # install Docker and deploy the app
```

Secrets (DB password) are encrypted with Ansible Vault in
`inventories/group_vars/all.yml`. `ansible.cfg` reads the vault password from
`~/.ansible/tp-td3-vault-pass`. To encrypt a new value:

```sh
ansible-vault encrypt_string --name my_var 'value'
```

The app is then available at `http://valentin.belougne.takima.school`.

## Answers

### 3-1 Inventory and base commands

`inventories/setup.yml` declares the server in a `prod` group, with the SSH user
(`admin`) and private key. `ansible.cfg` sets it as the default inventory, so
`-i` is not needed.

```sh
ansible all -m ping                                          # check connection
ansible all -m setup -a "filter=ansible_distribution*"       # read OS facts
ansible all -m apt -a "name=apache2 state=absent" --become   # remove apache2
```

Ansible describes the desired state: running the last command twice changes
nothing the second time.

### 3-2 Playbook

`playbook.yaml` has two plays:

1. **Install Docker** (`docker` role): checks the host is Debian, adds Docker's
   APT repository and key, installs Docker Engine, installs the Docker SDK for
   Python in `/opt/docker_venv`, and makes sure the service is started and
   enabled. A handler restarts Docker when the packages change.
2. **Deploy the app**: runs the `network`, `database`, `app`, `front` and
   `proxy` roles. This play sets `ansible_python_interpreter` to
   `/opt/docker_venv/bin/python`, because the Docker modules need the Docker
   SDK installed there.

### 3-3 docker_container tasks

Shared variables are in `inventories/group_vars/all.yml` (Docker Hub user,
network name, DB credentials, with the password encrypted by Ansible Vault).

| Role | Module | Config |
| --- | --- | --- |
| network | `docker_network` | creates `app-network` |
| database | `docker_container` | `tp-devops-database`, env `POSTGRES_DB/USER/PASSWORD`, volume `db-volume` for data |
| app | `docker_container` | `tp-devops-simple-api`, env `DATABASE_HOST=database` and `DATABASE_PASSWORD` (read by `application.yml`) |
| front | `docker_container` | `tp-devops-front` |
| proxy | `docker_container` | `tp-devops-httpd`, publishes port `80:80` |

All containers join `app-network` so they reach each other by container name,
use `pull: always` to get the latest image, and `restart_policy: unless-stopped`.
Only the proxy exposes a port.

### Is it safe to deploy every new image automatically?

Not completely. Anything that ends up as `latest` on Docker Hub goes to
production: a bad commit that passes tests, a leaked Docker Hub token, or a
compromised base image would all be deployed with no human check. To make it
safer:

- deploy only after tests pass on `main`, and protect `main` (required reviews);
- deploy a specific SHA tag instead of `latest`, so you know exactly what runs
  and can roll back;
- keep the SSH key and passwords in GitHub secrets / Ansible Vault, with a
  dedicated, limited deploy user;
- scan images for vulnerabilities and require a manual approval (GitHub
  Environment) before production.

### Earlier parts

- **2-2 Secured variables:** GitHub secrets keep credentials out of the code
  and Git history, and are masked in logs.
- **2-3 `needs`:** images are only published once both test jobs pass.
- **2-4 Why push images:** the server pulls the exact tested image instead of
  rebuilding it, and SHA tags allow rollback.
