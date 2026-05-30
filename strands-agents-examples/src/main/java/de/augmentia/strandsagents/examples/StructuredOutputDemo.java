package de.augmentia.strandsagents.examples;

import java.time.Instant;

import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.StreamingAgent;
import de.augmentia.strandsagents.core.config.AgentConfig;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.model.agent.AgentResult;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.model.chat.ChatModel;

public class StructuredOutputDemo {

    private static ObjectMapper createMapper() {
        var m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return m;
    }

    private static final ObjectMapper MAPPER = createMapper();

    // --- Static Mode Records ---

    public record Address(String street, String city, String zip) {}

    public record Customer(
        String name,
        int age,
        String email,
        java.time.LocalDate registeredAt,
        Address address
    ) {}

    // --- Dynamic Mode ---

    private static final String CUSTOMER_SCHEMA = """
        {
          "title": "Customer",
          "type": "object",
          "properties": {
            "name": { "type": "string" },
            "age": { "type": "integer" },
            "email": { "type": "string" },
            "registeredAt": { "type": "string" },
            "address": {
              "type": "object",
              "properties": {
                "street": { "type": "string" },
                "city": { "type": "string" },
                "zip": { "type": "string" }
              },
              "required": ["street", "city", "zip"]
            }
          },
          "required": ["name", "age", "email", "registeredAt", "address"]
        }
        """;

    private static final String SHOPPING_CART_SCHEMA = """
        {
          "title": "Cart",
          "type": "object",
          "properties": {
            "customer": {
              "type": "object",
              "properties": {
                "name": { "type": "string" },
                "email": { "type": "string" }
              },
              "required": ["name", "email"]
            },
            "items": {
              "type": "array",
              "items": {
                "type": "object",
                "properties": {
                  "product": { "type": "string" },
                  "quantity": { "type": "integer" },
                  "price": { "type": "number" }
                },
                "required": ["product", "quantity", "price"]
              }
            },
            "total": { "type": "number" }
          },
          "required": ["customer", "items", "total"]
        }
        """;

    // --- Main ---

    public static void main(String[] args) {
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank()) {
            System.out.println("Fehler: OPENAI_API_KEY ist nicht gesetzt.");
            System.out.println("  Setze die Umgebungsvariable: export OPENAI_API_KEY=sk-...");
            System.exit(1);
        }

        demoStaticRecord();
        //demoDynamicSchema();
        //demoShoppingCart();
    }

    // --- Static Mode ---

    static void demoStaticRecord() {
        System.out.println("=== STATIC Mode: Nested Records ===");

        var agent = new StreamingAgent(ModelFactory.createOpenAiStreamingFromEnv(null),
                null, new ToolExecutor(), null, null, null);
        agent.setStructuredOutputModel(Customer.class);
        agent.setToolRegistry(new ToolRegistry());

        var result = agent.execute(
            "Extrahiere den Kunden: Max Mustermann, 25 Jahre, max@test.de, " +
            "registriert seit dem 15.03.2024, wohnhaft Musterstr. 42, 12345 Berlin.");

        printResult(result);

        try {
            var c = MAPPER.readValue(result.structuredOutput(), Customer.class);
            System.out.println("  Geparst (typsicher über Record-Konstruktor):");
            System.out.println("    name:         " + c.name());
            System.out.println("    age:          " + c.age());
            System.out.println("    email:        " + c.email());
            System.out.println("    registeredAt: " + c.registeredAt());
            System.out.println("    address:      " + c.address().street()
                + ", " + c.address().zip() + " " + c.address().city());
            System.out.println("  -> Customer.address ist ein eigenes Record");
        } catch (Exception e) {
            System.out.println("  Parse-Fehler: " + e.getMessage());
        }
        System.out.println();
    }

    // --- Dynamic Mode (Customer) ---

    static void demoDynamicSchema() {
        System.out.println("=== DYNAMIC Mode: JSON Schema (gleiche Struktur) ===");

        var agent = AgentConfig.builder()
            .structuredOutputSchema(CUSTOMER_SCHEMA)
            .build()
            .createAgent(ModelFactory.createOpenAiFromEnv());

        var result = agent.execute(
            "Extrahiere den Kunden: Erika Musterfrau, 32, erika@test.de, " +
            "registriert am 2024-07-01, Hauptstr. 7, 89073 Ulm.");

        printResult(result);

        try {
            var json = MAPPER.readTree(result.structuredOutput());
            System.out.println("  JsonNode-Zugriff (kein Record nötig):");
            System.out.println("    name:         " + json.get("name").asText());
            System.out.println("    age:          " + json.get("age").asInt());
            System.out.println("    email:        " + json.get("email").asText());
            System.out.println("    registeredAt: " + json.get("registeredAt").asText());
            System.out.println("    address:      " + json.get("address").get("street").asText()
                + ", " + json.get("address").get("zip").asText()
                + " " + json.get("address").get("city").asText());
            System.out.println("  -> Flexibel: Schema kann zur Laufzeit kommen");
        } catch (Exception e) {
            System.out.println("  Parse-Fehler: " + e.getMessage());
        }
        System.out.println();
    }

    // --- Dynamic Mode (Shopping Cart) ---

    static void demoShoppingCart() {
        System.out.println("=== DYNAMIC Mode: Shopping Cart mit Array ===");

        var agent = AgentConfig.builder()
            .structuredOutputSchema(SHOPPING_CART_SCHEMA)
            .build()
            .createAgent(ModelFactory.createOpenAiFromEnv());

        var result = agent.execute(
            "Erstelle einen Warenkorb: Kunde ist Max Mustermann (max@test.de). " +
            "Enthält: 2x Laptop (je 899.99), 1x Maus (je 24.95), 3x USB-C Kabel (je 9.99).");

        printResult(result);

        try {
            var json = MAPPER.readTree(result.structuredOutput());
            System.out.println("  Kunde: " + json.get("customer").get("name").asText());
            System.out.println("  Artikel:");
            for (var item : json.get("items")) {
                System.out.println("    - " + item.get("quantity").asInt() + "x "
                    + item.get("product").asText()
                    + " \u00e0 " + item.get("price").asText());
            }
            System.out.println("  Gesamt: " + json.get("total").asText());
            System.out.println("  -> Dynamisch: Array-L\u00e4nge variiert mit Eingabe");
        } catch (Exception e) {
            System.out.println("  Parse-Fehler: " + e.getMessage());
        }
        System.out.println();
    }

    // --- Output ---

    static void printResult(AgentResult result) {
        System.out.println("  Antwort: " + truncate(result.finalAnswer(), 120));
        System.out.println("  Structured: " + truncate(result.structuredOutput(), 200));
        System.out.println("  StopReason: " + result.stopReason());
        System.out.println("  Tokens: " + result.metrics().inputTokens()
            + " in / " + result.metrics().outputTokens() + " out, "
            + result.metrics().durationMs() + " ms");
    }

    static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
