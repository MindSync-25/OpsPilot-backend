package com.itops.controller;

import com.itops.dto.CreatePhaseRequest;
import com.itops.dto.PhaseResponse;
import com.itops.dto.UpdatePhaseRequest;
import com.itops.security.JwtUtil;
import com.itops.service.ProjectPhaseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/phases")
@RequiredArgsConstructor
public class ProjectPhaseController {

    private final ProjectPhaseService phaseService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<PhaseResponse>> getPhasesByProject(@PathVariable UUID projectId,
                                                                   HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        String userRole = getUserRoleFromRequest(request);
        UUID teamId = getTeamIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(phaseService.getPhasesByProject(projectId, companyId, userRole, teamId, userId));
    }

    @GetMapping("/{phaseId}")
    public ResponseEntity<PhaseResponse> getPhaseById(@PathVariable UUID projectId,
                                                       @PathVariable UUID phaseId,
                                                       HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        return ResponseEntity.ok(phaseService.getPhaseById(projectId, phaseId, companyId));
    }

    @PostMapping
    public ResponseEntity<PhaseResponse> createPhase(@PathVariable UUID projectId,
                                                      @Valid @RequestBody CreatePhaseRequest createRequest,
                                                      HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(phaseService.createPhase(projectId, createRequest, companyId));
    }

    @PutMapping("/{phaseId}")
    public ResponseEntity<PhaseResponse> updatePhase(@PathVariable UUID projectId,
                                                      @PathVariable UUID phaseId,
                                                      @Valid @RequestBody UpdatePhaseRequest updateRequest,
                                                      HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        return ResponseEntity.ok(phaseService.updatePhase(projectId, phaseId, updateRequest, companyId));
    }

    @DeleteMapping("/{phaseId}")
    public ResponseEntity<Void> deletePhase(@PathVariable UUID projectId,
                                             @PathVariable UUID phaseId,
                                                HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        phaseService.deletePhase(projectId, phaseId, companyId);
        return ResponseEntity.noContent().build();
    }

    private UUID getCompanyIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getCompanyIdFromToken(token);
    }
    
    private String getUserRoleFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getRoleFromToken(token);
    }
    
    private UUID getTeamIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getTeamIdFromToken(token);
    }
    
    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }
}
