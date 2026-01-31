function renderSidebar() {
  const container = $('listContainer');
  container.innerHTML = '';

  if (state.activeTab === 'settings' || state.activeTab === 'accounts') {
    $('sidebarTitle').textContent = state.activeTab === 'settings' ? 'OPTIONS' : 'ACCOUNTS';
    $('bottomBar').style.display = 'none';

    if (state.activeTab === 'accounts') {
      const addBtn = document.createElement('button');
      addBtn.className = 'add-instance-btn';
      addBtn.innerHTML = `<span class="material-symbols-rounded" style="font-size: 14px;">add</span> Add Account`;
      addBtn.onclick = openLoginModal;
      container.appendChild(addBtn);

      state.accounts.forEach(acc => {
        const avatarUuid = String(acc.uuid || '').replace(/-/g, '');
        const isActive = state.activeAccount?.uuid === acc.uuid;
        const div = document.createElement('div');
        div.className = `instance-item ${acc.uuid === state.selectedId ? 'selected' : ''}`;
        div.innerHTML = `
          <img src="https://mc-heads.net/avatar/${avatarUuid}/32" class="pixelated" style="width:32px;height:32px;">
          <div class="instance-meta">
            <div class="instance-name">${escapeHtml(acc.username)}</div>
            <div class="instance-version">${isActive ? 'Active' : ''}</div>
          </div>
        `;
        div.onclick = () => { state.selectedId = acc.uuid; render(); };
        container.appendChild(div);
      });
    }
    return;
  }

  $('sidebarTitle').textContent = 'INSTANCES';
  $('bottomBar').style.display = 'flex';

  const addBtn = document.createElement('button');
  addBtn.className = 'add-instance-btn';
  addBtn.innerHTML = `<span class="material-symbols-rounded" style="font-size: 14px;">add</span> New Instance`;
  addBtn.onclick = openCreateModal;
  container.appendChild(addBtn);

  state.instances.forEach(inst => {
    const div = document.createElement('div');
    div.className = `instance-item ${inst.id === state.selectedId ? 'selected' : ''}`;
    const iconSrc = inst.isMCSRRanked ? 'icons/trophe.png' : 'icons/square.png';
    div.innerHTML = `
      <div class="instance-icon"><img src="${iconSrc}" class="pixelated instance-icon-img"></div>
      <div class="instance-meta">
        <div class="instance-name">${escapeHtml(inst.displayName || inst.id)}</div>
        <div class="instance-version">${escapeHtml(inst.minecraftVersion)}</div>
      </div>
      ${inst.isRunning ? '<div class="instance-status"></div>' : ''}
    `;
    div.onclick = () => { state.selectedId = inst.id; render(); };
    div.oncontextmenu = (e) => {
      e.preventDefault();
      if (typeof window.openInstanceContextMenu === 'function') window.openInstanceContextMenu(e, inst);
    };
    container.appendChild(div);
  });
}
function renderBottomBar() {
  const inst = state.instances.find(i => i.id === state.selectedId);
  if (inst) {
    const isLaunchingThis = state.launching && state.launchingInstanceId === inst.id;
    $('selectedName').textContent = inst.displayName || inst.id;
    $('selectedDetail').textContent = `${inst.type || 'Fabric'} ${inst.minecraftVersion} - ${formatPlayTime(inst.playTime || 0)} played`;
    $('playBtn').disabled = inst.isRunning || state.launching;
    $('playBtn').textContent = inst.isRunning ? 'RUNNING' : isLaunchingThis ? 'LAUNCHING...' : 'PLAY';
  } else {
    $('selectedName').textContent = 'Select an instance';
    $('selectedDetail').textContent = '-';
    $('playBtn').disabled = true;
    $('playBtn').textContent = 'PLAY';
  }
}

