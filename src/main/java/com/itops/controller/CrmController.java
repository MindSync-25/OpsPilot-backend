    // ...existing code...

package com.itops.controller;

import com.itops.domain.ClientCrm;
import com.itops.dto.ClientResponse;
import com.itops.dto.CreateClientRequest;
import com.itops.service.CrmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/crm")
@RequiredArgsConstructor
public class CrmController {
    private final CrmService crmService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER')")
    public ResponseEntity<List<com.itops.dto.ClientCrmResponse>> listCrmClients(Authentication authentication) {
        UUID companyId = extractCompanyId(authentication);
        List<com.itops.dto.ClientCrmResponse> clients = crmService.listAllCrmClients(companyId);
        return ResponseEntity.ok(clients);
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<ClientCrm> createCrmClient(Authentication authentication, @Valid @RequestBody CreateClientRequest request) {
        UUID companyId = extractCompanyId(authentication);
        UUID userId = extractUserId(authentication);
        ClientCrm crm = crmService.createCrmClient(companyId, userId, request);
        return ResponseEntity.ok(crm);
    }

    @PutMapping("/clients/{clientId}")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<com.itops.dto.ClientCrmResponse> updateCrmClient(
            @PathVariable UUID clientId,
            @Valid @RequestBody com.itops.dto.UpdateCrmClientRequest request) {
        com.itops.dto.ClientCrmResponse crm = crmService.updateCrmClient(clientId, request);
        return ResponseEntity.ok(crm);
    }

    @PutMapping("/{crmId}")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<com.itops.dto.ClientCrmResponse> updateCrmById(
            @PathVariable UUID crmId,
            @Valid @RequestBody com.itops.dto.UpdateCrmClientRequest request) {
        com.itops.dto.ClientCrmResponse crm = crmService.updateCrmById(crmId, request);
        return ResponseEntity.ok(crm);
    }

    // Helper to extract companyId from authentication principal
    private UUID extractCompanyId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof java.util.Map claims && claims.containsKey("companyId")) {
            return UUID.fromString(claims.get("companyId").toString());
        }
        throw new IllegalArgumentException("Invalid authentication principal");
    }
    
    // Helper to extract userId from authentication principal
    private UUID extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof java.util.Map claims && claims.containsKey("userId")) {
            return UUID.fromString(claims.get("userId").toString());
        }
        throw new IllegalArgumentException("Invalid authentication principal");
    }
}
