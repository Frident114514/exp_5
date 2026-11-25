package edu.szu.moments.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "moments.diagnostics.enabled", havingValue = "true")
class M01DiagnosticsController {
    private final M01Diagnostics diagnostics;
    M01DiagnosticsController(M01Diagnostics diagnostics) { this.diagnostics = diagnostics; }

    @GetMapping("/api/v1/internal/m01-diagnostics")
    Map<String, Object> snapshot(@RequestParam String trial) { return diagnostics.snapshot(trial); }
}
