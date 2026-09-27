package org.example.claimsservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.example.claimsservice.exception.ConflictException;
import org.example.claimsservice.exception.ValidationException;
import org.example.claimsservice.model.Claim;
import org.example.claimsservice.model.ClaimDocument;
import org.example.claimsservice.model.ClaimStatus;
import org.example.claimsservice.repository.ClaimDocumentRepository;
import org.example.claimsservice.repository.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ClaimDocumentServiceTest{
    private static final byte[] PNG={(byte)0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A,1,2,3};
    private static final byte[] PDF="%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path uploadDir;

    private ClaimRepository claimRepository;
    private ClaimDocumentRepository documentRepository;
    private ClaimDocumentService service;

    @BeforeEach
    void setUp(){
        claimRepository=mock(ClaimRepository.class);
        documentRepository=mock(ClaimDocumentRepository.class);
        NotificationService notifications=mock(NotificationService.class);
        when(notifications.audit(anyString(),anyString(),anyLong(),anyLong(),anyString()))
                .thenReturn(Mono.empty());
        when(documentRepository.save(any(ClaimDocument.class)))
                .thenAnswer(invocation->{
                    ClaimDocument document=invocation.getArgument(0);
                    document.setId(1L);
                    return Mono.just(document);
                });
        service=new ClaimDocumentService(claimRepository,documentRepository,notifications,
                uploadDir.toString());
    }

    private void claim(String status,long existingDocuments){
        when(claimRepository.findByIdAndCustomerId(10L,3L))
                .thenReturn(Mono.just(Claim.builder().id(10L).customerId(3L).status(status).build()));
        when(documentRepository.countByClaimId(10L)).thenReturn(Mono.just(existingDocuments));
    }

    private FilePart file(String name,byte[] bytes){
        FilePart part=mock(FilePart.class);
        DataBuffer buffer=DefaultDataBufferFactory.sharedInstance.wrap(bytes);
        when(part.filename()).thenReturn(name);
        when(part.content()).thenReturn(Flux.just(buffer));
        return part;
    }

    @Test
    void storesImagesAndPdfsDetectedFromContent() throws Exception{
        claim(ClaimStatus.SUBMITTED.name(),0);

        StepVerifier.create(service.upload(3L,10L,List.of(
                        file("roof.png",PNG),
                        file("../../report.pdf",PDF))))
                .assertNext(saved->{
                    assertThat(saved).hasSize(2);
                    assertThat(saved.get(0).getContentType()).isEqualTo("image/png");
                    assertThat(saved.get(1).getContentType()).isEqualTo("application/pdf");
                    assertThat(saved.get(1).getFileName()).isEqualTo("report.pdf");
                })
                .verifyComplete();

        try(var stored=Files.list(uploadDir.resolve("claims").resolve("10"))){
            assertThat(stored.count()).isEqualTo(2);
        }
    }

    @Test
    void rejectsUnsupportedContentEvenWithImageExtension(){
        claim(ClaimStatus.SUBMITTED.name(),0);

        StepVerifier.create(service.upload(3L,10L,List.of(
                        file("photo.jpg","not really an image".getBytes(StandardCharsets.UTF_8)))))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void enforcesThreeFilesPerClaim(){
        claim(ClaimStatus.SUBMITTED.name(),2);

        StepVerifier.create(service.upload(3L,10L,List.of(
                        file("a.png",PNG),
                        file("b.png",PNG))))
                .expectErrorMatches(error->error instanceof ValidationException
                        &&error.getMessage().contains("1 more allowed"))
                .verify();
    }

    @Test
    void rejectsUploadsOnceClaimIsDecided(){
        claim(ClaimStatus.APPROVED.name(),0);

        StepVerifier.create(service.upload(3L,10L,List.of(file("a.png",PNG))))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void rejectsFilesOverFiveMegabytes(){
        claim(ClaimStatus.SUBMITTED.name(),0);
        byte[] large=new byte[ClaimDocumentService.MAX_FILE_BYTES+1];
        System.arraycopy(PNG,0,large,0,PNG.length);

        StepVerifier.create(service.upload(3L,10L,List.of(file("big.png",large))))
                .expectErrorMatches(error->error instanceof ValidationException
                        &&error.getMessage().contains("larger than 5 MB"))
                .verify();
    }
}
