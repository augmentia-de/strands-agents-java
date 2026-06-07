package de.augmentia.strandsagents.quarkus.service;

import de.augmentia.strandsagents.core.plugin.hitl.checkpoint.EmailChannel;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class EMailBridge {

    private static final Logger LOG = Logger.getLogger(EMailBridge.class);

    @Inject
    ReactiveMailer reactiveMailer;

    @ConfigProperty(name = "strands.hitl.email.recipient")
    String recipient;

    void initLibraryBridge(@Observes StartupEvent ev) {
        LOG.info("Registriere Quarkus-Mailer im EmailChannel für: " + recipient);

        // Wir übergeben der Library ein Lambda.
        // Die Library bleibt Quarkus-frei, nutzt aber beim Aufruf echten Quarkus-Code!
        EmailChannel.registerSender((subject, body) -> {
            reactiveMailer.send(Mail.withText(recipient, subject, body))
                    .subscribe().with(
                            success -> LOG.info("HITL-Mail erfolgreich versendet."),
                            failure -> LOG.error("Fehler beim HITL-Mailversand: ", failure)
                    );
        });
    }
}