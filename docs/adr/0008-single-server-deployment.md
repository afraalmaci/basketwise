# 8. Single-server deployment with Docker Compose and Caddy

**Status:** Accepted
**Date:** 2026-10-25

## Context

The project needs a public live demo (synthetic data only, see ADR 0002). The stack is a
Spring Boot backend, PostgreSQL and a static React frontend served by nginx, already
packaged with Docker Compose.

Options considered:

- **Free PaaS tiers** (e.g. Render free web service + a free hosted Postgres): no cost,
  but the free service sleeps after 15 minutes without traffic and needs about a minute
  to wake up, and a JVM on 0.1 CPU starts slowly on top of that. Visitors opening the demo
  link would often see a long blank wait.
- **Free VM offers** (e.g. Oracle Cloud Always Free): no cost, but sign-up is unreliable
  and capacity is not guaranteed.
- **A small paid VM** (Hetzner CX23, a few euros per month): always on, enough memory for
  the JVM next to Postgres, and the existing Compose setup runs almost unchanged.

## Decision

- Run the whole stack on one small VM with a separate `docker-compose.prod.yml`.
- Put Caddy in front as the only service with published ports. It obtains and renews
  Let's Encrypt certificates automatically, which also makes browser geolocation work
  (it requires HTTPS).
- Use an `sslip.io` hostname derived from the server IP instead of buying a domain.
- Restrict inbound traffic with the provider's firewall (22, 80, 443), not `ufw`, because
  Docker's published ports bypass `ufw` rules.
- Deploy manually (`git pull` + `docker compose up -d --build`) for now.

## Consequences

- The demo responds immediately; no cold starts.
- Postgres, the backend and the frontend are not reachable from the internet directly.
- The server needs basic upkeep: unattended security upgrades are on, SSH allows keys only.
- Images are built on the server, which needs swap for the Maven build on a 4 GB VM.
- Deploys cause a short downtime while the backend restarts; acceptable for a demo.
- Possible follow-ups: build images in GitHub Actions and push them to GHCR, then deploy
  from CI over SSH; an own domain by changing `SITE_ADDRESS`.
