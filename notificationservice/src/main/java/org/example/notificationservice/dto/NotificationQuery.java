package org.example.notificationservice.dto;

import lombok.Getter;
import org.example.notificationservice.model.NotificationType;

import java.time.LocalDateTime;

@Getter
public class NotificationQuery {
    private final boolean unreadOnly;
    private final NotificationType notificationType;
    private final LocalDateTime createdAfter;
    private final int page;
    private final int size;
    private final boolean sortAsc;
    private final boolean paginated;

    private NotificationQuery(boolean unreadOnly, NotificationType notificationType,
                              LocalDateTime createdAfter, int page, int size,
                              boolean sortAsc, boolean paginated) {
        this.unreadOnly = unreadOnly;
        this.notificationType = notificationType;
        this.createdAfter = createdAfter;
        this.page = page;
        this.size = size;
        this.sortAsc = sortAsc;
        this.paginated = paginated;
    }

    public static NotificationQuery defaults() {
        return new NotificationQuery(false, null, null, 0, 20, false, false);
    }

    public static NotificationQuery unreadOnly() {
        return new NotificationQuery(true, null, null, 0, 20, false, false);
    }

    public static NotificationQuery of(boolean unreadOnly, NotificationType notificationType,
                                       LocalDateTime createdAfter, int page, int size,
                                       boolean sortAsc, boolean paginated) {
        return new NotificationQuery(unreadOnly, notificationType, createdAfter, page,
                Math.max(1, size), sortAsc, paginated);
    }
}