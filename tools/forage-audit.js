// The hunting grounds on each overworld: are they there, can they be reached, do they name a
// hunt the game can actually run — and, the question the whole feature exists to answer, can an
// island be crossed and worked without walking back to town for food?
//
// Player.stepTaken() burns one food per step and the only other supply is a town, so an island
// has a range in steps. This prints food in versus food out per island, the way xp-ladder.js
// prints the XP climb, so "does hunting actually help" is a number rather than a feeling.
const fs = require('fs');
const R = require("path").resolve(__dirname, "..") + "/";

const defs = JSON.parse(fs.readFileSync(R + 'data/tiles.json', 'utf8'));
const byId = new Map(defs.map(t => [t.id, t]));

// The hunts HuntOverlay.forId() knows how to run, and the most food each can pay.
// Kept here rather than read out of the Java so a rename on either side shows up as a failure.
const HUNTS = {
  snare_line:      { name: 'Snare Line',          max: 5 * 24 },
  ember_flush:     { name: 'Ember Flush',         max: 8 * 16 },
  skyfish_net:     { name: 'Skyfish Netting',     max: 6 * 20 },
  spore_lure:      { name: 'Spore Lure',          max: 5 * 24 },
  pressure_line:   { name: 'Pressure Line',       max: 140    },
  remembered_meal: { name: 'The Remembered Meal', max: 6 * 4 * 5 },
  ration_run:      { name: 'Ration Run',          max: 140    },
};

const WANT_PER_ISLAND = 8;
/** What a fed player starts a crossing with, from Player.food. */
const FOOD_CAP = 500;

let fatal = 0, soft = 0;
const rows = [];

for (const f of fs.readdirSync(R + 'data/overworlds').filter(x => x.endsWith('.rfmap'))) {
  const island = f.replace('.rfmap', '');
  const md = JSON.parse(fs.readFileSync(R + 'data/overworlds/' + f, 'utf8'));
  const T = md.tiles, H = T.length, W = T[0].length;

  // Every hunting ground on this island, and which hunt it runs
  const grounds = [];
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const d = byId.get(T[y][x]);
    if (d && d.onStepEffect && d.onStepEffect.type === 'hunt') {
      grounds.push({ x, y, game: (d.onStepEffect.params || {}).game, tile: d.name });
    }
  }

  const notes = [];
  if (!grounds.length) {
    notes.push('no hunting grounds at all');
  } else if (grounds.length !== WANT_PER_ISLAND) {
    notes.push(grounds.length + ' hunting grounds, expected ' + WANT_PER_ISLAND);
  }

  // Each must name a hunt the overlay can run
  const kinds = new Set();
  for (const g of grounds) {
    kinds.add(g.game);
    if (!HUNTS[g.game]) notes.push('ground at ' + g.x + ',' + g.y + ' names unknown hunt "' + g.game + '"');
  }
  if (kinds.size > 1) notes.push('mixes ' + kinds.size + ' different hunts on one island');

  // Reachable on foot from a town
  const te = (md.townEntrances || [])[0];
  let reachable = 0;
  if (te && grounds.length) {
    const seen = new Set([te.worldX + ',' + te.worldY]);
    const q = [[te.worldX, te.worldY]];
    while (q.length) {
      const [cx, cy] = q.shift();
      for (const [dx, dy] of [[0,-1],[0,1],[-1,0],[1,0]]) {
        const nx = cx + dx, ny = cy + dy, k = nx + ',' + ny;
        if (nx < 0 || ny < 0 || nx >= W || ny >= H || seen.has(k)) continue;
        const t = byId.get(T[ny][nx]);
        if (!t || !t.walkable) continue;
        seen.add(k); q.push([nx, ny]);
      }
    }
    for (const g of grounds) if (seen.has(g.x + ',' + g.y)) reachable++;
    if (reachable < grounds.length)
      notes.push((grounds.length - reachable) + ' of ' + grounds.length + ' grounds are walled off from town');
  }

  // Two grounds close enough to be worked from one spot defeat the point of spreading them
  for (let i = 0; i < grounds.length; i++)
    for (let j = i + 1; j < grounds.length; j++) {
      const d = Math.max(Math.abs(grounds[i].x - grounds[j].x), Math.abs(grounds[i].y - grounds[j].y));
      if (d < 6) notes.push('grounds at ' + grounds[i].x + ',' + grounds[i].y + ' and ' +
                            grounds[j].x + ',' + grounds[j].y + ' are ' + d + ' tiles apart');
    }

  const kind = grounds.length ? HUNTS[grounds[0].game] : null;
  const yield_ = kind ? grounds.length * kind.max : 0;
  rows.push({
    island,
    hunt: kind ? kind.name : '—',
    grounds: grounds.length,
    reachable,
    yield_,
    range: FOOD_CAP + yield_,
    size: W + 'x' + H,
  });

  if (notes.length) {
    const isFatal = notes.some(n => /unknown hunt|walled off|no hunting grounds/.test(n));
    if (isFatal) fatal++; else soft++;
    console.log((isFatal ? '!! ' : ' ~ ') + island.padEnd(11) + notes.join('; '));
  }
}

console.log('\nisland      map        hunt                  spots  best haul   range (steps)');
for (const r of rows.sort((a, b) => a.island.localeCompare(b.island))) {
  console.log('  ' + r.island.padEnd(11) + r.size.padEnd(10) + r.hunt.padEnd(22) +
              String(r.grounds).padStart(3) + '   ' + String(r.yield_).padStart(8) + '   ' +
              String(r.range).padStart(6));
}
console.log('\nrange = a full belly (' + FOOD_CAP + ') plus every ground on the island worked perfectly.');
console.log(rows.length + ' islands: ' + fatal + ' broken, ' + soft + ' questionable');
if (fatal) process.exit(1);
