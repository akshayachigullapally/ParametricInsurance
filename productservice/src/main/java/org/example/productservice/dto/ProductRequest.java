package org.example.productservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest{
    @NotBlank(message="Product code is required")
    @Pattern(regexp="^[A-Za-z0-9_-]+$",message="Product code can contain letters, numbers, hyphen and underscore")
    @Size(max=50,message="Product code must not exceed 50 characters")
    private String productCode;

    @NotBlank(message="Product name is required")
    @Size(min=3,max=150,message="Product name must be 3 to 150 characters")
    private String name;

    @Size(max=1000,message="Description must not exceed 1000 characters")
    private String description;

    @NotBlank(message="Product category is required")
    @Pattern(regexp="(?i)^\\s*(WEATHER|FLOOD|AGRICULTURE|BUSINESS_INTERRUPTION|TRAVEL|CYCLONE|OTHER)\\s*$",message="Category must be one of WEATHER, FLOOD, AGRICULTURE, BUSINESS_INTERRUPTION, TRAVEL, CYCLONE, OTHER")
    private String category;

    @NotNull(message="Minimum sum insured is required")
    @Positive(message="Minimum sum insured must be positive")
    private BigDecimal minimumSumInsured;

    @NotNull(message="Maximum sum insured is required")
    @Positive(message="Maximum sum insured must be positive")
    private BigDecimal maximumSumInsured;

    @NotNull(message="Minimum term is required")
    @Min(value=1,message="Minimum term must be at least 1 month")
    private Integer minimumTermMonths;

    @NotNull(message="Maximum term is required")
    @Min(value=1,message="Maximum term must be at least 1 month")
    @Max(value=120,message="Maximum term cannot exceed 120 months")
    private Integer maximumTermMonths;

    @Size(max=1000,message="Eligibility description must not exceed 1000 characters")
    private String eligibilityDescription;

    @Size(max=50,message="Peril code must not exceed 50 characters")
    private String perilCode;

    @Size(max=50,message="Measurement type must not exceed 50 characters")
    private String measurementType;

    @Size(max=40,message="Measurement unit must not exceed 40 characters")
    private String measurementUnit;

    @Positive(message="Threshold value must be positive")
    private BigDecimal thresholdValue;

    @Size(max=40,message="Comparison operator must not exceed 40 characters")
    private String comparisonOperator;

    @Size(max=40,message="Aggregation type must not exceed 40 characters")
    private String aggregationType;

    @Min(value=1,message="Observation window must be at least 1 hour")
    @Max(value=720,message="Observation window cannot exceed 720 hours")
    private Integer observationWindowHours;

    @Min(value=1,message="Minimum observation count must be at least 1")
    @Max(value=1000,message="Minimum observation count cannot exceed 1000")
    private Integer minimumObservationCount;

    @Size(max=1000,message="Trigger description must not exceed 1000 characters")
    private String triggerDescription;

    @Valid
    private List<CoverageRequest> coverages;

    @JsonIgnore
    @AssertTrue(message="Maximum sum insured must be greater than or equal to the minimum sum insured")
    public boolean isSumInsuredRangeValid(){
        return minimumSumInsured==null||maximumSumInsured==null
                ||maximumSumInsured.compareTo(minimumSumInsured)>=0;
    }

    @JsonIgnore
    @AssertTrue(message="Maximum term must be greater than or equal to the minimum term")
    public boolean isTermRangeValid(){
        return minimumTermMonths==null||maximumTermMonths==null
                ||maximumTermMonths>=minimumTermMonths;
    }
}
