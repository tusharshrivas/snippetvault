package com.snippetvault.service;

import com.snippetvault.dto.AuthResponse;
import com.snippetvault.dto.LoginRequest;
import com.snippetvault.dto.RegisterRequest;
import com.snippetvault.exception.DuplicateResourceException;
import com.snippetvault.model.User;
import com.snippetvault.repository.UserRepository;
import com.snippetvault.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository       userRepository;
    @Mock private PasswordEncoder      passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil              jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest validRegisterRequest;
    private User            savedUser;

    @BeforeEach
    void setUp() {
        validRegisterRequest = RegisterRequest.builder()
                .username("tushar")
                .email("tushar@example.com")
                .password("password123")
                .build();

        savedUser = User.builder()
                .id(1L)
                .username("tushar")
                .email("tushar@example.com")
                .password("hashed-password")
                .apiKey("abc123apikey")
                .build();
    }

    // -------------------------------------------------------------------------
    // register()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("register — success: saves user and returns tokens")
    void register_success_savesUserAndReturnsTokens() {
        when(userRepository.existsByUsername("tushar")).thenReturn(false);
        when(userRepository.existsByEmail("tushar@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateAccessToken("tushar")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("tushar")).thenReturn("refresh-token");
        when(jwtUtil.getAccessTokenExpiryMs()).thenReturn(1_800_000L);

        AuthResponse response = authService.register(validRegisterRequest);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.apiKey()).isNotNull().hasSize(32);;
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(1800L);

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register — duplicate username throws DuplicateResourceException")
    void register_duplicateUsername_throwsDuplicateResourceException() {
        when(userRepository.existsByUsername("tushar")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("tushar");

        // User must never be saved when username is taken
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register — duplicate email throws DuplicateResourceException")
    void register_duplicateEmail_throwsDuplicateResourceException() {
        when(userRepository.existsByUsername("tushar")).thenReturn(false);
        when(userRepository.existsByEmail("tushar@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(validRegisterRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("tushar@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register — password is BCrypt-encoded before saving")
    void register_passwordIsEncoded() {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtUtil.generateAccessToken(any())).thenReturn("token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");
        when(jwtUtil.getAccessTokenExpiryMs()).thenReturn(1_800_000L);

        authService.register(validRegisterRequest);

        // Verify encode was called with the raw password
        verify(passwordEncoder).encode("password123");

        // Capture what was actually saved and assert raw password is NOT there
        verify(userRepository).save(argThat(user ->
                !user.getPassword().equals("password123") &&
                        user.getPassword().equals("$2a$10$hashed")
        ));
    }

    // -------------------------------------------------------------------------
    // login()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("login — success: authenticates and returns tokens")
    void login_success_returnsTokens() {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("tushar")
                .password("password123")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null); // authenticate() returns Authentication; we don't use the return value
        when(userRepository.findByUsername("tushar")).thenReturn(Optional.of(savedUser));
        when(jwtUtil.generateAccessToken("tushar")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("tushar")).thenReturn("refresh-token");
        when(jwtUtil.getAccessTokenExpiryMs()).thenReturn(1_800_000L);

        AuthResponse response = authService.login(loginRequest);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(authenticationManager).authenticate(any());
    }

    @Test
    @DisplayName("login — wrong password: AuthenticationManager throws BadCredentialsException")
    void login_wrongPassword_throwsBadCredentialsException() {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("tushar")
                .password("wrongpassword")
                .build();

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class);

        // Token generation must never happen after a failed authentication
        verify(jwtUtil, never()).generateAccessToken(any());
    }
}