'use strict';

// ── Socket ───────────────────────────────────────────────────────────────────
const socket = io();

// ── DOM refs ─────────────────────────────────────────────────────────────────
const lobbyScreen  = document.getElementById('screen-lobby');
const gameScreen   = document.getElementById('screen-game');
const deathScreen  = document.getElementById('screen-death');
const canvas       = document.getElementById('canvas');
const ctx          = canvas.getContext('2d');
const nameInput    = document.getElementById('name-input');
const btnJoin      = document.getElementById('btn-join');
const btnRespawn   = document.getElementById('btn-respawn');
const btnQuit      = document.getElementById('btn-quit');
const btnBoost     = document.getElementById('btn-boost');
const btnLb        = document.getElementById('btn-lb');
const coinCountEl  = document.getElementById('coin-count');
const lenCountEl   = document.getElementById('len-count');
const hudNameEl    = document.getElementById('hud-name');
const lbPanel      = document.getElementById('leaderboard');
const lbList       = document.getElementById('lb-list');
const toastEl      = document.getElementById('toast');
const deathKiller  = document.getElementById('death-killer');
const deathLength  = document.getElementById('death-length');
const deathCoinsL  = document.getElementById('death-coins-lost');
const deathCoinsH  = document.getElementById('death-coins-have');
const entryFeeDisp = document.getElementById('entry-fee-display');

// ── State ────────────────────────────────────────────────────────────────────
let myId       = null;
let myCoins    = 0;
let myColor    = '#33ff33';
let myName     = '';
let mapW       = 200;
let mapH       = 200;
let CELL       = 10;
let entryFee   = 50;
let camera     = { x: 0, y: 0 };
let lbVisible  = false;
let boosting   = false;
let lastState  = null;
let coinsPrev  = 0;

// LCD color palette (Nokia green)
const LCD_BG    = '#8bac0f';
const LCD_DARK  = '#0f380f';
const LCD_MID   = '#306230';
const LCD_LIGHT = '#9bbc0f';
const LCD_PIXEL = '#0f380f';

// ── Canvas sizing ────────────────────────────────────────────────────────────
function resizeCanvas() {
  const screen = document.getElementById('screen');
  const hud    = document.getElementById('hud');
  const hint   = document.getElementById('ctrl-hint');
  const avail  = screen.clientHeight - hud.clientHeight - hint.clientHeight - 4;
  canvas.width  = screen.clientWidth;
  canvas.height = Math.max(avail, 100);
}

// ── Show/hide screens ────────────────────────────────────────────────────────
function showScreen(name) {
  lobbyScreen.classList.add('hidden');
  gameScreen.classList.add('hidden');
  deathScreen.classList.add('hidden');
  if (name === 'lobby')  lobbyScreen.classList.remove('hidden');
  if (name === 'game')   gameScreen.classList.remove('hidden');
  if (name === 'death')  deathScreen.classList.remove('hidden');
}

// ── Toast notification ───────────────────────────────────────────────────────
let toastTimer = null;
function showToast(msg, duration = 2500) {
  toastEl.textContent = msg;
  toastEl.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.add('hidden'), duration);
}

// ── Retro beep via Web Audio ─────────────────────────────────────────────────
let audioCtx = null;
function beep(freq = 880, dur = 0.08, vol = 0.15) {
  try {
    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const osc  = audioCtx.createOscillator();
    const gain = audioCtx.createGain();
    osc.type = 'square';
    osc.frequency.value = freq;
    gain.gain.value = vol;
    osc.connect(gain);
    gain.connect(audioCtx.destination);
    osc.start();
    osc.stop(audioCtx.currentTime + dur);
  } catch (_) {}
}
function beepEat()  { beep(660, 0.05, 0.1); }
function beepDie()  { beep(110, 0.3, 0.2); beep(80, 0.5, 0.15); }
function beepKill() { beep(880, 0.1); beep(1100, 0.1, 0.1); }

// ── Camera tracking ──────────────────────────────────────────────────────────
function updateCamera(head) {
  if (!head) return;
  camera.x = head.x * CELL - canvas.width  / 2 + CELL / 2;
  camera.y = head.y * CELL - canvas.height / 2 + CELL / 2;
  camera.x = Math.max(0, Math.min(camera.x, mapW * CELL - canvas.width));
  camera.y = Math.max(0, Math.min(camera.y, mapH * CELL - canvas.height));
}

