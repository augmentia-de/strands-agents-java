package de.augmentia.strandsagents.tools.builtin;

import dev.langchain4j.agent.tool.Tool;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

/**
 * Tool that provides the agent with the current date, time, and timezone information.
 */
public class TimeTool {

    @Tool("Returns the current date, time, and timezone information.")
    public String getCurrentTime() {
        ZonedDateTime now = ZonedDateTime.now();
        return String.format("Current Date and Time: %s\nTimezone: %s",
            now.format(DateTimeFormatter.RFC_1123_DATE_TIME),
            ZoneId.systemDefault().toString());
    }

    @Tool("Returns the current date in YYYY-MM-DD format.")
    public String getCurrentDate() {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
