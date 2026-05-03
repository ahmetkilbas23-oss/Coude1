'use strict';

const socket = io();

// ── DOM refs ─────────────────────────────────────────────────────────────────
const lobbyScreen   = document.getElementById('screen-lobby');
const gameScreen    = document.getElementById('screen-game');
const deathScreen   = document.getElementById('screen-death');
const canvas        = document.getElementById('canvas');
const ctx           = canvas.getContext('2d');
const minimap       = document.getElementById('minimap');
const mctx          = minimap.getContext('2d');
const nameInput     = document.getElementById('name-input');
const btnJoin       = document.getElementById('btn-join');
const btnRespawn    = document.getElementById('btn-respawn');
const btnQuit       = document.getElementById('btn-quit');
const btnBoost      = document.getElementById('btn-boost');
const btnLb         = document.getElementById('btn-lb');
const coinCountEl   = document.getElementById('coin-count');
const lenCountEl    = document.getElementById('len-count');
const killCountEl   = document.getElementById('kill-count');
const hudNameEl     = document.getElementById('hud-name');
const lbPanel       = document.getElementById('leaderboard');
const lbList        = document.getElementById('lb-list');
const toastEl       = document.getElementById('toast');
const deathKiller   = document.getElementById('death-killer');
const deathLength   = document.getElementById('death-length');
const deathKills    = document.getElementById('death-kills');
const deathCoinsH   = document.getElementById('death-coins-have');
const entryFeeDisp  = document.getElementById('entry-fee-display');
const onlineCountEl = document.getElementById('online-count');
const startCoinsEl  = document.getElementById('start-coins-display');
const boostFill     = document.getElementById('boost-fill');

// ── State ────────────────────────────────────────────────────────────────────
let myId       = null;
let myCoins    = 0;
let myKills    = 0;
let myColor    = '#33ff33';
let myName     = '';
let mapW       = 220;
let mapH       = 220;
let CELL       = 10;
let entryFee   = 50;
let tickMs     = 90;
let camera     = { x: 0, y: 0 };
let lbVisible  = true;
let boosting   = false;
let prevState  = null;
let curState   = null;
let stateTime  = 0;
let particles  = [];
let pickupFx   = [];
let frameCount = 0;
let alive      = false;

// LCD palette
const LCD_BG    = '#8bac0f';
const LCD_BG2   = '#9bbc0f';
const LCD_DARK  = '#0f380f';
const LCD_MID   = '#306230';
const LCD_LIGHT = '#cadc9f';

// ── Canvas sizing ────────────────────────────────────────────────────────────
function resizeCanvas() {
  const playArea = document.getElementById('play-area');
  if (!playArea) return;
  const rect = playArea.getBoundingClientRect();
  const dpr = window.devicePixelRatio || 1;
  canvas.width  = Math.floor(rect.width * dpr);
  canvas.height = Math.floor(rect.height * dpr);
  canvas.style.width  = rect.width + 'px';
  canvas.style.height = rect.height + 'px';
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

  minimap.width  = 60;
  minimap.height = 60;
}

// ── Show/hide screens ────────────────────────────────────────────────────────
function showScreen(name) {
  lobbyScreen.classList.add('hidden');
  gameScreen.classList.add('hidden');
  deathScreen.classList.add('hidden');
  if (name === 'lobby')  lobbyScreen.classList.remove('hidden');
  if (name === 'game')   { gameScreen.classList.remove('hidden'); requestAnimationFrame(resizeCanvas); }
  if (name === 'death')  deathScreen.classList.remove('hidden');
}

// ── Toast ────────────────────────────────────────────────────────────────────
let toastTimer = null;
function showToast(msg, duration = 2000) {
  toastEl.textContent = msg;
  toastEl.classList.remove('hidden');
  toastEl.style.animation = 'none';
  toastEl.offsetWidth;
  toastEl.style.animation = '';
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.add('hidden'), duration);
}