function renderContent() {
  const container = $('contentArea');
  container.innerHTML = '';

  if (state.activeTab === 'settings') {
    renderSettings(container);
  } else if (state.activeTab === 'accounts') {
    renderAccountDetail(container);
  }
}
function renderAccountDetail(container) {
  const acc = state.accounts.find(a => a.uuid === state.selectedId);
  if (!acc) {
    container.innerHTML = '<div style="color: var(--text-muted); padding: 40px; text-align: center; font-family: var(--font-pixel); font-size: 11px;">Select an account</div>';
    return;
  }

  const isActive = state.activeAccount?.uuid === acc.uuid;
  const avatarUuid = String(acc.uuid || '').replace(/-/g, '');

  container.innerHTML = `
    <div style="background: rgba(0,0,0,0.7); padding: 20px; max-width: 400px;">
      <div style="display: flex; gap: 16px; align-items: center; margin-bottom: 16px;">
        <img src="https://mc-heads.net/avatar/${avatarUuid}/64" class="pixelated" style="width:64px;height:64px;">
        <div>
          <div style="font-family: var(--font-pixel); font-size: 16px;">${escapeHtml(acc.username)}</div>
          <div style="font-size: 10px; color: var(--text-muted); word-break: break-all;">${escapeHtml(acc.uuid)}</div>
        </div>
      </div>
      <div style="display: flex; gap: 10px;">
        <button class="mc-btn" onclick="setActiveAccount('${acc.uuid}')" ${isActive ? 'disabled' : ''} style="flex:1;">
          ${isActive ? 'ACTIVE' : 'SET ACTIVE'}
        </button>
        <button class="mc-btn" onclick="removeAccount('${acc.uuid}')" style="flex:1;">REMOVE</button>
      </div>
    </div>
  `;
}

function renderSettings(container) {
  const o = state.options || {};
  container.innerHTML = `
    <div style="background: rgba(0,0,0,0.7); padding: 20px; max-width: 500px;">
      <div style="font-family: var(--font-pixel); font-size: 14px; margin-bottom: 16px;">Settings</div>

      <div class="form-group">
        <label class="form-label">Java Path</label>
        <input class="form-input" type="text" id="optJavaPath" value="${escapeHtml(o.javaPath || '')}" placeholder="Auto-detect">
      </div>

      <div class="form-group">
        <label class="form-label">JVM Arguments</label>
        <input class="form-input" type="text" id="optJvmArgs" value="${escapeHtml(o.jvmArguments || '')}" placeholder="-XX:+UseG1GC">
      </div>

      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
        <div class="form-group">
          <label class="form-label">Min Memory (MB)</label>
          <input class="form-input" type="number" id="optMinMem" value="${o.minMemory || 1024}" min="256" step="256">
        </div>
        <div class="form-group">
          <label class="form-label">Max Memory (MB)</label>
          <input class="form-input" type="number" id="optMaxMem" value="${o.maxMemory || 4096}" min="256" step="256">
        </div>
      </div>

      <button class="mc-btn" onclick="saveSettings()" style="width: 100%; margin-top: 12px;">SAVE SETTINGS</button>
    </div>
  `;
}

function renderPlayerInfo() {
  if (state.activeAccount) {
    const avatarUuid = String(state.activeAccount.uuid || '').replace(/-/g, '');
    $('playerAvatar').src = `https://mc-heads.net/avatar/${avatarUuid}/24`;
    $('playerName').textContent = state.activeAccount.username;
    $('playerInfo').style.display = 'flex';
  } else {
    $('playerInfo').style.display = 'none';
  }
}

function render() {
  renderSidebar();
  renderBottomBar();
  renderContent();
  renderPlayerInfo();
}

