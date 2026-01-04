package com.itops.controller;

import com.itops.dto.InvoiceGenerateRequest;
import com.itops.dto.InvoiceGenerateResponse;
import com.itops.dto.InvoiceGenerationPreviewRequest;
import com.itops.dto.InvoiceGenerationPreviewResponse;
import com.itops.security.JwtUtil;
import com.itops.service.InvoiceGenerationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/invoices/generation")
@RequiredArgsConstructor
@Slf4j
public class InvoiceGenerationController {

    private final InvoiceGenerationService invoiceGenerationService;
    private final JwtUtil jwtUtil;

    /**
     * Preview invoice generation from time entries
     * Shows what the invoice would look like before generating
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<InvoiceGenerationPreviewResponse> previewInvoice(
            @Valid @RequestBody InvoiceGenerationPreviewRequest request,
            HttpServletRequest httpRequest) {
        
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        UUID userId = getUserIdFromRequest(httpRequest);
        
        log.info("Preview invoice generation request from user: {} for client: {}", 
                 userId, request.getClientId());
        
        InvoiceGenerationPreviewResponse response = invoiceGenerationService.previewFromTime(
            companyId,
            userId,
            request
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Generate DRAFT invoice from time entries
     * Creates actual invoice and links time entries
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<InvoiceGenerateResponse> generateInvoice(
            @Valid @RequestBody InvoiceGenerateRequest request,
            HttpServletRequest httpRequest) {
        
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        UUID userId = getUserIdFromRequest(httpRequest);
        
        log.info("Generate invoice request from user: {} for client: {}", 
                 userId, request.getClientId());
        
        InvoiceGenerateResponse response = invoiceGenerationService.generateDraftInvoiceFromTime(
            companyId,
            userId,
            request
        );
        
        log.info("Invoice {} generated successfully with {} billed entries", 
                 response.getInvoiceNumber(), response.getBilledEntriesCount());
        
        return ResponseEntity.ok(response);
    }

    private UUID getCompanyIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getCompanyIdFromToken(token);
        }
        throw new RuntimeException("Company ID not found in token");
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        throw new RuntimeException("User ID not found in token");
    }
}
