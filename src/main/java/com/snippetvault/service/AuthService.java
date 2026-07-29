package com.snippetvault.service;

import com.snippetvault.dto.AuthResponse;
import com.snippetvault.dto.LoginRequest;
import com.snippetvault.dto.RefreshTokenRequest;
import com.snippetvault.dto.RegisterRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);
}