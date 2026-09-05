package com.gscrm.dto.request;

import com.gscrm.model.enums.InviteKind;
import com.gscrm.model.enums.OrganizationType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Platform panelinden davet kodu oluşturma girdisi.
 *
 * <p>Uç daha önce ham {@code Map<String,String>} alıyor ve değerleri elle
 * {@code valueOf} / {@code parseInt} / {@code LocalDateTime.parse} ile çözüyordu:
 * hatalı bir tür ya da tarih, kullanıcıya doğrulama mesajı değil 500 döndürüyordu.
 */
@Data
public class InviteCreateRequest {

    private InviteKind kind = InviteKind.PILOT;

    @Size(max = 32)
    private String planCode = "SOLO";

    private OrganizationType organizationType = OrganizationType.STANDALONE;

    @Min(value = 1, message = "Kullanım hakkı en az 1 olmalı")
    @Max(value = 999, message = "Kullanım hakkı en fazla 999 olabilir")
    private Integer maxUses = 1;

    /** Davet sahibine verilen ücretsiz kullanım süresi; pilot müşteri için 90. */
    @Min(value = 1, message = "Ücretsiz süre en az 1 gün olmalı")
    @Max(value = 365, message = "Ücretsiz süre en fazla 365 gün olabilir")
    private Integer trialDays = 90;

    @Future(message = "Son kullanma tarihi gelecekte olmalı")
    private LocalDateTime expiresAt;

    @Size(max = 255)
    private String note;
}
