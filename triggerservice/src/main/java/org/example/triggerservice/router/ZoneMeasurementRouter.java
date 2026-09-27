package org.example.triggerservice.router;

import org.example.triggerservice.dto.ZoneMeasurementResponse;
import org.example.triggerservice.service.ZoneMeasurementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class ZoneMeasurementRouter{
    /** Read-only weather readings; any signed-in user (the zone map is shown to customers too). */
    @Bean
    public RouterFunction<ServerResponse> zoneMeasurementRoutes(ZoneMeasurementService service){
        return RouterFunctions.route()
                .GET("/api/triggers/measurements/zones",
                        request->ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(service.latestPerZone(),ZoneMeasurementResponse.class))
                .build();
    }
}
