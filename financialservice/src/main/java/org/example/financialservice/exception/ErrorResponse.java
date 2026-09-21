package org.example.financialservice.exception;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse{
    private int status;
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;
}