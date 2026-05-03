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

// ── Game state ───────────────────────────────────────────────────────────────
let myId      = null;
let myCoins   = 0;
let myKills   = 0;
let myColor   = '#33ff33';
let myName    = '';
let mapW      = 220;
let mapH      = 220;
let CELL      = 10;
let entryFee  = 50;
let tickMs    = 50;
let camera    = { x: 0, y: 0 };
let lbVisible = true;
let boosting  = false;
let alive     = false;
let frameCount = 0;
let particles  = [];

// LCD palette
const LCD_BG    = '#8bac0f';
const LCD_BG2   = '#9bbc0f';
const LCD_DARK  = '#0f380f';
const LCD_MID   = '#306230';
const LCD_LIGHT = '#cadc9f';

// ── Render-delay interpolation buffer ────────────────────────────────────────
// We keep timestamped server states and always render INTERP_DELAY ms in the past.
// This guarantees we always have a prev+next bracket → zero stuttering.
const stateBuffer = [];
const INTERP_DELAY = 100; // ms behind latest state

function pushState(s) {
  stateBuffer.push({ s, t: performance.now() });
  // Keep buffer bounded
  if (stateBuffer.length > 30) stateBuffer.shift();
}

// Returns { prev, cur, alpha } for the render time
function getInterpFrame() {
  if (stateBuffer.length === 0) return null;
  const renderT = performance.now() - INTERP_DELAY;

  // Walk backwards to find the bracket [prev.t <= renderT < cur.t]
  for (let i = stateBuffer.length - 1; i > 0; i--) {
    if (stateBuffer[i - 1].t <= renderT) {
      const prev = stateBuffer[i - 1];
      const cur  = stateBuffer[i];
      const span = cur.t - prev.t;
      const alpha = span > 0 ? Math.max(0, Math.min(1, (renderT - prev.t) / span)) : 1;
      return { prev: prev.s, cur: cur.s, alpha };
    }
  }

  // renderT is before all buffered states — show oldest
  return { prev: null, cur: stateBuffer[0].s, alpha: 1 };
}

// ── Client-side prediction (own snake only) ───────────────────────────────────
// Predict own snake position so input feels instant (no waiting for server tick).
let pred = {
  body:     null,   // predicted body segments
  dir:      'RIGHT',
  nextTick: 0,
  growing:  false
};

function dirVec(dir) {
  return { UP:{x:0,y:-1}, DOWN:{x:0,y:1}, LEFT:{x:-1,y:0}, RIGHT:{x:1,y:0} }[dir] || {x:1,y:0};
}

// Advance prediction up to `now`, one tick at a time
function advancePrediction(now) {
  if (!pred.body || !alive) return;
  while (now >= pred.nextTick) {
    const dv   = dirVec(pred.dir);
    const head = pred.body[0];
    const nx   = head.x + dv.x;
    const ny   = head.y + dv.y;
    if (nx < 0 || nx >= mapW || ny < 0 || ny >= mapH) break; // wall
    const newBody = [{ x: nx, y: ny }, ...pred.body];
    if (!pred.growing) newBody.pop();
    pred.body    = newBody;
    pred.growing = false;
    pred.nextTick += tickMs;
  }
}

// Reconcile prediction with server-confirmed body
function reconcile(serverBody) {
  if (!serverBody || serverBody.length === 0) return;
  if (!pred.body) {
    pred.body     = serverBody;
    pred.nextTick = performance.now() + tickMs;
    return;
  }
  const sh = serverBody[0], ph = pred.body[0];
  const dist = Math.abs(sh.x - ph.x) + Math.abs(sh.y - ph.y);
  if (dist > 3) {
    // Too far off → snap to server
    pred.body     = serverBody;
    pred.nextTick = performance.now() + tickMs;
  }
  // Small or zero diff → keep prediction running
}

