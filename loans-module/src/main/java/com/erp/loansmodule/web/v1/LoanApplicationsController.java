package com.erp.loansmodule.web.v1;

import com.erp.loansmodule.domain.LoanApplication;
import com.erp.loansmodule.service.LoanApplicationService;
import com.erp.loansmodule.web.v1.dto.LoanDtos;
import com.erp.loansmodule.web.v1.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/tenants/{tenantId}/loans")
public class LoanApplicationsController {

    private final LoanApplicationService loanService;

    public LoanApplicationsController(LoanApplicationService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    @PreAuthorize("(hasAuthority('lending:loans:write') and @loansModuleSecurity.isTenant(#tenantId)) or @loansModuleSecurity.hasElevatedAccess()")
    public LoanDtos.LoanDto create(
            @PathVariable UUID tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationIdHeader,
            @Valid @RequestBody LoanDtos.CreateLoanRequest req
    ) {
        return toDto(loanService.create(tenantId, LoansModuleSecurityUtil.principal().getUserId(), req,
                AuditHttp.parseCorrelationId(correlationIdHeader)));
    }

    @GetMapping
    @PreAuthorize("(hasAuthority('lending:loans:read') and @loansModuleSecurity.isTenant(#tenantId)) or @loansModuleSecurity.hasElevatedAccess()")
    public PageResponse<LoanDtos.LoanDto> list(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        Page<LoanApplication> p = loanService.list(tenantId, page, pageSize);
        return new PageResponse<>(
                p.getContent().stream().map(LoanApplicationsController::toDto).toList(),
                page,
                p.getSize(),
                p.getTotalElements()
        );
    }

    @GetMapping("/{loanId}")
    @PreAuthorize("(hasAuthority('lending:loans:read') and @loansModuleSecurity.isTenant(#tenantId)) or @loansModuleSecurity.hasElevatedAccess()")
    public LoanDtos.LoanDto get(@PathVariable UUID tenantId, @PathVariable UUID loanId) {
        return toDto(loanService.get(tenantId, loanId));
    }

    @PatchMapping("/{loanId}")
    @PreAuthorize("(hasAuthority('lending:loans:write') and @loansModuleSecurity.isTenant(#tenantId)) or @loansModuleSecurity.hasElevatedAccess()")
    public LoanDtos.LoanDto patch(
            @PathVariable UUID tenantId,
            @PathVariable UUID loanId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationIdHeader,
            @Valid @RequestBody LoanDtos.PatchLoanRequest req
    ) {
        return toDto(loanService.patch(tenantId, LoansModuleSecurityUtil.principal().getUserId(), loanId, req,
                AuditHttp.parseCorrelationId(correlationIdHeader)));
    }

    private static LoanDtos.LoanDto toDto(LoanApplication la) {
        return new LoanDtos.LoanDto(
                la.getId(),
                la.getTenantId(),
                la.getStatus(),
                la.getRequestedAmount(),
                la.getProductCode(),
                la.getCreatedAt(),
                la.getUpdatedAt()
        );
    }
}
