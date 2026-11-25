package edu.szu.moments.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class M01DiagnosticsTest {
    private MockHttpServletRequest request(String trial, String id) {
        var request = new MockHttpServletRequest();
        request.addHeader("X-M01-Trial", trial);
        request.addHeader("X-M01-Request-Id", id);
        return request;
    }

    @Test
    void disabledDiagnosticsIgnoreInputAndEnabledCaptureIsBounded() {
        var disabled = new M01Diagnostics(false);
        assertThat(disabled.begin(request("trial", "0"))).isNull();
        assertThat(disabled.snapshot("trial").get("found")).isEqualTo(false);
        var diagnostics = new M01Diagnostics(true);
        for (int i = 0; i < 16; i++) {
            var observation = diagnostics.begin(request("run_" + i, "0"));
            diagnostics.finish(observation, 200, false);
        }
        assertThat(diagnostics.begin(request("run_16", "0"))).isNull();
        assertThat(diagnostics.begin(request("run_0", "60000"))).isNull();
        assertThat(diagnostics.snapshot("run_0").get("dropped")).isEqualTo(2L);
        assertThat(diagnostics.begin(request("bad/name", "0"))).isNull();
    }

    @Test
    void globalDatabaseErrorsAreCountedOnceAndDuplicateIdsAreVisible() {
        var diagnostics = new M01Diagnostics(true);
        var handler = new ApiErrorHandler(diagnostics);
        var request = request("global", "0");
        var observation = diagnostics.begin(request);
        diagnostics.admitted(observation);
        assertThat(handler.handleDatabaseBusy(request).getStatusCode().value()).isEqualTo(503);
        diagnostics.released(observation);
        diagnostics.finish(observation, 503, false);
        var snapshot = diagnostics.snapshot("global");
        assertThat(snapshot.get("reasons503")).isEqualTo(java.util.Map.of("database_global", 1L));
        assertThat(snapshot.get("completed")).isEqualTo(1L);
        var duplicate = diagnostics.begin(request("global", "0"));
        diagnostics.finish(duplicate, 503, false);
        assertThat(diagnostics.snapshot("global").get("duplicates")).isEqualTo(1L);
    }
}
