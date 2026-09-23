package org.example.triggerservice.handler;

import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.PolicyTriggerEligibilityService;
import org.example.triggerservice.dto.PolicyTriggerEligibilityResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.util.Map;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PolicyTriggerEligibilityHandlerTest {

    @Mock
    private PolicyTriggerEligibilityService eligibilityService;

    @InjectMocks
    private PolicyTriggerEligibilityHandler eligibilityHandler;

    private ServerRequest createRequest(String method, String path, String policyId) {
        return MockServerRequest.builder()
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(URI.create(path))
                .pathVariables(Map.of("policyId", policyId))
                .build();
    }

    @Test
    void checkEligibility_shouldReturnResponseForValidPolicyId() {
        PolicyTriggerEligibilityResponse response = PolicyTriggerEligibilityResponse.builder()
                .policyId(1L)
                .eligible(true)
                .eligibilityReason("A verified trigger event applies")
                .build();

        when(eligibilityService.checkEligibility(1L)).thenReturn(Mono.just(response));

        ServerRequest request = createRequest("GET", "/api/triggers/policy-trigger-eligibility/1", "1");

        StepVerifier.create(eligibilityHandler.checkEligibility(request))
                .assertNext(resp -> assertThat(resp.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void checkEligibility_shouldReturnValidationForInvalidId() {
        ServerRequest request = createRequest("GET", "/api/triggers/policy-trigger-eligibility/abc", "abc");

        StepVerifier.create(eligibilityHandler.checkEligibility(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void checkEligibility_shouldReturnValidationForNegativeId() {
        ServerRequest request = createRequest("GET", "/api/triggers/policy-trigger-eligibility/-1", "-1");

        StepVerifier.create(eligibilityHandler.checkEligibility(request))
                .expectError(ValidationException.class)
                .verify();
    }
}
