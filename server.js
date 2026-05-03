const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const { randomUUID: uuidv4 } = require('crypto');
const path = require('path');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' }
});

app.use(express.static(path.join(__dirname, 'public')));

const MAP_WIDTH = 220;
const MAP_HEIGHT = 220;
const CELL = 10;
const TICK_MS = 90;
const FOOD_COUNT = 100;
const COIN_FOOD_COUNT = 25;
const ENTRY_FEE = 50;
const START_COINS = 200;
const KILL_REWARD_PCT = 0.4;
const FOOD_VALUE = 1;
const COIN_FOOD_VALUE = 10;
const STARTING_LENGTH = 5;
const BOOST_DRAIN = 0.2;

const COLORS = ['#33ff33', '#ffff33', '#ff3333', '#33ffff', '#ff33ff', '#ff9933', '#33ff99', '#ff6699'];

let players = {};
let food = [];
let coinFood = [];
let leaderboard = [];

function rndCell(max) { return Math.floor(Math.random() * max); }

function spawnFood(count, arr, max) {
  while (arr.length < count) {
    arr.push({ x: rndCell(MAP_WIDTH), y: rndCell(MAP_HEIGHT), id: uuidv4() });
  }
}

function initFood() {
  food = [];
  coinFood = [];
  spawnFood(FOOD_COUNT, food, MAP_WIDTH);
  spawnFood(COIN_FOOD_COUNT, coinFood, MAP_WIDTH);
}

function makeSnake(startX, startY) {
  const body = [];
  for (let i = 0; i < STARTING_LENGTH; i++) {
    body.push({ x: startX - i, y: startY });
  }
  return body;
}

function dirVector(dir) {
  const map = { UP: { x: 0, y: -1 }, DOWN: { x: 0, y: 1 }, LEFT: { x: -1, y: 0 }, RIGHT: { x: 1, y: 0 } };
  return map[dir] || { x: 1, y: 0 };
}

function oppositeDir(dir) {
  const map = { UP: 'DOWN', DOWN: 'UP', LEFT: 'RIGHT', RIGHT: 'LEFT' };
  return map[dir];
}

function updateLeaderboard() {
  leaderboard = Object.values(players)
    .filter(p => p.alive)
    .sort((a, b) => b.body.length - a.body.length)
    .slice(0, 10)
    .map(p => ({ name: p.name, length: p.body.length, coins: p.coins, color: p.color }));
}

function dropCoins(x, y, amount) {
  if (amount <= 0) return;
  const drops = Math.min(amount, 30);
  for (let i = 0; i < drops; i++) {
    const ox = x + Math.floor(Math.random() * 7) - 3;
    const oy = y + Math.floor(Math.random() * 7) - 3;
    coinFood.push({
      x: Math.max(0, Math.min(MAP_WIDTH - 1, ox)),
      y: Math.max(0, Math.min(MAP_HEIGHT - 1, oy)),
      id: uuidv4(),
      value: Math.max(1, Math.ceil(amount / drops))
    });
  }
}

function killPlayer(pid, killerId) {
  const p = players[pid];
  if (!p || !p.alive) return;
  p.alive = false;

  const killed_coins = p.coins;
  const killer_reward = Math.floor(killed_coins * KILL_REWARD_PCT);

  // drop body segments as food
  for (let i = 0; i < p.body.length; i += 2) {
    food.push({ x: p.body[i].x, y: p.body[i].y, id: uuidv4() });
  }

  const dropped = Math.floor(killed_coins * (1 - KILL_REWARD_PCT));
  if (p.body.length > 0) {
    dropCoins(p.body[0].x, p.body[0].y, dropped);
  }

  // explosion broadcast
  if (p.body.length > 0) {
    io.emit('explosion', {
      x: p.body[0].x,
      y: p.body[0].y,
      color: p.color
    });
  }

  if (killerId && players[killerId] && players[killerId].alive) {
    players[killerId].coins += killer_reward;
    players[killerId].kills = (players[killerId].kills || 0) + 1;
    io.to(killerId).emit('kill_reward', { amount: killer_reward, victim: p.name });
  }

  io.to(pid).emit('you_died', {
    coins: p.coins,
    length: p.body.length,
    kills: p.kills || 0,
    killer: killerId ? (players[killerId] ? players[killerId].name : '?') : 'wall'
  });

  io.emit('player_died', { id: pid, name: p.name });
}

