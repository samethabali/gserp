package com.gscrm.dto.response;

/**
 * Kullanıcı yönetimi ekranının gördüğü hesap özeti.
 *
 * <p>Uçlar daha önce {@code User} varlığını olduğu gibi döndürüyordu; parola
 * hash'i de dahil her alan istemciye gidiyordu. Panelin ihtiyacı olan alanlar
 * burada açıkça sayılır.
 *
 * <p>{@code temporaryPassword} yalnızca hesabın açıldığı ya da parolanın
 * sıfırlandığı yanıtta doludur — parola hash'lenerek saklandığından başka hiçbir
 * uçtan tekrar okunamaz.
 */
public record UserAccountResponse(
        Long id,
        String username,
        String role,
        Long staffId,
        String staffName,
        boolean enabled,
        boolean mustChangePassword,
        boolean self,
        String temporaryPassword
) {
    public UserAccountResponse withoutSecret() {
        return new UserAccountResponse(id, username, role, staffId, staffName,
                enabled, mustChangePassword, self, null);
    }
}
