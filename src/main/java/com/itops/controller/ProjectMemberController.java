package com.itops.controller;

import com.itops.dto.request.AddProjectMemberRequest;
import com.itops.dto.response.ProjectMemberResponse;
import com.itops.service.ProjectMemberService;
import com.itops.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/members")
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;
    private final JwtUtil jwtUtil;

    @GetMapping
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER', 'CLIENT')")
    public ResponseEntity<List<ProjectMemberResponse>> getProjectMembers(
            @PathVariable UUID projectId,
            HttpServletRequest request) {
        log.info("GET /api/v1/projects/{}/members", projectId);
        UUID companyId = getCompanyIdFromRequest(request);
        List<ProjectMemberResponse> members = projectMemberService.getProjectMembers(projectId, companyId);
        return ResponseEntity.ok(members);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<ProjectMemberResponse> addMember(
            @PathVariable UUID projectId,
            @Valid @RequestBody AddProjectMemberRequest request,
            HttpServletRequest httpRequest) {
        log.info("POST /api/v1/projects/{}/members - adding user: {}", projectId, request.getUserId());
        UUID companyId = getCompanyIdFromRequest(httpRequest);
        ProjectMemberResponse member = projectMemberService.addMember(projectId, request, companyId);
        return ResponseEntity.ok(member);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            HttpServletRequest request) {
        log.info("DELETE /api/v1/projects/{}/members/{}", projectId, userId);
        UUID companyId = getCompanyIdFromRequest(request);
        projectMemberService.removeMember(projectId, userId, companyId);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/sync")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER')")
    public ResponseEntity<String> syncMembersFromTasks(
            @PathVariable UUID projectId,
            HttpServletRequest request) {
        log.info("POST /projects/{}/members/sync", projectId);
        UUID companyId = getCompanyIdFromRequest(request);
        int addedCount = projectMemberService.syncProjectMembersFromTasks(projectId, companyId);
        return ResponseEntity.ok("Synced " + addedCount + " members from task assignments");
    }

    private UUID getCompanyIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getCompanyIdFromToken(token);
    }
}
