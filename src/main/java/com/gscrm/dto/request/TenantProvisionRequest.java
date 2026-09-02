package com.gscrm.dto.request;

import com.gscrm.model.enums.OrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TenantProvisionRequest {

    @NotBlank
    private String organizationName;

    private OrganizationType organizationType = OrganizationType.STANDALONE;

    @NotBlank
    private String salonName;

    @NotBlank
    @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,62}", message = "Geçerli bir slug girin")
    private String salonSlug;

    @NotBlank
    @Size(min = 3, max = 64)
    private String adminUsername;

    @NotBlank
    @Size(min = 8, max = 128)
    private String adminPassword;

    private String contactEmail;

    private String planCode = "SOLO";
}
