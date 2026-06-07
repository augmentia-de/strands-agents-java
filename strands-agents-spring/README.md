# Strands Agents Spring Module

This module demonstrates the use of the Strands Agents SDK within a Spring Boot application. It provides a simple chat application that showcases how to integrate and utilize the Strands Agents functionalities.

## Features

*   **Chat Application**: A basic chat interface to interact with the Strands Agents.
*   **Strands Agents SDK Integration**: Demonstrates how to initialize and use the SDK to manage agents and their capabilities.
*   **Spring Boot**: Built with Spring Boot for easy setup and deployment.
*   **React UI**: A simple React frontend for interacting with the chat application.

## Getting Started

### Prerequisites

*   Java 17 or higher
*   Maven
*   Node.js and npm (for the React UI)
*   A running Strands Agents platform (or a mock setup for local development)

### Building the Spring Boot Application

To build the Spring Boot application, navigate to the `strands-agents-spring` directory and run:

```bash
mvn clean install
```

### Running the Spring Boot Application

You can run the Spring Boot application using the following command:

```bash
mvn spring-boot:run
```

Alternatively, you can build a JAR file and run it:

```bash
java -jar target/strands-agents-spring-0.0.1-SNAPSHOT.jar
```

The Spring Boot application will typically be accessible at `http://localhost:8080`.

### Running the React UI

The React UI is located in the `ui` subdirectory.

1.  Navigate to the `ui` directory:
    ```bash
    cd ui
    ```
2.  Install the dependencies:
    ```bash
    npm install
    ```
3.  Start the React development server:
    ```bash
    npm run dev
    ```

The React application will typically be accessible at `http://localhost:5173` (or another port if 5173 is in use). It will proxy API requests to the running Spring Boot backend.

## Configuration

The application's configuration can be found in `src/main/resources/application.properties`. Key configurations include:

*   `strands.agents.api.url`: The URL of the Strands Agents API.
*   `strands.agents.api.key`: Your API key for authentication with the Strands Agents platform.

## Usage

Once both the Spring Boot backend and the React UI are running, you can access the chat interface through your web browser (usually `http://localhost:5173`). Enter your messages, and the application will use the integrated Strands Agents to process and respond to them.

## Contributing

Feel free to contribute to this example by submitting pull requests or opening issues.