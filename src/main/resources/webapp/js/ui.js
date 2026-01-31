(function initLauncherEventBus() {
  const queued = [];
  const listeners = new Map();

  function dispatch(event, data) {
    const list = listeners.get(event);
    if (!list || list.length === 0) {
      queued.push({ event, data });
      return;
    }
    list.forEach(fn => {
      try { fn(data); } catch (e) { console.error('Event handler error:', e); }
    });
  }

  function on(event, handler, opts = {}) {
    const list = listeners.get(event) || [];
    list.push(handler);
    listeners.set(event, list);

    if (opts.replay !== false) {
      for (let i = queued.length - 1; i >= 0; i--) {
        const item = queued[i];
        if (item.event === event) {
          queued.splice(i, 1);
          try { handler(item.data); } catch (e) { console.error('Event replay error:', e); }
        }
      }
    }
  }

  window.launcher = window.launcher || {};
  window.launcher.on = on;
  window.launcher._handleEvent = (event, data) => dispatch(event, data);
})();

function setStatus(connected, label) {
  const dot = $('statusDot');
  const text = $('statusText');
  if (dot) dot.style.background = connected ? 'var(--mc-green)' : 'var(--text-muted)';
  if (text) text.textContent = label ?? (connected ? 'Connected' : 'Disconnected');
}

function setVersionText(versionLabel) {
  const el = $('versionText');
  if (el) el.textContent = versionLabel ?? '-';
}

function showLoading(title, progress) {
  const screen = $('loadingScreen');
  if (!screen) return;

  screen.classList.add('active');
  const text = $('loadingText');
  if (text && title) text.textContent = title;

  const fill = $('loadingFill');
  if (fill) {
    if (typeof progress === 'number' && isFinite(progress)) {
      fill.style.animation = 'none';
      const clamped = Math.max(0, Math.min(1, progress));
      fill.style.width = `${Math.round(clamped * 100)}%`;
    } else {
      fill.style.animation = '';
      fill.style.width = '';
    }
  }
}

function hideLoading() {
  const screen = $('loadingScreen');
  if (!screen) return;
  screen.classList.remove('active');
}

function toast(message, kind = 'info', timeoutMs = 2500) {
  const container = $('toastContainer');
  if (!container) return;

  const el = document.createElement('div');
  el.className = `toast toast-${kind}`;
  el.textContent = String(message ?? '');
  container.appendChild(el);

  setTimeout(() => {
    el.classList.add('hide');
    setTimeout(() => el.remove(), 250);
  }, timeoutMs);
}

window.setStatus = setStatus;
window.setVersionText = setVersionText;
window.showLoading = showLoading;
window.hideLoading = hideLoading;
window.toast = toast;

let contextMenuOpenedAt = 0;

function openContextMenu(items, x, y) {
  const el = document.getElementById('contextMenu');
  if (!el) return;

  el.innerHTML = '';
  items.forEach(item => {
    const btn = document.createElement('button');
    btn.className = `context-menu-item ${item.danger ? 'danger' : ''}`;
    btn.innerHTML = item.icon
      ? `<span class="material-symbols-rounded" style="font-size: 16px;">${item.icon}</span><span>${escapeHtml(item.label)}</span>`
      : `<span>${escapeHtml(item.label)}</span>`;
    btn.onclick = () => {
      closeContextMenu();
      item.onClick?.();
    };
    el.appendChild(btn);
  });

  const pad = 8;
  el.classList.add('active');
  contextMenuOpenedAt = Date.now();
  const rect = el.getBoundingClientRect();
  const maxX = (window.innerWidth / 1.5) - rect.width - pad;
  const maxY = (window.innerHeight / 1.5) - rect.height - pad;
  const left = Math.max(pad, Math.min(x / 1.5, maxX));
  const top = Math.max(pad, Math.min(y / 1.5, maxY));
  el.style.left = `${left}px`;
  el.style.top = `${top}px`;
}

function closeContextMenu() {
  const el = document.getElementById('contextMenu');
  if (!el) return;
  el.classList.remove('active');
}

window.addEventListener('pointerdown', (e) => {
  const el = document.getElementById('contextMenu');
  if (!el || !el.classList.contains('active')) return;
  if (e.button === 2) return;
  if (Date.now() - contextMenuOpenedAt < 150) return;
  if (el.contains(e.target)) return;
  closeContextMenu();
}, true);

window.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeContextMenu(); });

