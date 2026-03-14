package com.nagarro.eclaims.claims.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Inbound DTO for FNOL submission. Validated before reaching service layer. */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimRequest {

    @NotBlank(message = "Policy ID is required")
    @Size(max = 50)
    private String policyId;

    @NotBlank(message = "Vehicle registration is required")
    @Size(max = 20)
    private String vehicleReg;

    @Size(max = 50)
    private String vehicleMake;

    @Size(max = 50)
    private String vehicleModel;

    @DecimalMin("-90.0") @DecimalMax("90.0")
    private BigDecimal accidentLat;

    @DecimalMin("-180.0") @DecimalMax("180.0")
    private BigDecimal accidentLng;

    @Size(max = 500)
    private String accidentAddress;

    @NotNull(message = "Incident date is required")
    @PastOrPresent(message = "Incident date cannot be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate incidentDate;

    @NotBlank(message = "Incident description is required")
    @Size(min = 20, max = 2000)
    private String incidentDescription;
}
