package de.augmentia.strandsagents.examples.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import java.util.concurrent.ThreadLocalRandom;

public class UnreliableWeatherTool {

    @Tool("Gets the current weather for a city. May timeout or return errors.")
    public String getCurrentWeather(@P("city") String city) {
        var r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.40) {
            return "Sunny, 22°C";
        }
        if (r < 0.60) {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            }
            return "";
        }
        if (r < 0.80) {
            return "{}";
        }
        throw new RuntimeException("API rate limit exceeded");
    }
}
