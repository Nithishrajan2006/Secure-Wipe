package com.securewipe.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditIntegrityDto {
    private boolean valid;
    private int checkedCount;
    private Long firstBrokenRecordId;
    private String message;
}