// ── Draw helpers ─────────────────────────────────────────────────────────────
function px(v) { return Math.round(v) - 0.5; }

function worldToScreen(wx, wy) {
  return { sx: wx * CELL - camera.x, sy: wy * CELL - camera.y };
}

function inView(wx, wy) {
  const { sx, sy } = worldToScreen(wx, wy);
  return sx > -CELL * 2 && sy > -CELL * 2 && sx < canvas.width + CELL * 2 && sy < canvas.height + CELL * 2;
}

function drawGrid() {
  ctx.strokeStyle = LCD_MID;
  ctx.lineWidth = 0.3;
  ctx.globalAlpha = 0.4;

  const startX = Math.floor(camera.x / CELL);
  const endX   = Math.ceil((camera.x + canvas.width) / CELL);
  const startY = Math.floor(camera.y / CELL);
  const endY   = Math.ceil((camera.y + canvas.height) / CELL);

  for (let x = startX; x <= endX; x++) {
    const sx = px(x * CELL - camera.x);
    ctx.beginPath(); ctx.moveTo(sx, 0); ctx.lineTo(sx, canvas.height); ctx.stroke();
  }
  for (let y = startY; y <= endY; y++) {
    const sy = px(y * CELL - camera.y);
    ctx.beginPath(); ctx.moveTo(0, sy); ctx.lineTo(canvas.width, sy); ctx.stroke();
  }
  ctx.globalAlpha = 1;
}

function drawWalls() {
  ctx.fillStyle = LCD_DARK;
  const t = 4;
  // left wall
  ctx.fillRect(-camera.x - t * CELL, -camera.y, t * CELL, mapH * CELL);
  // right wall
  ctx.fillRect(mapW * CELL - camera.x, -camera.y, t * CELL, mapH * CELL);
  // top wall
  ctx.fillRect(-camera.x - t * CELL, -camera.y - t * CELL, (mapW + t * 2) * CELL, t * CELL);
  // bottom wall
  ctx.fillRect(-camera.x - t * CELL, mapH * CELL - camera.y, (mapW + t * 2) * CELL, t * CELL);

  ctx.strokeStyle = LCD_MID;
  ctx.lineWidth = 2;
  ctx.strokeRect(-camera.x, -camera.y, mapW * CELL, mapH * CELL);
}

function drawFood(foodArr, isCoin) {
  foodArr.forEach(f => {
    if (!inView(f.x, f.y)) return;
    const { sx, sy } = worldToScreen(f.x, f.y);
    if (isCoin) {
      // Coin = pixel diamond
      ctx.fillStyle = LCD_DARK;
      ctx.beginPath();
      ctx.moveTo(sx + CELL/2, sy + 1);
      ctx.lineTo(sx + CELL - 1, sy + CELL/2);
      ctx.lineTo(sx + CELL/2, sy + CELL - 1);
      ctx.lineTo(sx + 1, sy + CELL/2);
      ctx.closePath();
      ctx.fill();
      ctx.fillStyle = LCD_MID;
      ctx.fillRect(sx + CELL/2 - 1, sy + CELL/2 - 1, 2, 2);
    } else {
      // Food = small square pixel
      ctx.fillStyle = LCD_DARK;
      const s = Math.max(2, CELL - 4);
      ctx.fillRect(sx + 2, sy + 2, s, s);
    }
  });
}

