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

## API

| Endpoint | Description |
|---|---|
| `GET /api/chains` | Supermarket chains with color and store count |
| `GET /api/stores` | All stores with coordinates |
| `GET /api/products/search?q=melk` | Fuzzy product search; every hit includes each chain's offers with normalized unit prices |
| `GET /api/system/info` | Data mode and database status |

## Demo data

On first start the backend generates a deterministic dataset (fixed seed): 4 fictional
chains, 20 stores across Den Haag, ~165 products and ~750 offers. Each chain writes names
and pack sizes in its own style (`1,5 L`, `1500 ml`, `1,5l`, `1,5 liter`), and every label
is run through the quantity parser and checked against the generator's ground truth
before it is stored.

| Chain | Price level | Assortment | Stores |
|---|---|---|---|
| Budgetbaas | cheapest | ~70% | 3, on the outskirts |
| Hoekwinkel | most expensive | everything | 8, everywhere |
| Versplein | average, cheap produce | ~85% | 5 |
| Marktwijs | slightly below average, cheap drinks | ~90% | 4 |

## Project structure

```
backend/    Spring Boot API (modular monolith)
frontend/   React SPA
docs/adr/   Architecture Decision Records
```

## Architecture decisions

See [`docs/adr`](docs/adr) for the reasoning behind the main design choices.
