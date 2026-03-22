package com.erp.loansmodule.service;

import com.erp.audit.AuditActions;
import com.erp.audit.AuditEvent;
import com.erp.audit.AuditLogWriter;
import com.erp.audit.AuditOperations;
import com.erp.audit.AuditResourceTypes;
import com.erp.loansmodule.domain.LoanApplication;
import com.erp.loansmodule.repository.LoanApplicationRepository;
import com.erp.loansmodule.web.ApiException;
import com.erp.loansmodule.web.v1.dto.LoanDtos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class LoanApplicationService {

    private static final String AUDIT_SOURCE_SERVICE = "loans-module";

    private final LoanApplicationRepository repository;
    private final AuditLogWriter auditLogWriter;

    public LoanApplicationService(
            LoanApplicationRepository repository,
            @Autowired(required = false) AuditLogWriter auditLogWriter
    ) {
        this.repository = repository;
        this.auditLogWriter = auditLogWriter;
    }

    @Transactional
    public LoanApplication create(UUID tenantId, UUID userId, LoanDtos.CreateLoanRequest req, UUID correlationId) {
        LoanApplication la = new LoanApplication();
        la.setTenantId(tenantId);
        la.setStatus(req.getStatus().trim());
        la.setRequestedAmount(req.getRequestedAmount());
        if (req.getProductCode() != null && !req.getProductCode().isBlank()) {
            la.setProductCode(req.getProductCode().trim());
        }
        LoanApplication saved = repository.save(la);
        if (auditLogWriter != null) {
            List<Map<String, Object>> changes = new ArrayList<>();
            changes.add(loanFieldChange("status", null, saved.getStatus(), "string"));
            changes.add(loanFieldChange("requestedAmount", null, saved.getRequestedAmount(), "number"));
            if (saved.getProductCode() != null) {
                changes.add(loanFieldChange("productCode", null, saved.getProductCode(), "string"));
            }
            auditLogWriter.append(AuditEvent.builder()
                    .tenantId(tenantId)
                    .actorId(userId)
                    .sourceService(AUDIT_SOURCE_SERVICE)
                    .operation(AuditOperations.CREATE)
                    .action(AuditActions.LOAN_APPLICATION_CREATE)
                    .resourceType(AuditResourceTypes.LOAN_APPLICATION)
                    .resourceId(saved.getId())
                    .correlationId(correlationId)
                    .putPayload("changes", changes)
                    .build());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public LoanApplication get(UUID tenantId, UUID loanId) {
        return repository.findByIdAndTenantId(loanId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Loan not found"));
    }

    @Transactional
    public LoanApplication patch(UUID tenantId, UUID userId, UUID loanId, LoanDtos.PatchLoanRequest req, UUID correlationId) {
        LoanApplication la = get(tenantId, loanId);
        String prevStatus = la.getStatus();
        BigDecimal prevAmount = la.getRequestedAmount();
        String prevProduct = la.getProductCode();

        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            la.setStatus(req.getStatus().trim());
        }
        if (req.getRequestedAmount() != null) {
            la.setRequestedAmount(req.getRequestedAmount());
        }
        if (req.getProductCode() != null) {
            la.setProductCode(req.getProductCode().isBlank() ? null : req.getProductCode().trim());
        }
        LoanApplication saved = repository.save(la);

        if (auditLogWriter != null) {
            List<Map<String, Object>> changes = new ArrayList<>();
            if (req.getStatus() != null && !req.getStatus().isBlank() && !Objects.equals(prevStatus, saved.getStatus())) {
                changes.add(loanFieldChange("status", prevStatus, saved.getStatus(), "string"));
            }
            if (req.getRequestedAmount() != null && prevAmount.compareTo(saved.getRequestedAmount()) != 0) {
                changes.add(loanFieldChange("requestedAmount", prevAmount, saved.getRequestedAmount(), "number"));
            }
            if (req.getProductCode() != null && !Objects.equals(prevProduct, saved.getProductCode())) {
                changes.add(loanFieldChange("productCode", prevProduct, saved.getProductCode(), "string"));
            }
            if (!changes.isEmpty()) {
                auditLogWriter.append(AuditEvent.builder()
                        .tenantId(tenantId)
                        .actorId(userId)
                        .sourceService(AUDIT_SOURCE_SERVICE)
                        .operation(AuditOperations.UPDATE)
                        .action(AuditActions.LOAN_APPLICATION_PATCH)
                        .resourceType(AuditResourceTypes.LOAN_APPLICATION)
                        .resourceId(saved.getId())
                        .correlationId(correlationId)
                        .putPayload("changes", changes)
                        .build());
            }
        }
        return saved;
    }

    private static Map<String, Object> loanFieldChange(String name, Object oldV, Object newV, String fieldType) {
        Map<String, Object> ch = new LinkedHashMap<>();
        ch.put("path", name);
        ch.put("old", oldV);
        ch.put("new", newV);
        ch.put("fieldType", fieldType);
        return ch;
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<LoanApplication> list(UUID tenantId, int page, int pageSize) {
        int p = Math.max(0, page - 1);
        int size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(p, size));
    }
}
