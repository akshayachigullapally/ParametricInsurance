package org.example.claimsservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("claim_documents")
public class ClaimDocument{
    @Id
    private Long id;

    @Column("claim_id")
    private Long claimId;

    @Column("original_file_name")
    private String originalFileName;

    @Column("stored_file_name")
    private String storedFileName;

    @Column("content_type")
    private String contentType;

    @Column("size_bytes")
    private Long sizeBytes;

    @Column("uploaded_by")
    private Long uploadedBy;

    @Column("uploaded_at")
    private LocalDateTime uploadedAt;
}