// ── Audio (Web Audio chiptune beeps) ─────────────────────────────────────────
let audioCtx = null;
function ensureAudio() {
  if (!audioCtx) {
    try { audioCtx = new (window.AudioContext || window.webkitAudioContext)(); } catch (_) {}
  }
}
function beep(freq = 880, dur = 0.08, vol = 0.12, type = 'square') {
  ensureAudio();
  if (!audioCtx) return;
  try {
    const osc  = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = type;
    osc.frequency.value = freq;
    gain.gain.setValueAtTime(vol, audioCtx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + dur);
    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start();
    osc.stop(audioCtx.currentTime + dur);
  } catch (_) {}
}
function beepEat()  { beep(720, 0.04, 0.08); }
function beepCoin() { beep(880, 0.06, 0.10); setTimeout(() => beep(1320, 0.06, 0.08), 50); }
function beepDie()  { beep(220, 0.15, 0.18, 'sawtooth'); setTimeout(() => beep(110, 0.3, 0.14, 'sawtooth'), 80); }
function beepKill() { beep(660, 0.06); setTimeout(() => beep(990, 0.06), 50); setTimeout(() => beep(1320, 0.1), 100); }
function beepJoin() { beep(440, 0.08); setTimeout(() => beep(660, 0.08), 60); setTimeout(() => beep(880, 0.12), 120); }

// ── Camera ───────────────────────────────────────────────────────────────────
function updateCamera(head) {
  if (!head) return;
  const targetX = head.x * CELL - canvas.clientWidth  / 2 + CELL / 2;
  const targetY = head.y * CELL - canvas.clientHeight / 2 + CELL / 2;
  camera.x += (targetX - camera.x) * 0.25;
  camera.y += (targetY - camera.y) * 0.25;
  camera.x = Math.max(-20, Math.min(camera.x, mapW * CELL - canvas.clientWidth + 20));
  camera.y = Math.max(-20, Math.min(camera.y, mapH * CELL - canvas.clientHeight + 20));
}

// ── Helpers ──────────────────────────────────────────────────────────────────
function px(v) { return Math.round(v); }
function worldToScreen(wx, wy) {
  return { sx: wx * CELL - camera.x, sy: wy * CELL - camera.y };
}
function inView(wx, wy, pad = 2) {
  const { sx, sy } = worldToScreen(wx, wy);
  return sx > -CELL * pad && sy > -CELL * pad &&
         sx < canvas.clientWidth + CELL * pad && sy < canvas.clientHeight + CELL * pad;
}
function hexToRgb(hex) {
  const m = hex.match(/^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i);
  return m ? { r: parseInt(m[1],16), g: parseInt(m[2],16), b: parseInt(m[3],16) } : { r: 51, g: 255, b: 51 };
}
function mixColor(hex1, hex2, t) {
  const c1 = hexToRgb(hex1), c2 = hexToRgb(hex2);
  const r = Math.round(c1.r + (c2.r - c1.r) * t);
  const g = Math.round(c1.g + (c2.g - c1.g) * t);
  const b = Math.round(c1.b + (c2.b - c1.b) * t);
  return `rgb(${r},${g},${b})`;
}

// ── Interpolate body between prev/cur states ─────────────────────────────────
function lerp(a, b, t) { return a + (b - a) * t; }

function interpBodies(t) {
  if (!curState) return [];
  if (!prevState) return curState.players.map(p => ({ ...p, ibody: p.body }));

  const prevMap = {};
  prevState.players.forEach(p => { prevMap[p.id] = p; });

  return curState.players.map(cur => {
    const prev = prevMap[cur.id];
    if (!prev || !prev.body) return { ...cur, ibody: cur.body };

    const ibody = cur.body.map((seg, i) => {
      const pSeg = prev.body[i] || prev.body[prev.body.length - 1] || seg;
      // Avoid wrap-around interp glitches with large deltas
      const dx = seg.x - pSeg.x;
      const dy = seg.y - pSeg.y;
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
        return { x: seg.x, y: seg.y };
      }
      return {
        x: lerp(pSeg.x, seg.x, t),
        y: lerp(pSeg.y, seg.y, t)
      };
    });
    return { ...cur, ibody };
  });
}

