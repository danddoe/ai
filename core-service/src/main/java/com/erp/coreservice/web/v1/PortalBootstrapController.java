package com.erp.coreservice.web.v1;

import com.erp.coreservice.portal.HttpHostExtractor;
import com.erp.coreservice.portal.PortalBootstrapContext;
import com.erp.coreservice.service.PortalHostBindingService;
import com.erp.coreservice.web.v1.dto.PortalDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/portal")
public class PortalBootstrapController {

    private final PortalHostBindingService portalHostBindingService;

    public PortalBootstrapController(PortalHostBindingService portalHostBindingService) {
        this.portalHostBindingService = portalHostBindingService;
    }

    @GetMapping("/bootstrap")
    public PortalDtos.PortalBootstrapResponse bootstrap(
            HttpServletRequest request,
            @RequestParam(required = false) String hostname
    ) {
        String host = HttpHostExtractor.effectiveHost(request, hostname);
        PortalBootstrapContext ctx = portalHostBindingService.resolveBootstrap(host);
        return new PortalDtos.PortalBootstrapResponse(
                ctx.tenantId(),
                ctx.companyId(),
                ctx.defaultBuId(),
                ctx.scope()
        );
    }
}
