package com.gscrm.dto.response;

import com.gscrm.model.Staff;
import com.gscrm.model.enums.ServiceCategory;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

/**
 * Online randevu sayfasının gördüğü uzman bilgisi.
 *
 * <p>Bu uç kimlik doğrulaması istemez. {@code Staff} entity'si doğrudan
 * döndürüldüğünde çalışanların cep telefonu ve e-posta adresi de herkese açık
 * hale geliyordu; randevu ekranının ihtiyacı yalnızca ad, renk ve uzmanlık
 * alanları. Yeni bir alan eklenirken buraya da eklenmesi bilinçli bir karar
 * olmalı.
 */
@Data
@Builder
public class PublicStaffResponse {

    private Long id;
    private String name;
    private String colorHex;
    private Set<ServiceCategory> specializations;

    public static PublicStaffResponse from(Staff staff) {
        return PublicStaffResponse.builder()
                .id(staff.getId())
                .name(staff.getName())
                .colorHex(staff.getColorHex())
                .specializations(staff.getSpecializations())
                .build();
    }
}
