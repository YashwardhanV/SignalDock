package dev.signaldock.endpoint;

import dev.signaldock.config.PageResponse;
import dev.signaldock.exception.ResourceNotFoundException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {
    private final EndpointRepository endpointRepository;
    private final EndpointUrlValidator urlValidator;
    private final SecureRandom secureRandom = new SecureRandom();

    public EndpointService(EndpointRepository endpointRepository, EndpointUrlValidator urlValidator) {
        this.endpointRepository = endpointRepository;
        this.urlValidator = urlValidator;
    }

    @Transactional
    public EndpointDtos.CreatedResponse create(EndpointDtos.CreateRequest request) {
        String url = request.url().trim();
        urlValidator.validate(url);
        WebhookEndpoint endpoint = endpointRepository.save(new WebhookEndpoint(
                request.name().trim(),
                url,
                generateSecret(),
                request.maxAttempts()
        ));
        return new EndpointDtos.CreatedResponse(EndpointDtos.Response.from(endpoint), endpoint.getSigningSecret());
    }

    @Transactional(readOnly = true)
    public PageResponse<EndpointDtos.Response> list(int page, int size) {
        return PageResponse.from(
                endpointRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)),
                EndpointDtos.Response::from
        );
    }

    @Transactional(readOnly = true)
    public EndpointDtos.Response get(UUID endpointId) {
        return EndpointDtos.Response.from(getEntity(endpointId));
    }

    @Transactional
    public EndpointDtos.Response update(UUID endpointId, EndpointDtos.UpdateRequest request) {
        WebhookEndpoint endpoint = getEntity(endpointId);
        String url = request.url().trim();
        urlValidator.validate(url);
        endpoint.update(request.name().trim(), url, request.maxAttempts(), request.active());
        return EndpointDtos.Response.from(endpoint);
    }

    @Transactional(readOnly = true)
    public WebhookEndpoint getEntity(UUID endpointId) {
        return endpointRepository.findById(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found: " + endpointId));
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

