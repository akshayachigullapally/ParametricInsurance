package org.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data@Builder@NoArgsConstructor@AllArgsConstructor
public class UnreadCountResponse{
private long unreadCount;
private long count;

public UnreadCountResponse(long count){
this.unreadCount=count;
this.count=count;
}
}