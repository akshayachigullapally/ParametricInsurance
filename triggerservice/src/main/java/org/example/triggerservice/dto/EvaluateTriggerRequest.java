package org.example.triggerservice.dto;

import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateTriggerRequest{
    @PastOrPresent(message="Evaluation end time cannot be in the future")
    private LocalDateTime evaluationEnd;
}