// Sub-tick alpha for own snake (how far into the current predicted tick are we?)
function ownAlpha(now) {
  const since = now - (pred.nextTick - tickMs);
  return Math.max(0, Math.min(1, since / tickMs));
}

// ── Canvas sizing ────────────────────────────────────────────────────────────
function resizeCanvas() {
  const pa = document.getElementById('play-area');
  if (!pa) return;
  const r = pa.getBoundingClientRect();
  canvas.width        = r.width  | 0;
  canvas.height       = r.height | 0;
  canvas.style.width  = r.width  + 'px';
  canvas.style.height = r.height + 'px';
  minimap.width  = 60;
  minimap.height = 60;
}

// ── Screen management ────────────────────────────────────────────────────────
function showScreen(name) {
  lobbyScreen.classList.toggle('hidden', name !== 'lobby');
  gameScreen .classList.toggle('hidden', name !== 'game');
  deathScreen.classList.toggle('hidden', name !== 'death');
  if (name === 'game') requestAnimationFrame(resizeCanvas);
}

// ── Toast ────────────────────────────────────────────────────────────────────
let toastTimer = null;
function showToast(msg, ms = 2000) {
  toastEl.textContent = msg;
  toastEl.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.add('hidden'), ms);
}

// ── Audio ────────────────────────────────────────────────────────────────────
let audioCtx = null;
function ensureAudio() {
  if (!audioCtx) try { audioCtx = new (window.AudioContext || window.webkitAudioContext)(); } catch(_){}
}
function beep(freq, dur, vol = 0.12, type = 'square') {
  ensureAudio();
  if (!audioCtx) return;
  try {
    const o = audioCtx.createOscillator(), g = audioCtx.createGain();
    o.type = type; o.frequency.value = freq;
    g.gain.setValueAtTime(vol, audioCtx.currentTime);
    g.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + dur);
    o.connect(g); g.connect(audioCtx.destination);
    o.start(); o.stop(audioCtx.currentTime + dur);
  } catch(_){}
}
function beepEat()  { beep(720, 0.04, 0.08); }
function beepDie()  { beep(220, 0.15, 0.18, 'sawtooth'); setTimeout(()=>beep(110,0.3,0.14,'sawtooth'),80); }
function beepKill() { beep(660,0.06); setTimeout(()=>beep(990,0.06),50); setTimeout(()=>beep(1320,0.1),100); }
function beepJoin() { beep(440,0.08); setTimeout(()=>beep(660,0.08),60); setTimeout(()=>beep(880,0.12),120); }

// ── Camera (smooth follow) ───────────────────────────────────────────────────
function updateCamera(wx, wy) {
  const tx = wx * CELL - canvas.width  / 2 + CELL / 2;
  const ty = wy * CELL - canvas.height / 2 + CELL / 2;
  // Lerp speed: higher = snappier. 0.18 is smooth but responsive.
  camera.x += (tx - camera.x) * 0.18;
  camera.y += (ty - camera.y) * 0.18;
  camera.x = Math.max(-20, Math.min(camera.x, mapW * CELL - canvas.width  + 20));
  camera.y = Math.max(-20, Math.min(camera.y, mapH * CELL - canvas.height + 20));
}

// ── Coordinate helpers ───────────────────────────────────────────────────────
function ws(wx, wy) { return { sx: wx * CELL - camera.x, sy: wy * CELL - camera.y }; }
function inView(wx, wy, pad = 2) {
  const { sx, sy } = ws(wx, wy);
  return sx > -CELL*pad && sy > -CELL*pad && sx < canvas.width+CELL*pad && sy < canvas.height+CELL*pad;
}
function hexRgb(hex) {
  const m = hex.match(/^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i);
  return m ? {r:parseInt(m[1],16),g:parseInt(m[2],16),b:parseInt(m[3],16)} : {r:51,g:255,b:51};
}
function mixColor(h1, h2, t) {
  const a = hexRgb(h1), b = hexRgb(h2);
  return `rgb(${a.r+(b.r-a.r)*t|0},${a.g+(b.g-a.g)*t|0},${a.b+(b.b-a.b)*t|0})`;
}
function lerp(a, b, t) { return a + (b - a) * t; }

