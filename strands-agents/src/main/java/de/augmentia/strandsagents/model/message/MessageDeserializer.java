package de.augmentia.strandsagents.model.message;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/** Jackson deserializer for Message. */
public class MessageDeserializer extends StdDeserializer<Message> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public MessageDeserializer() {
        super(Message.class);
    }

    /** Deserializes a Message from its JSON representation. */
    @Override
    public Message deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        var id = node.get("id").asText();
        var timestamp = Instant.parse(node.get("timestamp").asText());
        var delegateStr = node.get("delegate").asText();
        var messages = ChatMessageDeserializer.messagesFromJson(delegateStr);
        var delegate = messages.isEmpty() ? null : messages.get(0);
        var metaNode = node.get("metadata");
        var metadata = metaNode != null && !metaNode.isNull()
            ? MAPPER.convertValue(metaNode, Map.class)
            : Map.of();
        return new Message(id, timestamp, delegate, metadata);
    }
}
