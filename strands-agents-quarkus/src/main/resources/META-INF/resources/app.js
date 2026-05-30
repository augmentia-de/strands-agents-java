var state = {
  sessionId: crypto.randomUUID(),
  currentTools: [],
  currentSkills: [],
  currentMcpTools: [],
  fetchedMcpTools: [],
  isInitialized: false,
  streamingMsg: null,
  messages: [],
  toolCallData: {}
};

document.getElementById('session-id').textContent = state.sessionId.slice(0, 8) + '...';

/* ── Utility ── */
function escapeHtml(s) {
  var d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

function renderMarkdown(s) {
  return escapeHtml(s)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`(.+?)`/g, '<code style="background:#0f3460;padding:2px 6px;border-radius:4px;font-size:13px">$1</code>')
    .replace(/\n/g, '<br>');
}

function toolIcon(name) {
  var n = name.toLowerCase();
  if (n.includes('calc') || n.includes('math')) return '🔢';
  if (n.includes('web') || n.includes('fetch') || n.includes('search')) return '🌐';
  if (n.includes('file') || n.includes('read') || n.includes('write') || n.includes('edit')) return '📁';
  if (n.includes('bash') || n.includes('shell') || n.includes('exec')) return '🖥️';
  if (n.includes('mcp')) return '🔌';
  if (n.includes('time') || n.includes('date')) return '⏰';
  return '🔧';
}

function now() { return new Date(); }
function timeStr(d) { return d.toTimeString().slice(0, 5); }
function isSameGroup(a, b) {
  if (!a || !b) return false;
  return a.role === b.role && (now() - a._time < 120000);
}

/* ── Render ── */
function scrollToBottom() {
  var el = document.getElementById('messages');
  el.scrollTop = el.scrollHeight;
}

function addMessage(role, content, meta, opts) {
  opts = opts || {};
  var msgs = document.getElementById('messages');
  var lastMsg = state.messages[state.messages.length - 1];

  var entry = { role: role, content: content, meta: meta || '', _time: now(), _opts: opts };
  state.messages.push(entry);

  if (role === 'system' || role === 'tool-call') {
    var div = createBubble(entry, false);
    msgs.appendChild(div);
    scrollToBottom();
    return div;
  }

  if (isSameGroup(lastMsg, entry)) {
    var lastRow = msgs.lastElementChild;
    if (lastRow && lastRow.classList.contains('msg-group')) {
      var bubble = createBubble(entry, true);
      lastRow.appendChild(bubble);
      scrollToBottom();
      return bubble;
    }
  }

  var row = document.createElement('div');
  row.className = 'msg-row ' + role;

  var avatar = document.createElement('div');
  avatar.className = 'msg-avatar ' + role;
  avatar.textContent = role === 'user' ? '👤' : '🤖';

  var group = document.createElement('div');
  group.className = 'msg-group';
  var bubble = createBubble(entry, true);
  group.appendChild(bubble);

  row.appendChild(avatar);
  row.appendChild(group);
  msgs.appendChild(row);
  scrollToBottom();
  return bubble;
}

function createBubble(entry, showMeta) {
  var div = document.createElement('div');
  if (entry.role === 'tool-call') {
    div.className = 'message tool-call' + (entry._opts.error ? ' error' : '') + (entry._opts.open ? ' open' : '');
    div.innerHTML =
      '<span class="tool-icon">' + toolIcon(entry._opts.toolName) + '</span>' +
      '<span class="tool-name">' + escapeHtml(entry._opts.toolName || 'Tool') + '</span>' +
      '<span class="tool-duration">' + (entry._opts.duration || '') + '</span>' +
      '<div class="tool-detail">' + escapeHtml(entry.content) + '</div>';
    div.addEventListener('click', function() { this.classList.toggle('open'); });
    return div;
  }
  div.className = 'message ' + entry.role;
  div.innerHTML = '<div class="msg-content">' +
    (entry._opts.html ? entry.content : renderMarkdown(entry.content)) +
    '</div>';
  if (showMeta && (entry.meta || entry._opts.status)) {
    var metaDiv = document.createElement('div');
    metaDiv.className = 'msg-meta';
    if (entry._opts.status) {
      var status = document.createElement('span');
      status.className = 'status-icon';
      status.textContent = entry._opts.status;
      metaDiv.appendChild(status);
    }
    if (entry.meta) {
      var time = document.createElement('span');
      time.textContent = entry.meta;
      metaDiv.appendChild(time);
    }
    div.appendChild(metaDiv);
  }
  return div;
}

