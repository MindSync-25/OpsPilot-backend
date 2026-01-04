package com.itops.controller;

import com.itops.dto.AttachmentResponse;
import com.itops.dto.CreateAttachmentRequest;
import com.itops.security.JwtUtil;
import com.itops.service.AttachmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/attachments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AttachmentController {
    
    private final AttachmentService attachmentService;
    private final JwtUtil jwtUtil;
    
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestParam("entityType") String entityType,
            @RequestParam("entityId") UUID entityId,
            HttpServletRequest httpRequest) {
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        UUID userId = getUserIdFromRequest(httpRequest);
        AttachmentResponse response = attachmentService.uploadAttachment(file, entityType, entityId, companyId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping
    public ResponseEntity<List<AttachmentResponse>> getAttachments(
            @RequestParam String entityType,
            @RequestParam UUID entityId,
            HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        List<AttachmentResponse> attachments = attachmentService.getAttachmentsByEntity(entityType, entityId, companyId);
        return ResponseEntity.ok(attachments);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<AttachmentResponse> getAttachment(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        AttachmentResponse response = attachmentService.getAttachment(id, companyId);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        attachmentService.deleteAttachment(id, companyId);
        return ResponseEntity.noContent().build();
    }
    
    private UUID getCompanyIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getCompanyIdFromToken(token);
    }
    
    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
}
