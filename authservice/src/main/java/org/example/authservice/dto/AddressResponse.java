package org.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse{
    private Long id;
    private String addressLineOne;
    private String addressLineTwo;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private Boolean primaryAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}