function addTyping() {
  var msgs = document.getElementById('messages');
  var el = document.createElement('div');
  el.className = 'typing-indicator';
  el.id = 'typing-indicator';
  el.innerHTML = '<div class="typing-dot"></div><div class="typing-dot"></div><div class="typing-dot"></div>';
  msgs.appendChild(el);
  scrollToBottom();
}

function removeTyping() {
  var el = document.getElementById('typing-indicator');
  if (el) el.remove();
}

function updateStreamingMsg(text) {
  if (!state.streamingMsg) return;
  var content = state.streamingMsg.querySelector('.msg-content');
  if (content) content.innerHTML = renderMarkdown(text);
}

function addToolCallChip(name, args, result, duration, success) {
  var argsStr = typeof args === 'object' ? JSON.stringify(args, null, 2) : String(args);
  var content = 'Args:\n' + argsStr + '\n\nResult:\n' + (String(result).slice(0, 500));
  addMessage('tool-call', content, '', {
    toolName: name,
    duration: (duration / 1000).toFixed(1) + 's',
    error: !success,
    open: !success
  });
}

function updateMemoryBar(toolNames, memoryCount) {
  var bar = document.getElementById('memory-bar');
  var parts = [];
  if (toolNames && toolNames.length > 0) {
    parts.push('<span class="mem-chip"><span class="mem-icon">🔧</span>' + escapeHtml(toolNames.join(', ')) + '</span>');
  }
  if (memoryCount > 0) {
    parts.push('<span class="mem-chip"><span class="mem-icon">🧠</span>' + memoryCount + ' Erinnerungen</span>');
  }
  bar.innerHTML = parts.join('');
}

/* ── Drawer ── */
function toggleDrawer() {
  var s = document.getElementById('sidebar');
  var o = document.getElementById('drawer-overlay');
  var isOpen = s.classList.toggle('open');
  o.classList.toggle('visible', isOpen);
}

function closeDrawer() {
  document.getElementById('sidebar').classList.remove('open');
  document.getElementById('drawer-overlay').classList.remove('visible');
}

/* ── Auto-Resize Textarea ── */
function autoResize() {
  var ta = document.getElementById('prompt-input');
  ta.style.height = 'auto';
  ta.style.height = Math.min(ta.scrollHeight, 150) + 'px';
}

/* ── API Key ── */
function renderApiKeyState(stored, active) {
  document.getElementById('apikey-setup').style.display = (!stored && !active) ? 'flex' : 'none';
  document.getElementById('apikey-activate').style.display = (stored && !active) ? 'flex' : 'none';
  document.getElementById('apikey-active').style.display = active ? 'flex' : 'none';
}

function showApiKeyError(msg) {
  document.getElementById('apikey-error').textContent = msg;
}

async function checkApiKeyStatus() {
  try {
    var resp = await fetch('/api/admin/status');
    if (!resp.ok) return;
    var data = await resp.json();
    renderApiKeyState(data.stored, data.active);
  } catch (err) {}
}

async function setupApiKey() {
  var apiKey = document.getElementById('setup-api-key').value.trim();
  var password = document.getElementById('setup-password').value.trim();
  if (!apiKey || !password) { showApiKeyError('API-Key und Passwort erforderlich'); return; }
  showApiKeyError('');
  try {
    var resp = await fetch('/api/admin/setup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ apiKey: apiKey, password: password })
    });
    var data = await resp.json();
    if (!resp.ok) { showApiKeyError(data.error || 'Fehler'); return; }
    document.getElementById('setup-api-key').value = '';
    document.getElementById('setup-password').value = '';
    renderApiKeyState(true, false);
  } catch (err) {
    showApiKeyError('Fehler: ' + err.message);
  }
}

async function activate() {
  var password = document.getElementById('activate-password').value.trim();
  if (!password) { showApiKeyError('Passwort erforderlich'); return; }
  showApiKeyError('');
  try {
    var resp = await fetch('/api/admin/activate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: password })
    });
    var data = await resp.json();
    if (!resp.ok) { showApiKeyError(data.error || 'Fehler'); return; }
    document.getElementById('activate-password').value = '';
    renderApiKeyState(true, true);
  } catch (err) {
    showApiKeyError('Fehler: ' + err.message);
  }
}