// ── Interpolate a player's body between two server states ─────────────────────
function interpBody(prevP, curP, alpha) {
  if (!curP || !curP.body) return [];
  if (!prevP || !prevP.body || alpha >= 1) return curP.body;

  // The snake moves by pushing one new head and popping one tail per tick.
  // The most natural interp: treat as a continuous ribbon.
  // We build the interpolated body by lerping each segment.
  // To avoid the "whipping" at corners, we detect large jumps (> 2 cells) and snap.
  return curP.body.map((seg, i) => {
    const pSeg = prevP.body[i] || prevP.body[prevP.body.length - 1] || seg;
    if (Math.abs(seg.x - pSeg.x) > 2 || Math.abs(seg.y - pSeg.y) > 2) return seg;
    return { x: lerp(pSeg.x, seg.x, alpha), y: lerp(pSeg.y, seg.y, alpha) };
  });
}

// ── Drawing ──────────────────────────────────────────────────────────────────
function drawBg() {
  ctx.fillStyle = LCD_BG;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  // dot grid
  ctx.fillStyle = 'rgba(48,98,48,0.18)';
  const x0 = Math.floor(camera.x / (CELL*4)) * (CELL*4);
  const y0 = Math.floor(camera.y / (CELL*4)) * (CELL*4);
  for (let x = x0; x < camera.x + canvas.width + CELL*4; x += CELL*4) {
    for (let y = y0; y < camera.y + canvas.height + CELL*4; y += CELL*4) {
      ctx.fillRect((x - camera.x)|0, (y - camera.y)|0, 1, 1);
    }
  }
}

function drawWalls() {
  const t = 6;
  ctx.fillStyle = LCD_DARK;
  ctx.fillRect(-camera.x - t*CELL, -camera.y - t*CELL, t*CELL, (mapH+t*2)*CELL);
  ctx.fillRect(mapW*CELL - camera.x, -camera.y - t*CELL, t*CELL, (mapH+t*2)*CELL);
  ctx.fillRect(-camera.x - t*CELL, -camera.y - t*CELL, (mapW+t*2)*CELL, t*CELL);
  ctx.fillRect(-camera.x - t*CELL, mapH*CELL - camera.y, (mapW+t*2)*CELL, t*CELL);
  ctx.fillStyle = LCD_MID;
  for (let x = 0; x < mapW; x += 2) {
    const sx = x*CELL - camera.x;
    ctx.fillRect(sx, -camera.y - 2, CELL, 2);
    ctx.fillRect(sx, mapH*CELL - camera.y, CELL, 2);
  }
  for (let y = 0; y < mapH; y += 2) {
    const sy = y*CELL - camera.y;
    ctx.fillRect(-camera.x - 2, sy, 2, CELL);
    ctx.fillRect(mapW*CELL - camera.x, sy, 2, CELL);
  }
}

function drawFood(arr) {
  const s = Math.max(2, CELL - 4);
  ctx.fillStyle = LCD_DARK;
  arr.forEach((f, i) => {
    if (!inView(f.x, f.y)) return;
    const { sx, sy } = ws(f.x, f.y);
    const off = (Math.sin(frameCount * 0.08 + i * 0.4) * 0.6) | 0;
    ctx.fillRect(sx + 2, sy + 2 + off, s, s);
  });
}

function drawCoinFood(arr) {
  arr.forEach((f, i) => {
    if (!inView(f.x, f.y)) return;
    const { sx, sy } = ws(f.x, f.y);
    const cx = sx + CELL/2, cy = sy + CELL/2;
    const pulse = 0.5 + 0.5 * Math.sin(frameCount * 0.18 + i * 0.5);
    ctx.fillStyle = LCD_DARK;
    ctx.beginPath();
    ctx.moveTo(cx, sy+1); ctx.lineTo(sx+CELL-1, cy); ctx.lineTo(cx, sy+CELL-1); ctx.lineTo(sx+1, cy);
    ctx.closePath(); ctx.fill();
    ctx.fillStyle = LCD_MID;
    const r = 1 + pulse;
    ctx.fillRect((cx - r)|0, (cy - r)|0, r*2|0, r*2|0);
  });
}

