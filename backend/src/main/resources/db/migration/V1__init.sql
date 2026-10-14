-- Baseline migration.
-- pg_trgm powers fuzzy product search ("halfvolle melk" vs "melk halfvol").
-- Domain tables (chains, stores, products, prices) arrive in the next milestone.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
