package org.example.notificationservice.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.model.RelatedEntityType;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SendFromTemplateRequest {
    /** Template to render; when omitted the template is chosen by notificationType. */
    @NotBlank(message="Template code is required")
    @Size(max=50,message="Template code must not exceed 50 characters")
    private String templateCode;
    private NotificationType notificationType;
    @Positive(message="Recipient user id must be positive")
    private Long recipientUserId;
    @Positive(message="Recipient customer id must be positive")
    private Long recipientCustomerId;
    @Size(max=50,message="Recipient role must not exceed 50 characters")
    private String recipientRole;
    private DeliveryChannel deliveryChannel;
    private List<DeliveryChannel> deliveryChannels;
    @Min(value=0,message="Max retries cannot be negative")
    @Max(value=10,message="Max retries cannot exceed 10")
    private Integer maxRetries;
    private RelatedEntityType relatedEntityType;
    private Long relatedEntityId;
    private Map<String, String> variables;
}