package org.example.authservice.service;

import org.example.authservice.util.GatewayHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceClientTest{
    private final AtomicReference<ClientRequest> captured=new AtomicReference<>();
    private NotificationServiceClient client;

    @BeforeEach
    void setUp(){
        WebClient.Builder builder=WebClient.builder().exchangeFunction(request->{
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.CREATED).build());
        });
        client=new NotificationServiceClient(builder,"internal-key");
    }

    @Test
    void audit_withExplicitActor_shouldPostToAuditEndpoint(){
        StepVerifier.create(client.audit("USER_LOGIN","USER",5L,5L,"ROLE_CUSTOMER","Signed in"))
                .verifyComplete();

        ClientRequest request=captured.get();
        assertThat(request.url().toString()).isEqualTo("http://NOTIFICATION-SERVICE/internal/audit-events");
        assertThat(request.headers().getFirst(GatewayHeaders.GATEWAY_KEY)).isEqualTo("internal-key");
        assertThat(bodyOf(request))
                .contains("\"eventType\":\"USER_LOGIN\"")
                .contains("\"actorUserId\":5")
                .contains("\"actorRole\":\"ROLE_CUSTOMER\"");
    }

    @Test
    void audit_withoutActor_shouldUseSignedInUser(){
        UsernamePasswordAuthenticationToken admin=new UsernamePasswordAuthenticationToken(
                "1",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        StepVerifier.create(client.audit("USER_STATUS_CHANGED","USER",9L,null,null,"Deactivated")
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(admin)))
                .verifyComplete();

        assertThat(bodyOf(captured.get()))
                .contains("\"actorUserId\":1")
                .contains("\"actorRole\":\"ROLE_ADMIN\"");
    }

    @Test
    void audit_withoutActorOrSignedInUser_shouldRecordSystem(){
        StepVerifier.create(client.audit("CLEANUP","USER",null,null,null,"Scheduled job"))
                .verifyComplete();

        assertThat(bodyOf(captured.get())).contains("\"actorRole\":\"SYSTEM\"");
    }

    @Test
    void audit_whenNotificationServiceFails_shouldNotPropagateError(){
        NotificationServiceClient failing=new NotificationServiceClient(
                WebClient.builder().exchangeFunction(request->Mono.error(new IllegalStateException("down"))),
                "internal-key");

        StepVerifier.create(failing.audit("USER_LOGIN","USER",5L,5L,"ROLE_CUSTOMER","Signed in"))
                .verifyComplete();
    }

    @Test
    void audit_whenNotificationServiceReturnsError_shouldNotPropagateError(){
        NotificationServiceClient rejecting=new NotificationServiceClient(
                WebClient.builder().exchangeFunction(request->
                        Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST).build())),
                "internal-key");

        StepVerifier.create(rejecting.audit("USER_LOGIN","USER",5L,5L,"ROLE_CUSTOMER","Signed in"))
                .verifyComplete();
    }

    private static String bodyOf(ClientRequest request){
        MockClientHttpRequest mock=new MockClientHttpRequest(request.method(),request.url());
        request.body().insert(mock,new BodyInserter.Context(){
            @Override
            public List<HttpMessageWriter<?>> messageWriters(){
                return ExchangeStrategies.withDefaults().messageWriters();
            }

            @Override
            public Optional<ServerHttpRequest> serverRequest(){
                return Optional.empty();
            }

            @Override
            public Map<String,Object> hints(){
                return Map.of();
            }
        }).block();
        return mock.getBodyAsString().block();
    }
}
