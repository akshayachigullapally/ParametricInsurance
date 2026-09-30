package org.example.triggerservice.router;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.triggerservice.handler.LocationZoneHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.ServerResponse;

class LocationZoneRouterTest{
    private final LocationZoneHandler handler=mock(LocationZoneHandler.class);
    private WebTestClient client;

    @BeforeEach
    void setUp(){
        when(handler.getInternalActive(any())).thenReturn(ServerResponse.ok().build());
        when(handler.getInternalById(any())).thenReturn(ServerResponse.ok().build());
        client=WebTestClient.bindToRouterFunction(new LocationZoneRouter().locationZoneRoutes(handler)).build();
    }

    @Test
    void activeZonesAreNotMistakenForAZoneId(){
        client.get().uri("/internal/triggers/zones/active").exchange().expectStatus().isOk();

        verify(handler).getInternalActive(any());
        verify(handler,never()).getInternalById(any());
    }

    @Test
    void numericIdStillReachesTheZone(){
        client.get().uri("/internal/triggers/zones/5").exchange().expectStatus().isOk();

        verify(handler).getInternalById(any());
    }

}
