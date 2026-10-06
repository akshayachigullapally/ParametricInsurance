package org.example.notificationservice.dto;

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
    private String templateCode;
    private NotificationType notificationType;
    private Long recipientUserId;
    private Long recipientCustomerId;
    private String recipientRole;
    private DeliveryChannel deliveryChannel;
    private List<DeliveryChannel> deliveryChannels;
    private Integer maxRetries;
    private RelatedEntityType relatedEntityType;
    private Long relatedEntityId;
    private Map<String, String> variables;
}