function drawSnakeBody(body, color, dir, isMe, boosting) {
  if (!body || body.length === 0) return;
  const dirStr = dir || 'RIGHT';

  // Boost glow
  if (boosting && body.length > 1 && inView(body[0].x, body[0].y)) {
    const { sx, sy } = ws(body[0].x, body[0].y);
    ctx.globalAlpha = 0.25;
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(sx + CELL/2, sy + CELL/2, CELL * 0.9, 0, Math.PI*2);
    ctx.fill();
    ctx.globalAlpha = 1;
  }

  for (let i = body.length - 1; i >= 0; i--) {
    const seg = body[i];
    const { sx, sy } = ws(seg.x, seg.y);
    if (!inView(seg.x, seg.y, 3)) continue;
    const x = sx|0, y = sy|0;

    if (i === 0) {
      // Head
      ctx.fillStyle = LCD_DARK;
      ctx.fillRect(x, y, CELL, CELL);
      ctx.fillStyle = color;
      ctx.fillRect(x+1, y+1, CELL-2, CELL-2);
      // Eyes
      ctx.fillStyle = LCD_DARK;
      const ew = Math.max(2, CELL/4 | 0);
      let e1x, e1y, e2x, e2y;
      switch (dirStr) {
        case 'UP':    e1x=x+2;e1y=y+1;           e2x=x+CELL-2-ew;e2y=y+1;           break;
        case 'DOWN':  e1x=x+2;e1y=y+CELL-1-ew;   e2x=x+CELL-2-ew;e2y=y+CELL-1-ew;  break;
        case 'LEFT':  e1x=x+1;e1y=y+2;            e2x=x+1;         e2y=y+CELL-2-ew; break;
        default:      e1x=x+CELL-1-ew;e1y=y+2;   e2x=x+CELL-1-ew;e2y=y+CELL-2-ew;
      }
      ctx.fillRect(e1x, e1y, ew, ew);
      ctx.fillRect(e2x, e2y, ew, ew);
    } else {
      ctx.fillStyle = LCD_DARK;
      ctx.fillRect(x, y, CELL, CELL);
      ctx.fillStyle = i % 2 === 0 ? color : mixColor(color, LCD_DARK, 0.35);
      ctx.fillRect(x+1, y+1, CELL-2, CELL-2);
    }
  }

  // Name above head for other players
  if (!isMe) {
    const h = body[0];
    if (inView(h.x, h.y, 4)) {
      const { sx, sy } = ws(h.x, h.y);
      ctx.fillStyle = LCD_DARK;
      ctx.font = `bold 6px 'Press Start 2P',monospace`;
      ctx.textAlign = 'center';
      ctx.fillText('', sx + CELL/2, sy - 3); // name drawn in outer loop
    }
  }
}

// ── Particles ────────────────────────────────────────────────────────────────
function spawnExplosion(wx, wy, color) {
  const c = hexRgb(color);
  for (let i = 0; i < 28; i++) {
    const a = Math.PI * 2 * i / 28 + Math.random() * 0.3;
    const spd = 1 + Math.random() * 3;
    particles.push({
      x: wx*CELL + CELL/2, y: wy*CELL + CELL/2,
      vx: Math.cos(a)*spd, vy: Math.sin(a)*spd,
      life: 30 + Math.random()*20,
      color: `rgb(${c.r},${c.g},${c.b})`,
      size: 1 + Math.random()*2 | 0
    });
  }
}

