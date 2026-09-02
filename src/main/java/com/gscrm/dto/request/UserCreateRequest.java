package com.gscrm.dto.request;

import com.gscrm.model.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String username;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotNull
    private UserRole role;

    private Long staffId;
}
