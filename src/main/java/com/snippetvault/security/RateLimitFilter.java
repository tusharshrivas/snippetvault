package com.snippetvault.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.snippetvault.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int WINDOW_SECONDS = 60;
    private static final String KEY_PREFIX   = "rate_limit:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rate.limit.unauthenticated.requests-per-minute}")
    private int unauthenticatedLimit;

    @Value("${rate.limit.authenticated.requests-per-minute}")
    private int authenticatedLimit;

    @Override
    public void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (isDocumentationRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String identifier = resolveIdentifier(request);
        int    limit      = resolveLimit();
        String redisKey   = KEY_PREFIX + identifier;

        long currentCount = increment(redisKey);

        if (currentCount > limit) {
            long ttl = getRemainingTtl(redisKey);
            rejectRequest(response, ttl);
            return;
        }

        response.setHeader("X-RateLimit-Limit",     String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - currentCount)));
        response.setHeader("X-RateLimit-Reset",     String.valueOf(System.currentTimeMillis() / 1000 + WINDOW_SECONDS));

        filterChain.doFilter(request, response);
    }

    private long increment(String redisKey) {
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count == null) {
            log.warn("Redis INCR returned null for key: {}. Failing open.", redisKey);
            return 0L;
        }
        if (count == 1L) {
            redisTemplate.expire(redisKey, WINDOW_SECONDS, TimeUnit.SECONDS);
        }
        return count;
    }

    private String resolveIdentifier(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return "user:" + auth.getName();
        }
        return "ip:" + extractClientIp(request);
    }

    private int resolveLimit() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return authenticatedLimit;
        }
        return unauthenticatedLimit;
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private long getRemainingTtl(String redisKey) {
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : WINDOW_SECONDS;
    }

    private void rejectRequest(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(429)
                .error("Too Many Requests")
                .message("Rate limit exceeded. Retry after %d seconds.".formatted(retryAfterSeconds))
                .timestamp(LocalDateTime.now())
                .build();

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    private boolean isDocumentationRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/swagger-ui.html");
    }
}