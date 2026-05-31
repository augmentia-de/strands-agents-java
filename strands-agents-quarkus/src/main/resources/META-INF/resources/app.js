let sessionId = crypto.randomUUID();
let currentTools = [];
let currentSkills = [];
let currentMcpTools = [];
let fetchedMcpTools = [];
let isInitialized = false;

document.getElementById('session-id').textContent = sessionId.slice(0, 8) + '...';

function getSelected(name) {
  return Array.from(document.querySelectorAll('input[name="' + name + '"]:checked')).map(cb => cb.value);
}

function updateTools() { currentTools = getSelected('tool'); }
function updateSkills() { currentSkills = getSelected('skill'); }
function updateMcpTools() { currentMcpTools = getSelected('mcp-tool'); }

function setInitialized(state) {
  isInitialized = state;
  document.getElementById('init-btn').disabled = state;
  document.getElementById('send-btn').disabled = !state;
  document.getElementById('prompt-input').disabled = !state;
  document.getElementById('init-status').textContent = state ? '\u2705 Agent bereit' : '\u23f3 Nicht initialisiert';
  document.getElementById('init-status').className = state ? 'status-ready' : 'status-pending';
}

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
    fetchedMcpTools = tools;
    renderMcpTools(tools);
    currentMcpTools = [];
    status.textContent = '\u2705 ' + tools.length + ' MCP Tools entdeckt';
    status.className = 'status-ready';
    document.getElementById('mcp-count').textContent = tools.length + ' Tools verf\u00fcgbar, keine ausgew\u00e4hlt';
  } catch (err) {
    status.textContent = '\u274c Fehler: ' + err.message;
    status.className = 'status-error';
    fetchedMcpTools = [];
    renderMcpTools([]);
  }
}

function newSession() {
  fetch('/api/agent/release', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId: sessionId })
  }).catch(function() {});
  sessionId = crypto.randomUUID();
  document.getElementById('session-id').textContent = sessionId.slice(0, 8) + '...';
  document.getElementById('messages').innerHTML =
    '<div class="message system"><div class="msg-content">Neue Session gestartet. W\u00e4hle Tools/Skills und klicke "Agent initialisieren".</div></div>';
  setInitialized(false);
}

function addMessage(role, content, meta) {
  const div = document.createElement('div');
  div.className = 'message ' + role;
  div.innerHTML = '<div class="msg-content">' + escapeHtml(content) + '</div>' +
    (meta ? '<div class="msg-meta">' + escapeHtml(meta) + '</div>' : '');
  document.getElementById('messages').appendChild(div);
  div.scrollIntoView({ behavior: 'smooth' });
  return div;
}

function escapeHtml(s) {
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

async function initAgent() {
  const btn = document.getElementById('init-btn');
  btn.disabled = true;
  document.getElementById('init-status').textContent = '\u23f3 Initialisiere...';

  addMessage('system', 'Initialisiere Agent mit aktuellen Tools/Skills/MCP-Tools...');

  try {
    const resp = await fetch('/api/agent/init', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        tools: currentTools.length > 0 ? currentTools : undefined,
        skills: currentSkills.length > 0 ? currentSkills : undefined,
        mcpUrl: document.getElementById('mcp-url').value.trim() || undefined,
        mcpTools: currentMcpTools.length > 0 ? currentMcpTools : undefined,
        systemPrompt: document.getElementById('system-prompt').value.trim() || undefined,
        skillSearchEnabled: document.getElementById('skill-search-enabled').checked,
        mcpIngestEnabled: document.getElementById('mcp-ingest-enabled').checked,
        capabilityDirs: document.getElementById('cap-dirs').value.trim() || undefined,
        capabilityMcp: document.getElementById('cap-mcp').value.trim() || undefined
      })
    });

    const data = await resp.json();
    if (data.error) {
      addMessage('system', 'Fehler bei Initialisierung: ' + data.error);
      btn.disabled = false;
      return;
    }

    sessionId = data.sessionId;
    document.getElementById('session-id').textContent = sessionId.slice(0, 8) + '...';
    setInitialized(true);
    var toolCount = currentTools.length + currentMcpTools.length;
    addMessage('system', '\u2705 Agent initialisiert (' + toolCount + ' Tools)');
    document.getElementById('prompt-input').focus();
  } catch (err) {
    addMessage('system', 'Fehler: ' + err.message);
    btn.disabled = false;
  }
}

async function sendMessage() {
  const input = document.getElementById('prompt-input');
  const btn = document.getElementById('send-btn');
  const prompt = input.value.trim();
  if (!prompt) return;

  input.value = '';
  btn.disabled = true;

  addMessage('user', prompt);

  const loadingMsg = addMessage('agent', '<span class="spinner"></span> Denke nach...');

  try {
    const resp = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        prompt: prompt,
        sessionId: sessionId,
        tools: currentTools.length > 0 ? currentTools : undefined,
        skills: currentSkills.length > 0 ? currentSkills : undefined
      })
    });

    const data = await resp.json();

    if (data.error) {
      loadingMsg.querySelector('.msg-content').textContent = 'Fehler: ' + data.error;
    } else {
      loadingMsg.querySelector('.msg-content').textContent = data.answer;
      let meta = (data.durationMs / 1000).toFixed(1) + 's';
      if (data.inputTokens > 0) meta += ' / ' + data.inputTokens + ' in / ' + data.outputTokens + ' out';
      if (data.toolCalls > 0) meta += ' / ' + data.toolCalls + ' Tool-Calls';
      if (data.phases && data.phases.length > 0) meta += ' / ' + data.phases.join(' ');
      const metaEl = loadingMsg.querySelector('.msg-meta');
      if (metaEl) metaEl.textContent = meta;
    }
  } catch (err) {
    loadingMsg.querySelector('.msg-content').textContent = 'Fehler: ' + err.message;
  }

  btn.disabled = false;
  input.focus();
}

function toggleSection(id) {
  const list = document.getElementById(id + '-list');
  list.style.display = list.style.display === 'none' ? 'flex' : 'none';
}

window.onload = function() {
  currentTools = getSelected('tool');
  currentSkills = getSelected('skill');
  setInitialized(false);

  document.getElementById('init-btn').addEventListener('click', initAgent);
  document.getElementById('send-btn').addEventListener('click', sendMessage);
  document.getElementById('new-session-btn').addEventListener('click', newSession);
  document.getElementById('mcp-refresh-btn').addEventListener('click', fetchMcpTools);
  document.getElementById('prompt-input').addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (isInitialized) sendMessage();
    }
  });

  document.querySelectorAll('[data-toggle]').forEach(function(el) {
    el.addEventListener('click', function() {
      toggleSection(this.getAttribute('data-toggle'));
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
