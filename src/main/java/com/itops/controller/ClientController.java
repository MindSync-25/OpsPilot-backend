package com.itops.controller;

import com.itops.dto.ClientResponse;
import com.itops.dto.CreateClientRequest;
import com.itops.dto.UpdateClientRequest;
import com.itops.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {
    
    private final ClientService clientService;
    
    /**
     * GET /api/v1/clients?status=&search=
     * List all clients with optional filters
     * Accessible by: TOP_USER, SUPER_USER, ADMIN, USER (read-only)
     * Blocked: CLIENT role
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER')")
    public ResponseEntity<List<ClientResponse>> listClients(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        
        UUID companyId = extractCompanyId(authentication);
        List<ClientResponse> clients = clientService.listClients(companyId, status, search);
        return ResponseEntity.ok(clients);
    }
    
    /**
     * GET /api/v1/clients/{id}
     * Get a single client by ID
     * Accessible by: TOP_USER, SUPER_USER, ADMIN, USER (read-only)
     * Blocked: CLIENT role
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER')")
    public ResponseEntity<ClientResponse> getClient(
            Authentication authentication,
            @PathVariable UUID id) {
        
        UUID companyId = extractCompanyId(authentication);
        ClientResponse client = clientService.getClient(companyId, id);
        return ResponseEntity.ok(client);
    }
    
    /**
     * POST /api/v1/clients
     * Create a new client
     * Accessible by: TOP_USER, SUPER_USER, ADMIN only
     * Blocked: USER, CLIENT roles
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<ClientResponse> createClient(
            Authentication authentication,
            @Valid @RequestBody CreateClientRequest request) {
        
        UUID companyId = extractCompanyId(authentication);
        ClientResponse client = clientService.createClient(companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(client);
    }
    
    /**
     * PUT /api/v1/clients/{id}
     * Update an existing client
     * Accessible by: TOP_USER, SUPER_USER, ADMIN only
     * Blocked: USER, CLIENT roles
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<ClientResponse> updateClient(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {
        
        UUID companyId = extractCompanyId(authentication);
        ClientResponse client = clientService.updateClient(companyId, id, request);
        return ResponseEntity.ok(client);
    }
    
    /**
     * DELETE /api/v1/clients/{id}
     * Soft delete a client
     * Accessible by: TOP_USER, SUPER_USER, ADMIN only
     * Blocked: USER, CLIENT roles
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<Void> deleteClient(
            Authentication authentication,
            @PathVariable UUID id) {
        
        UUID companyId = extractCompanyId(authentication);
        clientService.deleteClient(companyId, id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Extract company ID from JWT authentication
     */
    @SuppressWarnings("unchecked")
    private UUID extractCompanyId(Authentication authentication) {
        Map<String, Object> claims = (Map<String, Object>) authentication.getPrincipal();
        String companyIdStr = (String) claims.get("companyId");
        return UUID.fromString(companyIdStr);
    }
}
