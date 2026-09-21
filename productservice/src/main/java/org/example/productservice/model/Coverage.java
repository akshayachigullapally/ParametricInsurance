package org.example.productservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("coverages")
public class Coverage{
    @Id
    private Long id;

    @Column("product_id")
    private Long productId;

    @Column("coverage_code")
    private String coverageCode;

    private String name;
    private String description;

    @Column("coverage_limit")
    private BigDecimal coverageLimit;

    private Boolean mandatory;
    private Boolean active;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}