package dev.signaldock.endpoint;

import dev.signaldock.config.PageResponse;
import dev.signaldock.exception.ConflictException;
import dev.signaldock.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndpointService {
    private final EndpointRepository endpointRepository;
    private final EndpointSecretGenerator secretGenerator;
    private final EndpointUrlValidator urlValidator;

    public EndpointService(
            EndpointRepository endpointRepository,
            EndpointSecretGenerator secretGenerator,
            EndpointUrlValidator urlValidator
    ) {
        this.endpointRepository = endpointRepository;
        this.secretGenerator = secretGenerator;
        this.urlValidator = urlValidator;
    }

    @Transactional
    public EndpointDtos.CreatedResponse create(EndpointDtos.CreateRequest request) {
        String url = request.url().trim();
        urlValidator.validate(url);
        if (endpointRepository.existsByUrlIgnoreCaseAndActiveTrue(url)) {
            throw new ConflictException("An active endpoint already uses this URL.");
        }
        WebhookEndpoint endpoint = endpointRepository.save(new WebhookEndpoint(
                request.name().trim(),
                url,
                secretGenerator.generate(),
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
        if (request.active() && endpointRepository.existsByUrlIgnoreCaseAndActiveTrueAndIdNot(url, endpointId)) {
            throw new ConflictException("An active endpoint already uses this URL.");
        }
        endpoint.update(request.name().trim(), url, request.maxAttempts(), request.active());
        return EndpointDtos.Response.from(endpoint);
    }

    @Transactional(readOnly = true)
    public WebhookEndpoint getEntity(UUID endpointId) {
        return endpointRepository.findById(endpointId)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint not found: " + endpointId));
    }
}