async function deactivate() {
  showApiKeyError('');
  try {
    var resp = await fetch('/api/admin/deactivate', { method: 'POST' });
    if (!resp.ok) return;
    renderApiKeyState(true, false);
  } catch (err) {
    showApiKeyError('Fehler: ' + err.message);
  }
}

/* ── Selection ── */
function getSelected(name) {
  return Array.from(document.querySelectorAll('input[name="' + name + '"]:checked')).map(function(cb) { return cb.value; });
}

function updateTools() { state.currentTools = getSelected('tool'); }
function updateSkills() { state.currentSkills = getSelected('skill'); }
function updateMcpTools() { state.currentMcpTools = getSelected('mcp-tool'); }

function setInitialized(val) {
  state.isInitialized = val;
  document.getElementById('init-btn').disabled = val;
  document.getElementById('send-btn').disabled = !val;
  document.getElementById('prompt-input').disabled = !val;
  document.getElementById('init-status').textContent = val ? '\u2705 Agent bereit' : '\u23f3 Nicht initialisiert';
  document.getElementById('init-status').className = val ? 'status-ready' : 'status-pending';
}

/* ── MCP ── */
function renderMcpTools(tools) {
  var list = document.getElementById('mcp-tools-list');
  if (!tools || tools.length === 0) {
    list.innerHTML = '<p class="empty">Keine MCP Tools gefunden</p>';
    return;
  }
  list.innerHTML = '';
  for (var i = 0; i < tools.length; i++) {
    var t = tools[i];
    var label = document.createElement('label');
    label.className = 'chip-select chip-mcp';
    var cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.name = 'mcp-tool';
    cb.value = t.name;
    cb.addEventListener('change', updateMcpTools);
    var span = document.createElement('span');
    span.textContent = t.name + (t.description ? ': ' + t.description.substring(0, 60) : '');
    span.title = t.name + ': ' + t.description;
    label.appendChild(cb);
    label.appendChild(span);
    list.appendChild(label);
  }
}

async function fetchMcpTools() {
  var url = document.getElementById('mcp-url').value.trim();
  if (!url) return;
  var status = document.getElementById('mcp-status');
  status.textContent = '\u23f3 Entdecke MCP Tools...';
  status.className = 'status-pending';
  try {
    var resp = await fetch('/api/mcp/discover', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: url })
    });
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    var tools = await resp.json();
    state.fetchedMcpTools = tools;
    renderMcpTools(tools);
    state.currentMcpTools = [];
    status.textContent = '\u2705 ' + tools.length + ' MCP Tools entdeckt';
    status.className = 'status-ready';
    document.getElementById('mcp-count').textContent = tools.length + ' Tools verf\u00fcgbar, keine ausgew\u00e4hlt';
  } catch (err) {
    status.textContent = '\u274c Fehler: ' + err.message;
    status.className = 'status-error';
    state.fetchedMcpTools = [];
    renderMcpTools([]);
  }
}

/* ── Sessions ── */
async function loadSessions() {
  try {
    var resp = await fetch('/api/sessions');
    if (!resp.ok) return;
    var sessions = await resp.json();
    renderSessions(sessions);
  } catch (err) {}
}

function renderSessions(sessions) {
  var list = document.getElementById('session-list');
  if (!sessions || sessions.length === 0) {
    list.innerHTML = '<p class="empty">Keine Sessions</p>';
    return;
  }
  list.innerHTML = '';
  for (var i = 0; i < sessions.length; i++) {
    var s = sessions[i];
    var item = document.createElement('div');
    item.className = 'session-list-item' + (s.sessionId === state.sessionId ? ' active' : '');
    var title = (s.metadata && s.metadata.lastPrompt) ? s.metadata.lastPrompt : s.sessionId.slice(0, 12) + '...';
    item.innerHTML =
      '<div><button class="sess-delete" data-sid="' + escapeHtml(s.sessionId) + '">✕</button><span class="sess-title">' + escapeHtml(title) + '</span></div>' +
      '<span class="sess-meta">' + (s.createdAt ? new Date(s.createdAt).toLocaleString() : '') + ' · ' + (s.messageCount || 0) + ' Nachrichten</span>';
    item.addEventListener('click', function(e) {
      if (e.target.classList.contains('sess-delete')) return;
      switchSession(s.sessionId);
    });
    var delBtn = item.querySelector('.sess-delete');
    delBtn.addEventListener('click', function(e) {
      e.stopPropagation();
      deleteSession(s.sessionId);
    });
    list.appendChild(item);
  }
}

