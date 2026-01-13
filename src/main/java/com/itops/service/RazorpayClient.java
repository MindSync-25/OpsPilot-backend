package com.itops.service;

import com.itops.config.RazorpayConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RazorpayClient {
    
    private final RazorpayConfig razorpayConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String RAZORPAY_API_BASE = "https://api.razorpay.com/v1";
    
    /**
     * Create or fetch Razorpay customer
     */
    public Map<String, Object> createCustomer(String email, String name, String companyName) {
        String url = RAZORPAY_API_BASE + "/customers";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("email", email);
        requestBody.put("name", name);
        requestBody.put("fail_existing", "0"); // Return existing customer if found
        
        Map<String, String> notes = new HashMap<>();
        notes.put("company", companyName);
        requestBody.put("notes", notes);
        
        HttpHeaders headers = createHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            log.info("Razorpay customer created/fetched: {}", response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("Error creating Razorpay customer", e);
            throw new RuntimeException("Failed to create Razorpay customer: " + e.getMessage());
        }
    }
    
    /**
     * Create Razorpay subscription
     */
    public Map<String, Object> createSubscription(String planId, String customerId, int totalCount, Map<String, String> notes) {
        String url = RAZORPAY_API_BASE + "/subscriptions";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("plan_id", planId);
        requestBody.put("customer_id", customerId);
        requestBody.put("total_count", totalCount); // 0 for ongoing
        requestBody.put("customer_notify", 1);
        
        if (notes != null) {
            requestBody.put("notes", notes);
        }
        
        HttpHeaders headers = createHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            log.info("Razorpay subscription created: {}", response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("Error creating Razorpay subscription", e);
            throw new RuntimeException("Failed to create Razorpay subscription: " + e.getMessage());
        }
    }
    
    /**
     * Fetch subscription details
     */
    public Map<String, Object> fetchSubscription(String subscriptionId) {
        String url = RAZORPAY_API_BASE + "/subscriptions/" + subscriptionId;
        
        HttpHeaders headers = createHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error fetching Razorpay subscription", e);
            throw new RuntimeException("Failed to fetch Razorpay subscription: " + e.getMessage());
        }
    }
    
    /**
     * Cancel subscription
     */
    public Map<String, Object> cancelSubscription(String subscriptionId, boolean cancelAtCycleEnd) {
        String url = RAZORPAY_API_BASE + "/subscriptions/" + subscriptionId + "/cancel";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("cancel_at_cycle_end", cancelAtCycleEnd ? 1 : 0);
        
        HttpHeaders headers = createHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            log.info("Razorpay subscription cancelled: {}", response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("Error cancelling Razorpay subscription", e);
            throw new RuntimeException("Failed to cancel Razorpay subscription: " + e.getMessage());
        }
    }
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        String auth = razorpayConfig.getKeyId() + ":" + razorpayConfig.getKeySecret();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);
        
        return headers;
    }
}
