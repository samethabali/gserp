-- Token iptali için sürüm sayacı.
--
-- Erişim token'ları durumsuzdur; parola değiştiğinde veya kullanıcı devre dışı
-- bırakıldığında halihazırda dağıtılmış token'lar kendiliğinden geçersizleşmez.
-- Bu sayaç token'a claim olarak yazılır ve doğrulamada karşılaştırılır: sayaç
-- artınca o kullanıcının tüm eski token'ları tek hamlede geçersiz olur.
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;
