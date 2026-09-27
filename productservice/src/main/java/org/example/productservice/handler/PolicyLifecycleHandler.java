package org.example.productservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.productservice.dto.AutoRenewRequest;
import org.example.productservice.exception.ValidationException;
import org.example.productservice.service.PolicyCertificateService;
import org.example.productservice.service.PolicyService;
import org.example.productservice.util.SecurityUtil;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/** Renewal, auto-renew and certificate endpoints for policies. */
@Component
@RequiredArgsConstructor
public class PolicyLifecycleHandler{
    private final PolicyService policyService;
    private final PolicyCertificateService certificateService;

    public Mono<ServerResponse> renew(ServerRequest request){
        Long policyId=policyId(request);
        return SecurityUtil.getUserId(request)
                .flatMap(customerId->policyService.renewPolicy(customerId,policyId))
                .flatMap(renewal->ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(renewal));
    }

    public Mono<ServerResponse> autoRenew(ServerRequest request){
        Long policyId=policyId(request);
        return SecurityUtil.getUserId(request)
                .flatMap(customerId->request.bodyToMono(AutoRenewRequest.class)
                        .switchIfEmpty(Mono.error(new ValidationException("enabled is required")))
                        .flatMap(body->{
                            if(body.getEnabled()==null){
                                return Mono.error(new ValidationException("enabled is required"));
                            }
                            return policyService.setAutoRenew(customerId,policyId,body.getEnabled());
                        }))
                .flatMap(policy->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(policy));
    }

    public Mono<ServerResponse> customerCertificate(ServerRequest request){
        Long policyId=policyId(request);
        return SecurityUtil.getUserId(request)
                .flatMap(customerId->certificateService.forCustomer(customerId,policyId))
                .flatMap(this::pdf);
    }

    public Mono<ServerResponse> staffCertificate(ServerRequest request){
        return certificateService.forStaff(policyId(request)).flatMap(this::pdf);
    }

    private Mono<ServerResponse> pdf(PolicyCertificateService.Certificate certificate){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                        .filename(certificate.fileName())
                        .build()
                        .toString())
                .bodyValue(certificate.bytes());
    }

    private Long policyId(ServerRequest request){
        try{
            long id=Long.parseLong(request.pathVariable("policyId"));
            if(id<=0){
                throw new NumberFormatException();
            }
            return id;
        }catch(NumberFormatException e){
            throw new ValidationException("Invalid policyId");
        }
    }
}
