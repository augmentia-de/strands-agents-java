import { useRef, useState, useCallback, useEffect } from "react";
import ReactMarkdown from "react-markdown";

type Phase = "IDLE" | "PLANNING" | "EXECUTING" | "REVIEWING" | "REVISING" | "COMPLETED" | "FAILED";

type ToolCall = { name: string; arguments: string };
type ToolResult = { name: string; result: string; error: boolean };

type SseEvent =
  | { event: "started"; prompt: string }
  | { event: "token"; token: string }
  | { event: "phase"; phase: Phase; goal: string }
  | { event: "tool_start"; name: string; arguments: string }
  | { event: "tool_end"; name: string; result: string; error: boolean }
  | { event: "finished"; answer: string }
  | { event: "done"; answer: string; sessionId: string }
  | { event: "error"; error: string };

interface Message {
  role: "user" | "assistant" | "system";
  content: string;
}

class SseParser {
  private buffer = "";
  private eventType = "";
  private dataLines: string[] = [];

  feed(chunk: string): SseEvent[] {
    this.buffer += chunk;
    const events: SseEvent[] = [];

    while (true) {
      const nl = this.buffer.indexOf("\n");
      if (nl === -1) break;

      const line = this.buffer.slice(0, nl);
      this.buffer = this.buffer.slice(nl + 1);

      if (line === "") {
        // empty line = event delimiter
        if (this.dataLines.length > 0 && this.eventType) {
          const parsed = this.dispatch();
          if (parsed) events.push(parsed);
        }
        this.eventType = "";
        this.dataLines = [];
        continue;
      }

      const colon = line.indexOf(":");
      if (colon === -1) continue;
      const name = line.slice(0, colon);
      let data = line.slice(colon + 1);
      if (data.startsWith(" ")) data = data.slice(1);

      if (name === "event") {
        this.eventType = data;
      } else if (name === "data") {
        this.dataLines.push(data);
      }
    }

    return events;
  }

  private dispatch(): SseEvent | null {
    const dataStr = this.dataLines.join("\n");
    const event = this.eventType;

    if (event === "token") {
      // token data is a plain string wrapped in JSON quotes, e.g. "Hello"
      try {
        const parsed = JSON.parse(dataStr);
        if (typeof parsed === "string") {
          return { event: "token", token: parsed };
        }
        // fallback: use raw
        return { event: "token", token: dataStr };
      } catch {
        return { event: "token", token: dataStr };
      }
    }

    try {
      const json = JSON.parse(dataStr);
      switch (event) {
        case "started":
          return { event: "started", prompt: json.prompt };
        case "phase":
          return { event: "phase", phase: json.phase, goal: json.goal };
        case "tool_start":
          return { event: "tool_start", name: json.name, arguments: json.arguments };
        case "tool_end":
          return { event: "tool_end", name: json.name, result: json.result, error: json.error };
        case "finished":
          return { event: "finished", answer: json.answer };
        case "done":
          return { event: "done", answer: json.answer, sessionId: json.sessionId };
        case "error":
          return { event: "error", error: json.error };
        default:
          return null;
      }
    } catch {
      return null;
    }
  }
}

interface PhaseBadgeProps {
  phase: Phase;
}

function PhaseBadge({ phase }: PhaseBadgeProps) {
  const colors: Record<Phase, string> = {
    IDLE: "gray",
    PLANNING: "blue",
    EXECUTING: "orange",
    REVIEWING: "purple",
    REVISING: "red",
    COMPLETED: "green",
    FAILED: "red",
  };
  return (
    <span
      style={{
        display: "inline-block",
        padding: "2px 8px",
        borderRadius: 4,
        fontSize: 12,
        fontWeight: 600,
        color: "#fff",
        background: colors[phase],
        marginBottom: 4,
      }}
    >
      {phase}
    </span>
  );
}

interface ToolCallCardProps {
  tool: ToolCall;
  result?: ToolResult;
}

