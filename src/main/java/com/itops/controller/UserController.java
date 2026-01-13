package com.itops.controller;

import com.itops.dto.CreateUserRequest;
import com.itops.dto.UserResponse;
import com.itops.security.JwtUtil;
import com.itops.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(userService.getAllUsers(companyId, userId));
    }

    @GetMapping("/all-for-assignment")
    public ResponseEntity<List<UserResponse>> getAllUsersForAssignment(HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(userService.getAllUsersForAssignment(companyId, userId));
    }
    
    @GetMapping("/mentions")
    public ResponseEntity<List<UserResponse>> getUsersForMentions(HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(userService.getUsersForMentions(companyId, userId));
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest createRequest,
            HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        return ResponseEntity.ok(userService.createUser(createRequest, companyId, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @RequestBody CreateUserRequest updateRequest,
            HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        return ResponseEntity.ok(userService.updateUser(id, updateRequest, companyId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID id,
            HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        userService.deleteUser(id, companyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        UUID companyId = getCompanyIdFromRequest(request);
        return ResponseEntity.ok(userService.getCurrentUser(userId, companyId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUserProfile(
            @Valid @RequestBody com.itops.dto.UpdateUserProfileRequest updateRequest,
            HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        UUID companyId = getCompanyIdFromRequest(request);
        return ResponseEntity.ok(userService.updateCurrentUserProfile(userId, companyId, updateRequest));
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
