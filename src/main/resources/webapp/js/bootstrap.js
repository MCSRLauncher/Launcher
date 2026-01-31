(async function bootstrap() {
  const mount = document.getElementById('appMount');
  if (!mount) return;

  try {
    const res = await fetch('pages/app.html', { cache: 'no-store' });
    if (!res.ok) throw new Error(`Failed to load UI (${res.status})`);
    mount.innerHTML = await res.text();
  } catch (e) {
    console.error(e);
    mount.innerHTML = `<div style="padding:24px;color:#fff;font-family:system-ui;">Failed to load UI. Check logs.</div>`;
    return;
  }

  window.appInit?.();
})();

