package com.securewipe.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.securewipe.backend.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private UserRole role;

    // Jackson strips "is" prefix from boolean getters → serialize explicitly as "isActive"
    @JsonProperty("isActive")
    private boolean isActive;

    private LocalDateTime createdAt;
    private long totalWipes;
}
