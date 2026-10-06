package org.example.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.example.notificationservice.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

class NotificationStreamTest{

    private static NotificationResponse notification(long id){
        NotificationResponse response=new NotificationResponse();
        response.setId(id);
        return response;
    }

    @Test
    void keepsWorkingAfterEveryListenerDisconnects(){
        NotificationStream stream=new NotificationStream();

        // First browser connects and leaves (page reload / closed tab).
        Disposable first=stream.asFlux().subscribe();
        first.dispose();

        // The next browser must still receive new notifications.
        List<Long> received=new CopyOnWriteArrayList<>();
        Disposable second=stream.asFlux().subscribe(n->received.add(n.getId()));
        stream.publish(notification(7L));
        second.dispose();

        assertThat(received).containsExactly(7L);
    }

    @Test
    void publishingWithNobodyConnectedIsHarmless(){
        NotificationStream stream=new NotificationStream();
        stream.publish(notification(1L));

        List<Long> received=new CopyOnWriteArrayList<>();
        Disposable listener=stream.asFlux().subscribe(n->received.add(n.getId()));
        stream.publish(notification(2L));
        listener.dispose();

        // Only what happens while connected; earlier events come from the inbox endpoint.
        assertThat(received).containsExactly(2L);
    }

    @Test
    void everyConnectedListenerGetsTheEvent(){
        NotificationStream stream=new NotificationStream();
        List<Long> a=new CopyOnWriteArrayList<>();
        List<Long> b=new CopyOnWriteArrayList<>();
        Disposable first=stream.asFlux().subscribe(n->a.add(n.getId()));
        Disposable second=stream.asFlux().subscribe(n->b.add(n.getId()));

        stream.publish(notification(3L));
        first.dispose();
        second.dispose();

        assertThat(a).containsExactly(3L);
        assertThat(b).containsExactly(3L);
    }
}
