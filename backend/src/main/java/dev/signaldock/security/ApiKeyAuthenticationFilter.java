package dev.signaldock.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.signaldock.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-API-Key";

    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(
            ApiKeyHasher apiKeyHasher,
            ApiKeyRepository apiKeyRepository,
            ObjectMapper objectMapper
    ) {
        this.apiKeyHasher = apiKeyHasher;
        this.apiKeyRepository = apiKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || path.startsWith("/actuator/health")
                || path.startsWith("/docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/api/v1/admin/api-keys")
                || path.startsWith("/api/v1/demo/receiver");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            reject(response, "Missing X-API-Key header");
            return;
        }

        var key = apiKeyRepository.findByKeyHashAndActiveTrueAndRevokedAtIsNull(apiKeyHasher.hash(rawKey.trim()));
        if (key.isEmpty()) {
            reject(response, "API key is invalid or revoked");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                key.get().getName(),
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiError(
                "unauthorized",
                message,
                Map.of(),
                null,
                Instant.now()
        ));
    }
}
