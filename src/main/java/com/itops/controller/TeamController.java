package com.itops.controller;

import com.itops.dto.TeamRequest;
import com.itops.dto.TeamResponse;
import com.itops.security.JwtUtil;
import com.itops.service.TeamService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TeamController {

    private final TeamService teamService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<TeamResponse>> getAllTeams(HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(teamService.getAllTeams(companyId, userId));
    }

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(
            @RequestBody TeamRequest teamRequest,
            HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        TeamResponse response = teamService.createTeam(teamRequest, companyId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(
            @PathVariable UUID id,
            @RequestBody TeamRequest teamRequest,
            HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(teamService.updateTeam(id, teamRequest, companyId, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        teamService.deleteTeam(id, companyId, userId);
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
