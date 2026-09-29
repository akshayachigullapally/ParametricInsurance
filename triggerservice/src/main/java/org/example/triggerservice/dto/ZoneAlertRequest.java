package org.example.triggerservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneAlertRequest{
    /** true raises the weather alert (new sales stop), false clears it. */
    @NotNull(message="active is required")
    private Boolean active;

    @Size(max=500,message="Alert message must be at most 500 characters")
    private String message;
}