function drawSnake(player, isMe) {
  const body = player.body;
  if (!body || body.length === 0) return;

  const color = player.color || '#33ff33';
  const darkColor = LCD_DARK;

  for (let i = body.length - 1; i >= 0; i--) {
    const seg = body[i];
    if (!inView(seg.x, seg.y)) continue;
    const { sx, sy } = worldToScreen(seg.x, seg.y);
    const isHead = i === 0;
    const ratio  = i / body.length;

    if (isHead) {
      // Head: darker fill with border
      ctx.fillStyle = darkColor;
      ctx.fillRect(px(sx), px(sy), CELL, CELL);

      // Eyes
      ctx.fillStyle = color;
      if (CELL >= 6) {
        const ew = Math.max(1, Math.floor(CELL / 5));
        ctx.fillRect(px(sx + 2), px(sy + 2), ew, ew);
        ctx.fillRect(px(sx + CELL - 2 - ew), px(sy + 2), ew, ew);
      }
    } else {
      // Body: alternating pixels for retro look
      const shade = (i % 2 === 0) ? color : mixColor(color, LCD_BG, 0.4);
      ctx.fillStyle = darkColor;
      ctx.fillRect(px(sx), px(sy), CELL, CELL);
      ctx.fillStyle = shade;
      ctx.fillRect(px(sx + 1), px(sy + 1), CELL - 2, CELL - 2);
    }

    // Boost effect: flickering outline
    if (isMe && player.boost && isHead) {
      ctx.strokeStyle = color;
      ctx.lineWidth = 1;
      ctx.strokeRect(px(sx - 1), px(sy - 1), CELL + 2, CELL + 2);
    }
  }

  // Name tag above head
  const head = body[0];
  if (inView(head.x, head.y)) {
    const { sx, sy } = worldToScreen(head.x, head.y);
    ctx.fillStyle = isMe ? LCD_DARK : LCD_MID;
    ctx.font = `${Math.max(4, CELL - 4)}px 'Press Start 2P'`;
    ctx.textAlign = 'center';
    ctx.fillText(player.name, sx + CELL/2, sy - 2);
  }
}

function mixColor(hex1, hex2, t) {
  const c1 = hexToRgb(hex1), c2 = hexToRgb(hex2);
  if (!c1 || !c2) return hex1;
  const r = Math.round(c1.r + (c2.r - c1.r) * t);
  const g = Math.round(c1.g + (c2.g - c1.g) * t);
  const b = Math.round(c1.b + (c2.b - c1.b) * t);
  return `rgb(${r},${g},${b})`;
}
function hexToRgb(hex) {
  const m = hex.match(/^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i);
  return m ? { r: parseInt(m[1],16), g: parseInt(m[2],16), b: parseInt(m[3],16) } : null;
}

// ── Main render ───────────────────────────────────────────────────────────────
function render(state) {
  if (!state) return;
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  // LCD background
  ctx.fillStyle = LCD_BG;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  drawGrid();
  drawWalls();
  drawFood(state.food, false);
  drawFood(state.coinFood, true);

  // Draw other players first, then self on top
  const myPlayer = state.players.find(p => p.id === myId);
  state.players.forEach(p => {
    if (p.id !== myId) drawSnake(p, false);
  });
  if (myPlayer) {
    drawSnake(myPlayer, true);
    updateCamera(myPlayer.body[0]);
    // Update HUD
    lenCountEl.textContent = myPlayer.body.length;
  }

  // Leaderboard
  if (lbVisible && state.leaderboard) {
    renderLeaderboard(state.leaderboard);
  }
}

