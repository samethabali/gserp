package com.gscrm.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class BookingConsentRequest {

    private boolean privacy;
    private boolean marketing;
    private boolean reminder;
    private List<String> consentTypes;
}
