package dev.signaldock.demo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/receiver")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoReceiverController {
    @PostMapping("/{mode}")
    ResponseEntity<Map<String, Object>> receive(@PathVariable String mode, @RequestBody JsonNode payload) {
        if ("failure".equalsIgnoreCase(mode)) {
            return ResponseEntity.status(503).body(Map.of("accepted", false, "mode", mode));
        }
        return ResponseEntity.accepted().body(Map.of(
                "accepted", true,
                "mode", mode,
                "payloadFields", payload.isObject() ? payload.size() : 0
        ));
    }
}

