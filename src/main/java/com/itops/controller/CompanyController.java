package com.itops.controller;

import com.itops.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping("/{companyId}")
    public ResponseEntity<?> getCompany(
            @PathVariable UUID companyId,
            Authentication authentication
    ) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) authentication.getPrincipal();
            UUID userId = UUID.fromString((String) claims.get("userId"));
            
            return ResponseEntity.ok(companyService.getCompany(companyId, userId));
        } catch (IllegalArgumentException e) {
            log.error("Unauthorized company access attempt: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error fetching company: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch company: " + e.getMessage()));
        }
    }

    @PutMapping("/{companyId}")
    public ResponseEntity<?> updateCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody com.itops.dto.UpdateCompanyRequest request,
            Authentication authentication
    ) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) authentication.getPrincipal();
            UUID userId = UUID.fromString((String) claims.get("userId"));
            
            return ResponseEntity.ok(companyService.updateCompany(companyId, userId, request));
        } catch (IllegalArgumentException e) {
            log.error("Unauthorized company update attempt: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating company: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update company: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{companyId}")
    public ResponseEntity<?> deleteCompany(
            @PathVariable UUID companyId,
            Authentication authentication
    ) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) authentication.getPrincipal();
            UUID userId = UUID.fromString((String) claims.get("userId"));
            
            companyService.deleteCompany(companyId, userId);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Company and all related data deleted successfully",
                    "companyId", companyId
            ));
        } catch (IllegalArgumentException e) {
            log.error("Unauthorized company deletion attempt: {}", e.getMessage());
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error deleting company: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to delete company: " + e.getMessage()));
        }
    }
}
