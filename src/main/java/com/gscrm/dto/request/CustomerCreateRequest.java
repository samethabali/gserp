package com.gscrm.dto.request;

import com.gscrm.validation.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerCreateRequest {

    @NotBlank(message = "Ad girilmelidir")
    @Size(max = 255)
    private String firstName;

    @Size(max = 255)
    private String lastName;

    @PhoneNumber
    private String phone;

    /** Aynı telefonla kayıtlı müşteri varken yine de eklemek/kaydetmek için. */
    private boolean allowDuplicate;

    @Size(max = 255)
    private String email;

    private String notes;
}
