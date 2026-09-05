package com.gscrm.dto.response;

/**
 * Bir personel kaydına bağlı giriş hesabının panelde gösterilen özeti.
 *
 * <p>{@code temporaryPassword} yalnızca hesabın açıldığı veya parolanın
 * sıfırlandığı yanıtta doludur: parola hash'lenerek saklandığı için başka hiçbir
 * uçtan tekrar okunamaz, salon sahibi onu o an personele iletmek zorundadır.
 */
public record StaffAccountResponse(
        Long staffId,
        Long userId,
        String username,
        String role,
        boolean enabled,
        boolean mustChangePassword,
        String temporaryPassword
) {
    public StaffAccountResponse withoutSecret() {
        return new StaffAccountResponse(staffId, userId, username, role, enabled, mustChangePassword, null);
    }
}
