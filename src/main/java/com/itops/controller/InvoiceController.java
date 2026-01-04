package com.itops.controller;

import com.itops.dto.*;
import com.itops.security.JwtUtil;
import com.itops.service.InvoiceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
@Slf4j
public class InvoiceController {
    
    private final InvoiceService invoiceService;
    private final JwtUtil jwtUtil;
    
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices(
        HttpServletRequest request,
        @RequestParam(required = false) UUID clientId,
        @RequestParam(required = false) UUID projectId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromIssueDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toIssueDate,
        @RequestParam(required = false) Boolean overdueOnly,
        @RequestParam(required = false) String sortBy
    ) {
        UUID companyId = getCompanyIdFromRequest(request);
        String role = getRoleFromRequest(request);
        
        // Block CLIENT role
        if ("CLIENT".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        // Block USER role (or make read-only later)
        if ("USER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<InvoiceResponse> invoices = invoiceService.getAllInvoices(
            companyId, clientId, projectId, status, fromIssueDate, toIssueDate, overdueOnly, sortBy
        );
        
        return ResponseEntity.ok(invoices);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(
        @PathVariable UUID id,
        HttpServletRequest request
    ) {
        UUID companyId = getCompanyIdFromRequest(request);
        String role = getRoleFromRequest(request);
        
        if ("CLIENT".equals(role) || "USER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        InvoiceResponse invoice = invoiceService.getInvoice(id, companyId);
        return ResponseEntity.ok(invoice);
    }
    
    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(
        @Valid @RequestBody CreateInvoiceRequest request,
        HttpServletRequest httpRequest
    ) {
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        UUID userId = getUserIdFromRequest(httpRequest);
        String role = getRoleFromRequest(httpRequest);
        
        // Only admins can create invoices
        if (!isAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        InvoiceResponse invoice = invoiceService.createInvoice(request, companyId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<InvoiceResponse> updateInvoice(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateInvoiceRequest request,
        HttpServletRequest httpRequest
    ) {
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        String role = getRoleFromRequest(httpRequest);
        
        if (!isAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        InvoiceResponse invoice = invoiceService.updateInvoice(id, request, companyId);
        return ResponseEntity.ok(invoice);
    }
    
    @PatchMapping("/{id}/status")
    public ResponseEntity<InvoiceResponse> updateInvoiceStatus(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateInvoiceStatusRequest request,
        HttpServletRequest httpRequest
    ) {
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        UUID actorId = getUserIdFromRequest(httpRequest);
        String role = getRoleFromRequest(httpRequest);
        
        if (!isAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        InvoiceResponse invoice = invoiceService.updateInvoiceStatus(id, request.getStatus(), companyId, actorId);
        return ResponseEntity.ok(invoice);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvoice(
        @PathVariable UUID id,
        HttpServletRequest httpRequest
    ) {
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        String role = getRoleFromRequest(httpRequest);
        
        if (!isAdmin(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        invoiceService.deleteInvoice(id, companyId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> generateInvoicePDF(
        @PathVariable UUID id,
        HttpServletRequest httpRequest
    ) {
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        String role = getRoleFromRequest(httpRequest);
        
        if ("CLIENT".equals(role) || "USER".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        byte[] pdfBytes = invoiceService.generateInvoicePDF(id, companyId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "invoice-" + id + ".pdf");
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(pdfBytes);
    }
    
    // Helper methods
    private UUID getCompanyIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getCompanyIdFromToken(token);
    }
    
    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
    
    private String getRoleFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getRoleFromToken(token);
    }
    
    private boolean isAdmin(String role) {
        return "TOP_USER".equals(role) || "SUPER_USER".equals(role) || "ADMIN".equals(role);
    }
}

