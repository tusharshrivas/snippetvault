package com.snippetvault.service;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snippetvault.security.RateLimitFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private StringRedisTemplate  redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private HttpServletRequest   request;
    @Mock private HttpServletResponse  response;
    @Mock private FilterChain          filterChain;

    private RateLimitFilter rateLimitFilter;
    private ObjectMapper    objectMapper;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        rateLimitFilter  = new RateLimitFilter(redisTemplate, objectMapper);

        // Inject @Value fields that Spring normally injects at runtime
        ReflectionTestUtils.setField(rateLimitFilter, "unauthenticatedLimit", 10);
        ReflectionTestUtils.setField(rateLimitFilter, "authenticatedLimit",   100);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // Default: non-documentation path
        when(request.getRequestURI()).thenReturn("/api/snippets");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Clear Security context between tests
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Unauthenticated requests (IP-based)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("unauthenticated — under limit: request passes through filter chain")
    void unauthenticated_underLimit_requestPassesThrough() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(5L); // well under limit of 10
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(55L);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("unauthenticated — at limit exactly: last allowed request passes through")
    void unauthenticated_atLimit_requestPassesThrough() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(10L); // exactly at limit of 10
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(30L);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("unauthenticated — over limit: request is rejected with HTTP 429")
    void unauthenticated_overLimit_requestRejected() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(11L); // over limit of 10
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(45L);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("unauthenticated — over limit: Retry-After header is set")
    void unauthenticated_overLimit_retryAfterHeaderSet() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(11L);
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(42L);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("Retry-After", "42");
    }

    // -------------------------------------------------------------------------
    // Authenticated requests (username-based)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("authenticated — uses higher limit of 100 requests per minute")
    void authenticated_usesHigherLimit() throws Exception {
        // Simulate authenticated user in SecurityContext
        setAuthenticatedUser("tushar");

        // 95 is under 100 (authenticated limit) but over 10 (unauthenticated limit)
        when(valueOps.increment(anyString())).thenReturn(95L);
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(30L);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Should pass through — authenticated limit is 100, not 10
        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    @DisplayName("authenticated — uses username as Redis key identifier, not IP")
    void authenticated_usesUsernameAsRedisKey() throws Exception {
        setAuthenticatedUser("tushar");

        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(60L);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Key must contain the username, not the IP
        verify(valueOps).increment(argThat(key ->
                key.contains("tushar") && !key.contains("127.0.0.1")));
    }

    // -------------------------------------------------------------------------
    // TTL and first-request window setup
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("first request in window — TTL is set on Redis key")
    void firstRequest_ttlIsSetOnRedisKey() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L); // first request
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(60L);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // TTL must be set when count == 1 (start of new window)
        verify(redisTemplate).expire(anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("subsequent requests — TTL is NOT reset after first request")
    void subsequentRequest_ttlIsNotReset() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(5L); // 5th request, not first
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(40L);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // TTL must NOT be reset on every request — only on count == 1
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    // -------------------------------------------------------------------------
    // Documentation bypass
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("swagger-ui requests — bypass rate limiting entirely")
    void swaggerUiRequest_bypassesRateLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        // Redis must never be touched for documentation requests
        verify(valueOps, never()).increment(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("v3/api-docs requests — bypass rate limiting entirely")
    void apiDocsRequest_bypassesRateLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/v3/api-docs/swagger-config");

        rateLimitFilter.doFilterInternal(request, response, filterChain);

        verify(valueOps, never()).increment(anyString());
        verify(filterChain).doFilter(request, response);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void setAuthenticatedUser(String username) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }
}