#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---- Port (default 8080) ----
PORT="${SPRING_PORT:-8081}"

# ---- API-Keys laden (OPENAI_API_KEY, OPENAI_BASE_URL, OPENAI_MODEL) ----
if [ -f "$SCRIPT_DIR/scripts/set_keys.sh" ]; then
    source "$SCRIPT_DIR/scripts/set_keys.sh"
fi

usage() {
    cat <<EOF
Usage: ./start-spring.sh [mode]

Modes:
  dev           Spring Boot Dev Mode (Hot Reload) – Standard
  dev-ui        Spring Boot + React Dev Server (Vite)
  prod          Produktions-Modus (build + jar)
  build-only    Nur bauen, nicht starten
  help          Diese Hilfe

Umgebungsvariablen:
  SPRING_PORT  Port (default: 8081)

EOF
}

cmd_dev_ui() {
    echo ">>> Spring Boot + React Dev Server starten"
    echo "    Model:       ${OPENAI_MODEL:-gpt-4o-mini}"
    echo "    Base URL:    ${OPENAI_BASE_URL:-https://api.openai.com/v1}"
    echo "    Port:        $PORT"
    echo ""

    # Spring Boot im Hintergrund starten
    cd "$SCRIPT_DIR"
    mvn spring-boot:run -pl strands-agents-spring \
        -Dspring-boot.run.arguments="--server.port=$PORT" &
    SPRING_PID=$!

    cleanup() {
        echo ""
        echo ">>> Spring Boot beenden (PID $SPRING_PID)"
        kill "$SPRING_PID" 2>/dev/null || true
        wait "$SPRING_PID" 2>/dev/null || true
    }
    trap cleanup EXIT INT TERM

    # React Dev Server im Vordergrund starten
    cd "$SCRIPT_DIR/strands-agents-spring/ui"
    npx vite
}

cmd_dev() {
    echo ">>> Spring Boot Dev Mode starten"
    echo "    Model:       ${OPENAI_MODEL:-gpt-4o-mini}"
    echo "    Base URL:    ${OPENAI_BASE_URL:-https://api.openai.com/v1}"
    echo "    Port:        $PORT"
    echo ""
    cd "$SCRIPT_DIR"
    mvn spring-boot:run -pl strands-agents-spring \
        -Dspring-boot.run.arguments="--server.port=$PORT"
}

cmd_prod() {
    echo ">>> Produktions-Build + Start"
    echo "    Model:       ${OPENAI_MODEL:-gpt-4o-mini}"
    echo "    Port:        $PORT"
    echo ""
    cd "$SCRIPT_DIR"
    mvn clean package -DskipTests -pl strands-agents-spring -am
    java -jar strands-agents-spring/target/strands-agents-spring-*.jar \
        --server.port="$PORT"
}

cmd_build_only() {
    echo ">>> Nur bauen"
    cd "$SCRIPT_DIR"
    mvn clean package -DskipTests -pl strands-agents-spring -am
    echo ""
    echo "✅ Jar liegt in strands-agents-spring/target/"
}

case "${1:-dev}" in
    dev)        cmd_dev ;;
    dev-ui)     cmd_dev_ui ;;
    prod)       cmd_prod ;;
    build-only) cmd_build_only ;;
    help|--help|-h) usage ;;
    *)
        echo "Unbekannter Modus: $1"
        usage
        exit 1
        ;;
esac
