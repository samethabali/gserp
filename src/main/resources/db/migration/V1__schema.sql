-- GSERP — initial schema (PostgreSQL)
-- Aşama 1.5 — Flyway baseline. Tablo isimleri JPA @Table'larıyla birebir eşleşmeli.

CREATE TABLE staff (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(64),
    email       VARCHAR(255),
    role        VARCHAR(32)  NOT NULL,
    color_hex   VARCHAR(16),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE TABLE resource (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    resource_type VARCHAR(32)  NOT NULL,
    capacity      INTEGER      NOT NULL DEFAULT 1,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP
);

CREATE TABLE service_definition (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    duration_minutes  INTEGER      NOT NULL,
    base_price        NUMERIC(12,2) NOT NULL DEFAULT 0,
    category          VARCHAR(32)  NOT NULL,
    requires_resource BOOLEAN      NOT NULL DEFAULT FALSE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP
);

CREATE TABLE service_required_resources (
    service_id  BIGINT NOT NULL REFERENCES service_definition(id) ON DELETE CASCADE,
    resource_id BIGINT NOT NULL
);
CREATE INDEX idx_srr_service ON service_required_resources(service_id);

CREATE TABLE customer (
    id          BIGSERIAL PRIMARY KEY,
    first_name  VARCHAR(255) NOT NULL,
    last_name   VARCHAR(255),
    phone       VARCHAR(64),
    email       VARCHAR(255),
    notes       TEXT,
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP
);

CREATE TABLE working_hours (
    id           BIGSERIAL PRIMARY KEY,
    staff_id     BIGINT      NOT NULL,
    day_of_week  VARCHAR(16) NOT NULL,
    start_time   TIME,
    end_time     TIME,
    day_off      BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_working_hours_staff ON working_hours(staff_id);

CREATE TABLE waitlist_entry (
    id                  BIGSERIAL PRIMARY KEY,
    customer_name       VARCHAR(255) NOT NULL,
    customer_phone      VARCHAR(64),
    service_id          BIGINT,
    preferred_staff_id  BIGINT,
    preferred_date      DATE,
    preferred_time      TIME,
    notes               TEXT,
    fulfilled           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP
);

CREATE TABLE appointment (
    id                  BIGSERIAL PRIMARY KEY,
    customer_name       VARCHAR(255),
    customer_phone      VARCHAR(64),
    staff_id            BIGINT       NOT NULL,
    service_id          BIGINT       NOT NULL,
    start_time          TIMESTAMP    NOT NULL,
    end_time            TIMESTAMP    NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    base_price          NUMERIC(12,2),
    adjustment          NUMERIC(12,2),
    adjustment_note     VARCHAR(255),
    final_price         NUMERIC(12,2),
    internal_note       TEXT,
    cancellation_reason VARCHAR(255),
    session_group_id    VARCHAR(64),
    session_number      INTEGER,
    total_sessions      INTEGER,
    version             INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP
);
CREATE INDEX idx_appointment_staff_start ON appointment(staff_id, start_time);
CREATE INDEX idx_appointment_start ON appointment(start_time);

CREATE TABLE appointment_resources (
    appointment_id BIGINT NOT NULL REFERENCES appointment(id) ON DELETE CASCADE,
    resource_id    BIGINT NOT NULL
);
CREATE INDEX idx_appt_resources_resource ON appointment_resources(resource_id);
CREATE INDEX idx_appt_resources_appt ON appointment_resources(appointment_id);

CREATE TABLE appointment_flag (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT       NOT NULL REFERENCES appointment(id) ON DELETE CASCADE,
    flag_type      VARCHAR(32)  NOT NULL,
    flag_value     VARCHAR(255),
    icon           VARCHAR(32)
);
CREATE INDEX idx_appointment_flag_appt ON appointment_flag(appointment_id);

CREATE TABLE audit_log_entry (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    entity_type VARCHAR(64),
    entity_id   BIGINT,
    action      VARCHAR(32) NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    created_at  TIMESTAMP
);
CREATE INDEX idx_audit_created_at ON audit_log_entry(created_at);
