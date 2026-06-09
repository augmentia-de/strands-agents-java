var state = {
  sessionId: generateUUID(),
  currentTools: [],
  currentSkills: [],
  currentMcpTools: [],
  mcpServers: [],
  mcpServersTools: {},
  mcpServersOpen: {},
  mcpCustomTools: [],
  mcpCustomUrls: {},
  isInitialized: false,
  streamingMsg: null,
  messages: [],
  toolCallData: {},
  abortController: null,
  isStreaming: false
};

document.getElementById('session-id').textContent = state.sessionId.slice(0, 8) + '...';


function generateUUID() {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // Fallback for non-secure HTTP contexts
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    var r = Math.random() * 16 | 0,
        v = c == 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
}

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
    var resp = await fetch('api/admin/status');
    if (!resp.ok) return false;
    var data = await resp.json();
    renderApiKeyState(data.stored, data.active);
    return true;
  } catch (err) { return false; }
}

async function setupApiKey() {
  var apiKey = document.getElementById('setup-api-key').value.trim();
  var password = document.getElementById('setup-password').value.trim();
  if (!apiKey || !password) { showApiKeyError('API Key and password required'); return; }
  showApiKeyError('');
  try {
    var resp = await fetch('api/admin/setup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ apiKey: apiKey, password: password })
    });
    var data = await resp.json();
    if (!resp.ok) { showApiKeyError(data.error || 'Error'); return; }
    document.getElementById('setup-api-key').value = '';
    document.getElementById('setup-password').value = '';
    renderApiKeyState(true, false);
  } catch (err) {
    showApiKeyError('Error: ' + err.message);
  }
}

async function activate() {
  var password = document.getElementById('activate-password').value.trim();
  if (!password) { showApiKeyError('Password required'); return; }
  showApiKeyError('');
  try {
    var resp = await fetch('api/admin/activate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ password: password })
    });
    var data = await resp.json();
    if (!resp.ok) { showApiKeyError(data.error || 'Error'); return; }
    document.getElementById('activate-password').value = '';
    renderApiKeyState(true, true);
  } catch (err) {
    showApiKeyError('Error: ' + err.message);
  }
}

async function deactivate() {
  showApiKeyError('');
  try {
    var resp = await fetch('api/admin/deactivate', { method: 'POST' });
    if (!resp.ok) return;
    renderApiKeyState(true, false);
  } catch (err) {
    showApiKeyError('Error: ' + err.message);
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
  document.getElementById('init-btn').disabled = val ? false : false;
  document.getElementById('init-btn').textContent = val ? '\u27F3 Re-initialize' : 'Initialize Agent';
  document.getElementById('send-btn').disabled = !val;
  document.getElementById('prompt-input').disabled = !val;
  document.getElementById('init-status').textContent = val ? '\u2705 Agent ready' : '\u23f3 Not initialized';
  document.getElementById('init-status').className = val ? 'status-ready' : 'status-pending';
}

/* ── MCP ── */
function mcpToolCheckboxHtml(name, description) {
  var desc = description ? ': ' + description.substring(0, 60) : '';
  return '<label class="chip-select chip-mcp">' +
    '<input type="checkbox" name="mcp-tool" value="' + escapeHtml(name) + '" onchange="updateMcpTools()">' +
    '<span title="' + escapeHtml(name) + ': ' + escapeHtml(description || '') + '">' + escapeHtml(name) + desc + '</span>' +
    '</label>';
}

async function fetchMcpServers() {
  try {
    var resp = await fetch('api/mcp/servers');
    if (!resp.ok) return;
    state.mcpServers = await resp.json();
    renderMcpServers();
  } catch (err) {
    console.error('Error loading MCP servers:', err);
  }
}

function renderMcpServers() {
  var container = document.getElementById('mcp-servers-list');
  container.innerHTML = '';
  for (var i = 0; i < state.mcpServers.length; i++) {
    var s = state.mcpServers[i];
    var isOpen = !!state.mcpServersOpen[s.name];
    var safeId = s.name.replace(/[^a-zA-Z0-9_-]/g, '_');
    var item = document.createElement('div');
    item.className = 'mcp-server-item';
    item.innerHTML =
      '<label class="mcp-server-label">' +
        '<input type="checkbox" data-server="' + escapeHtml(s.name) + '"' + (isOpen ? ' checked' : '') + '>' +
        '<span>' + escapeHtml(s.name) + '</span>' +
        '<span class="mcp-server-type">' + escapeHtml(s.type) + '</span>' +
      '</label>' +
      '<div class="mcp-server-tools' + (isOpen ? ' open' : '') + '" id="mcp-tools-' + safeId + '">' +
        (isOpen && state.mcpServersTools[s.name] ? renderToolChips(state.mcpServersTools[s.name]) : '<p class="empty">Select server to load tools</p>') +
      '</div>';
    var cb = item.querySelector('input[type="checkbox"]');
    cb.addEventListener('change', function(srv) {
      return function() { toggleMcpServer(srv, this.checked); };
    }(s.name));
    container.appendChild(item);
  }
}

function renderToolChips(tools) {
  if (!tools || tools.length === 0) return '<p class="empty">No tools found</p>';
  var html = '';
  for (var i = 0; i < tools.length; i++) {
    html += mcpToolCheckboxHtml(tools[i].name, tools[i].description);
  }
  return html;
}

async function toggleMcpServer(serverName, checked) {
  state.mcpServersOpen[serverName] = checked;
  if (!checked) {
    // Deselect all tools from this server
    state.mcpServersTools[serverName] = [];
    var toolsDiv = document.getElementById('mcp-tools-' + serverName.replace(/[^a-zA-Z0-9_-]/g, '_'));
    if (toolsDiv) { toolsDiv.classList.remove('open'); toolsDiv.innerHTML = '<p class="empty">Select server to load tools</p>'; }
    updateMcpTools();
    return;
  }
  var toolsDiv = document.getElementById('mcp-tools-' + serverName.replace(/[^a-zA-Z0-9_-]/g, '_'));
  if (toolsDiv) toolsDiv.innerHTML = '<p class="status-pending" style="font-size:12px">\u23f3 Loading tools...</p>';
  try {
    var resp = await fetch('api/mcp/discover', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ server: serverName })
    });
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    var tools = await resp.json();
    state.mcpServersTools[serverName] = tools;
    if (toolsDiv) {
      toolsDiv.classList.add('open');
      toolsDiv.innerHTML = renderToolChips(tools);
    }
  } catch (err) {
    if (toolsDiv) toolsDiv.innerHTML = '<p class="status-error" style="font-size:12px">\u274c ' + escapeHtml(err.message) + '</p>';
  }
}

