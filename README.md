# BasketWise

[![CI](https://github.com/afraalmaci/basketwise/actions/workflows/ci.yml/badge.svg)](https://github.com/afraalmaci/basketwise/actions/workflows/ci.yml)

BasketWise splits a grocery list across supermarkets and plans the route between them,
so you can see whether visiting a second store is actually worth the extra time.

Add products with how much you need. The **Compare** tab prices the whole list at every
chain, item by item, with the cheapest price highlighted. The **Plan** tab shows the best
plan for one, two or three stores and the route, with what to buy at each stop.

> **Demo data only.** All stores, products and prices in this project are synthetic.
> Store names are fictional and do not represent real supermarket chains.

## What makes it interesting

| Module | Problem it solves |
|---|---|
| **Route-aware optimizer** | Splits a shopping list across up to 3 stores and decides whether each extra store is worth the detour: score = basket price + travel time × the value of your time (Lowest price / Balanced / Least time). Exact search over chain subsets, checked against brute force on random inputs. |
| **Pack selection** | "2 L of milk" when stores sell 1 L and 1.5 L packs: dynamic programming finds the cheapest packs that cover the need, buying a little extra when that is cheaper (a 6 × 33 cl multipack can beat 1.5 L bottles). |
| **Routing** | Uses your browser location (or a draggable pin) and real bike or walking times. One travel-time matrix per optimization; the round trip through each store combination comes from an exact tour planner that also considers sibling branches. |
| **Unit normalization** | Parses messy quantity strings (`1L`, `1000 ml`, `6 x 0,33 L`) into comparable unit prices (EUR/L, EUR/kg). |
| **Product search** | Fuzzy search with PostgreSQL `pg_trgm`. |

## Tech stack

- **Backend:** Java 21, Spring Boot 4, PostgreSQL, Flyway
- **Frontend:** React 19, TypeScript, Vite, Leaflet, OpenStreetMap or CARTO basemaps
- **Infra:** Docker Compose, Caddy, GitHub Actions, Hetzner Cloud

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

### Real bike and walking routes (optional)

Without configuration, travel times are straight-line estimates (distance × 1.3 detour
factor at 15 km/h by bike, 5 km/h on foot), drawn as dashed lines. For real routes, get a
free [OpenRouteService](https://openrouteservice.org/dev/#/signup) API key:

```bash
ORS_API_KEY=your-key mvn spring-boot:run   # or put ORS_API_KEY in .env for docker compose
```

If the API fails or the quota runs out, the app falls back to estimates automatically.

### Map style (optional)

Without configuration the map uses the standard OpenStreetMap tiles. For the cleaner
CARTO Voyager style (and CARTO Dark Matter in dark mode), get a free non-commercial key at
[carto.com/basemaps/apikey](https://www.carto.com/basemaps/apikey/) and put it in
`frontend/.env.local`:

```bash
VITE_CARTO_API_KEY=your-key
```

Restart `npm run dev` after adding it. For `docker compose`, put it in the root `.env`.

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
| `GET /api/routing/config` | Demo service area, default location, travel modes, max stops |
| `GET /api/routing/nearest?lat=52.08&lon=4.32&mode=bike` | Nearest store of each chain by travel time |
| `POST /api/optimize` | Where to buy a shopping list: `{"origin": {"latitude": 52.08, "longitude": 4.32}, "mode": "bike", "valueOfTimeEurPerHour": 10, "items": [{"productId": 1, "quantity": 2000}]}`. Quantities are in ml, g or pieces. Returns the list priced at every chain (`comparison`) and up to three plans (the best, the cheapest and the fastest, each with its `roles`), with packs per store and the route. |
| `POST /api/routing/trip` | Round trips through 1 to 3 stores, fastest first plus up to two alternatives (other visiting orders or nearby branches of the same chains): `{"origin": {"latitude": 52.08, "longitude": 4.32}, "storeIds": [3, 12], "mode": "walk"}` |
| `GET /api/system/info` | Data mode and database status |

Your location is only used to answer routing and optimization requests: it is not stored or logged. Your shopping list stays in your browser (localStorage).

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

## Deployment

The live demo runs on a single small VM: `docker-compose.prod.yml` adds Caddy for automatic
HTTPS and keeps Postgres and the backend off the public internet. Step-by-step guide:
[`docs/deploy.md`](docs/deploy.md).

## Project structure

```
backend/    Spring Boot API (modular monolith)
frontend/   React SPA
docs/adr/   Architecture Decision Records
```

## Architecture decisions

See [`docs/adr`](docs/adr) for the reasoning behind the main design choices.
