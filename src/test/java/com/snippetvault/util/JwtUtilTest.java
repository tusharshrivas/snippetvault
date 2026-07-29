package com.snippetvault.util;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    // A valid Base64-encoded 256-bit key for tests
    private static final String TEST_SECRET =
            Base64.getEncoder().encodeToString(
                    "test-secret-key-must-be-at-least-32-bytes!!".getBytes());

    private static final long ACCESS_EXPIRY_MS  = 1_800_000L; // 30 min
    private static final long REFRESH_EXPIRY_MS = 604_800_000L; // 7 days

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, ACCESS_EXPIRY_MS, REFRESH_EXPIRY_MS);
    }

    // -------------------------------------------------------------------------
    // Access token
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateAccessToken — returns a non-null, non-blank token")
    void generateAccessToken_returnsToken() {
        String token = jwtUtil.generateAccessToken("tushar");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("generateAccessToken — token is valid immediately after creation")
    void generateAccessToken_isValidImmediately() {
        String token = jwtUtil.generateAccessToken("tushar");
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("generateAccessToken — extracted username matches input")
    void generateAccessToken_extractsCorrectUsername() {
        String token = jwtUtil.generateAccessToken("tushar");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("tushar");
    }

    @Test
    @DisplayName("generateAccessToken — isAccessToken returns true, isRefreshToken returns false")
    void generateAccessToken_typeClaimsAreCorrect() {
        String token = jwtUtil.generateAccessToken("tushar");
        assertThat(jwtUtil.isAccessToken(token)).isTrue();
        assertThat(jwtUtil.isRefreshToken(token)).isFalse();
    }

    @Test
    @DisplayName("generateAccessToken — expiration is roughly 30 minutes in the future")
    void generateAccessToken_expiryIsCorrect() {
        long before = System.currentTimeMillis();
        String token = jwtUtil.generateAccessToken("tushar");
        long after  = System.currentTimeMillis();

        Date expiry = jwtUtil.extractExpiration(token);

        // Allow a 2-second window to account for test execution time
        assertThat(expiry.getTime()).isBetween(
                before + ACCESS_EXPIRY_MS - 2000,
                after  + ACCESS_EXPIRY_MS + 2000
        );
    }

    // -------------------------------------------------------------------------
    // Refresh token
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateRefreshToken — isRefreshToken returns true, isAccessToken returns false")
    void generateRefreshToken_typeClaimsAreCorrect() {
        String token = jwtUtil.generateRefreshToken("tushar");
        assertThat(jwtUtil.isRefreshToken(token)).isTrue();
        assertThat(jwtUtil.isAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("generateRefreshToken — extracted username matches input")
    void generateRefreshToken_extractsCorrectUsername() {
        String token = jwtUtil.generateRefreshToken("tushar");
        assertThat(jwtUtil.extractUsername(token)).isEqualTo("tushar");
    }

    // -------------------------------------------------------------------------
    // Validation edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateToken — returns false for a tampered token")
    void validateToken_returnsFalseForTamperedToken() {
        String token = jwtUtil.generateAccessToken("tushar");
        // Corrupt the signature by appending extra characters
        String tampered = token + "corrupted";
        assertThat(jwtUtil.validateToken(tampered)).isFalse();
    }

    @Test
    @DisplayName("validateToken — returns false for an empty string")
    void validateToken_returnsFalseForEmptyString() {
        assertThat(jwtUtil.validateToken("")).isFalse();
    }

    @Test
    @DisplayName("validateToken — returns false for a random non-JWT string")
    void validateToken_returnsFalseForRandomString() {
        assertThat(jwtUtil.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    @DisplayName("validateToken — returns false for token signed with a different secret")
    void validateToken_returnsFalseForWrongSecret() {
        String differentSecret = Base64.getEncoder().encodeToString(
                "completely-different-secret-key-32-bytes!".getBytes());
        JwtUtil otherUtil = new JwtUtil(differentSecret, ACCESS_EXPIRY_MS, REFRESH_EXPIRY_MS);

        String tokenFromOther = otherUtil.generateAccessToken("tushar");
        // Our jwtUtil should reject a token signed by a different key
        assertThat(jwtUtil.validateToken(tokenFromOther)).isFalse();
    }

    @Test
    @DisplayName("isTokenExpired — returns false for a freshly issued token")
    void isTokenExpired_freshToken_returnsFalse() {
        String token = jwtUtil.generateAccessToken("tushar");
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("validateToken — expired token is rejected (zero-expiry JwtUtil)")
    void validateToken_expiredToken_returnsFalse() throws InterruptedException {
        // Create a JwtUtil that issues tokens expiring in 1ms
        JwtUtil shortLivedUtil = new JwtUtil(TEST_SECRET, 1L, 1L);
        String token = shortLivedUtil.generateAccessToken("tushar");

        Thread.sleep(5); // let it expire

        assertThat(shortLivedUtil.validateToken(token)).isFalse();
    }
}