// ── Drawing ──────────────────────────────────────────────────────────────────
function drawBackground() {
  ctx.fillStyle = LCD_BG;
  ctx.fillRect(0, 0, canvas.clientWidth, canvas.clientHeight);

  // subtle dot grid
  ctx.fillStyle = 'rgba(48, 98, 48, 0.18)';
  const startX = Math.floor(camera.x / CELL);
  const endX   = Math.ceil((camera.x + canvas.clientWidth) / CELL);
  const startY = Math.floor(camera.y / CELL);
  const endY   = Math.ceil((camera.y + canvas.clientHeight) / CELL);
  for (let x = startX; x <= endX; x += 4) {
    for (let y = startY; y <= endY; y += 4) {
      const sx = x * CELL - camera.x;
      const sy = y * CELL - camera.y;
      ctx.fillRect(sx, sy, 1, 1);
    }
  }
}

function drawWalls() {
  ctx.fillStyle = LCD_DARK;
  const t = 6;
  ctx.fillRect(-camera.x - t * CELL, -camera.y - t * CELL, t * CELL, mapH * CELL + t * 2 * CELL);
  ctx.fillRect(mapW * CELL - camera.x, -camera.y - t * CELL, t * CELL, mapH * CELL + t * 2 * CELL);
  ctx.fillRect(-camera.x - t * CELL, -camera.y - t * CELL, (mapW + t * 2) * CELL, t * CELL);
  ctx.fillRect(-camera.x - t * CELL, mapH * CELL - camera.y, (mapW + t * 2) * CELL, t * CELL);

  // Inner border with pixel pattern
  ctx.fillStyle = LCD_MID;
  for (let x = 0; x < mapW; x += 2) {
    const sx = x * CELL - camera.x;
    ctx.fillRect(sx, -camera.y - 2, CELL, 2);
    ctx.fillRect(sx, mapH * CELL - camera.y, CELL, 2);
  }
  for (let y = 0; y < mapH; y += 2) {
    const sy = y * CELL - camera.y;
    ctx.fillRect(-camera.x - 2, sy, 2, CELL);
    ctx.fillRect(mapW * CELL - camera.x, sy, 2, CELL);
  }
}

function drawFood(foodArr) {
  const pulse = 0.5 + 0.5 * Math.sin(frameCount * 0.15);
  const s = Math.max(2, CELL - 4);
  ctx.fillStyle = LCD_DARK;
  foodArr.forEach((f, i) => {
    if (!inView(f.x, f.y)) return;
    const { sx, sy } = worldToScreen(f.x, f.y);
    // tiny pulse
    const offset = (Math.sin(frameCount * 0.1 + i * 0.5) * 0.5) | 0;
    ctx.fillRect(sx + 2, sy + 2 + offset, s, s);
  });
}

function drawCoinFood(arr) {
  const pulse = 0.5 + 0.5 * Math.sin(frameCount * 0.2);
  arr.forEach((f, i) => {
    if (!inView(f.x, f.y)) return;
    const { sx, sy } = worldToScreen(f.x, f.y);
    const cx = sx + CELL / 2;
    const cy = sy + CELL / 2;

    // outer diamond (dark)
    ctx.fillStyle = LCD_DARK;
    ctx.beginPath();
    ctx.moveTo(cx, sy + 1);
    ctx.lineTo(sx + CELL - 1, cy);
    ctx.lineTo(cx, sy + CELL - 1);
    ctx.lineTo(sx + 1, cy);
    ctx.closePath();
    ctx.fill();

    // inner pulse
    ctx.fillStyle = LCD_MID;
    const r = 1 + pulse * 1.5;
    ctx.fillRect(cx - r, cy - r, r * 2, r * 2);

    // sparkle
    if ((frameCount + i * 7) % 30 < 4) {
      ctx.fillStyle = LCD_DARK;
      ctx.fillRect(cx - 2, cy - 4, 1, 1);
      ctx.fillRect(cx + 1, cy + 2, 1, 1);
    }
  });
}