function updateDrawParticles() {
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i];
    p.x += p.vx; p.y += p.vy;
    p.vx *= 0.9; p.vy *= 0.9;
    p.life--;
    if (p.life <= 0) { particles.splice(i, 1); continue; }
    const sx = p.x - camera.x, sy = p.y - camera.y;
    if (sx < -10 || sx > canvas.width+10 || sy < -10 || sy > canvas.height+10) continue;
    ctx.globalAlpha = Math.min(1, p.life / 20);
    ctx.fillStyle = p.color;
    ctx.fillRect(sx|0, sy|0, p.size, p.size);
  }
  ctx.globalAlpha = 1;
}

// ── Mini-map ─────────────────────────────────────────────────────────────────
function drawMinimap(state) {
  if (!state) return;
  const mw = minimap.width, mh = minimap.height;
  mctx.clearRect(0, 0, mw, mh);
  mctx.fillStyle = 'rgba(15,56,15,0.7)';
  mctx.fillRect(0, 0, mw, mh);
  mctx.strokeStyle = LCD_LIGHT;
  mctx.lineWidth = 1;
  mctx.strokeRect(0.5, 0.5, mw-1, mh-1);
  const sx = mw / mapW, sy = mh / mapH;
  // coin food
  mctx.fillStyle = LCD_BG2;
  state.coinFood.forEach(f => mctx.fillRect(f.x*sx|0, f.y*sy|0, 1, 1));
  // players
  state.players.forEach(p => {
    const h = p.body && p.body[0];
    if (!h) return;
    const isMe = p.id === myId;
    mctx.fillStyle = p.color;
    const r = isMe ? 3 : 2;
    mctx.fillRect((h.x*sx - r/2)|0, (h.y*sy - r/2)|0, r, r);
    if (isMe) {
      mctx.strokeStyle = LCD_LIGHT;
      mctx.lineWidth = 1;
      mctx.beginPath();
      mctx.arc(h.x*sx, h.y*sy, 4 + Math.sin(frameCount*0.15)*1.5, 0, Math.PI*2);
      mctx.stroke();
    }
  });
}

// ── Leaderboard ──────────────────────────────────────────────────────────────
let lbLastFrame = -999;
function renderLeaderboard(lb) {
  if (!lb || frameCount - lbLastFrame < 6) return;
  lbLastFrame = frameCount;
  lbList.innerHTML = '';
  lb.slice(0, 5).forEach((e, i) => {
    const li = document.createElement('li');
    if (e.name === myName) li.classList.add('me');
    li.style.color = e.color || LCD_LIGHT;
    const ns = document.createElement('span'); ns.className = 'lb-name'; ns.textContent = `${i+1}.${e.name}`;
    const ls = document.createElement('span'); ls.textContent = e.length;
    li.append(ns, ls); lbList.appendChild(li);
  });
}

