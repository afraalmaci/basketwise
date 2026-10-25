# Deploying to a single server

The live demo runs on one small virtual server with Docker Compose. Caddy sits in front of
the app and handles HTTPS. The reasoning is in [ADR 0008](adr/0008-single-server-deployment.md).

```
Internet ──443──▶ Caddy ──▶ frontend (nginx: static files, /api proxy) ──▶ backend ──▶ Postgres
```

Only Caddy publishes ports. Everything else talks over the internal Docker network.

## 1. Create the server

On [Hetzner Cloud](https://console.hetzner.cloud):

1. New project, then **Add Server**.
2. **Location:** Nuremberg or Falkenstein (closest to the Netherlands).
3. **Image:** Ubuntu 24.04.
4. **Type:** Shared vCPU, x86, **CX23**.
5. **Networking:** keep **Public IPv4** on (many networks still have no IPv6).
6. **SSH key:** add your public key (`cat ~/.ssh/id_ed25519.pub`; create one with
   `ssh-keygen -t ed25519` if you have none). Do not use password login.
7. **Firewall:** create one with these inbound rules and attach it to the server:

   | Protocol | Port | Why |
   |---|---|---|
   | TCP | 22 | SSH |
   | TCP | 80 | HTTP, needed for the certificate challenge and the redirect to HTTPS |
   | TCP | 443 | HTTPS |
   | UDP | 443 | HTTP/3 |

   Use the Hetzner firewall rather than `ufw`: Docker writes its own iptables rules for
   published ports, so `ufw` alone would not block them.

Note the server's IPv4 address. Below it is written as `203.0.113.10`.

## 2. Prepare the server

```bash
ssh root@203.0.113.10

# Updates (Ubuntu installs security updates automatically via unattended-upgrades)
apt update && apt upgrade -y

# A non-root user that can use sudo, with the same SSH key
adduser --disabled-password --gecos "" deploy
usermod -aG sudo deploy
passwd deploy                      # needed for sudo
rsync --archive --chown=deploy:deploy ~/.ssh /home/deploy

# Docker Engine + Compose plugin
curl -fsSL https://get.docker.com | sh
usermod -aG docker deploy

# 2 GB swap: building the backend with Maven needs memory headroom next to Postgres
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

Before turning off root login, open a **second terminal** and check that
`ssh deploy@203.0.113.10` works. Then, back as root:

```bash
cat > /etc/ssh/sshd_config.d/99-hardening.conf <<'EOF'
PermitRootLogin no
PasswordAuthentication no
EOF
systemctl reload ssh
exit
```

## 3. Configure and start the app

```bash
ssh deploy@203.0.113.10
git clone https://github.com/afraalmaci/basketwise.git
cd basketwise
cp .env.example .env
nano .env
```

Set these in `.env`:

```bash
DB_PASSWORD=<long random string, e.g. from: openssl rand -hex 24>
SITE_ADDRESS=basketwise.203-0-113-10.sslip.io
# optional, same as local development
ORS_API_KEY=
VITE_CARTO_API_KEY=
```

`sslip.io` is a free DNS service: any name ending in `<ip-with-dashes>.sslip.io` resolves
to that IP, so no domain purchase is needed. Caddy requests a Let's Encrypt certificate
for it automatically. To switch to an own domain later, point its A record to the server
and change `SITE_ADDRESS`.

`DB_PASSWORD` is only set once: Postgres stores it when the volume is first created.
Changing it later means changing it inside Postgres too.

Start everything (the first build takes several minutes):

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f caddy backend   # Ctrl+C to stop following
```

Check it:

```bash
curl https://basketwise.203-0-113-10.sslip.io/api/system/info
```

Then open the address in a browser. Browser geolocation only works over HTTPS, which is
one more reason the certificate matters.

## 4. Deploying updates

After pushing to `main`:

```bash
ssh deploy@203.0.113.10
cd basketwise
git pull
docker compose -f docker-compose.prod.yml up -d --build
docker image prune -f              # remove old image layers
```

Only changed services are rebuilt and restarted. The site is briefly unavailable while the
backend restarts.

## Operations

| Task | Command (in `~/basketwise`) |
|---|---|
| Status | `docker compose -f docker-compose.prod.yml ps` |
| Logs | `docker compose -f docker-compose.prod.yml logs --tail 100 backend` |
| Memory use | `docker stats --no-stream` |
| Restart one service | `docker compose -f docker-compose.prod.yml restart backend` |
| Stop everything | `docker compose -f docker-compose.prod.yml down` |
| Reset the database | `docker compose -f docker-compose.prod.yml down`, `docker volume rm basketwise_db-data`, then `up -d` |

No database backups are needed: the demo dataset is generated deterministically on first
start (see [ADR 0002](adr/0002-synthetic-demo-data.md)), so a reset recreates the same
data.

Do not remove the `caddy-data` volume casually: it holds the certificate, and requesting
new ones repeatedly can hit Let's Encrypt rate limits.

## Troubleshooting

- **Browser shows a certificate error or Caddy logs ACME errors:** check that ports 80
  and 443 are open in the Hetzner firewall and that `SITE_ADDRESS` contains the server's
  current IP.
- **Backend keeps restarting:** `logs backend`. If the logs end abruptly, check
  `docker inspect basketwise-backend-1 --format '{{.State.OOMKilled}}'`; if it prints
  `true`, raise `mem_limit` in `docker-compose.prod.yml`.
- **Build fails with "killed" during `mvn` or `npm`:** the server ran out of memory;
  check that swap is on with `free -h`.