function drawSnake(player, isMe) {
  const body = player.ibody || player.body;
  if (!body || body.length === 0) return;

  const color = player.color || '#33ff33';
  const dir = player.dir || 'RIGHT';

  // Boost trail glow particles
  if (player.boost && body.length > 1) {
    const head = body[0];
    if (inView(head.x, head.y)) {
      const { sx, sy } = worldToScreen(head.x, head.y);
      ctx.fillStyle = mixColor(color, LCD_BG, 0.3);
      const r = CELL * 0.7;
      ctx.globalAlpha = 0.3;
      ctx.beginPath();
      ctx.arc(sx + CELL/2, sy + CELL/2, r, 0, Math.PI * 2);
      ctx.fill();
      ctx.globalAlpha = 1;
    }
  }

  // Draw body segments tail to head
  for (let i = body.length - 1; i >= 0; i--) {
    const seg = body[i];
    if (!inView(seg.x, seg.y, 3)) continue;
    const { sx, sy } = worldToScreen(seg.x, seg.y);
    const isHead = i === 0;

    if (isHead) {
      // Head: fully filled with eyes facing direction
      ctx.fillStyle = LCD_DARK;
      ctx.fillRect(sx, sy, CELL, CELL);

      // Inner head color
      ctx.fillStyle = color;
      ctx.fillRect(sx + 1, sy + 1, CELL - 2, CELL - 2);

      // Eyes based on direction
      ctx.fillStyle = LCD_DARK;
      const eyeSize = Math.max(2, Math.floor(CELL / 4));
      let e1x, e1y, e2x, e2y;
      switch (dir) {
        case 'UP':
          e1x = sx + 2; e1y = sy + 1;
          e2x = sx + CELL - 2 - eyeSize; e2y = sy + 1;
          break;
        case 'DOWN':
          e1x = sx + 2; e1y = sy + CELL - 1 - eyeSize;
          e2x = sx + CELL - 2 - eyeSize; e2y = sy + CELL - 1 - eyeSize;
          break;
        case 'LEFT':
          e1x = sx + 1; e1y = sy + 2;
          e2x = sx + 1; e2y = sy + CELL - 2 - eyeSize;
          break;
        default: // RIGHT
          e1x = sx + CELL - 1 - eyeSize; e1y = sy + 2;
          e2x = sx + CELL - 1 - eyeSize; e2y = sy + CELL - 2 - eyeSize;
      }
      ctx.fillRect(e1x, e1y, eyeSize, eyeSize);
      ctx.fillRect(e2x, e2y, eyeSize, eyeSize);
    } else {
      // Body: alternating shade for retro pattern
      const dark = i % 2 === 0;
      ctx.fillStyle = LCD_DARK;
      ctx.fillRect(sx, sy, CELL, CELL);
      ctx.fillStyle = dark ? color : mixColor(color, LCD_DARK, 0.35);
      ctx.fillRect(sx + 1, sy + 1, CELL - 2, CELL - 2);
    }
  }

  // Name above head (only for others; mine uses HUD)
  if (!isMe) {
    const head = body[0];
    if (inView(head.x, head.y, 4)) {
      const { sx, sy } = worldToScreen(head.x, head.y);
      ctx.fillStyle = LCD_DARK;
      ctx.font = `bold 6px 'Press Start 2P', monospace`;
      ctx.textAlign = 'center';
      ctx.fillText(player.name, sx + CELL/2, sy - 3);
    }
  }
}