// ── Main render loop ──────────────────────────────────────────────────────────
function animate() {
  frameCount++;
  const now = performance.now();

  if (alive) {
    // Advance client prediction
    advancePrediction(now);

    const frame = getInterpFrame();
    const curState = frame ? frame.cur : null;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    drawBg();
    drawWalls();

    if (curState) {
      drawFood(curState.food);
      drawCoinFood(curState.coinFood);

      // Build prev player map for interpolation
      const prevMap = {};
      if (frame && frame.prev) {
        frame.prev.players.forEach(p => { prevMap[p.id] = p; });
      }

      // Draw other players (interpolated)
      curState.players.forEach(p => {
        if (p.id === myId) return;
        const body = interpBody(prevMap[p.id], p, frame ? frame.alpha : 1);
        if (body.length > 0) {
          drawSnakeBody(body, p.color, p.dir, false, p.boost);
          // Name label
          const h = body[0];
          if (inView(h.x, h.y, 4)) {
            const { sx, sy } = ws(h.x, h.y);
            ctx.fillStyle = LCD_DARK;
            ctx.font = 'bold 6px "Press Start 2P",monospace';
            ctx.textAlign = 'center';
            ctx.fillText(p.name, sx + CELL/2, sy - 3);
          }
        }
      });

      // Draw own snake (client-predicted)
      if (pred.body && pred.body.length > 0) {
        // Sub-tick lerp: smoothly advance within current tick
        const alpha = ownAlpha(now);
        const prevBody = curState.players.find(p => p.id === myId)?.body || pred.body;
        // Interpolate between last confirmed position and predicted
        const smoothBody = pred.body.map((seg, i) => {
          const ps = prevBody[i] || prevBody[prevBody.length-1] || seg;
          if (Math.abs(seg.x-ps.x) > 2 || Math.abs(seg.y-ps.y) > 2) return seg;
          return { x: lerp(ps.x, seg.x, alpha), y: lerp(ps.y, seg.y, alpha) };
        });

        const meData = curState.players.find(p => p.id === myId);
        drawSnakeBody(smoothBody, myColor, pred.dir, true, boosting);
        updateCamera(smoothBody[0].x, smoothBody[0].y);

        lenCountEl.textContent = pred.body.length;
        const minLen = 8;
        const ratio = Math.max(0, Math.min(1, (pred.body.length - minLen) / 30));
        boostFill.style.width = (ratio * 100) + '%';
      }

      if (lbVisible) renderLeaderboard(curState.leaderboard);
      if (frameCount % 3 === 0) drawMinimap(curState);
    }

    updateDrawParticles();
  }

  requestAnimationFrame(animate);
}

// ── Socket events ────────────────────────────────────────────────────────────
socket.on('lobby_info', ({ online, entryFee: ef, startCoins }) => {
  onlineCountEl.textContent = online || 0;
  if (ef)         entryFeeDisp.textContent  = ef;
  if (startCoins) startCoinsEl.textContent  = startCoins;
});

socket.on('joined', (data) => {
  myId     = data.id;
  myColor  = data.color;
  myCoins  = data.coins;
  myKills  = 0;
  mapW     = data.mapWidth;
  mapH     = data.mapHeight;
  CELL     = data.cell;
  entryFee = data.entryFee;
  tickMs   = data.tickMs || 50;
  alive    = true;
  pred.body = null; // reset prediction
  stateBuffer.length = 0;

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
  pushState(state);
  if (state.online !== undefined) onlineCountEl.textContent = state.online;

  // Reconcile own prediction with confirmed server body
  const me = state.players.find(p => p.id === myId);
  if (me && me.body) {
    reconcile(me.body);
    // Update prediction dir from server if we don't have one yet
    if (!pred.body) pred.dir = me.dir || pred.dir;
  }
});

socket.on('explosion', ({ x, y, color }) => {
  spawnExplosion(x, y, color || '#33ff33');
});

socket.on('kill_reward', ({ amount, victim }) => {
  myCoins += amount; myKills++;
  coinCountEl.textContent = myCoins;
  killCountEl.textContent = myKills;
  showToast(`+${amount} ◆  ${victim} oldu!`, 1800);
  beepKill();
});

socket.on('you_died', ({ coins, length, kills, killer }) => {
  myCoins = coins;
  alive = false;
  pred.body = null;
  beepDie();
  deathKiller.textContent = killer === 'wall' ? '* DUVARA CARPTIN *' : `OLDUREN: ${killer}`;
  deathLength.textContent = `UZUNLUK: ${length}`;
  deathKills.textContent  = `OLDURME: ${kills || 0}`;
  deathCoinsH.textContent = coins;
  btnRespawn.disabled     = coins < entryFee;
  btnRespawn.textContent  = coins < entryFee ? '[ COIN YOK ]' : '[ TEKRAR ]';
  showScreen('death');
});

socket.on('respawned', ({ coins }) => {
  myCoins = coins; myKills = 0;
  alive = true;
  pred.body = null;
  stateBuffer.length = 0;
  coinCountEl.textContent = myCoins;
  killCountEl.textContent = '0';
  showScreen('game');
  beepJoin();
});

