CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone         VARCHAR(20)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL CHECK (role IN ('SELLER', 'RESELLER', 'ADMIN')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE seller_profile (
    user_id         BIGINT PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    shop_name       VARCHAR(150) NOT NULL,
    market_location VARCHAR(150) NOT NULL,
    contact_phone   VARCHAR(20)  NOT NULL,
    whatsapp_number VARCHAR(20)
);

CREATE TABLE saree_type (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name   VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE fabric (
    id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name   VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE collection (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id     BIGINT       NOT NULL REFERENCES seller_profile (user_id) ON DELETE CASCADE,
    title         VARCHAR(200) NOT NULL,
    saree_type_id BIGINT       NOT NULL REFERENCES saree_type (id),
    fabric_id     BIGINT       NOT NULL REFERENCES fabric (id),
    price         INTEGER      NOT NULL CHECK (price > 0),
    description   TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE item (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    collection_id  BIGINT      NOT NULL REFERENCES collection (id) ON DELETE CASCADE,
    seller_id      BIGINT      NOT NULL REFERENCES seller_profile (user_id) ON DELETE CASCADE,
    colour         VARCHAR(50),
    price_override INTEGER CHECK (price_override > 0),
    status         VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'SOLD_OUT')),
    image_path     VARCHAR(300) NOT NULL,
    thumb_path     VARCHAR(300) NOT NULL,
    image_hash     BIGINT      NOT NULL,
    search_vector  TSVECTOR,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE seller_follow (
    reseller_id BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    seller_id   BIGINT      NOT NULL REFERENCES seller_profile (user_id) ON DELETE CASCADE,
    notify_mode VARCHAR(10) NOT NULL DEFAULT 'INSTANT' CHECK (notify_mode IN ('INSTANT', 'MUTED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (reseller_id, seller_id)
);

CREATE TABLE notification (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reseller_id   BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    seller_id     BIGINT      NOT NULL REFERENCES seller_profile (user_id) ON DELETE CASCADE,
    collection_id BIGINT      NOT NULL REFERENCES collection (id) ON DELETE CASCADE,
    type          VARCHAR(20) NOT NULL CHECK (type IN ('NEW_COLLECTION', 'ITEMS_ADDED', 'BACK_IN_STOCK')),
    item_count    INTEGER     NOT NULL CHECK (item_count > 0),
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_item_status ON item (status);
CREATE INDEX idx_item_seller ON item (seller_id);
CREATE INDEX idx_item_collection ON item (collection_id);
CREATE INDEX idx_item_search ON item USING GIN (search_vector);
CREATE INDEX idx_collection_type ON collection (saree_type_id);
CREATE INDEX idx_collection_fabric ON collection (fabric_id);
CREATE INDEX idx_collection_seller ON collection (seller_id);
CREATE INDEX idx_follow_seller ON seller_follow (seller_id);
CREATE INDEX idx_notification_bell ON notification (reseller_id, read_at, created_at);

-- Full-text over collection title + description + item colour. The vector lives on item
-- because a PostgreSQL index cannot span tables; triggers keep it in sync.
CREATE FUNCTION item_search_vector(p_collection_id BIGINT, p_colour TEXT) RETURNS TSVECTOR AS $$
    SELECT to_tsvector('simple', c.title || ' ' || coalesce(c.description, '') || ' ' || coalesce(p_colour, ''))
    FROM collection c
    WHERE c.id = p_collection_id
$$ LANGUAGE sql STABLE;

CREATE FUNCTION item_set_search_vector() RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := item_search_vector(NEW.collection_id, NEW.colour);
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_item_search
    BEFORE INSERT OR UPDATE OF colour, collection_id ON item
    FOR EACH ROW EXECUTE FUNCTION item_set_search_vector();

CREATE FUNCTION collection_refresh_item_search() RETURNS TRIGGER AS $$
BEGIN
    UPDATE item SET search_vector = item_search_vector(NEW.id, colour) WHERE collection_id = NEW.id;
    RETURN NULL;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_collection_search
    AFTER UPDATE OF title, description ON collection
    FOR EACH ROW EXECUTE FUNCTION collection_refresh_item_search();
