package com.erp.globalsearch.web;

import com.erp.globalsearch.security.TenantPrincipal;
import com.erp.globalsearch.service.OmniboxService;
import com.erp.globalsearch.web.dto.OmniboxDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/search")
public class OmniboxController {

    private final OmniboxService omniboxService;

    public OmniboxController(OmniboxService omniboxService) {
        this.omniboxService = omniboxService;
    }

    @GetMapping("/omnibox")
    public ResponseEntity<OmniboxDtos.OmniboxResponse> omnibox(
            @RequestParam("q") String q,
            @RequestParam(value = "limitPerCategory", required = false) Integer limitPerCategory,
            HttpServletRequest request
    ) {
        String trimmed = q != null ? q.trim() : "";
        if (trimmed.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q must be at least 2 characters");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TenantPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String bearer = request.getHeader("Authorization");
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }

        OmniboxDtos.OmniboxResponse body = omniboxService.search(trimmed, limitPerCategory, bearer, principal.getTenantId());
        return ResponseEntity.ok(body);
    }
}
