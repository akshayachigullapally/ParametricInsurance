package org.example.notificationservice.service;

import org.example.notificationservice.model.Notification;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock
    private NotificationRepository repository;

    @InjectMocks
    private NotificationScheduler scheduler;

    private static Notification notification(boolean read, LocalDateTime createdAt) {
        return Notification.builder().id(1L).type(NotificationType.GENERAL)
                .read(read).createdAt(createdAt).build();
    }

    @Test
    void cleanup_deletesOnlyReadNotificationsOlderThan90Days() {
        Notification oldRead = notification(true, LocalDateTime.now().minusDays(100));
        Notification oldUnread = notification(false, LocalDateTime.now().minusDays(100));
        Notification recentRead = notification(true, LocalDateTime.now().minusDays(5));
        Notification noDate = notification(true, null);
        when(repository.findAll()).thenReturn(Flux.just(oldRead, oldUnread, recentRead, noDate));
        when(repository.delete(any(Notification.class))).thenReturn(Mono.empty());

        scheduler.cleanupOldNotifications();

        verify(repository).delete(oldRead);
        verify(repository, times(1)).delete(any(Notification.class));
    }

    @Test
    void cleanup_repositoryError_isSwallowed() {
        when(repository.findAll()).thenReturn(Flux.error(new IllegalStateException("db down")));

        scheduler.cleanupOldNotifications();

        verify(repository, never()).delete(any(Notification.class));
    }
}
