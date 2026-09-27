package org.example.financialservice.handler;

import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.service.PayoutReceiptService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class PayoutReceiptHandler{
    private final PayoutReceiptService receipts;

    public PayoutReceiptHandler(PayoutReceiptService receipts){
        this.receipts=receipts;
    }

    public Mono<ServerResponse> customerReceipt(ServerRequest request){
        return receipts.receiptForCustomer(payoutId(request)).flatMap(this::pdf);
    }

    public Mono<ServerResponse> adminReceipt(ServerRequest request){
        return receipts.receiptForAdmin(payoutId(request)).flatMap(this::pdf);
    }

    private Mono<ServerResponse> pdf(PayoutReceiptService.Receipt receipt){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment()
                        .filename(receipt.fileName())
                        .build()
                        .toString())
                .bodyValue(receipt.bytes());
    }

    private Long payoutId(ServerRequest request){
        try{
            long id=Long.parseLong(request.pathVariable("payoutId"));
            if(id<=0){
                throw new NumberFormatException();
            }
            return id;
        }catch(NumberFormatException e){
            throw new ValidationException("Invalid payoutId");
        }
    }
}
