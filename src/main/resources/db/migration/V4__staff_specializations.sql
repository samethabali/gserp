-- V4: Personel uzmanlık alanları
CREATE TABLE staff_specialization (
    staff_id         BIGINT NOT NULL REFERENCES staff(id) ON DELETE CASCADE,
    service_category VARCHAR(32) NOT NULL,
    PRIMARY KEY (staff_id, service_category)
);
