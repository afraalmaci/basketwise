# BasketWise

[![CI](https://github.com/afraalmaci/basketwise/actions/workflows/ci.yml/badge.svg)](https://github.com/afraalmaci/basketwise/actions/workflows/ci.yml)

BasketWise splits a grocery list across supermarkets and plans the route between them,
so you can see whether visiting a second store is actually worth the extra time.

> **Demo data only.** All stores, products and prices in this project are synthetic.
> Store names are fictional and do not represent real supermarket chains.

## What makes it interesting

| Module | Problem it solves |
|---|---|
| **Optimization engine** | Picks which store to buy each item from, respecting pack sizes and a max number of stores. Exact search over store subsets, verified with property-based tests. |
| **Route-aware optimizer** | Adds travel time to the cost function: chooses store branches and visiting order from your location, by bike or on foot. |
| **Unit normalization** | Parses messy quantity strings (`1L`, `1000 ml`, `6 x 0,33 L`) into comparable unit prices (EUR/L, EUR/kg). |
| **Product search** | Fuzzy search with PostgreSQL `pg_trgm`. |

## Tech stack

- **Backend:** Java 21, Spring Boot 4, PostgreSQL, Flyway
- **Frontend:** React 19, TypeScript, Vite, Leaflet + OpenStreetMap
- **Infra:** Docker Compose, GitHub Actions

## Running locally

### Everything in Docker

```bash
docker compose up --build
```

- App: http://localhost:3000
- API: http://localhost:8080/api/system/info

### Development mode

```bash
# 1. Database
docker compose up db

# 2. Backend (Maven 3.9+, JDK 21)
cd backend && mvn spring-boot:run

# 3. Frontend (Node 22)
cd frontend && npm install && npm run dev
```

The Vite dev server (http://localhost:5173) proxies `/api` to the backend.

## Tests

```bash
cd backend && mvn verify   # needs PostgreSQL running (docker compose up db)
cd frontend && npm test
```

## Project structure

```
backend/    Spring Boot API (modular monolith)
frontend/   React SPA
docs/adr/   Architecture Decision Records
```

## Architecture decisions

See [`docs/adr`](docs/adr) for the reasoning behind the main design choices.
