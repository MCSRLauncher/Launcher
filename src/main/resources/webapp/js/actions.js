function switchTab(tab) {
  state.activeTab = tab;
  state.selectedId = null;
  document.querySelectorAll('.nav-btn').forEach(btn => btn.classList.toggle('active', btn.dataset.tab === tab));
  refresh();
}

async function refresh() {
  state.bridgeConnected = hasBridge();
  setStatus(state.bridgeConnected);

  try {
    await Promise.all([loadInstances(), loadAccounts(), loadOptions(), loadLauncherInfo()]);
  } catch (e) {
    console.error('Refresh error:', e);
    toast(e?.message || 'Refresh failed', 'error');
  }

  if (state.activeTab === 'instances') {
    if (state.instances.length && !state.instances.find(i => i.id === state.selectedId)) {
      state.selectedId = state.instances[0].id;
    }
  } else if (state.activeTab === 'accounts') {
    if (state.accounts.length && !state.accounts.find(a => a.uuid === state.selectedId)) {
      state.selectedId = state.accounts[0].uuid;
    }
  }

  render();
  if (state.rankedMode) renderRankedMode(null);
}

async function launchSelected() {
  if (!state.selectedId || state.launching) return;
  if (!hasBridge()) return toast('Backend bridge not available', 'error');

  state.launching = true;
  state.launchingInstanceId = state.selectedId;
  render();
  try {
    await bridgeCall('instance.launch', { instanceId: state.selectedId });
  } catch (e) {
    toast(e?.message || 'Launch failed', 'error');
  } finally {
    await loadInstances().catch(() => {});
    const inst = state.instances.find(i => i.id === state.launchingInstanceId);
    if (inst?.isRunning) {
      state.launching = false;
      state.launchingInstanceId = null;
    }
    render();
  }
}

async function launchRankedSelected() {
  if (!state.rankedSelectedId || state.launching) return;
  if (!hasBridge()) return toast('Backend bridge not available', 'error');

  state.launching = true;
  state.launchingInstanceId = state.rankedSelectedId;
  renderRankedBottomBar();
  try {
    await bridgeCall('instance.launch', { instanceId: state.rankedSelectedId });
  } catch (e) {
    toast(e?.message || 'Launch failed', 'error');
  } finally {
    await loadInstances().catch(() => {});
    const inst = state.instances.find(i => i.id === state.launchingInstanceId);
    if (inst?.isRunning) {
      state.launching = false;
      state.launchingInstanceId = null;
    }
    if (state.rankedMode) renderRankedMode(null);
    render();
  }
}

async function setActiveAccount(uuid) {
  if (!hasBridge()) return toast('Backend bridge not available', 'error');
  try {
    await bridgeCall('account.setActive', { uuid });
    await loadAccounts();
    toast('Active account updated', 'success');
    render();
  } catch (e) {
    toast(e?.message || 'Failed to set active account', 'error');
  }
}

async function removeAccount(uuid) {
  if (!hasBridge()) return toast('Backend bridge not available', 'error');
  if (!confirm('Remove this account?')) return;

  try {
    await bridgeCall('account.remove', { uuid });
    state.selectedId = null;
    await loadAccounts();
    toast('Account removed', 'success');
    render();
  } catch (e) {
    toast(e?.message || 'Failed to remove account', 'error');
  }
}

async function saveSettings() {
  if (!hasBridge()) return toast('Backend bridge not available', 'error');
  const params = {
    javaPath: $('optJavaPath')?.value || '',
    jvmArguments: $('optJvmArgs')?.value || '',
    minMemory: Number($('optMinMem')?.value || 1024),
    maxMemory: Number($('optMaxMem')?.value || 4096)
  };
  try {
    await bridgeCall('launcher.updateOptions', params);
    toast('Settings saved', 'success');
    await loadOptions();
    render();
  } catch (e) {
    toast(e?.message || 'Failed to save settings', 'error');
  }
}

async function createInstance() {
  if (!hasBridge()) return toast('Backend bridge not available', 'error');
  const name = $('inputName')?.value?.trim();
  const version = $('inputVersion')?.value;
  if (!name) return toast('Name required', 'error');
  if (!version) return toast('Version required', 'error');

  try {
    closeModal();
    await bridgeCall('instance.create', { name, minecraftVersion: version });
    toast('Instance created', 'success');
    await refresh();
  } catch (e) {
    toast(e?.message || 'Failed to create instance', 'error');
  }
}

async function createRankedInstance() {
  if (!hasBridge()) return toast('Backend bridge not available', 'error');
  const name = $('inputName')?.value?.trim();
  const packType = $('rankedPackType')?.value || 'BASIC';
  if (!name) return toast('Name required', 'error');

  try {
    closeModal();
    await bridgeCall('instance.create', {
      name,
      minecraftVersion: '1.16.1',
      isRanked: true,
      mcsrRankedPackType: packType
    });
    toast('Ranked instance created', 'success');
    await refresh();
    if (state.rankedMode) renderRankedMode(null);
  } catch (e) {
    toast(e?.message || 'Failed to create ranked instance', 'error');
  }
}

