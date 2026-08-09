package dev.signaldock.security;

import dev.signaldock.config.AppProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/api-keys")
public class ApiKeyAdminController {
    private final ApiKeyHasher hasher;
    private final ApiKeyRepository repository;
    private final AppProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyAdminController(ApiKeyHasher hasher, ApiKeyRepository repository, AppProperties properties) {
        this.hasher = hasher;
        this.repository = repository;
        this.properties = properties;
    }

    @PostMapping
    @Transactional
    ResponseEntity<CreateResponse> create(
            @RequestHeader("X-Admin-Key") String adminKey,
            @Valid @RequestBody CreateRequest request
    ) {
        if (!constantTimeEquals(properties.adminKey(), adminKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin key is invalid.");
        }

        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String plaintext = "sd_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        ApiKey saved = repository.save(new ApiKey(request.name().trim(), hasher.hash(plaintext)));
        CreateResponse response = new CreateResponse(saved.getId(), saved.getName(), plaintext);
        return ResponseEntity.created(URI.create("/api/v1/admin/api-keys/" + saved.getId())).body(response);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    public record CreateRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record CreateResponse(UUID id, String name, String apiKey) {
    }
}

