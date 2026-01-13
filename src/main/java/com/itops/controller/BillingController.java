package com.itops.controller;

import com.itops.domain.User;
import com.itops.dto.billing.CheckoutRequest;
import com.itops.dto.billing.CheckoutResponse;
import com.itops.dto.billing.PlanResponse;
import com.itops.dto.billing.SubscriptionResponse;
import com.itops.repository.UserRepository;
import com.itops.security.JwtUtil;
import com.itops.service.PlanService;
import com.itops.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class BillingController {

    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    /**
     * Public endpoint - List all active plans
     */
    @GetMapping("/public/plans")
    public ResponseEntity<List<PlanResponse>> getPlans() {
        return ResponseEntity.ok(planService.listPlans());
    }

    /**
     * Get current subscription for the authenticated company
     */
    @GetMapping("/billing/subscription")
    public ResponseEntity<SubscriptionResponse> getSubscription(HttpServletRequest request) {
        UUID companyId = getCompanyIdFromRequest(request);
        return ResponseEntity.ok(subscriptionService.getCompanySubscription(companyId));
    }

    /**
     * Create checkout session for upgrading/subscribing to a plan
     * Only TOP_USER, SUPER_USER, or ADMIN can create checkouts
     */
    @PostMapping("/billing/checkout")
    public ResponseEntity<CheckoutResponse> createCheckout(
            @Valid @RequestBody CheckoutRequest checkoutRequest,
            HttpServletRequest request) {
        
        UUID companyId = getCompanyIdFromRequest(request);
        UUID userId = getUserIdFromRequest(request);
        String userRole = getUserRoleFromRequest(request);
        
        // Check if user has permission to create checkout
        if (!userRole.equals("TOP_USER") && !userRole.equals("SUPER_USER") && !userRole.equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(null);
        }
        
        // Get user details
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        CheckoutResponse response = subscriptionService.createCheckout(
            companyId,
            checkoutRequest.getPlanCode(),
            checkoutRequest.getBillingCycle(),
            user.getEmail(),
            user.getName()
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Webhook endpoint for Razorpay events
     * NO authentication required - signature verification is done internally
     */
    @PostMapping("/billing/webhook/razorpay")
    public ResponseEntity<Map<String, String>> handleRazorpayWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        
        try {
            log.info("Received Razorpay webhook: {}", payload.get("event"));
            
            subscriptionService.handleWebhook(payload, signature);
            
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            log.error("Webhook processing failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private UUID getCompanyIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getCompanyIdFromToken(token);
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    private String getUserRoleFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getRoleFromToken(token);
    }
}
