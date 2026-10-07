package org.example.productservice.handler;

import jakarta.validation.Validation;
import org.example.productservice.dto.AutoRenewRequest;
import org.example.productservice.dto.PolicyResponse;
import org.example.productservice.exception.ValidationException;
import org.example.productservice.service.PolicyCertificateService;
import org.example.productservice.service.PolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class PolicyLifecycleHandlerTest{

    @Mock
    private PolicyService policyService;
    @Mock
    private PolicyCertificateService certificateService;
    @Mock
    private ServerRequest request;

    private PolicyLifecycleHandler handler;

    @BeforeEach
    void setUp(){
        handler=new PolicyLifecycleHandler(policyService,certificateService,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void customer(long id){
        when(request.principal()).thenReturn((Mono)Mono.just(
                new UsernamePasswordAuthenticationToken(id,null,Collections.emptyList())));
    }

    @Test
    void renewAndAutoRenewActOnTheCustomersPolicy(){
        customer(3L);
        when(request.pathVariable("policyId")).thenReturn("9");
        when(policyService.renewPolicy(3L,9L)).thenReturn(Mono.just(PolicyResponse.builder().id(10L).build()));
        when(request.bodyToMono(AutoRenewRequest.class)).thenReturn(Mono.just(AutoRenewRequest.builder().enabled(true).build()));
        when(policyService.setAutoRenew(3L,9L,true)).thenReturn(Mono.just(PolicyResponse.builder().id(9L).build()));

        StepVerifier.create(handler.renew(request))
                .assertNext(r->assertThat(r.statusCode()).isEqualTo(HttpStatus.CREATED)).verifyComplete();
        StepVerifier.create(handler.autoRenew(request))
                .assertNext(r->assertThat(r.statusCode()).isEqualTo(HttpStatus.OK)).verifyComplete();
    }

    @Test
    void autoRenewNeedsTheEnabledFlag(){
        customer(3L);
        when(request.pathVariable("policyId")).thenReturn("9");
        when(request.bodyToMono(AutoRenewRequest.class)).thenReturn(Mono.just(new AutoRenewRequest()),Mono.empty());

        StepVerifier.create(handler.autoRenew(request)).expectError(ValidationException.class).verify();
        StepVerifier.create(handler.autoRenew(request)).expectErrorMessage("enabled is required").verify();
        verify(policyService,never()).setAutoRenew(anyLong(),anyLong(),anyBoolean());
    }

    @Test
    void certificatesAreReturnedAsPdfDownloads(){
        customer(3L);
        when(request.pathVariable("policyId")).thenReturn("9");
        PolicyCertificateService.Certificate pdf=new PolicyCertificateService.Certificate("POL-9.pdf",new byte[]{37,80});
        when(certificateService.forCustomer(3L,9L)).thenReturn(Mono.just(pdf));
        when(certificateService.forStaff(9L)).thenReturn(Mono.just(pdf));

        StepVerifier.create(handler.customerCertificate(request))
                .assertNext(r->{
                    assertThat(r.headers().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
                    assertThat(r.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("POL-9.pdf");
                }).verifyComplete();
        StepVerifier.create(handler.staffCertificate(request))
                .assertNext(r->assertThat(r.statusCode()).isEqualTo(HttpStatus.OK)).verifyComplete();
    }

    @Test
    void invalidPolicyIdIsRejected(){
        when(request.pathVariable("policyId")).thenReturn("-1");

        assertThatThrownBy(()->handler.staffCertificate(request)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(certificateService);
    }
}
