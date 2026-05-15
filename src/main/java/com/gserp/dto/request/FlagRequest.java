package com.gserp.dto.request;

import com.gserp.model.enums.FlagType;
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