async function switchSession(sid) {
  state.sessionId = sid;
  document.getElementById('session-id').textContent = sid.slice(0, 8) + '...';
  closeDrawer();
  addMessage('system', 'Zu Session ' + sid.slice(0, 8) + '... gewechselt');
  setInitialized(false);
  loadSessions();
}

async function deleteSession(sid) {
  try {
    await fetch('/api/sessions/' + encodeURIComponent(sid), { method: 'DELETE' });
    if (state.sessionId === sid) {
      newSession();
    }
    loadSessions();
  } catch (err) {}
}

/* ── Agent Init ── */
function newSession() {
  fetch('/api/agent/release', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId: state.sessionId })
  }).catch(function() {});
  state.sessionId = crypto.randomUUID();
  state.messages = [];
  document.getElementById('session-id').textContent = state.sessionId.slice(0, 8) + '...';
  document.getElementById('messages').innerHTML =
    '<div class="message system"><div class="msg-content">Neue Session gestartet. W\u00e4hle Tools/Skills und klicke "Agent initialisieren".</div></div>';
  updateMemoryBar([], 0);
  setInitialized(false);
  loadSessions();
}

async function initAgent() {
  var btn = document.getElementById('init-btn');
  btn.disabled = true;
  document.getElementById('init-status').textContent = '\u23f3 Initialisiere...';
  addMessage('system', 'Initialisiere Agent mit aktuellen Tools/Skills/MCP-Tools...');

  try {
    var resp = await fetch('/api/agent/init', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tools: state.currentTools.length > 0 ? state.currentTools : undefined,
        skills: state.currentSkills.length > 0 ? state.currentSkills : undefined,
        mcpUrl: document.getElementById('mcp-url').value.trim() || undefined,
        mcpTools: state.currentMcpTools.length > 0 ? state.currentMcpTools : undefined,
        skillSearchEnabled: document.getElementById('skill-search-enabled').checked,
        mcpIngestEnabled: document.getElementById('mcp-ingest-enabled').checked,
        capabilityDirs: document.getElementById('cap-dirs').value.trim() || undefined,
        capabilityMcp: document.getElementById('cap-mcp').value.trim() || undefined
      })
    });

    var data = await resp.json();
    if (data.error) {
      addMessage('system', 'Fehler bei Initialisierung: ' + data.error);
      btn.disabled = false;
      return;
    }

    state.sessionId = data.sessionId;
    document.getElementById('session-id').textContent = state.sessionId.slice(0, 8) + '...';
    setInitialized(true);
    var toolCount = state.currentTools.length + state.currentMcpTools.length;
    addMessage('system', '\u2705 Agent initialisiert (' + toolCount + ' Tools)');

    updateMemoryBar(
      state.currentTools.concat(state.currentMcpTools),
      data.memoryCount || 0
    );

    // Personalisierte Begrüßung
    var greeting = 'Hey! Ich bin dein Strands Agent.';
    if (toolCount > 0) {
      greeting += ' Ich habe Zugriff auf ' + toolCount + ' Tool' + (toolCount > 1 ? 's' : '') + '.';
    }
    greeting += ' Was kann ich für dich tun?';
    addMessage('agent', greeting);

    document.getElementById('prompt-input').focus();
    loadSessions();
  } catch (err) {
    addMessage('system', 'Fehler: ' + err.message);
    btn.disabled = false;
  }
}

