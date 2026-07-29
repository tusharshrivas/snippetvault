package com.snippetvault.dto;

import lombok.Builder;

@Builder
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String apiKey
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresInMs, String apiKey) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresInMs / 1000)
                .apiKey(apiKey)
                .build();
    }
}