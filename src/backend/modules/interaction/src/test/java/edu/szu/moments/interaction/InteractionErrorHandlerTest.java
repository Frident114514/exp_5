package edu.szu.moments.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;

class InteractionErrorHandlerTest {
    private final InteractionErrorHandler handler = new InteractionErrorHandler();

    @Test
    void mapsTemporaryDatabaseFailuresToRetryableServiceBusy() {
        List<Exception> failures = List.of(
            new DataAccessResourceFailureException("connection lost"),
            new TransientDataAccessResourceException("database timeout"),
            new CannotCreateTransactionException("connection pool unavailable")
        );

        for (Exception failure : failures) {
            var response = handler.serviceBusy(failure);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1");
            assertThat(response.getBody()).extracting("code").isEqualTo("SERVICE_BUSY");
        }
    }
}
