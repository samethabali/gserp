package com.gscrm.dto.request;

import com.gscrm.model.enums.FlagType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlagRequest {
    private FlagType flagType;
    private String flagValue;
    private String icon;
}
