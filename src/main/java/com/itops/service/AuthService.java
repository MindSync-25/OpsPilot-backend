package com.itops.service;

import com.itops.domain.Company;
import com.itops.domain.User;
import com.itops.dto.LoginRequest;
import com.itops.dto.LoginResponse;
import com.itops.dto.SignupRequest;
import com.itops.dto.SignupResponse;
import com.itops.repository.CompanyRepository;
import com.itops.repository.UserRepository;
import com.itops.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final SubscriptionService subscriptionService;

    public LoginResponse login(LoginRequest request) {
        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Authenticate
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // Generate tokens
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getCompanyId(),
                user.getTeamId()
        );
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // Build response
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .companyId(user.getCompanyId())
                        .teamId(user.getTeamId())
                        .build())
                .build();
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // Check if email already exists globally
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }

        // Create new company
        Company company = Company.builder()
                .name(request.getCompanyName())
                .subscriptionStatus("ACTIVE")
                .build();
        company = companyRepository.save(company);

        // Create trial subscription for the new company on selected plan
        String planCode = request.getPlanCode() != null ? request.getPlanCode() : "STARTER";
        subscriptionService.createOrUpdateTrial(company.getId(), planCode);

        // Hash password
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // Create new user with TOP_USER role (company owner)
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(hashedPassword)
                .role(User.UserRole.TOP_USER)
                .isActive(true)
                .build();
        user.setCompanyId(company.getId());
        user = userRepository.save(user);

        // Generate tokens
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.getCompanyId(),
                user.getTeamId()
        );
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // Build response
        return SignupResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(SignupResponse.UserInfo.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .companyId(user.getCompanyId())
                        .teamId(user.getTeamId())
                        .build())
                .build();
    }
}
