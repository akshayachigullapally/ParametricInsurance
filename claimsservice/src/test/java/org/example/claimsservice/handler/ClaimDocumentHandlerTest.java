package org.example.claimsservice.handler;

import org.example.claimsservice.dto.ClaimDocumentResponse;
import org.example.claimsservice.exception.ValidationException;
import org.example.claimsservice.model.ClaimDocument;
import org.example.claimsservice.service.ClaimDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimDocumentHandlerTest{

    @Mock
    private ClaimDocumentService documentService;

    @Mock
    private ServerRequest request;

    private ClaimDocumentHandler handler;

    @BeforeEach
    void setUp(){
        handler=new ClaimDocumentHandler(documentService);
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private void signedInAs(long userId){
        when(request.principal()).thenReturn((Mono)Mono.just(
                new UsernamePasswordAuthenticationToken(userId,null,Collections.emptyList())));
    }

    private static ClaimDocumentService.DocumentContent pdf(){
        ClaimDocument document=ClaimDocument.builder()
                .id(5L).claimId(9L).originalFileName("photo report.pdf").contentType("application/pdf").build();
        return new ClaimDocumentService.DocumentContent(document,new byte[]{1,2,3});
    }

    @Test
    void upload_passesOnlyFilePartsToTheServiceAndReturnsCreated(){
        signedInAs(3L);
        when(request.pathVariable("claimId")).thenReturn("9");
        FilePart file=mock(FilePart.class);
        MultiValueMap<String,Part> parts=new LinkedMultiValueMap<>();
        parts.add("files",file);
        parts.add("files",mock(Part.class));
        when(request.multipartData()).thenReturn(Mono.just(parts));
        when(documentService.upload(eq(3L),eq(9L),anyList()))
                .thenReturn(Mono.just(List.of(ClaimDocumentResponse.builder().id(5L).build())));

        StepVerifier.create(handler.upload(request))
                .assertNext(response->assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED))
                .verifyComplete();
        verify(documentService).upload(3L,9L,List.of(file));
    }

    @Test
    void customerCanListAndDownloadTheirDocuments(){
        signedInAs(3L);
        when(request.pathVariable("claimId")).thenReturn("9");
        when(request.pathVariable("documentId")).thenReturn("5");
        when(documentService.listForCustomer(3L,9L)).thenReturn(Flux.just(ClaimDocumentResponse.builder().id(5L).build()));
        when(documentService.downloadForCustomer(3L,9L,5L)).thenReturn(Mono.just(pdf()));

        StepVerifier.create(handler.listMine(request))
                .assertNext(response->assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
        StepVerifier.create(handler.downloadMine(request))
                .assertNext(response->{
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.headers().getContentType()).hasToString("application/pdf");
                    assertThat(response.headers().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline");
                    assertThat(response.headers().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
                })
                .verifyComplete();
    }

    @Test
    void staffCanListAndDownloadAnyClaimDocument(){
        when(request.pathVariable("claimId")).thenReturn("9");
        when(request.pathVariable("documentId")).thenReturn("5");
        when(documentService.listForStaff(9L)).thenReturn(Flux.empty());
        when(documentService.downloadForStaff(9L,5L)).thenReturn(Mono.just(pdf()));

        StepVerifier.create(handler.listForReview(request))
                .assertNext(response->assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
        StepVerifier.create(handler.downloadForReview(request))
                .assertNext(response->assertThat(response.statusCode()).isEqualTo(HttpStatus.OK))
                .verifyComplete();
    }

    @Test
    void invalidIdsAreRejected(){
        when(request.pathVariable("claimId")).thenReturn("0","abc");

        assertThatThrownBy(()->handler.listForReview(request))
                .isInstanceOf(ValidationException.class).hasMessage("Invalid claimId");
        assertThatThrownBy(()->handler.listForReview(request))
                .isInstanceOf(ValidationException.class).hasMessage("Invalid claimId");
    }
}