function gameTick() {
  const allBodies = {};
  Object.values(players).forEach(p => {
    if (p.alive) {
      p.body.forEach(seg => {
        const key = `${seg.x},${seg.y}`;
        if (!allBodies[key]) allBodies[key] = [];
        allBodies[key].push(p.id);
      });
    }
  });

  Object.values(players).forEach(p => {
    if (!p.alive) return;

    const boosting = p.boost && p.body.length > STARTING_LENGTH + 2;
    const ticks = boosting ? 2 : 1;

    for (let t = 0; t < ticks; t++) {
      const dv = dirVector(p.dir);
      const head = p.body[0];
      const nx = head.x + dv.x;
      const ny = head.y + dv.y;

      if (nx < 0 || nx >= MAP_WIDTH || ny < 0 || ny >= MAP_HEIGHT) {
        killPlayer(p.id, null);
        return;
      }

      const newHead = { x: nx, y: ny };

      const fi = food.findIndex(f => f.x === nx && f.y === ny);
      let grew = false;
      if (fi !== -1) {
        food.splice(fi, 1);
        p.coins += FOOD_VALUE;
        grew = true;
      }

      const ci = coinFood.findIndex(f => f.x === nx && f.y === ny);
      if (ci !== -1) {
        const cf = coinFood.splice(ci, 1)[0];
        p.coins += cf.value || COIN_FOOD_VALUE;
        grew = true;
      }

      p.body.unshift(newHead);
      if (!grew) {
        const tail = p.body.pop();
        const tk = `${tail.x},${tail.y}`;
        if (allBodies[tk]) {
          allBodies[tk] = allBodies[tk].filter(id => id !== p.id);
          if (allBodies[tk].length === 0) delete allBodies[tk];
        }
      }

      if (boosting) {
        p.body.pop();
      }

      const hk = `${nx},${ny}`;
      if (!allBodies[hk]) allBodies[hk] = [];
      allBodies[hk].push(p.id);
    }
  });

  // collision detection (head-body)
  Object.values(players).forEach(p => {
    if (!p.alive) return;
    const head = p.body[0];

    Object.values(players).forEach(other => {
      if (!other.alive || other.id === p.id) return;
      for (let i = 1; i < other.body.length; i++) {
        if (other.body[i].x === head.x && other.body[i].y === head.y) {
          killPlayer(p.id, other.id);
          return;
        }
      }
      if (other.body[0].x === head.x && other.body[0].y === head.y && p.id !== other.id) {
        if (p.body.length <= other.body.length) {
          killPlayer(p.id, other.id);
        } else {
          killPlayer(other.id, p.id);
        }
      }
    });
  });

  spawnFood(FOOD_COUNT, food, MAP_WIDTH);
  spawnFood(COIN_FOOD_COUNT, coinFood, MAP_WIDTH);

  updateLeaderboard();

  const onlineCount = Object.values(players).filter(p => p.alive).length;

  // Per-player state with personalized coin info
  Object.values(players).forEach(p => {
    if (!p.alive) return;
    io.to(p.id).emit('coin_update', { coins: p.coins });
  });

  const state = {
    players: Object.values(players)
      .filter(p => p.alive)
      .map(p => ({
        id: p.id,
        name: p.name,
        color: p.color,
        body: p.body,
        boost: p.boost,
        dir: p.dir
      })),
    food,
    coinFood,
    leaderboard,
    online: onlineCount,
    mapW: MAP_WIDTH,
    mapH: MAP_HEIGHT
  };

  io.emit('state', state);
}

initFood();
setInterval(gameTick, TICK_MS);

io.on('connection', (socket) => {
  console.log('connect:', socket.id);

  socket.emit('lobby_info', {
    online: Object.keys(players).length,
    entryFee: ENTRY_FEE,
    startCoins: START_COINS
  });

  socket.on('join', ({ name }) => {
    const cleanName = String(name).slice(0, 12).replace(/[<>]/g, '') || 'SNAKE';
    const colorIdx = Object.keys(players).length % COLORS.length;
    const sx = 20 + Math.floor(Math.random() * (MAP_WIDTH - 40));
    const sy = 20 + Math.floor(Math.random() * (MAP_HEIGHT - 40));
    const dirs = ['RIGHT', 'LEFT', 'UP', 'DOWN'];

    players[socket.id] = {
      id: socket.id,
      name: cleanName,
      color: COLORS[colorIdx],
      body: makeSnake(sx, sy),
      dir: dirs[Math.floor(Math.random() * 4)],
      nextDir: null,
      alive: true,
      coins: START_COINS - ENTRY_FEE,
      boost: false,
      kills: 0
    };

    socket.emit('joined', {
      id: socket.id,
      color: players[socket.id].color,
      coins: players[socket.id].coins,
      mapWidth: MAP_WIDTH,
      mapHeight: MAP_HEIGHT,
      cell: CELL,
      tickMs: TICK_MS,
      entryFee: ENTRY_FEE
    });

    io.emit('player_joined', { id: socket.id, name: cleanName });
  });

  socket.on('dir', ({ dir }) => {
    const p = players[socket.id];
    if (!p || !p.alive) return;
    if (dir !== oppositeDir(p.dir)) {
      p.dir = dir;
    }
  });

  socket.on('boost', ({ active }) => {
    const p = players[socket.id];
    if (!p || !p.alive) return;
    p.boost = active && p.body.length > STARTING_LENGTH + 2;
  });

  socket.on('respawn', () => {
    const p = players[socket.id];
    if (!p || p.alive) return;
    if (p.coins < ENTRY_FEE) {
      socket.emit('not_enough_coins', { need: ENTRY_FEE, have: p.coins });
      return;
    }
    const sx = 20 + Math.floor(Math.random() * (MAP_WIDTH - 40));
    const sy = 20 + Math.floor(Math.random() * (MAP_HEIGHT - 40));
    const dirs = ['RIGHT', 'LEFT', 'UP', 'DOWN'];
    p.body = makeSnake(sx, sy);
    p.dir = dirs[Math.floor(Math.random() * 4)];
    p.nextDir = null;
    p.alive = true;
    p.coins -= ENTRY_FEE;
    p.boost = false;
    socket.emit('respawned', { coins: p.coins });
  });

  socket.on('disconnect', () => {
    const p = players[socket.id];
    if (p && p.alive) killPlayer(socket.id, null);
    delete players[socket.id];
    io.emit('player_left', { id: socket.id });
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => console.log(`Nokia Snake running on http://localhost:${PORT}`));
