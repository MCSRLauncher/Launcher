const MCSR_API_BASE = 'https://api.mcsrranked.com';
const MCSR_API_FALLBACK = 'https://mcsrranked.com/api';
const MCSR_API_TIMEOUT_MS = 8000;

async function fetchApiJson(url, timeoutMs = MCSR_API_TIMEOUT_MS) {
  const controller = (typeof AbortController !== 'undefined') ? new AbortController() : null;

  const fetchPromise = (async () => {
    const response = await fetch(url, { cache: 'no-store', signal: controller?.signal });
    if (!response.ok) return null;
    const json = await response.json();
    return json;
  })();

  const timeoutPromise = new Promise((resolve) => {
    setTimeout(() => resolve(null), timeoutMs);
  });

  try {
    const result = await Promise.race([fetchPromise, timeoutPromise]);
    if (result === null && controller) {
      try { controller.abort(); } catch {}
    }
    return result;
  } catch (e) {
    if (controller) {
      try { controller.abort(); } catch {}
    }
    return null;
  }
}

async function fetchWithFallback(path) {
  const primary = await fetchApiJson(`${MCSR_API_BASE}${path}`);
  if (primary?.status === 'success') return primary;
  const fallback = await fetchApiJson(`${MCSR_API_FALLBACK}${path}`);
  return fallback;
}

async function fetchRankedData(username) {
  const json = await fetchWithFallback(`/users/${encodeURIComponent(username)}`);
  if (json?.status === 'success') return json.data;
  return null;
}

async function fetchRecentMatches(username, count = 8) {
  const json = await fetchWithFallback(`/users/${encodeURIComponent(username)}/matches?count=${count}`);
  if (json?.status === 'success') return json.data;
  return [];
}

async function fetchLiveData() {
  const json = await fetchWithFallback('/live');
  if (json?.status === 'success') return json.data;
  return null;
}

async function fetchLeaderboard(count = 10) {
  const json = await fetchWithFallback(`/leaderboard?count=${count}`);
  if (json?.status === 'success') return json.data;
  return [];
}

async function fetchSeasonResults(username) {
  const json = await fetchWithFallback(`/users/${encodeURIComponent(username)}/seasons`);
  if (json?.status === 'success') return json.data;
  return null;
}

window.fetchRankedData = fetchRankedData;
window.fetchRecentMatches = fetchRecentMatches;
window.fetchLiveData = fetchLiveData;
window.fetchLeaderboard = fetchLeaderboard;
window.fetchSeasonResults = fetchSeasonResults;
