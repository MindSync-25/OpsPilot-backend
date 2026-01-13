package com.itops.controller;

import com.itops.dto.AnalyticsResponse;
import com.itops.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<AnalyticsResponse> getAnalytics(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        
        UUID companyId = extractCompanyId(authentication);
        AnalyticsResponse analytics = analyticsService.getCompanyAnalytics(companyId);
        return ResponseEntity.ok(analytics);
    }

    private UUID extractCompanyId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Map claims && claims.containsKey("companyId")) {
            return UUID.fromString(claims.get("companyId").toString());
        }
        throw new IllegalArgumentException("Invalid authentication principal");
    }
}

