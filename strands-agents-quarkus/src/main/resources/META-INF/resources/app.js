let sessionId = crypto.randomUUID();
let currentTools = [];
let currentSkills = [];

document.getElementById('session-id').textContent = sessionId.slice(0, 8) + '...';

function getSelected(name) {
  return Array.from(document.querySelectorAll('input[name="' + name + '"]:checked')).map(cb => cb.value);
}

function updateTools() { currentTools = getSelected('tool'); }
function updateSkills() { currentSkills = getSelected('skill'); }

function newSession() {
  sessionId = crypto.randomUUID();
  document.getElementById('session-id').textContent = sessionId.slice(0, 8) + '...';
  document.getElementById('messages').innerHTML =
    '<div class="message system"><div class="msg-content">Neue Session gestartet.</div></div>';
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

// initialize on load
window.onload = function() {
  currentTools = getSelected('tool');
  currentSkills = getSelected('skill');

  document.getElementById('send-btn').addEventListener('click', sendMessage);
  document.getElementById('new-session-btn').addEventListener('click', newSession);
  document.getElementById('prompt-input').addEventListener('keydown', function(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
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
};
