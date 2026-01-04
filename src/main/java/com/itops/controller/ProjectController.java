package com.itops.controller;

import com.itops.dto.ProjectRequest;
import com.itops.dto.ProjectResponse;
import com.itops.security.JwtUtil;
import com.itops.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAllProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "updatedAt") String sortBy,
            HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        String userRole = getUserRoleFromRequest(request);
        UUID teamId = getTeamIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(projectService.getAllProjectsWithFilters(
            companyId, status, priority, clientId, search, sortBy, userRole, teamId, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable UUID id, HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        return ResponseEntity.ok(projectService.getProjectById(id, companyId));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectRequest projectRequest,
                                                          HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID actorId = getUserIdFromRequest(request);
        return ResponseEntity.ok(projectService.createProject(projectRequest, companyId, actorId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(@PathVariable UUID id,
                                                          @Valid @RequestBody ProjectRequest projectRequest,
                                                          HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID actorId = getUserIdFromRequest(request);
        return ResponseEntity.ok(projectService.updateProject(id, projectRequest, companyId, actorId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id, HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        projectService.deleteProject(id, companyId);
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
