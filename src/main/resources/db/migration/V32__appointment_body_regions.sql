-- V32: Epilasyon randevularında seçilen vücut bölgeleri
--
-- Epilasyon randevusunda "hangi bölge" bilgisi bugüne kadar yalnızca iç nota
-- serbest metin olarak yazılabiliyordu; ne raporlanabiliyor ne de insan vücudu
-- şablonu üzerinde gösterilebiliyordu. Bölgeler artık ayrı satırlar hâlinde
-- tutulur: kod kümesi BodyRegion enum'u ile sınırlıdır.
--
-- Randevu silinince bölgeleri de gider (ON DELETE CASCADE) — bunlar randevunun
-- kendi verisidir, bağımsız bir kaydı yoktur.

CREATE TABLE appointment_body_region (
    appointment_id BIGINT      NOT NULL REFERENCES appointment(id) ON DELETE CASCADE,
    region         VARCHAR(32) NOT NULL,
    -- Aynı bölgenin iki kez yazılması anlamsız; birincil anahtar bunu engeller
    -- ve appointment_id ile başladığı için randevu bazlı okumaya da yeter.
    PRIMARY KEY (appointment_id, region)
);