function renderRankedMode(liveData) {
  const data = state.rankedData;
  const matches = state.rankedMatches || [];
  const debug = window.DEBUG_RANKED_STATS === true;

  function coerceNumber(value) {
    if (typeof value === 'number' && isFinite(value)) return value;
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (trimmed !== '' && isFinite(Number(trimmed))) return Number(trimmed);
    }
    if (value && typeof value === 'object') {
      for (const key of ['ranked', 'overall', 'total', 'value', 'count']) {
        const maybe = coerceNumber(value[key]);
        if (maybe !== null && maybe !== undefined) return maybe;
      }
      for (const v of Object.values(value)) {
        const maybe = coerceNumber(v);
        if (maybe !== null && maybe !== undefined) return maybe;
      }
    }
    return null;
  }

  function pickSeasonStats(seasons) {
    if (!seasons) return null;
    if (Array.isArray(seasons)) {
      const items = seasons.filter(Boolean);
      if (!items.length) return null;
      const withSeason = items.filter(s => typeof s.season === 'number');
      if (withSeason.length) return withSeason.sort((a, b) => b.season - a.season)[0];
      return items[0];
    }
    if (typeof seasons === 'object') {
      const keys = Object.keys(seasons).filter(k => String(Number(k)) === k);
      if (keys.length) {
        const maxKey = keys.map(Number).sort((a, b) => b - a)[0];
        return seasons[String(maxKey)] || null;
      }
      return seasons.current || seasons.latest || seasons.season || seasons;
    }
    return null;
  }

  function stat(stats, ...keys) {
    for (const key of keys) {
      const n = coerceNumber(stats?.[key]);
      if (n !== null && n !== undefined) return n;
    }
    return null;
  }

  const rankedInstances = state.instances.filter(i => i.isMCSRRanked);
  const listEl = $('rankedInstanceList');
  listEl.innerHTML = '';
  const addBtn = document.createElement('button');
  addBtn.className = 'add-instance-btn';
  addBtn.innerHTML = `<span class="material-symbols-rounded" style="font-size: 14px;">add</span> Create Ranked`;
  addBtn.onclick = openRankedCreateModal;
  listEl.appendChild(addBtn);
  rankedInstances.forEach(inst => {
    const div = document.createElement('div');
    div.className = `instance-item ${inst.id === state.rankedSelectedId ? 'selected' : ''}`;
    div.innerHTML = `
      <div class="instance-icon" style="border-color: var(--ranked-gold-dark);"><img src="icons/trophe.png" class="pixelated instance-icon-img"></div>
      <div class="instance-meta">
        <div class="instance-name">${escapeHtml(inst.displayName || inst.id)}</div>
        <div class="instance-version">${escapeHtml(inst.minecraftVersion)}</div>
      </div>
      ${inst.isRunning ? '<div class="instance-status"></div>' : ''}
    `;
    div.onclick = () => {
      state.rankedSelectedId = inst.id;
      renderRankedBottomBar();
      listEl.querySelectorAll('.instance-item').forEach(el => el.classList.remove('selected'));
      div.classList.add('selected');
    };
    div.oncontextmenu = (e) => {
      e.preventDefault();
      if (typeof window.openInstanceContextMenu === 'function') window.openInstanceContextMenu(e, inst);
    };
    listEl.appendChild(div);
  });
  if (!state.rankedSelectedId && rankedInstances.length) {
    state.rankedSelectedId = rankedInstances[0].id;
  }
  if (data) {
    const uuid = data.uuid?.replace(/-/g, '') || '';
    const seasonStats =
      pickSeasonStats(state.rankedSeasons) ||
      data.seasonResult ||
      data.statistics?.season ||
      data.statistics ||
      data.stats ||
      data.seasonStats ||
      {};

    window.ensureRankedSkinViewer?.({ uuid, username: data.nickname || 'Unknown' });
    $('rankedUsername').textContent = data.nickname || 'Unknown';
    $('rankedElo').textContent = data.eloRate?.toLocaleString() || '---';
    $('rankedRankText').textContent = data.eloRank ? `#${data.eloRank} Global` : '---';

    let wins = stat(seasonStats, 'wins', 'win');
    let losses = stat(seasonStats, 'loses', 'losses');
    let playedMatches = stat(seasonStats, 'playedMatches', 'games', 'matches');
    let bestTime = stat(seasonStats, 'bestTime', 'best');
    let avgTime = stat(seasonStats, 'avgTime', 'averageTime', 'avg');
    let winStreak = stat(seasonStats, 'currentWinStreak', 'streak', 'winStreak');

    if ((wins == null || losses == null || playedMatches == null || bestTime == null || avgTime == null) && Array.isArray(matches)) {
      const myUuid = data.uuid;
      const finished = matches.filter(m => m?.result && typeof m.result.time === 'number' && isFinite(m.result.time));
      const decided = finished.filter(m => typeof m.result.uuid === 'string' && m.result.uuid.length > 0);
      const w = decided.filter(m => m.result.uuid === myUuid);
      const l = decided.filter(m => m.result.uuid !== myUuid);

      if (wins == null) wins = w.length;
      if (losses == null) losses = l.length;
      if (playedMatches == null) playedMatches = matches.length;

      const winTimes = w.map(m => m.result.time).filter(t => typeof t === 'number' && isFinite(t));
      if (bestTime == null && winTimes.length) bestTime = Math.min(...winTimes);
      if (avgTime == null && winTimes.length) avgTime = Math.round(winTimes.reduce((a, b) => a + b, 0) / winTimes.length);
    }

    if (debug) {
      console.log('[ranked] user data:', data);
      console.log('[ranked] seasons raw:', state.rankedSeasons);
      console.log('[ranked] picked seasonStats:', seasonStats);
      console.log('[ranked] computed:', { wins, losses, playedMatches, bestTime, avgTime, winStreak });
    }

    $('statWins').textContent = wins ?? '-';
    $('statLosses').textContent = losses ?? '-';
    $('statWinrate').textContent = (wins != null && losses != null && (wins + losses) > 0)
      ? `${Math.round(wins / (wins + losses) * 100)}%` : '-';
    $('statBestTime').textContent = bestTime != null ? formatTime(bestTime) : '-';
    $('statGames').textContent = playedMatches ?? '-';
    $('statRank').textContent = data.eloRank ? `#${data.eloRank}` : '-';
    $('statAvgTime').textContent = avgTime != null ? formatTime(avgTime) : '-';
    $('statStreak').textContent = winStreak ?? '-';

    const seasonNo = coerceNumber(seasonStats?.season) ?? coerceNumber(data?.seasonResult?.season);
    $('rankedSeason').textContent = `Season ${seasonNo ?? '?'}`;
  }

  if (liveData) {
    $('rankedOnline').textContent = `${liveData.playerCount || 0} online`;
  }

  const matchListEl = $('rankedMatchList');
  matchListEl.innerHTML = '';

  matches.forEach(match => {
    const isWin = match.result?.uuid === data?.uuid;
    const opponent = match.players?.find(p => p.uuid !== data?.uuid);
    const eloChange = match.changes?.find(c => c.uuid === data?.uuid)?.change;

    const el = document.createElement('div');
    el.className = 'match-item';
    el.innerHTML = `
      <div class="match-result ${isWin ? 'win' : 'loss'}">${isWin ? 'W' : 'L'}</div>
      <div class="match-opponent">
        <div class="match-opponent-name">vs ${escapeHtml(opponent?.nickname || '?')}</div>
        <div class="match-opponent-elo">${opponent?.eloRate || ''}</div>
      </div>
      <div class="match-time-value">${formatTime(match.result?.time)}</div>
      <div class="match-elo-change ${eloChange > 0 ? 'positive' : 'negative'}">${eloChange > 0 ? '+' : ''}${eloChange || 0}</div>
    `;
    matchListEl.appendChild(el);
  });

  renderRankedBottomBar();
}