// ── Particles (death explosions, pickup sparkles) ────────────────────────────
function spawnExplosion(wx, wy, color) {
  const c = hexToRgb(color);
  for (let i = 0; i < 30; i++) {
    const a = (Math.PI * 2 * i) / 30 + Math.random() * 0.3;
    const speed = 1 + Math.random() * 3;
    particles.push({
      x: wx * CELL + CELL/2,
      y: wy * CELL + CELL/2,
      vx: Math.cos(a) * speed,
      vy: Math.sin(a) * speed,
      life: 30 + Math.random() * 20,
      maxLife: 50,
      color: `rgb(${c.r},${c.g},${c.b})`,
      size: 1 + Math.random() * 2
    });
  }
}

function updateParticles() {
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.x += p.vx;
    p.y += p.vy;
    p.vx *= 0.92;
    p.vy *= 0.92;
    p.life--;
    if (p.life <= 0) particles.splice(i, 1);
  }
}

function drawParticles() {
  particles.forEach(p => {
    const sx = p.x - camera.x;
    const sy = p.y - camera.y;
    if (sx < -10 || sx > canvas.clientWidth + 10 || sy < -10 || sy > canvas.clientHeight + 10) return;
    const alpha = Math.min(1, p.life / 25);
    ctx.globalAlpha = alpha;
    ctx.fillStyle = p.color;
    ctx.fillRect(sx | 0, sy | 0, p.size, p.size);
    ctx.fillStyle = LCD_DARK;
    ctx.fillRect((sx | 0) - 1, (sy | 0) - 1, 1, 1);
  });
  ctx.globalAlpha = 1;
}

// ── Mini-map ─────────────────────────────────────────────────────────────────
function drawMinimap() {
  if (!curState) return;
  mctx.clearRect(0, 0, minimap.width, minimap.height);
  mctx.fillStyle = 'rgba(15, 56, 15, 0.7)';
  mctx.fillRect(0, 0, minimap.width, minimap.height);
  mctx.strokeStyle = LCD_LIGHT;
  mctx.lineWidth = 1;
  mctx.strokeRect(0.5, 0.5, minimap.width - 1, minimap.height - 1);

  const sx = minimap.width / mapW;
  const sy = minimap.height / mapH;

  // Coin food (subtle dots)
  mctx.fillStyle = LCD_BG2;
  curState.coinFood.forEach(f => {
    mctx.fillRect((f.x * sx) | 0, (f.y * sy) | 0, 1, 1);
  });

  // Players
  curState.players.forEach(p => {
    const head = p.body[0];
    if (!head) return;
    mctx.fillStyle = p.color;
    const isMe = p.id === myId;
    const r = isMe ? 3 : 2;
    mctx.fillRect((head.x * sx - r/2) | 0, (head.y * sy - r/2) | 0, r, r);
    if (isMe) {
      // Pulse ring around me
      mctx.strokeStyle = LCD_LIGHT;
      mctx.lineWidth = 1;
      mctx.beginPath();
      const pr = 4 + Math.sin(frameCount * 0.2) * 1.5;
      mctx.arc(head.x * sx, head.y * sy, pr, 0, Math.PI * 2);
      mctx.stroke();
    }
  });
}

// ── Render loop ──────────────────────────────────────────────────────────────
function animate() {
  frameCount++;
  if (alive && curState) {
    const now = performance.now();
    const dt = Math.min(1, (now - stateTime) / tickMs);

    const interp = interpBodies(dt);
    const me = interp.find(p => p.id === myId);

    ctx.clearRect(0, 0, canvas.clientWidth, canvas.clientHeight);
    drawBackground();
    drawWalls();
    drawFood(curState.food);
    drawCoinFood(curState.coinFood);

    // Draw others first
    interp.forEach(p => { if (p.id !== myId) drawSnake(p, false); });

    if (me && me.ibody && me.ibody[0]) {
      updateCamera(me.ibody[0]);
      drawSnake(me, true);

      // HUD updates
      lenCountEl.textContent = me.body.length;

      // Boost meter (length-based; can boost when long enough)
      const minLen = 8;
      const ratio = Math.min(1, Math.max(0, (me.body.length - minLen) / 30));
      boostFill.style.width = (ratio * 100) + '%';
      boostFill.style.background = ratio > 0 ? '#306230' : '#0f380f';
    }

    updateParticles();
    drawParticles();
    drawMinimap();

    if (lbVisible) renderLeaderboard(curState.leaderboard);
  }

  requestAnimationFrame(animate);
}

