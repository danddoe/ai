package com.erp.loansmodule.web.v1.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class LoanDtos {

    public record LoanDto(
            UUID id,
            UUID tenantId,
            String status,
            BigDecimal requestedAmount,
            String productCode,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public static class CreateLoanRequest {
        @NotBlank
        @Size(max = 50)
        private String status;

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        private BigDecimal requestedAmount;

        @Size(max = 100)
        private String productCode;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public BigDecimal getRequestedAmount() {
            return requestedAmount;
        }

        public void setRequestedAmount(BigDecimal requestedAmount) {
            this.requestedAmount = requestedAmount;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }
    }

    public static class PatchLoanRequest {
        @Size(max = 50)
        private String status;

        @DecimalMin(value = "0.0", inclusive = false)
        private BigDecimal requestedAmount;

        @Size(max = 100)
        private String productCode;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public BigDecimal getRequestedAmount() {
            return requestedAmount;
        }

        public void setRequestedAmount(BigDecimal requestedAmount) {
            this.requestedAmount = requestedAmount;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }
    }
}
