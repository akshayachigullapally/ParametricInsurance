package org.example.claimsservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.claimsservice.dto.ClaimDocumentResponse;
import org.example.claimsservice.exception.ValidationException;
import org.example.claimsservice.service.ClaimDocumentService;
import org.example.claimsservice.util.SecurityUtil;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ClaimDocumentHandler{
    private final ClaimDocumentService documentService;

    public Mono<ServerResponse> upload(ServerRequest request){
        Long claimId=pathId(request,"claimId");

        return SecurityUtil.getUserId(request)
                .flatMap(customerId->request.multipartData()
                        .map(parts->{
                            List<Part> fileParts=parts.getOrDefault("files",List.of());
                            return fileParts.stream()
                                    .filter(FilePart.class::isInstance)
                                    .map(FilePart.class::cast)
                                    .toList();
                        })
                        .flatMap(files->documentService.upload(customerId,claimId,files)))
                .flatMap(saved->ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
    }

    public Mono<ServerResponse> listMine(ServerRequest request){
        Long claimId=pathId(request,"claimId");

        return SecurityUtil.getUserId(request)
                .flatMap(customerId->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(documentService.listForCustomer(customerId,claimId),
                                ClaimDocumentResponse.class));
    }

    public Mono<ServerResponse> downloadMine(ServerRequest request){
        Long claimId=pathId(request,"claimId");
        Long documentId=pathId(request,"documentId");

        return SecurityUtil.getUserId(request)
                .flatMap(customerId->documentService.downloadForCustomer(
                        customerId,claimId,documentId))
                .flatMap(this::fileResponse);
    }

    public Mono<ServerResponse> listForReview(ServerRequest request){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(documentService.listForStaff(pathId(request,"claimId")),
                        ClaimDocumentResponse.class);
    }

    public Mono<ServerResponse> downloadForReview(ServerRequest request){
        return documentService.downloadForStaff(
                        pathId(request,"claimId"),
                        pathId(request,"documentId"))
                .flatMap(this::fileResponse);
    }

    private Mono<ServerResponse> fileResponse(ClaimDocumentService.DocumentContent content){
        return ServerResponse.ok()
                .contentType(MediaType.parseMediaType(content.document().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline()
                        .filename(content.document().getOriginalFileName(),StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header("X-Content-Type-Options","nosniff")
                .bodyValue(content.bytes());
    }

    private Long pathId(ServerRequest request,String name){
        try{
            long value=Long.parseLong(request.pathVariable(name));
            if(value<=0){
                throw new NumberFormatException();
            }
            return value;
        }catch(NumberFormatException e){
            throw new ValidationException("Invalid "+name);
        }
    }
}