function renderLeaderboard(lb) {
  if (!lb) return;
  // Only re-render every few frames to reduce DOM churn
  if (frameCount % 6 !== 0) return;
  lbList.innerHTML = '';
  lb.slice(0, 5).forEach((entry, i) => {
    const li = document.createElement('li');
    if (entry.name === myName) li.classList.add('me');
    li.style.color = entry.color || LCD_LIGHT;
    const nameSpan = document.createElement('span');
    nameSpan.className = 'lb-name';
    nameSpan.textContent = `${i+1}.${entry.name}`;
    const lenSpan = document.createElement('span');
    lenSpan.textContent = entry.length;
    li.appendChild(nameSpan);
    li.appendChild(lenSpan);
    lbList.appendChild(li);
  });
}

// ── Socket events ────────────────────────────────────────────────────────────
socket.on('lobby_info', ({ online, entryFee: ef, startCoins }) => {
  onlineCountEl.textContent = online;
  if (ef) entryFeeDisp.textContent = ef;
  if (startCoins) startCoinsEl.textContent = startCoins;
});

socket.on('joined', (data) => {
  myId      = data.id;
  myColor   = data.color;
  myCoins   = data.coins;
  myKills   = 0;
  mapW      = data.mapWidth;
  mapH      = data.mapHeight;
  CELL      = data.cell;
  entryFee  = data.entryFee;
  tickMs    = data.tickMs || 90;
  alive     = true;

  coinCountEl.textContent = myCoins;
  killCountEl.textContent = '0';
  hudNameEl.textContent   = myName;

  resizeCanvas();
  showScreen('game');
  beepJoin();
});

socket.on('coin_update', ({ coins }) => {
  if (coins !== myCoins) {
    if (coins > myCoins) beepEat();
    myCoins = coins;
    coinCountEl.textContent = myCoins;
  }
});

socket.on('state', (state) => {
  prevState = curState;
  curState  = state;
  stateTime = performance.now();
  if (state.online !== undefined) onlineCountEl.textContent = state.online;
});

socket.on('explosion', ({ x, y, color }) => {
  spawnExplosion(x, y, color || '#33ff33');
});

socket.on('kill_reward', ({ amount, victim }) => {
  myCoins += amount;
  myKills++;
  coinCountEl.textContent = myCoins;
  killCountEl.textContent = myKills;
  showToast(`+${amount} ◆  ${victim} oldu!`, 1800);
  beepKill();
});

socket.on('you_died', ({ coins, length, kills, killer }) => {
  myCoins = coins;
  alive = false;
  beepDie();
  deathKiller.textContent = killer === 'wall' ? '* DUVARA CARPTIN *' : `OLDUREN: ${killer}`;
  deathLength.textContent = `UZUNLUK: ${length}`;
  deathKills.textContent  = `OLDURME: ${kills || 0}`;
  deathCoinsH.textContent = coins;
  btnRespawn.disabled = coins < entryFee;
  btnRespawn.textContent = coins < entryFee ? '[ COIN YOK ]' : '[ TEKRAR ]';
  showScreen('death');
});

socket.on('respawned', ({ coins }) => {
  myCoins = coins;
  myKills = 0;
  alive = true;
  coinCountEl.textContent = myCoins;
  killCountEl.textContent = '0';
  showScreen('game');
  beepJoin();
});

socket.on('not_enough_coins', ({ need, have }) => {
  showToast(`Yetersiz coin: ${have}/${need}`, 2500);
});

