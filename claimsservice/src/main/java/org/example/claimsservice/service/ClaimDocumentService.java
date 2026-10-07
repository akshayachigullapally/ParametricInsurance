package org.example.claimsservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.claimsservice.dto.ClaimDocumentResponse;
import org.example.claimsservice.exception.ConflictException;
import org.example.claimsservice.exception.NotFoundException;
import org.example.claimsservice.exception.ValidationException;
import org.example.claimsservice.model.Claim;
import org.example.claimsservice.model.ClaimDocument;
import org.example.claimsservice.model.ClaimStatus;
import org.example.claimsservice.repository.ClaimDocumentRepository;
import org.example.claimsservice.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Evidence files (damage photos or a supporting document) attached to a claim.
 * Files are stored on disk under app.uploads.dir; only metadata is in the DB.
 * The file type is detected from the file's content, not from what the
 * browser claims, so only real JPEG/PNG/WEBP images and PDFs are accepted.
 */
@Slf4j
@Service
public class ClaimDocumentService{
    public static final int MAX_DOCUMENTS_PER_CLAIM=3;
    public static final int MAX_FILE_BYTES=5*1024*1024;

    private static final Set<String> UPLOAD_STATUSES=Set.of(
            ClaimStatus.SUBMITTED.name(),
            ClaimStatus.UNDER_REVIEW.name()
    );

    private final ClaimRepository claimRepository;
    private final ClaimDocumentRepository documentRepository;
    private final NotificationService notificationService;
    private final Path uploadRoot;

    public ClaimDocumentService(
            ClaimRepository claimRepository,
            ClaimDocumentRepository documentRepository,
            NotificationService notificationService,
            @Value("${app.uploads.dir:${java.io.tmpdir}/cityshield-uploads}") String uploadDir){
        this.claimRepository=claimRepository;
        this.documentRepository=documentRepository;
        this.notificationService=notificationService;
        this.uploadRoot=Paths.get(uploadDir,"claims").toAbsolutePath().normalize();
    }

    public Mono<List<ClaimDocumentResponse>> upload(
            Long customerId,
            Long claimId,
            List<FilePart> files){
        log.info("Upload [claim document]: customerId={}, claimId={}",customerId,claimId);
        if(files==null||files.isEmpty()){
            return Mono.error(new ValidationException("Select at least one file to upload"));
        }

        return ownedClaim(customerId,claimId)
                .flatMap(claim->{
                    if(!UPLOAD_STATUSES.contains(claim.getStatus())){
                        return Mono.error(new ConflictException(
                                "Documents can only be added while the claim is submitted or under review"
                        ));
                    }
                    return documentRepository.countByClaimId(claimId);
                })
                .flatMap(existing->{
                    if(existing+files.size()>MAX_DOCUMENTS_PER_CLAIM){
                        long remaining=Math.max(0,MAX_DOCUMENTS_PER_CLAIM-existing);
                        return Mono.error(new ValidationException(
                                "A claim can have at most "+MAX_DOCUMENTS_PER_CLAIM+
                                        " files ("+remaining+" more allowed)"
                        ));
                    }
                    return Flux.fromIterable(files)
                            .concatMap(file->store(customerId,claimId,file))
                            .collectList();
                })
                .map(saved->saved.stream().map(this::toResponse).toList());
    }

    public Flux<ClaimDocumentResponse> listForCustomer(Long customerId,Long claimId){
        log.debug("List for customer [claim document]: customerId={}, claimId={}",customerId,claimId);
        return ownedClaim(customerId,claimId)
                .flatMapMany(claim->documentRepository.findAllByClaimIdOrderByUploadedAtAsc(claimId))
                .map(this::toResponse);
    }

    public Flux<ClaimDocumentResponse> listForStaff(Long claimId){
        log.debug("List for staff [claim document]: claimId={}",claimId);
        return existingClaim(claimId)
                .flatMapMany(claim->documentRepository.findAllByClaimIdOrderByUploadedAtAsc(claimId))
                .map(this::toResponse);
    }

    public Mono<DocumentContent> downloadForCustomer(Long customerId,Long claimId,Long documentId){
        log.debug("Download for customer [claim document]: customerId={}, claimId={}, documentId={}",customerId,claimId,documentId);
        return ownedClaim(customerId,claimId)
                .then(readDocument(claimId,documentId));
    }