function renderLeaderboard(lb) {
  lbList.innerHTML = '';
  lb.slice(0, 5).forEach((entry, i) => {
    const li = document.createElement('li');
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

// ── Socket events ─────────────────────────────────────────────────────────────
socket.on('joined', (data) => {
  myId      = data.id;
  myColor   = data.color;
  myCoins   = data.coins;
  mapW      = data.mapWidth;
  mapH      = data.mapHeight;
  CELL      = data.cell;
  entryFee  = data.entryFee;
  coinsPrev = myCoins;

  coinCountEl.textContent = myCoins;
  hudNameEl.textContent   = myName;
  entryFeeDisp.textContent = `${entryFee} COIN`;

  resizeCanvas();
  showScreen('game');
  beep(440, 0.1); beep(660, 0.1);
});

socket.on('state', (state) => {
  lastState = state;

  // Track coin changes
  const me = state.players.find(p => p.id === myId);
  if (me) {
    // coins are tracked server-side; we get them via events
  }

  render(state);
});

socket.on('kill_reward', ({ amount, victim }) => {
  myCoins += amount;
  coinCountEl.textContent = myCoins;
  showToast(`+${amount} COIN! ${victim} yendi!`, 2000);
  beepKill();
});

socket.on('you_died', ({ coins, length, killer }) => {
  myCoins = coins;
  beepDie();
  deathKiller.textContent  = killer ? `OLDUREN: ${killer}` : 'DUVARA CARPTIN!';
  deathLength.textContent  = `UZUNLUK: ${length}`;
  deathCoinsL.textContent  = `COIN: ${coins}`;
  deathCoinsH.textContent  = coins >= entryFee ? `TEKRAR OYNAYABILIRSIN` : `YETERSIZ COIN!`;
  btnRespawn.disabled = coins < entryFee;
  showScreen('death');
});

socket.on('respawned', ({ coins }) => {
  myCoins = coins;
  coinCountEl.textContent = myCoins;
  showScreen('game');
  beep(440, 0.1); beep(660, 0.1);
});

socket.on('not_enough_coins', ({ need, have }) => {
  showToast(`Yetersiz coin! ${have}/${need}`, 3000);
});

socket.on('player_died', ({ name }) => {
  if (name !== myName) showToast(`${name} oldu!`, 1500);
});

socket.on('player_joined', ({ name }) => {
  showToast(`${name} katildi!`, 1500);
});

// ── Input handling ────────────────────────────────────────────────────────────
let inputDir = null;

function sendDir(dir) {
  if (dir === inputDir) return;
  inputDir = dir;
  socket.emit('dir', { dir });
}

document.addEventListener('keydown', (e) => {
  switch (e.key) {
    case 'ArrowUp':    case 'w': case 'W': sendDir('UP');    break;
    case 'ArrowDown':  case 's': case 'S': sendDir('DOWN');  break;
    case 'ArrowLeft':  case 'a': case 'A': sendDir('LEFT');  break;
    case 'ArrowRight': case 'd': case 'D': sendDir('RIGHT'); break;
    case ' ':
      e.preventDefault();
      if (!boosting) { boosting = true; socket.emit('boost', { active: true }); }
      break;
    case 'Tab':
      e.preventDefault();
      toggleLeaderboard();
      break;
  }
});

document.addEventListener('keyup', (e) => {
  if (e.key === ' ') {
    boosting = false;
    socket.emit('boost', { active: false });
  }
});

// D-pad buttons
document.getElementById('dpad-up').addEventListener('pointerdown',    () => sendDir('UP'));
document.getElementById('dpad-down').addEventListener('pointerdown',  () => sendDir('DOWN'));
document.getElementById('dpad-left').addEventListener('pointerdown',  () => sendDir('LEFT'));
document.getElementById('dpad-right').addEventListener('pointerdown', () => sendDir('RIGHT'));

// Boost button (hold)
btnBoost.addEventListener('pointerdown', () => {
  boosting = true;
  socket.emit('boost', { active: true });
});
btnBoost.addEventListener('pointerup', () => {
  boosting = false;
  socket.emit('boost', { active: false });
});
btnBoost.addEventListener('pointercancel', () => {
  boosting = false;
  socket.emit('boost', { active: false });
});

// Leaderboard toggle
function toggleLeaderboard() {
  lbVisible = !lbVisible;
  if (!lbVisible) {
    lbPanel.classList.add('hidden');
  } else {
    lbPanel.classList.remove('hidden');
    if (lastState && lastState.leaderboard) {
      renderLeaderboard(lastState.leaderboard);
    }
  }
}
btnLb.addEventListener('click', toggleLeaderboard);

// Join button
btnJoin.addEventListener('click', () => {
  const n = nameInput.value.trim() || 'SNAKE';
  myName = n.toUpperCase();
  socket.emit('join', { name: myName });
});
nameInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') btnJoin.click();
});

// Respawn / Quit
btnRespawn.addEventListener('click', () => {
  socket.emit('respawn');
});
btnQuit.addEventListener('click', () => {
  showScreen('lobby');
});

// ── Touch swipe for mobile ────────────────────────────────────────────────────
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

// ── Resize ────────────────────────────────────────────────────────────────────
window.addEventListener('resize', resizeCanvas);

// ── Init ──────────────────────────────────────────────────────────────────────
showScreen('lobby');
resizeCanvas();