/* ── Send Message (Streaming) ── */
async function sendMessage() {
  var input = document.getElementById('prompt-input');
  var btn = document.getElementById('send-btn');
  var prompt = input.value.trim();
  if (!prompt) return;

  input.value = '';
  input.style.height = 'auto';
  btn.disabled = true;

  addMessage('user', prompt);
  addTyping();

  try {
    var resp = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt: prompt,
        sessionId: state.sessionId,
        tools: state.currentTools.length > 0 ? state.currentTools : undefined,
        skills: state.currentSkills.length > 0 ? state.currentSkills : undefined
      })
    });

    if (!resp.ok) throw new Error('HTTP ' + resp.status);

    removeTyping();

    // Erstelle leere Agent-Nachricht für Streaming
    var agentMsg = addMessage('agent', '', '', { html: true, status: '✓' });
    state.streamingMsg = agentMsg;
    var fullText = '';
    var toolCalls = [];

    var reader = resp.body.getReader();
    var decoder = new TextDecoder();
    var buffer = '';

    while (true) {
      var result = await reader.read();
      if (result.done) break;
      buffer += decoder.decode(result.value, { stream: true });
      var lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (var i = 0; i < lines.length; i++) {
        var line = lines[i];
        if (line.startsWith('data:')) {
          var data = line.slice(5);
          if (data === '[DONE]') continue;
          try {
            var parsed = JSON.parse(data);
            if (parsed.token) {
              fullText += parsed.token;
              updateStreamingMsg(fullText);
            } else if (parsed.result) {
              // Result-Objekt mit Metadaten
              var r = parsed.result;
              if (r.answer && r.answer !== fullText) {
                fullText = r.answer;
                updateStreamingMsg(fullText);
              }
              if (r.toolCalls && r.toolCalls.length > 0) {
                for (var j = 0; j < r.toolCalls.length; j++) {
                  var tc = r.toolCalls[j];
                  addToolCallChip(tc.name, tc.arguments, tc.result, tc.durationMs, tc.success !== false);
                }
              }
              // Metadaten aktualisieren
              var metaParts = [];
              if (r.durationMs) metaParts.push((r.durationMs / 1000).toFixed(1) + 's');
              if (r.inputTokens > 0) metaParts.push(r.inputTokens + ' in / ' + r.outputTokens + ' out');
              if (r.toolCallsCount > 0) metaParts.push(r.toolCallsCount + ' Tool-Calls');
              if (metaParts.length > 0) {
                var metaEl = agentMsg.querySelector('.msg-meta');
                if (!metaEl) {
                  metaEl = document.createElement('div');
                  metaEl.className = 'msg-meta';
                  agentMsg.appendChild(metaEl);
                }
                metaEl.textContent = metaParts.join(' · ');
              }
              if (r.memoryUsed) {
                updateMemoryBar(
                  state.currentTools.concat(state.currentMcpTools),
                  (r.memorySources || []).length
                );
              }
            }
          } catch (e) {}
        }
      }
    }

    state.streamingMsg = null;
  } catch (err) {
    removeTyping();
    if (state.streamingMsg) {
      state.streamingMsg.querySelector('.msg-content').textContent = 'Fehler: ' + err.message;
      state.streamingMsg = null;
    } else {
      addMessage('agent', 'Fehler: ' + err.message);
    }
  }

  btn.disabled = false;
  input.focus();
}

/* ── Service Worker ── */
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(function() {});
}

/* ── Init ── */
window.onload = function() {
  state.currentTools = getSelected('tool');
  state.currentSkills = getSelected('skill');
  setInitialized(false);

  checkApiKeyStatus();
  loadSessions();

  document.getElementById('setup-btn').addEventListener('click', setupApiKey);
  document.getElementById('activate-btn').addEventListener('click', activate);
  document.getElementById('deactivate-btn').addEventListener('click', deactivate);
  document.getElementById('init-btn').addEventListener('click', initAgent);
  document.getElementById('send-btn').addEventListener('click', sendMessage);
  document.getElementById('new-session-btn').addEventListener('click', newSession);
  document.getElementById('mcp-refresh-btn').addEventListener('click', fetchMcpTools);
  document.getElementById('menu-btn').addEventListener('click', toggleDrawer);
  document.getElementById('drawer-overlay').addEventListener('click', closeDrawer);

  // Auto-Resize + Shift+Enter
  var input = document.getElementById('prompt-input');
  input.addEventListener('input', autoResize);
  input.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (state.isInitialized) sendMessage();
    }
  });

  document.querySelectorAll('[data-toggle]').forEach(function(el) {
    el.addEventListener('click', function() {
      var id = this.getAttribute('data-toggle');
      var list = document.getElementById(id + '-list');
      list.style.display = list.style.display === 'none' ? 'flex' : 'none';
    });
  });

  document.querySelectorAll('input[name="tool"]').forEach(function(cb) {
    cb.addEventListener('change', updateTools);
  });
  document.querySelectorAll('input[name="skill"]').forEach(function(cb) {
    cb.addEventListener('change', updateSkills);
  });

  fetchMcpTools();
};
