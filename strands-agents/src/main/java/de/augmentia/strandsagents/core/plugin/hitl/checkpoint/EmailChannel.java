package de.augmentia.strandsagents.core.plugin.hitl.checkpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailChannel implements CheckpointChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);
    private final String recipient;

    public EmailChannel(String recipient) {
        this.recipient = recipient;
    }

    @Override
    public void notify(Checkpoint checkpoint) {
        log.info("EmailChannel: would notify {} about checkpoint {} (tool={}, session={}) — stub, not implemented",
            recipient, checkpoint.id(), checkpoint.toolName(), checkpoint.sessionId());
    }
}