function initCustomSelects() {
  const selects = document.querySelectorAll('select.form-select');
  selects.forEach(select => {
    if (select.nextElementSibling && select.nextElementSibling.classList.contains('custom-select-container')) {
      select.nextElementSibling.remove();
    }

    const container = document.createElement('div');
    container.className = 'custom-select-container';

    const trigger = document.createElement('div');
    trigger.className = 'custom-select-trigger';
    
    const selectedOption = select.options[select.selectedIndex];
    trigger.innerHTML = `<span>${selectedOption ? selectedOption.text : 'Select...'}</span><span class="material-symbols-rounded" style="font-size:16px;">arrow_drop_down</span>`;
    
    const optionsPanel = document.createElement('div');
    optionsPanel.className = 'custom-select-options';

    Array.from(select.options).forEach((opt, index) => {
      const div = document.createElement('div');
      div.className = `custom-option ${opt.selected ? 'selected' : ''}`;
      div.textContent = opt.text;
      div.onclick = (e) => {
        e.stopPropagation();
        select.selectedIndex = index;
        select.dispatchEvent(new Event('change'));
        
        trigger.querySelector('span').textContent = opt.text;
        optionsPanel.querySelectorAll('.custom-option').forEach(o => o.classList.remove('selected'));
        div.classList.add('selected');
        optionsPanel.classList.remove('open');
        trigger.classList.remove('active');
      };
      optionsPanel.appendChild(div);
    });

    trigger.onclick = (e) => {
      e.stopPropagation();
      document.querySelectorAll('.custom-select-options.open').forEach(el => {
        if (el !== optionsPanel) {
          el.classList.remove('open');
          el.previousElementSibling?.classList.remove('active');
        }
      });
      
      optionsPanel.classList.toggle('open');
      trigger.classList.toggle('active');
    };

    container.appendChild(trigger);
    container.appendChild(optionsPanel);
    select.parentNode.insertBefore(container, select.nextSibling);
  });

  window.addEventListener('click', (e) => {
    if (!e.target.closest('.custom-select-container')) {
      document.querySelectorAll('.custom-select-options.open').forEach(el => {
        el.classList.remove('open');
        el.previousElementSibling?.classList.remove('active');
      });
    }
  });
}

window.initCustomSelects = initCustomSelects;


window.openContextMenu = openContextMenu;
window.closeContextMenu = closeContextMenu;

var instanceGridOpen = false;

function toggleInstanceGrid() {
  var sidebar = document.getElementById('sidebar');
  var iconImg = document.getElementById('gridBtnIcon');
  if (!sidebar) return;

  instanceGridOpen = !instanceGridOpen;

  if (instanceGridOpen) {
    sidebar.classList.add('grid-mode');
    if (iconImg) iconImg.src = 'icons/square.png';
  } else {
    sidebar.classList.remove('grid-mode');
    if (iconImg) iconImg.src = 'icons/grid.png';
  }
}

window.toggleInstanceGrid = toggleInstanceGrid;

var skyThemes = {
  night: { top: '#0a0a1a', bottom: '#1a1a2e', stars: 0.8 },
  dawn: { top: '#2d1b4e', bottom: '#ff7b54', stars: 0.2 },
  morning: { top: '#5a9fd4', bottom: '#87ceeb', stars: 0 },
  day: { top: '#78a7ff', bottom: '#c9e4ff', stars: 0 },
  afternoon: { top: '#5b8bd4', bottom: '#98c1d9', stars: 0 },
  sunset: { top: '#4a3f6b', bottom: '#ff6b35', stars: 0.1 },
  dusk: { top: '#1a1a3e', bottom: '#4a3060', stars: 0.5 }
};

function getSkyTheme(hour) {
  if (hour >= 0 && hour < 5) return 'night';
  if (hour >= 5 && hour < 7) return 'dawn';
  if (hour >= 7 && hour < 10) return 'morning';
  if (hour >= 10 && hour < 16) return 'day';
  if (hour >= 16 && hour < 18) return 'afternoon';
  if (hour >= 18 && hour < 20) return 'sunset';
  if (hour >= 20 && hour < 22) return 'dusk';
  return 'night';
}

function updateSky(forceHour) {
  var hour = (typeof forceHour === 'number') ? forceHour : new Date().getHours();
  var theme = skyThemes[getSkyTheme(hour)];

  document.documentElement.style.setProperty('--sky-top', theme.top);
  document.documentElement.style.setProperty('--sky-bottom', theme.bottom);
  document.documentElement.style.setProperty('--star-opacity', theme.stars);
}

function initDynamicSky() {
  updateSky();
  setInterval(updateSky, 60000);
}

window.updateSky = updateSky;
window.initDynamicSky = initDynamicSky;
