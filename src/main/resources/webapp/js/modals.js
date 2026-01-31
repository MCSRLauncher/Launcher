function openModal(html) {
  const overlay = $('modalOverlay');
  const content = $('modalContent');
  if (!overlay || !content) return;
  content.innerHTML = html;
  overlay.classList.add('active');
}

function closeModal() {
  const overlay = $('modalOverlay');
  if (!overlay) return;
  overlay.classList.remove('active');
}

async function openCreateModal() {
  openModal(`
    <div class="modal-header">
      <span class="modal-title">Create Instance</span>
      <button class="modal-close" onclick="closeModal()"><span class="material-symbols-rounded">close</span></button>
    </div>
    <div class="modal-body">
      <div class="form-group">
        <label class="form-label">Name</label>
        <input class="form-input" type="text" id="inputName" placeholder="My Instance" autocomplete="off">
      </div>
      <div class="form-group">
        <label class="form-label">Version</label>
        <select class="form-select" id="inputVersion">
          <option value="1.16.1">1.16.1</option>
          <option value="1.14.4">1.14.4</option>
        </select>
      </div>
      <button class="mc-btn" style="width: 100%; margin-top: 12px;" onclick="createInstance()">CREATE</button>
    </div>
  `);

  const select = $('inputVersion');
  if (!select) return;

  try {
    const versions = await getMinecraftVersions();
    const releases = versions.filter(v => (v.type || '').toUpperCase() === 'RELEASE');
    const list = (releases.length ? releases : versions).slice(0, 50);

    if (!list.length) return;

    const previous = select.value;
    select.innerHTML = '';
    list.forEach(v => {
      const opt = document.createElement('option');
      opt.value = v.version;
      opt.textContent = v.recommended ? `${v.version} (Recommended)` : v.version;
      select.appendChild(opt);
    });
    if (previous) select.value = previous;
    initCustomSelects(); 
  } catch (e) {

  }

  setTimeout(initCustomSelects, 0);
}

function openRankedCreateModal() {
  openModal(`
    <div class="modal-header">
      <span class="modal-title" style="color: var(--ranked-gold);">Create Ranked Instance</span>
      <button class="modal-close" onclick="closeModal()"><span class="material-symbols-rounded">close</span></button>
    </div>
    <div class="modal-body">
      <div class="form-group">
        <label class="form-label">Name</label>
        <input class="form-input" type="text" id="inputName" value="Ranked 1.16.1" placeholder="Ranked Instance" autocomplete="off">
      </div>
      <div class="form-group">
        <label class="form-label">Minecraft Version</label>
        <select class="form-select" id="inputVersion">
          <option value="1.16.1">1.16.1 (Required)</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">Pack Type</label>
        <select class="form-select" id="rankedPackType">
          <option value="BASIC">BASIC (Recommended)</option>
          <option value="STANDARD_SETTINGS">STANDARD_SETTINGS</option>
          <option value="ALL">ALL</option>
          <option value="MOD_ONLY">MOD_ONLY</option>
        </select>
      </div>
      <button class="mc-btn" style="width: 100%; margin-top: 12px;" onclick="createRankedInstance()">CREATE RANKED</button>
    </div>
  `);

  setTimeout(initCustomSelects, 0);
}

