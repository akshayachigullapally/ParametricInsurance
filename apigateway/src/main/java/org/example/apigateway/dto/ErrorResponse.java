package org.example.apigateway.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse{
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
}