async function enterRankedMode() {
  if (!state.activeAccount) {
    toast('Log in first', 'error');
    switchTab('accounts');
    return;
  }

  showLoading('Loading Ranked…');
  try {
    const [rankedData, matches, liveData, seasons] = await Promise.all([
      fetchRankedData(state.activeAccount.username),
      fetchRecentMatches(state.activeAccount.username, 8),
      fetchLiveData(),
      fetchSeasonResults(state.activeAccount.username)
    ]);

    if (!rankedData) {
      toast('Ranked profile not found', 'error');
      return;
    }

    state.rankedData = rankedData;
    state.rankedMatches = matches || [];
    state.rankedSeasons = seasons;

    $('rankedMode')?.classList.add('active');
    $('normalMode')?.classList.add('behind-ranked');
    state.rankedMode = true;
    renderRankedMode(liveData);
  } catch (e) {
    toast(e?.message || 'Failed to load ranked data', 'error');
  } finally {
    hideLoading();
  }
}

function exitRankedMode() {
  $('rankedMode')?.classList.remove('active');
  $('normalMode')?.classList.remove('behind-ranked');
  state.rankedMode = false;
  window.disposeRankedSkinViewer?.();
}

function appInit() {
  initDynamicSky();

  const overlay = $('modalOverlay');
  if (overlay) overlay.onclick = (e) => { if (e.target === overlay) closeModal(); };
  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeModal();
  });

  window.launcher?.on?.('auth.deviceCode', (data) => {
    const s1 = $('loginStep1');
    const s2 = $('loginStep2');
    if (s1 && s2) {
      s1.style.display = 'none';
      s2.style.display = 'block';
      if ($('loginUrl')) {
        $('loginUrl').href = data?.verification_uri || '#';
        $('loginUrl').textContent = data?.verification_uri || '---';
      }
      if ($('loginCode')) $('loginCode').textContent = data?.user_code || '----';
    }
  });

  window.launcher?.on?.('worker.progress', (data) => {
    if (!data || !data.title) {
      state.launching = false;
      state.launchingInstanceId = null;
      hideLoading();
      refreshInstancesOnly();
      return;
    }
    state.launching = true;
    showLoading(data.description || data.title, typeof data.progress === 'number' ? data.progress : null);
    renderBottomBar();
    renderRankedBottomBar();
  });

  window.launcher?.on?.('worker.error', (data) => {
    toast(data?.message || 'Operation failed', 'error', 4000);
  });

  showLoading('Starting…');
  refresh().finally(() => hideLoading());
  startInstancePolling();
}

function openInstanceContextMenu(event, inst) {
  if (!inst) return;
  if (typeof window.openContextMenu !== 'function') return;

  const x = event.clientX;
  const y = event.clientY;

  const isRunning = !!inst.isRunning;
  const items = [
    {
      label: 'Rename',
      icon: 'edit',
      onClick: () => {
        if (typeof window.openRenameModal === 'function') {
          window.openRenameModal(inst.displayName || inst.id, async (name) => {
            try {
              await bridgeCall('instance.rename', { instanceId: inst.id, name });
              toast('Renamed', 'success');
              await refreshInstancesOnly();
            } catch (e) {
              toast(e?.message || 'Rename failed', 'error');
            }
          });
        }
      }
    },
    {
      label: isRunning ? 'Delete (stop first)' : 'Delete',
      icon: 'delete',
      danger: true,
      onClick: () => {
        if (isRunning) return;
        if (typeof window.openDeleteModal === 'function') {
          window.openDeleteModal(inst.displayName || inst.id, async () => {
            try {
              await bridgeCall('instance.delete', { instanceId: inst.id });
              toast('Deleted', 'success');
              if (state.selectedId === inst.id) state.selectedId = null;
              if (state.rankedSelectedId === inst.id) state.rankedSelectedId = null;
              await refreshInstancesOnly();
              render();
            } catch (e) {
              toast(e?.message || 'Delete failed', 'error');
            }
          });
        }
      }
    }
  ];

  window.openContextMenu(items, x, y);
}

let instancePollTimer = null;

async function refreshInstancesOnly() {
  if (!hasBridge()) return;
  await loadInstances();
  if (state.activeTab === 'instances' && state.instances.length && !state.instances.find(i => i.id === state.selectedId)) {
    state.selectedId = state.instances[0].id;
  }
  if (state.rankedMode && state.instances.length && state.rankedSelectedId && !state.instances.find(i => i.id === state.rankedSelectedId)) {
    state.rankedSelectedId = state.instances.find(i => i.isMCSRRanked)?.id ?? null;
  }

  if (state.rankedMode) renderRankedMode(null);
  else {
    renderSidebar();
    renderBottomBar();
  }
}

function startInstancePolling() {
  if (instancePollTimer) return;
  instancePollTimer = setInterval(() => {
    refreshInstancesOnly().catch(() => {});
  }, 1500);
}

window.refreshInstancesOnly = refreshInstancesOnly;
window.startInstancePolling = startInstancePolling;

window.switchTab = switchTab;
window.refresh = refresh;
window.launchSelected = launchSelected;
window.launchRankedSelected = launchRankedSelected;
window.setActiveAccount = setActiveAccount;
window.removeAccount = removeAccount;
window.saveSettings = saveSettings;
window.createInstance = createInstance;
window.createRankedInstance = createRankedInstance;
window.enterRankedMode = enterRankedMode;
window.exitRankedMode = exitRankedMode;
window.appInit = appInit;
window.openInstanceContextMenu = openInstanceContextMenu;
