package com.gscrm.dto.request;

import com.gscrm.model.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateRequest {

    @NotBlank(message = "Kullanıcı adı zorunlu")
    @Size(max = 64, message = "Kullanıcı adı en fazla 64 karakter olabilir")
    private String username;

    /**
     * Boş bırakılırsa sunucu okunaklı bir geçici parola üretir ve yanıtta bir kez döner.
     * Ekranda parola yazdırmak zorunda kalmamak için varsayılan akış budur.
     */
    @Size(max = 72, message = "Parola en fazla 72 karakter olabilir")
    private String password;

    @NotNull(message = "Rol seçilmeli")
    private UserRole role;

    private Long staffId;
}