function ToolCallCard({ tool, result }: ToolCallCardProps) {
  const [open, setOpen] = useState(false);
  return (
    <div
      style={{
        border: "1px solid #ccc",
        borderRadius: 8,
        padding: "6px 12px",
        margin: "4px 0",
        background: "#f9f9f9",
        fontSize: 13,
      }}
    >
      <div
        onClick={() => setOpen(!open)}
        style={{ cursor: "pointer", userSelect: "none" }}
      >
        {open ? "▼" : "▶"} <strong>{tool.name}</strong>
        {result ? " ✓" : " ..."}
      </div>
      {open && (
        <div style={{ marginTop: 4, whiteSpace: "pre-wrap", fontSize: 12 }}>
          <div style={{ color: "#666" }}>Arguments: {tool.arguments}</div>
          {result && (
            <div style={{ color: result.error ? "red" : "#333", marginTop: 4 }}>
              {result.result}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function getSessionId(): string {
  let id = localStorage.getItem("sessionId");
  if (!id) {
    id = crypto.randomUUID();
    localStorage.setItem("sessionId", id);
  }
  return id;
}

export default function ChatView() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [phase, setPhase] = useState<Phase>("IDLE");
  const [activeTool, setActiveTool] = useState<ToolCall | null>(null);
  const [activeToolResult, setActiveToolResult] = useState<ToolResult | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  const chatEndRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const send = useCallback(async () => {
    const prompt = input.trim();
    if (!prompt || streaming) return;
    setInput("");
    setMessages((prev) => [...prev, { role: "user", content: prompt }]);
    setStreaming(true);
    setPhase("PLANNING");
    setActiveTool(null);
    setActiveToolResult(null);

    const assistantMessage: Message = { role: "assistant", content: "" };
    setMessages((prev) => [...prev, assistantMessage]);

    const ctrl = new AbortController();
    abortRef.current = ctrl;

    try {
      const res = await fetch("/api/chat/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ prompt, sessionId: getSessionId() }),
        signal: ctrl.signal,
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      if (!res.body) throw new Error("No response body");

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      const parser = new SseParser();

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const chunk = decoder.decode(value, { stream: true });
        console.log("[SSE raw chunk]", chunk);

        const events = parser.feed(chunk);
        for (const ev of events) {
          console.log("[SSE event]", ev.event, ev);
          switch (ev.event) {
            case "token":
              setMessages((prev) => {
                const copy = [...prev];
                const last = copy[copy.length - 1];
                if (last?.role === "assistant") {
                  copy[copy.length - 1] = { ...last, content: last.content + ev.token };
                }
                return copy;
              });
              break;
            case "phase":
              setPhase(ev.phase);
              break;
            case "tool_start":
              setActiveTool({ name: ev.name, arguments: ev.arguments });
              setActiveToolResult(null);
              break;
            case "tool_end":
              setActiveToolResult({
                name: ev.name,
                result: ev.result,
                error: ev.error,
              });
              break;
            case "done":
              if (ev.sessionId) localStorage.setItem("sessionId", ev.sessionId);
              setPhase("COMPLETED");
              break;
            case "finished":
              setPhase("COMPLETED");
              break;
            case "error":
              setMessages((prev) => [
                ...prev,
                { role: "system", content: `Error: ${ev.error}` },
              ]);
              setPhase("FAILED");
              break;
          }
        }
      }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === "AbortError") return;
      const msg = err instanceof Error ? err.message : String(err);
      setMessages((prev) => [
        ...prev,
        { role: "system", content: `Error: ${msg}` },
      ]);
      setPhase("FAILED");
    } finally {
      setStreaming(false);
      abortRef.current = null;
    }
  }, [input, streaming]);

  const cancel = () => {
    abortRef.current?.abort();
    setStreaming(false);
    setPhase("IDLE");
  };

  return (
    <div style={{ maxWidth: 800, margin: "0 auto", padding: 16, fontFamily: "sans-serif" }}>
      <h1 style={{ fontSize: 20, marginBottom: 8 }}>Strands Agents — Spring Chat</h1>

      <PhaseBadge phase={phase} />

      <div
        style={{
          border: "1px solid #ddd",
          borderRadius: 8,
          padding: 12,
          minHeight: 400,
          maxHeight: 600,
          overflowY: "auto",
          margin: "12px 0",
          background: "#fff",
        }}
      >
        {messages.map((m, i) => (
          <div
            key={i}
            style={{
              marginBottom: 12,
              textAlign: m.role === "user" ? "right" : "left",
            }}
          >
            <div
              style={{
                display: "inline-block",
                maxWidth: "80%",
                padding: "8px 14px",
                borderRadius: 12,
                background: m.role === "user" ? "#007bff" : m.role === "system" ? "#f8d7da" : "#f1f1f1",
                color: m.role === "user" ? "#fff" : "#000",
                fontSize: 14,
                lineHeight: 1.5,
                textAlign: "left",
                whiteSpace: "pre-wrap",
              }}
            >
              {m.role === "assistant" ? (
                <ReactMarkdown>{m.content}</ReactMarkdown>
              ) : (
                m.content
              )}
            </div>
          </div>
        ))}

        {activeTool && (
          <ToolCallCard tool={activeTool} result={activeToolResult ?? undefined} />
        )}

        {streaming && !activeTool && (
          <div style={{ color: "#999", fontSize: 13, fontStyle: "italic" }}>
            {phase === "PLANNING" ? "Thinking..." : "Generating..."}
          </div>
        )}
        <div ref={chatEndRef} />
      </div>

      <div style={{ display: "flex", gap: 8 }}>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && send()}
          placeholder="Ask something..."
          disabled={streaming}
          style={{
            flex: 1,
            padding: "10px 14px",
            borderRadius: 8,
            border: "1px solid #ccc",
            fontSize: 14,
          }}
        />
        <button
          onClick={streaming ? cancel : send}
          style={{
            padding: "10px 20px",
            borderRadius: 8,
            border: "none",
            background: streaming ? "#dc3545" : "#007bff",
            color: "#fff",
            fontSize: 14,
            cursor: "pointer",
          }}
        >
          {streaming ? "Cancel" : "Send"}
        </button>
      </div>
    </div>
  );
}
