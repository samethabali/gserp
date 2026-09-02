package com.gscrm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerCreateRequest {

    @NotBlank(message = "Ad girilmelidir")
    @Size(max = 255)
    private String firstName;

    @Size(max = 255)
    private String lastName;

    @Pattern(regexp = "^$|^0?5\\d{9}$|^0?5\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{4}$",
            message = "Geçerli bir telefon numarası girin")
    private String phone;

    @Size(max = 255)
    private String email;

    private String notes;
}
