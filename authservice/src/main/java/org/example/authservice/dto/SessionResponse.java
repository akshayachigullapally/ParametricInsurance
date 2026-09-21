package org.example.authservice.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse{
    private Long id;
    private String deviceName;
    private String ipAddress;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessedAt;
}