async function connectMcpServer() {
  var input = document.getElementById('mcp-custom-url');
  var url = input.value.trim();
  if (!url) return;
  var statusEl = document.getElementById('mcp-status');
  statusEl.textContent = '\u23f3 Connecting...';
  statusEl.className = 'status-pending';
  try {
    var customName = 'custom_' + url.replace(/[^a-zA-Z0-9]/g, '_');
    var resp = await fetch('api/mcp/connect', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: url, name: customName })
    });
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    var tools = await resp.json();
    if (!tools || tools.length === 0) {
      statusEl.textContent = '\u274c No tools found';
      statusEl.className = 'status-error';
      return;
    }
    // Add custom server entry
    state.mcpCustomTools = tools;
    state.mcpServers.push({ name: customName, type: 'http (custom)' });
    state.mcpServersOpen[customName] = true;
    state.mcpServersTools[customName] = tools;
    state.mcpCustomUrls[customName] = url;
    renderMcpServers();
    // Re-open the newly added server's tools
    var toolsDiv = document.getElementById('mcp-tools-' + customName.replace(/[^a-zA-Z0-9_-]/g, '_'));
    if (toolsDiv) {
      toolsDiv.classList.add('open');
      toolsDiv.innerHTML = renderToolChips(tools);
    }
    statusEl.textContent = '\u2705 ' + tools.length + ' Tools von ' + url;
    statusEl.className = 'status-ready';
    input.value = '';
  } catch (err) {
    statusEl.textContent = '\u274c Error: ' + err.message;
    statusEl.className = 'status-error';
  }
}

function updateMcpTools() {
  state.currentMcpTools = Array.from(document.querySelectorAll('input[name="mcp-tool"]:checked')).map(function(cb) { return cb.value; });
}

/* ── Sessions ── */
async function loadSessions() {
  try {
    var resp = await fetch('api/sessions');
    if (!resp.ok) return;
    var sessions = await resp.json();
    renderSessions(sessions);
  } catch (err) {}
}

function renderSessions(sessions) {
  var list = document.getElementById('session-list');
  if (!sessions || sessions.length === 0) {
    list.innerHTML = '<p class="empty">No sessions</p>';
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
      '<span class="sess-meta">' + (s.createdAt ? new Date(s.createdAt).toLocaleString() : '') + ' · ' + (s.messageCount || 0) + ' messages</span>';
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
  addMessage('system', 'Switched to session ' + sid.slice(0, 8) + '...');
  setInitialized(false);
  loadSessions();
}

async function deleteSession(sid) {
  try {
    await fetch('api/sessions/' + encodeURIComponent(sid), { method: 'DELETE' });
    if (state.sessionId === sid) {
      newSession();
    }
    loadSessions();
  } catch (err) {}
}

/* ── Agent Init ── */
function newSession() {
  fetch('api/agent/release', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId: state.sessionId })
  }).catch(function() {});
  state.sessionId = generateUUID();
  state.messages = [];
  document.getElementById('session-id').textContent = state.sessionId.slice(0, 8) + '...';
  document.getElementById('messages').innerHTML =
    '<div class="message system"><div class="msg-content">New session started. Select Tools/Skills and click "Initialize Agent".</div></div>';
  updateMemoryBar([], 0);
  setInitialized(false);
  loadSessions();
}

