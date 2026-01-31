async function loadLauncherInfo() {
  if (!hasBridge()) {
    state.launcherInfo = null;
    setVersionText('-');
    return null;
  }
  const info = await bridgeCall('launcher.getInfo');
  state.launcherInfo = info;
  if (info?.version) setVersionText(`v${info.version}`);
  return info;
}

async function loadInstances() {
  if (!hasBridge()) {
    state.instances = [];
    return;
  }
  const grouped = await bridgeCall('instance.getAll');
  const items = [];
  for (const group of Object.keys(grouped || {}).sort()) {
    for (const inst of grouped[group] || []) {
      items.push({ ...inst, group });
    }
  }
  state.instances = items;
}

async function loadAccounts() {
  if (!hasBridge()) {
    state.accounts = [];
    state.activeAccount = null;
    return;
  }
  state.accounts = (await bridgeCall('account.getAll')) || [];
  state.activeAccount = await bridgeCall('account.getActive');
}

async function loadOptions() {
  if (!hasBridge()) {
    state.options = null;
    return;
  }
  state.options = await bridgeCall('launcher.getOptions');
}

async function getMinecraftVersions() {
  if (state.minecraftVersions) return state.minecraftVersions;
  if (!hasBridge()) {
    state.minecraftVersions = [];
    return state.minecraftVersions;
  }

  const hasMeta = await bridgeCall('meta.hasLoadedPackages').catch(() => false);
  if (!hasMeta) {
    state.minecraftVersions = [];
    return state.minecraftVersions;
  }

  const versions = (await bridgeCall('meta.getMinecraftVersions')) || [];
  const sorted = [...versions].sort((a, b) => String(b.releaseTime || '').localeCompare(String(a.releaseTime || '')));
  state.minecraftVersions = sorted;
  return state.minecraftVersions;
}

window.loadLauncherInfo = loadLauncherInfo;
window.loadInstances = loadInstances;
window.loadAccounts = loadAccounts;
window.loadOptions = loadOptions;
window.getMinecraftVersions = getMinecraftVersions;

