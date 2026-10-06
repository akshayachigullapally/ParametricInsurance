package org.example.notificationservice.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchNotificationRequest {
    @NotEmpty
    @Valid
    @Size(max=500,message="A batch can contain at most 500 notifications")
    private List<InternalNotificationRequest> notifications;
}