    public Mono<DocumentContent> downloadForStaff(Long claimId,Long documentId){
        log.debug("Download for staff [claim document]: claimId={}, documentId={}",claimId,documentId);
        return existingClaim(claimId)
                .then(readDocument(claimId,documentId));
    }

    private Mono<ClaimDocument> store(Long customerId,Long claimId,FilePart file){
        return DataBufferUtils.join(file.content(),MAX_FILE_BYTES)
                .onErrorMap(DataBufferLimitException.class,e->new ValidationException(
                        file.filename()+" is larger than 5 MB"))
                .map(buffer->{
                    byte[] bytes=new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .switchIfEmpty(Mono.error(new ValidationException(file.filename()+" is empty")))
                .flatMap(bytes->{
                    DetectedType type=detectType(bytes);
                    if(type==null){
                        return Mono.error(new ValidationException(
                                file.filename()+" is not a supported file (JPG, PNG, WEBP or PDF only)"));
                    }
                    String storedName=UUID.randomUUID()+type.extension();
                    Path target=uploadRoot.resolve(String.valueOf(claimId)).resolve(storedName);

                    return Mono.fromCallable(()->{
                                Files.createDirectories(target.getParent());
                                Files.write(target,bytes);
                                return target;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .then(documentRepository.save(ClaimDocument.builder()
                                    .claimId(claimId)
                                    .originalFileName(safeName(file.filename()))
                                    .storedFileName(storedName)
                                    .contentType(type.contentType())
                                    .sizeBytes((long)bytes.length)
                                    .uploadedBy(customerId)
                                    .uploadedAt(LocalDateTime.now())
                                    .build()));
                });
    }

    private Mono<DocumentContent> readDocument(Long claimId,Long documentId){
        return documentRepository.findByIdAndClaimId(documentId,claimId)
                .switchIfEmpty(Mono.error(new NotFoundException("Document not found")))
                .flatMap(document->Mono.fromCallable(()->{
                            Path path=uploadRoot.resolve(String.valueOf(claimId))
                                    .resolve(document.getStoredFileName());
                            if(!Files.isRegularFile(path)){
                                throw new NotFoundException("Document file is missing");
                            }
                            try{
                                return new DocumentContent(document,Files.readAllBytes(path));
                            }catch(IOException e){
                                throw new UncheckedIOException(e);
                            }
                        })
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private Mono<Claim> ownedClaim(Long customerId,Long claimId){
        return claimRepository.findByIdAndCustomerId(claimId,customerId)
                .switchIfEmpty(Mono.error(new NotFoundException("Claim not found")));
    }

    private Mono<Claim> existingClaim(Long claimId){
        return claimRepository.findById(claimId)
                .switchIfEmpty(Mono.error(new NotFoundException("Claim not found")));
    }

    private ClaimDocumentResponse toResponse(ClaimDocument document){
        return ClaimDocumentResponse.builder()
                .id(document.getId())
                .claimId(document.getClaimId())
                .fileName(document.getOriginalFileName())
                .contentType(document.getContentType())
                .sizeBytes(document.getSizeBytes())
                .uploadedAt(document.getUploadedAt())
                .build();
    }

    private static String safeName(String name){
        String base=name==null?"file":Paths.get(name).getFileName().toString();
        base=base.replaceAll("[^A-Za-z0-9._ -]","_").trim();
        if(base.isEmpty()){
            base="file";
        }
        return base.length()>200?base.substring(base.length()-200):base;
    }

    static DetectedType detectType(byte[] b){
        if(b.length>=3&&(b[0]&0xFF)==0xFF&&(b[1]&0xFF)==0xD8&&(b[2]&0xFF)==0xFF){
            return new DetectedType("image/jpeg",".jpg");
        }
        if(b.length>=8&&(b[0]&0xFF)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'){
            return new DetectedType("image/png",".png");
        }
        if(b.length>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'
                &&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P'){
            return new DetectedType("image/webp",".webp");
        }
        if(b.length>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-'){
            return new DetectedType("application/pdf",".pdf");
        }
        return null;
    }

    record DetectedType(String contentType,String extension){}

    public record DocumentContent(ClaimDocument document,byte[] bytes){}
}
