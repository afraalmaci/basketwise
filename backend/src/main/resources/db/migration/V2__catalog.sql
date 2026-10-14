-- Supermarket chains. Prices are set per chain (uniform across its stores).
CREATE TABLE chain (
    id     BIGSERIAL PRIMARY KEY,
    code   TEXT NOT NULL UNIQUE,
    name   TEXT NOT NULL,
    color  TEXT NOT NULL
);

-- Physical stores. Location drives the route-aware optimizer.
CREATE TABLE store (
    id         BIGSERIAL PRIMARY KEY,
    chain_id   BIGINT NOT NULL REFERENCES chain (id),
    name       TEXT NOT NULL,
    latitude   DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude  DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180)
);
CREATE INDEX store_chain_idx ON store (chain_id);

-- Canonical products: "Halfvolle melk", independent of any chain.
CREATE TABLE product (
    id        BIGSERIAL PRIMARY KEY,
    slug      TEXT NOT NULL UNIQUE,
    name      TEXT NOT NULL,
    category  TEXT NOT NULL,
    unit      TEXT NOT NULL CHECK (unit IN ('ml', 'g', 'pc'))
);
CREATE INDEX product_name_trgm_idx ON product USING gin (name gin_trgm_ops);

-- A product as sold by a chain, in one pack size.
-- display_name and quantity_text are kept exactly as the chain writes them;
-- quantity_amount is the normalized value produced by the quantity parser.
CREATE TABLE offer (
    id               BIGSERIAL PRIMARY KEY,
    chain_id         BIGINT NOT NULL REFERENCES chain (id),
    product_id       BIGINT NOT NULL REFERENCES product (id),
    display_name     TEXT NOT NULL,
    quantity_text    TEXT NOT NULL,
    quantity_amount  BIGINT NOT NULL CHECK (quantity_amount > 0),
    price_cents      INTEGER NOT NULL CHECK (price_cents > 0),
    UNIQUE (chain_id, product_id, quantity_amount)
);
CREATE INDEX offer_product_idx ON offer (product_id);
