-- Bildirim log (WhatsApp / kanal takibi)
CREATE TABLE notification_log (
    id              BIGSERIAL PRIMARY KEY,
    appointment_id  BIGINT,
    channel         VARCHAR(32)  NOT NULL,
    template_name   VARCHAR(64),
    recipient       VARCHAR(64),
    status          VARCHAR(32)  NOT NULL,
    error_message   TEXT,
    sent_at         TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notification_log_appt ON notification_log(appointment_id);
