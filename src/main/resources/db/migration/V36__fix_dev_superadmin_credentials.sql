-- V35'in eklemek istediği admin/admin123 platform kullanıcısı, dev verisindeki
-- eski admin/admin kullanıcı adıyla çakışıyordu. Yalnızca bilinen demo
-- hesabını dönüştür; aynı adı taşıyan gerçek kurulum hesaplarına dokunma.
UPDATE users
SET password_hash = '$2a$10$7rRFactq27LIfLMx3U/37umBlabzh.OU4gF/t3nHzAMUIvmZsO3nq',
    role = 'PLATFORM_ADMIN',
    salon_id = NULL,
    organization_id = NULL,
    staff_id = NULL,
    customer_id = NULL,
    enabled = true,
    must_change_password = false,
    password_changed_at = current_timestamp,
    token_version = token_version + 1
WHERE username = 'admin'
  AND password_hash = '$2b$10$Wp.klXzKqqMEYZZyHREduen3D/sIwYS2UxyLt8McfBuc88jmLTKhG'
  AND role IN ('ADMIN', 'BRANCH_MANAGER');
