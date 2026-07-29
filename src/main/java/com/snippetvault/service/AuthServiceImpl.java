package com.snippetvault.service;

import com.snippetvault.dto.AuthResponse;
import com.snippetvault.dto.LoginRequest;
import com.snippetvault.dto.RefreshTokenRequest;
import com.snippetvault.dto.RegisterRequest;
import com.snippetvault.exception.DuplicateResourceException;
import com.snippetvault.exception.UnauthorizedException;
import com.snippetvault.model.User;
import com.snippetvault.repository.UserRepository;
import com.snippetvault.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException(
                    "Username '%s' is already taken".formatted(request.username()));
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "Email '%s' is already registered".formatted(request.email()));
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .apiKey(generateApiKey())
                .build();

        userRepository.save(user);

        String accessToken  = jwtUtil.generateAccessToken(user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        return AuthResponse.of(accessToken, refreshToken,
                jwtUtil.getAccessTokenExpiryMs(), user.getApiKey());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // authenticate() internally calls UserDetailsServiceImpl.loadUserByUsername
        // then BCrypt-compares the password. Throws BadCredentialsException on failure,
        // which GlobalExceptionHandler maps to HTTP 401.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        String accessToken  = jwtUtil.generateAccessToken(user.getUsername());
        String refreshToken = jwtUtil.generateRefreshToken(user.getUsername());

        return AuthResponse.of(accessToken, refreshToken,
                jwtUtil.getAccessTokenExpiryMs(), user.getApiKey());
    }

    @Override
    public AuthResponse refresh(RefreshTokenRequest request) {
        String token = request.refreshToken();

        if (!jwtUtil.validateToken(token)) {
            throw new UnauthorizedException("Refresh token is invalid or expired");
        }
        if (!jwtUtil.isRefreshToken(token)) {
            throw new UnauthorizedException("Provided token is not a refresh token");
        }

        String username = jwtUtil.extractUsername(token);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Refresh token rotation: issue a brand-new refresh token alongside the new access token.
        // The old refresh token is now effectively invalidated from the client's perspective
        // (server is stateless, so true revocation would require a Redis blocklist — noted
        // in the README as a known improvement).
        String newAccessToken  = jwtUtil.generateAccessToken(username);
        String newRefreshToken = jwtUtil.generateRefreshToken(username);

        return AuthResponse.of(newAccessToken, newRefreshToken,
                jwtUtil.getAccessTokenExpiryMs(), user.getApiKey());
    }

    private String generateApiKey() {
        // UUID gives 122 bits of randomness — collision probability is negligible
        return UUID.randomUUID().toString().replace("-", "");
    }
}