function renderRankedBottomBar() {
  const inst = state.instances.find(i => i.id === state.rankedSelectedId);
  if (inst) {
    const isLaunchingThis = state.launching && state.launchingInstanceId === inst.id;
    $('rankedSelectedName').textContent = inst.displayName || inst.id;
    $('rankedSelectedDetail').textContent = `${inst.type || 'Fabric'} ${inst.minecraftVersion}`;
    $('rankedPlayBtn').disabled = inst.isRunning || state.launching;
    $('rankedPlayBtn').textContent = inst.isRunning ? 'RUNNING' : isLaunchingThis ? 'LAUNCHING...' : 'PLAY RANKED';
  } else {
    $('rankedSelectedName').textContent = 'Select a ranked instance';
    $('rankedSelectedDetail').textContent = '-';
    $('rankedPlayBtn').disabled = true;
    $('rankedPlayBtn').textContent = 'PLAY RANKED';
  }
}
window.renderSidebar = renderSidebar;
window.renderBottomBar = renderBottomBar;
window.renderContent = renderContent;
window.renderAccountDetail = renderAccountDetail;
window.renderSettings = renderSettings;
window.renderPlayerInfo = renderPlayerInfo;
window.render = render;
window.renderRankedMode = renderRankedMode;
window.renderRankedBottomBar = renderRankedBottomBar;
