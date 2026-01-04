package com.itops.controller;

import com.itops.dto.DashboardResponse;
import com.itops.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {
    
    private final DashboardService dashboardService;
    
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DashboardResponse> getDashboardStats(Authentication authentication) {
        try {
            System.out.println("Dashboard stats endpoint called");
            
            // Get user details from authentication principal (which is a Map of claims)
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) authentication.getPrincipal();
            UUID userId = UUID.fromString((String) claims.get("userId"));
            String role = (String) claims.get("role");
            
            System.out.println("User ID: " + userId + ", Role: " + role);
            
            DashboardResponse response = dashboardService.getDashboardStats(userId, role);
            System.out.println("Dashboard stats response generated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("Error in dashboard controller: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
