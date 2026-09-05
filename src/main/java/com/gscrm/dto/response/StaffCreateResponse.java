package com.gscrm.dto.response;

import com.gscrm.model.Staff;

/**
 * Personel oluşturma yanıtı: kayıt ve — istendiyse — onunla birlikte açılan hesap.
 *
 * <p>{@code accountNote}, hesap açılamadığında nedenini taşır. Hesap açılamaması
 * personel kaydını geri almaz; kota dolduğu için personel eklenememesi, salonun
 * asıl işini engellerdi.
 */
public record StaffCreateResponse(
        Staff staff,
        StaffAccountResponse account,
        String accountNote
) {}