function buildMcpServerList() {
  var mcpServers = [];
  for (var srvName in state.mcpServersOpen) {
    if (!state.mcpServersOpen[srvName]) continue;
    var srvAllTools = state.mcpServersTools[srvName] || [];
    var srvPrefixedNames = srvAllTools.map(function(t) { return t.name; });
    var selectedSrvTools = state.currentMcpTools.filter(function(t) {
      return srvPrefixedNames.indexOf(t) !== -1;
    });
    var url = state.mcpCustomUrls[srvName] || undefined;
    mcpServers.push({ serverName: srvName, tools: selectedSrvTools.length > 0 ? selectedSrvTools : undefined, url: url });
  }
  return mcpServers;
}

function buildInitBody() {
  var mcpServers = buildMcpServerList();
  return {
    tools: state.currentTools.length > 0 ? state.currentTools : undefined,
    skills: state.currentSkills.length > 0 ? state.currentSkills : undefined,
    mcpServerName: mcpServers.length === 1 ? mcpServers[0].serverName : undefined,
    mcpTools: mcpServers.length === 1 && mcpServers[0].tools ? mcpServers[0].tools : undefined,
    mcpServers: mcpServers.length > 0 ? mcpServers : undefined,
    systemPrompt: document.getElementById('system-prompt').value.trim(),
    modelTier: document.getElementById('llm-tier').value,
    skillSearchEnabled: document.getElementById('skill-search-enabled').checked,
    mcpIngestEnabled: document.getElementById('mcp-ingest-enabled').checked,
    capabilityDirs: document.getElementById('cap-dirs').value.trim() || undefined
  };
}

async function initAgent() {
  var btn = document.getElementById('init-btn');
  btn.disabled = true;
  document.getElementById('init-status').textContent = '\u23f3 Initializing...';
  addMessage('system', 'Initializing agent with current Tools/Skills/MCP Tools...');

  try {
    var resp = await fetch('api/agent/init', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(buildInitBody())
    });

    var data = await resp.json();
    if (data.error) {
      addMessage('system', 'Error during initialization: ' + data.error);
      btn.disabled = false;
      return;
    }

    state.sessionId = data.sessionId;
    document.getElementById('session-id').textContent = state.sessionId.slice(0, 8) + '...';
    setInitialized(true);
    var toolCount = state.currentTools.length + state.currentMcpTools.length;
    addMessage('system', '\u2705 Agent initialized (' + toolCount + ' tools)');

    updateMemoryBar(
      state.currentTools.concat(state.currentMcpTools),
      data.memoryCount || 0
    );

    var greeting = 'Hey! Ich bin dein Strands Agent.';
    if (toolCount > 0) {
      greeting += ' Ich habe Zugriff auf ' + toolCount + ' Tool' + (toolCount > 1 ? 's' : '') + '.';
    }
    greeting += ' What can I do for you?';
    addMessage('agent', greeting);

    document.getElementById('prompt-input').focus();
    loadSessions();
  } catch (err) {
    addMessage('system', 'Error: ' + err.message);
    btn.disabled = false;
  }
}