function openLoginModal() {
  openModal(`
    <div class="modal-header">
      <span class="modal-title">Microsoft Login</span>
      <button class="modal-close" onclick="closeModal()"><span class="material-symbols-rounded">close</span></button>
    </div>
    <div class="modal-body" style="text-align: center;">
      <div id="loginStep1"><p style="color: var(--text-secondary); font-size: 12px;">Starting login…</p></div>
      <div id="loginStep2" style="display: none;">
        <p style="color: var(--text-secondary); font-size: 11px; margin-bottom: 8px;">Go to:</p>
        <a id="loginUrl" href="#" target="_blank" style="color: var(--mc-green); font-size: 12px;">---</a>
        <div id="loginCode" style="font-family: var(--font-pixel); font-size: 18px; background: #111; padding: 12px; margin: 12px 0; border: 2px solid #333;">----</div>
        <div style="display:flex; gap:10px; justify-content:center; margin-bottom: 8px;">
          <button class="mc-btn" style="min-width: 120px;" onclick="copyLoginUrl()">COPY URL</button>
          <button class="mc-btn" style="min-width: 120px;" onclick="copyLoginCode()">COPY CODE</button>
        </div>
        <p style="color: var(--text-muted); font-size: 10px;">Waiting for approval…</p>
      </div>
    </div>
  `);

  if (!hasBridge()) {
    toast('Backend bridge not available', 'error');
    return;
  }

  bridgeCall('account.login')
    .then(async () => {
      closeModal();
      await loadAccounts();
      render();
      toast('Logged in', 'success');
    })
    .catch((e) => {
      closeModal();
      toast(e?.message || 'Login failed', 'error');
    });
}

async function copyLoginUrl() {
  const href = $('loginUrl')?.textContent || '';
  const ok = await copyToClipboard(href);
  toast(ok ? 'Copied URL' : 'Copy failed', ok ? 'success' : 'error');
}

async function copyLoginCode() {
  const code = $('loginCode')?.textContent || '';
  const ok = await copyToClipboard(code);
  toast(ok ? 'Copied code' : 'Copy failed', ok ? 'success' : 'error');
}

window.openModal = openModal;
window.closeModal = closeModal;
window.openCreateModal = openCreateModal;
window.openRankedCreateModal = openRankedCreateModal;
window.openLoginModal = openLoginModal;
window.copyLoginUrl = copyLoginUrl;
window.copyLoginCode = copyLoginCode;

function openRenameModal(currentName, onConfirm) {
  openModal(`
    <div class="modal-header">
      <span class="modal-title">Rename Instance</span>
      <button class="modal-close" onclick="closeModal()"><span class="material-symbols-rounded">close</span></button>
    </div>
    <div class="modal-body">
      <div class="form-group">
        <label class="form-label">Name</label>
        <input class="form-input" type="text" id="renameInput" value="${escapeHtml(currentName)}" autocomplete="off">
      </div>
      <button class="mc-btn" style="width: 100%; margin-top: 12px;" id="renameConfirmBtn">RENAME</button>
    </div>
  `);

  const btn = document.getElementById('renameConfirmBtn');
  const input = document.getElementById('renameInput');

  if (input) {
    input.focus();
    input.select();
    input.onkeydown = (e) => { if (e.key === 'Enter') btn.click(); };
  }

  if (btn) {
    btn.onclick = () => {
      const val = input?.value?.trim();
      if (val) {
        closeModal();
        onConfirm(val);
      }
    };
  }
}

function openDeleteModal(name, onConfirm) {
  openModal(`
    <div class="modal-header">
      <span class="modal-title">Delete Instance</span>
      <button class="modal-close" onclick="closeModal()"><span class="material-symbols-rounded">close</span></button>
    </div>
    <div class="modal-body">
      <p style="font-size: 12px; color: var(--text-secondary); margin-bottom: 20px; line-height: 1.4;">
        Are you sure you want to delete <strong style="color: #fff;">${escapeHtml(name)}</strong>?
        <br>This cannot be undone.
      </p>
      <div style="display: flex; gap: 10px;">
        <button class="mc-btn" onclick="closeModal()" style="flex: 1; background: #333;">CANCEL</button>
        <button class="mc-btn" id="deleteConfirmBtn" style="flex: 1; background: #8b0000; border-color: #500000;">DELETE</button>
      </div>
    </div>
  `);

  const btn = document.getElementById('deleteConfirmBtn');
  if (btn) {
    btn.onclick = () => {
      closeModal();
      onConfirm();
    };
  }
}

window.openRenameModal = openRenameModal;
window.openDeleteModal = openDeleteModal;