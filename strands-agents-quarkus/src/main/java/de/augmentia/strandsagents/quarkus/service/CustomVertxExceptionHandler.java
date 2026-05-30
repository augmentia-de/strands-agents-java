package de.augmentia.strandsagents.quarkus.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.logging.Logger;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;

@ApplicationScoped
public class CustomVertxExceptionHandler {

    private static final Logger LOG = Logger.getLogger(CustomVertxExceptionHandler.class);

    void onStart(@Observes StartupEvent event, Vertx vertx) {
        vertx.exceptionHandler(throwable -> {
            if (isSuppressed(throwable)) {
                LOG.debug("Ignoriere leeres SSE-Event (OpenRouter-Kommentar)", throwable);
                return;
            }
            LOG.error("Unbehandelte Exception im Vert.x-Eventloop", throwable);
        });
    }

    private boolean isSuppressed(Throwable t) {
        var cause = t;
        while (cause != null) {
            if (cause.getClass().getName().contains("MismatchedInputException")
                    && cause.getMessage() != null
                    && cause.getMessage().contains("No content to map due to end-of-input")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
