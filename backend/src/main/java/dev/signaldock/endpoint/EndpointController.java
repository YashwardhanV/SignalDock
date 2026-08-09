package dev.signaldock.endpoint;

import dev.signaldock.config.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/endpoints")
public class EndpointController {
    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    @PostMapping
    ResponseEntity<EndpointDtos.CreatedResponse> create(@Valid @RequestBody EndpointDtos.CreateRequest request) {
        EndpointDtos.CreatedResponse response = endpointService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/endpoints/" + response.endpoint().id())).body(response);
    }

    @GetMapping
    PageResponse<EndpointDtos.Response> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return endpointService.list(page, size);
    }

    @GetMapping("/{endpointId}")
    EndpointDtos.Response get(@PathVariable UUID endpointId) {
        return endpointService.get(endpointId);
    }

    @PatchMapping("/{endpointId}")
    EndpointDtos.Response update(
            @PathVariable UUID endpointId,
            @Valid @RequestBody EndpointDtos.UpdateRequest request
    ) {
        return endpointService.update(endpointId, request);
    }
}

