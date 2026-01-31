var RankedSkinViewer = (function() {
  var SKINVIEW3D_URL = 'https://cdn.jsdelivr.net/npm/skinview3d@3.4.1/+esm';
  var BLOCKBENCH_URL = 'https://cdn.jsdelivr.net/npm/skinview3d-blockbench@1.0.19/+esm';
  var ANIMATION_URL = 'json/model.animation.json';
  var WIDTH = 120;
  var HEIGHT = 220;

  var viewer = null;
  var animation = null;
  var currentUuid = null;
  var animationData = null;

  function getCanvas() {
    return document.getElementById('playerSkin3D');
  }

  function setFallback(canvas, uuid) {
    canvas.style.backgroundImage = 'url(https://starlightskins.lunareclipse.studio/render/ultimate/' + uuid + '/full)';
    canvas.style.backgroundSize = 'contain';
    canvas.style.backgroundPosition = 'center';
    canvas.style.backgroundRepeat = 'no-repeat';
  }

  function clearFallback(canvas) {
    canvas.style.backgroundImage = '';
  }

  function normalizeAnimation(data) {
    if (!data || !data.animations) return data;

    Object.keys(data.animations).forEach(function(name) {
      var anim = data.animations[name];
      var length = anim.animation_length;
      if (!anim.bones) return;

      Object.keys(anim.bones).forEach(function(boneName) {
        var bone = anim.bones[boneName];
        ['rotation', 'position'].forEach(function(channel) {
          if (!bone[channel]) return;

          var keys = Object.keys(bone[channel]).sort(function(a, b) {
            return parseFloat(a) - parseFloat(b);
          });

          keys.forEach(function(key) {
            var frame = bone[channel][key];
            if (frame.pre == null && frame.post != null) frame.pre = frame.post;
            if (frame.post == null && frame.pre != null) frame.post = frame.pre;
          });

          if (keys.length === 1 && length && !bone[channel][String(length)]) {
            var first = bone[channel][keys[0]];
            bone[channel][String(length)] = {
              pre: first.pre || first.post,
              post: first.post || first.pre,
              lerp_mode: first.lerp_mode
            };
          }
        });
      });
    });

    return data;
  }

  async function loadAnimation() {
    if (animationData) return animationData;

    var response = await fetch(ANIMATION_URL);
    var json = await response.json();
    animationData = normalizeAnimation(json);
    return animationData;
  }

  async function loadSkinview3d() {
    return import(SKINVIEW3D_URL);
  }

  async function loadBlockbench() {
    var mod = await import(BLOCKBENCH_URL);
    return mod.SkinViewBlockbench || mod.default || mod;
  }

  async function waitForVisible(canvas, timeout) {
    timeout = timeout || 2000;
    var start = Date.now();

    while (Date.now() - start < timeout) {
      if (canvas.offsetParent && canvas.offsetWidth > 0) return true;
      await new Promise(requestAnimationFrame);
    }

    return canvas.offsetParent && canvas.offsetWidth > 0;
  }

  async function createViewer(canvas) {
    if (viewer) return viewer;

    var visible = await waitForVisible(canvas);
    if (!visible) return null;

    var skinview3d = await loadSkinview3d();

    viewer = new skinview3d.SkinViewer({
      canvas: canvas,
      width: WIDTH,
      height: HEIGHT,
      pixelRatio: 1,
      enableControls: false,
      renderPaused: false
    });

    viewer.fxaa = true;
    viewer.controls.enabled = false;
    canvas.style.pointerEvents = 'none';

    return viewer;
  }

  async function createAnimation(v) {
    if (animation) return animation;

    var Blockbench = await loadBlockbench();
    var data = await loadAnimation();

    animation = new Blockbench({
      animation: JSON.parse(JSON.stringify(data)),
      animationName: 'idling',
      forceLoop: true,
      bonesOverrides: {
        head: 'head',
        body: 'body',
        rightArm: 'right_arm',
        leftArm: 'left_arm',
        rightLeg: 'right_leg',
        leftLeg: 'left_leg'
      }
    });

    return animation;
  }

  async function loadSkin(v, uuid) {
    try {
      await v.loadSkin('https://crafatar.com/skins/' + uuid);
    } catch (e) {
      await v.loadSkin('https://mc-heads.net/skin/' + uuid);
    }

    try {
      await v.loadCape('https://crafatar.com/capes/' + uuid);
    } catch (e) {
      try {
        await v.loadCape('https://mc-heads.net/cape/' + uuid);
      } catch (e2) {}
    }
  }

  async function show(options) {
    var canvas = getCanvas();
    if (!canvas) return;

    var uuid = (options.uuid || '').replace(/-/g, '');
    if (!uuid) return;

    if (uuid === currentUuid && viewer) return;

    setFallback(canvas, uuid);

    try {
      var v = await createViewer(canvas);
      if (!v) {
        currentUuid = uuid;
        return;
      }

      currentUuid = uuid;
      await loadSkin(v, uuid);

      var anim = await createAnimation(v);
      v.animation = anim;
      v.animation.speed = 1;
      v.animation.paused = false;

      clearFallback(canvas);
    } catch (e) {
      console.error('Skin viewer error:', e);
    }
  }

  function dispose() {
    currentUuid = null;
    animation = null;
    if (viewer) {
      viewer.dispose();
      viewer = null;
    }
  }

  return {
    show: show,
    dispose: dispose
  };
})();

window.ensureRankedSkinViewer = RankedSkinViewer.show;
window.disposeRankedSkinViewer = RankedSkinViewer.dispose;
