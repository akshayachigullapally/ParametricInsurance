package org.example.triggerservice.router;

import org.example.triggerservice.handler.LocationZoneHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class LocationZoneRouter{
    @Bean
    public RouterFunction<ServerResponse> locationZoneRoutes(
            LocationZoneHandler handler
    ){
        return route()
                .path("/api/triggers/zones",builder->builder
                        .GET(
                                "",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getAll
                        )
                        .GET(
                                "/active",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getActive
                        )
                        .GET(
                                "/archived",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getArchived
                        )
                        .GET(
                                "/{zoneId:[0-9]+}",
                                accept(MediaType.APPLICATION_JSON),
                                handler::getById
                        )
                        .POST(
                                "",
                                contentType(MediaType.APPLICATION_JSON),
                                handler::create
                        )
                        .PUT(
                                "/{zoneId:[0-9]+}",
                                contentType(MediaType.APPLICATION_JSON),
                                handler::update
                        )
                        .PUT(
                                "/{zoneId:[0-9]+}/status",
                                contentType(MediaType.APPLICATION_JSON),
                                handler::updateStatus
                        )
                        .PUT(
                                "/{zoneId:[0-9]+}/alert",
                                contentType(MediaType.APPLICATION_JSON),
                                handler::updateAlert
                        )
                        .PUT(
                                "/{zoneId:[0-9]+}/exposure-cap",
                                contentType(MediaType.APPLICATION_JSON),
                                handler::updateExposureCap
                        )
                        .PUT(
                                "/{zoneId:[0-9]+}/restore",
                                handler::restore
                        )
                        .DELETE(
                                "/{zoneId:[0-9]+}",
                                handler::archive
                        ))
                // "active" must be matched before the numeric id route.
                .GET(
                        "/internal/triggers/zones/active",
                        handler::getInternalActive
                )
                .GET(
                        "/internal/triggers/zones/{zoneId:[0-9]+}",
                        handler::getInternalById
                )
                .build();
    }
}