async function reinitAgent() {
  var btn = document.getElementById('init-btn');
  btn.disabled = true;
  document.getElementById('init-status').textContent = '\u23f3 Reinitialisiere...';

  try {
    var body = buildInitBody();
    body.sessionId = state.sessionId;
    var resp = await fetch('api/agent/reinit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });

    var data = await resp.json();
    if (data.error) {
      addMessage('system', 'Error during reinitialization: ' + data.error);
      btn.disabled = false;
      return;
    }

    setInitialized(true);
    var toolCount = state.currentTools.length + state.currentMcpTools.length;
    addMessage('system', '\u2705 Agent re-initialized (' + toolCount + ' tools)');

    updateMemoryBar(
      state.currentTools.concat(state.currentMcpTools),
      data.memoryCount || 0
    );

    document.getElementById('prompt-input').focus();
    loadSessions();
  } catch (err) {
    addMessage('system', 'Error: ' + err.message);
  }
}

/* ── Send Message (Streaming) ── */
async function sendMessage() {
  var input = document.getElementById('prompt-input');
  var btn = document.getElementById('send-btn');
  var cancelBtn = document.getElementById('cancel-btn');
  var prompt = input.value.trim();
  if (!prompt) return;

  input.value = '';
  input.style.height = 'auto';
  input.disabled = true;
  btn.disabled = true;

  addMessage('user', prompt);
  addTyping();

  state.isStreaming = true;
  state.abortController = new AbortController();
  cancelBtn.style.display = '';

  try {
    var resp = await fetch('api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt: prompt,
        sessionId: state.sessionId,
        tools: state.currentTools.length > 0 ? state.currentTools : undefined,
        skills: state.currentSkills.length > 0 ? state.currentSkills : undefined
      }),
      signal: state.abortController.signal
    });

    if (!resp.ok) throw new Error('HTTP ' + resp.status);

    removeTyping();

    // Create empty agent message for streaming
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
              if (r.thinking) {
                var existingThinking = agentMsg.querySelector('.msg-thinking');
                if (!existingThinking) {
                  var thinkingDiv = document.createElement('div');
                  thinkingDiv.className = 'msg-thinking';
                  var thinkingToggle = document.createElement('div');
                  thinkingToggle.className = 'thinking-toggle';
                  thinkingToggle.textContent = '\u{1F9E0} Reasoning anzeigen';
                  var thinkingContent = document.createElement('div');
                  thinkingContent.className = 'thinking-content';
                  thinkingContent.innerHTML = renderMarkdown(r.thinking);
                  thinkingDiv.appendChild(thinkingToggle);
                  thinkingDiv.appendChild(thinkingContent);
                  var msgContent = agentMsg.querySelector('.msg-content');
                  msgContent.parentNode.insertBefore(thinkingDiv, msgContent.nextSibling);
                  thinkingToggle.addEventListener('click', function() {
                    thinkingDiv.classList.toggle('open');
                    thinkingToggle.textContent = thinkingDiv.classList.contains('open')
                      ? '\u{1F9E0} Reasoning ausblenden'
                      : '\u{1F9E0} Reasoning anzeigen';
                  });
                }
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
    if (err.name === 'AbortError') {
      // User cancelled — stream was aborted, backend was notified
    } else {
      removeTyping();
      if (state.streamingMsg) {
        state.streamingMsg.querySelector('.msg-content').textContent = 'Error: ' + err.message;
        state.streamingMsg = null;
      } else {
        addMessage('agent', 'Error: ' + err.message);
      }
    }
  } finally {
    state.isStreaming = false;
    state.abortController = null;
    cancelBtn.style.display = 'none';
  }

  input.disabled = false;
  btn.disabled = false;
  input.focus();
}

/* ── Cancel Streaming ── */
function cancelMessage() {
  var ac = state.abortController;
  if (ac) {
    ac.abort();
  }
  // Also tell backend to cancel the LLM call
  fetch('api/chat/cancel', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId: state.sessionId })
  }).catch(function() {});
  addMessage('system', '⏹ Generation cancelled');
}

/* ── Service Worker ── */
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(function() {});
}

/* ── Panel Toggle ── */
function togglePanel(id) {
  var list = document.getElementById(id + '-list');
  if (list) list.style.display = list.style.display === 'none' ? 'flex' : 'none';
}

/* ── Init ── */
window.onload = function() {
  state.currentTools = getSelected('tool');
  state.currentSkills = getSelected('skill');
  setInitialized(false);

  // All panels closed by default; open API key panel if not logged in
  checkApiKeyStatus().then(function() {
    var setup = document.getElementById('apikey-setup');
    var activatePanel = document.getElementById('apikey-activate');
    var active = document.getElementById('apikey-active');
    if ((setup && setup.style.display !== 'none') || (activatePanel && activatePanel.style.display !== 'none')) {
      togglePanel('apikey');
    }
  });
  loadSessions();

  document.getElementById('setup-btn').addEventListener('click', setupApiKey);
  document.getElementById('activate-btn').addEventListener('click', activate);
  document.getElementById('deactivate-btn').addEventListener('click', deactivate);
  document.getElementById('init-btn').addEventListener('click', function() {
    if (state.isInitialized) {
      reinitAgent();
    } else {
      initAgent();
    }
  });
  document.getElementById('send-btn').addEventListener('click', sendMessage);
  document.getElementById('cancel-btn').addEventListener('click', cancelMessage);
  document.getElementById('new-session-btn').addEventListener('click', newSession);
  document.getElementById('mcp-connect-btn').addEventListener('click', connectMcpServer);
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
      togglePanel(id);
    });
  });

  document.querySelectorAll('input[name="tool"]').forEach(function(cb) {
    cb.addEventListener('change', updateTools);
  });
  document.querySelectorAll('input[name="skill"]').forEach(function(cb) {
    cb.addEventListener('change', updateSkills);
  });

  fetchMcpServers();
};