socket.on('not_enough_coins', ({ need, have }) => showToast(`Yetersiz coin: ${have}/${need}`, 2500));
socket.on('player_died',   ({ name }) => { if (name && name !== myName) showToast(`${name} oldu`, 1200); });
socket.on('player_joined', ({ name }) => { if (name && name !== myName) showToast(`${name} katildi`, 1200); });

// ── Input ────────────────────────────────────────────────────────────────────
let inputDir = null;

function sendDir(dir) {
  if (dir === inputDir) return;
  inputDir  = dir;
  pred.dir  = dir; // immediate prediction update
  socket.emit('dir', { dir });
}

function startBoost() {
  if (boosting) return;
  boosting = true;
  socket.emit('boost', { active: true });
}
function stopBoost() {
  if (!boosting) return;
  boosting = false;
  socket.emit('boost', { active: false });
}

document.addEventListener('keydown', (e) => {
  switch (e.key) {
    case 'ArrowUp':   case 'w': case 'W': sendDir('UP');    e.preventDefault(); break;
    case 'ArrowDown': case 's': case 'S': sendDir('DOWN');  e.preventDefault(); break;
    case 'ArrowLeft': case 'a': case 'A': sendDir('LEFT');  e.preventDefault(); break;
    case 'ArrowRight':case 'd': case 'D': sendDir('RIGHT'); e.preventDefault(); break;
    case ' ': e.preventDefault(); startBoost(); break;
    case 'Tab': e.preventDefault(); toggleLeaderboard(); break;
  }
});
document.addEventListener('keyup', (e) => { if (e.key === ' ') stopBoost(); });

['up','down','left','right'].forEach(d => {
  const btn = document.getElementById('dpad-' + d);
  if (!btn) return;
  btn.addEventListener('pointerdown', (e) => { e.preventDefault(); sendDir(d.toUpperCase()); });
});

btnBoost.addEventListener('pointerdown',   (e) => { e.preventDefault(); startBoost(); });
btnBoost.addEventListener('pointerup',     stopBoost);
btnBoost.addEventListener('pointercancel', stopBoost);
btnBoost.addEventListener('pointerleave',  stopBoost);

function toggleLeaderboard() {
  lbVisible = !lbVisible;
  lbPanel.style.display = lbVisible ? '' : 'none';
}
btnLb.addEventListener('click', toggleLeaderboard);

btnJoin.addEventListener('click', () => {
  ensureAudio();
  if (audioCtx?.state === 'suspended') audioCtx.resume();
  const n = nameInput.value.trim() || 'SNAKE';
  myName = n.toUpperCase().slice(0, 10);
  socket.emit('join', { name: myName });
});
nameInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') btnJoin.click(); });

btnRespawn.addEventListener('click', () => socket.emit('respawn'));
btnQuit.addEventListener('click', () => { alive = false; showScreen('lobby'); });

// Touch swipe
let touch0 = null;
canvas.addEventListener('touchstart', (e) => {
  e.preventDefault();
  touch0 = { x: e.touches[0].clientX, y: e.touches[0].clientY };
}, { passive: false });
canvas.addEventListener('touchend', (e) => {
  e.preventDefault();
  if (!touch0) return;
  const dx = e.changedTouches[0].clientX - touch0.x;
  const dy = e.changedTouches[0].clientY - touch0.y;
  const ax = Math.abs(dx), ay = Math.abs(dy);
  if (ax < 8 && ay < 8) return;
  sendDir(ax > ay ? (dx > 0 ? 'RIGHT' : 'LEFT') : (dy > 0 ? 'DOWN' : 'UP'));
  touch0 = null;
}, { passive: false });

window.addEventListener('resize', () => requestAnimationFrame(resizeCanvas));

showScreen('lobby');
resizeCanvas();
requestAnimationFrame(animate);