socket.on('player_died', ({ name }) => {
  if (name && name !== myName) showToast(`${name} oldu`, 1200);
});

socket.on('player_joined', ({ name }) => {
  if (name && name !== myName) showToast(`${name} katildi`, 1200);
});

// ── Input ────────────────────────────────────────────────────────────────────
let inputDir = null;
function sendDir(dir) {
  if (dir === inputDir) return;
  inputDir = dir;
  socket.emit('dir', { dir });
}

function startBoost() {
  if (boosting) return;
  boosting = true;
  socket.emit('boost', { active: true });
  btnBoost.classList.add('active');
}
function stopBoost() {
  if (!boosting) return;
  boosting = false;
  socket.emit('boost', { active: false });
  btnBoost.classList.remove('active');
}

document.addEventListener('keydown', (e) => {
  switch (e.key) {
    case 'ArrowUp':    case 'w': case 'W': sendDir('UP');    e.preventDefault(); break;
    case 'ArrowDown':  case 's': case 'S': sendDir('DOWN');  e.preventDefault(); break;
    case 'ArrowLeft':  case 'a': case 'A': sendDir('LEFT');  e.preventDefault(); break;
    case 'ArrowRight': case 'd': case 'D': sendDir('RIGHT'); e.preventDefault(); break;
    case ' ': e.preventDefault(); startBoost(); break;
    case 'Tab': e.preventDefault(); toggleLeaderboard(); break;
  }
});
document.addEventListener('keyup', (e) => {
  if (e.key === ' ') stopBoost();
});

// D-pad
['up','down','left','right'].forEach(d => {
  const btn = document.getElementById('dpad-' + d);
  if (!btn) return;
  const dir = d.toUpperCase();
  btn.addEventListener('pointerdown', (e) => { e.preventDefault(); sendDir(dir); });
});

// Boost
btnBoost.addEventListener('pointerdown', (e) => { e.preventDefault(); startBoost(); });
btnBoost.addEventListener('pointerup',   stopBoost);
btnBoost.addEventListener('pointercancel', stopBoost);
btnBoost.addEventListener('pointerleave',  stopBoost);

// Leaderboard toggle
function toggleLeaderboard() {
  lbVisible = !lbVisible;
  lbPanel.style.display = lbVisible ? '' : 'none';
}
btnLb.addEventListener('click', toggleLeaderboard);

// Join
btnJoin.addEventListener('click', () => {
  ensureAudio();
  if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
  const n = nameInput.value.trim() || 'SNAKE';
  myName = n.toUpperCase().slice(0, 10);
  socket.emit('join', { name: myName });
});
nameInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') btnJoin.click();
});

// Respawn / Quit
btnRespawn.addEventListener('click', () => socket.emit('respawn'));
btnQuit.addEventListener('click', () => {
  alive = false;
  showScreen('lobby');
});

// Touch swipe
let touchStart = null;
canvas.addEventListener('touchstart', (e) => {
  e.preventDefault();
  const t = e.touches[0];
  touchStart = { x: t.clientX, y: t.clientY };
}, { passive: false });
canvas.addEventListener('touchend', (e) => {
  e.preventDefault();
  if (!touchStart) return;
  const t   = e.changedTouches[0];
  const dx  = t.clientX - touchStart.x;
  const dy  = t.clientY - touchStart.y;
  const adx = Math.abs(dx), ady = Math.abs(dy);
  if (adx < 8 && ady < 8) return;
  if (adx > ady) sendDir(dx > 0 ? 'RIGHT' : 'LEFT');
  else           sendDir(dy > 0 ? 'DOWN'  : 'UP');
  touchStart = null;
}, { passive: false });

window.addEventListener('resize', () => requestAnimationFrame(resizeCanvas));

// ── Init ─────────────────────────────────────────────────────────────────────
showScreen('lobby');
resizeCanvas();
requestAnimationFrame(animate);
