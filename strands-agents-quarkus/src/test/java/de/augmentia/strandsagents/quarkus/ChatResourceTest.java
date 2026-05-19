package de.augmentia.strandsagents.quarkus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ChatResourceTest {

    @Test
    void chatEndpointReturnsAnswer() {
        given()
            .contentType("application/json")
            .body("{\"prompt\":\"Hallo Welt\"}")
            .when().post("/api/chat")
            .then()
            .statusCode(200)
            .body("$", hasKey("answer"))
            .body("$", hasKey("sessionId"))
            .body("stopReason", notNullValue());
    }

    @Test
    void chatRejectsEmptyPrompt() {
        given()
            .contentType("application/json")
            .body("{\"prompt\":\"\"}")
            .when().post("/api/chat")
            .then()
            .statusCode(200)
            .body("error", containsString("leer"));
    }

    @Test
    void toolsEndpointReturnsList() {
        given()
            .when().get("/api/tools")
            .then()
            .statusCode(200)
            .body("$", not(empty()));
    }

    @Test
    void skillsEndpointReturnsList() {
        given()
            .when().get("/api/skills")
            .then()
            .statusCode(200);
    }

    @Test
    void sessionsEndpointWorks() {
        given()
            .when().get("/api/sessions")
            .then()
            .statusCode(200)
            .body("$", notNullValue());
    }

    @Test
    void chatWithToolSelection() {
        given()
            .contentType("application/json")
            .body("{\"prompt\":\"Hallo\",\"tools\":[\"add\"]}")
            .when().post("/api/chat")
            .then()
            .statusCode(200)
            .body("$", hasKey("answer"));
    }
}
