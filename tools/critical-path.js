// Can the game be finished? Walks the whole critical path over the shipped data:
// arrival -> towns -> dungeons -> the key -> the exit portal, island by island, and
// checks that every step is actually reachable on foot.
//
// Run it after any map or tile edit:  node critical-path.js
const fs = require('fs');
const R = require("path").resolve(__dirname, "..") + "/";

const tj = JSON.parse(fs.readFileSync(R + 'data/tiles.json', 'utf8'));
const TL = Array.isArray(tj) ? tj : tj.tiles;
const T = {}; for (const x of TL) if (!(x.id in T)) T[x.id] = x;
const walk = c => { const d = T[c]; return d ? !!d.walkable : false; };
const portalOf = c => { const d = T[c]; const e = d && d.onStepEffect;
                        return e && e.type === 'island_portal' ? e : null; };

const CHAIN = [
  { id: 'lirandel',  arrival: [20, 48],   key: 'key_of_tides',  next: 'pyralis'   },
  { id: 'pyralis',   arrival: [20, 92],   key: 'key_of_embers', next: 'zephyrion' },
  { id: 'zephyrion', arrival: [110, 78],  key: 'key_of_gales',  next: 'sylvandar' },
  { id: 'sylvandar', arrival: [61, 156],  key: 'key_of_roots',  next: 'thalorax'  },
  { id: 'thalorax',  arrival: [60, 98],   key: 'key_of_depths', next: 'umbryn'    },
  { id: 'umbryn',    arrival: [119, 150], key: 'key_of_echoes', next: 'bellorak'  },
  { id: 'bellorak',  arrival: [115, 180], key: 'key_of_iron',   next: 'cradle'    },
];

function flood(tiles, sx, sy, npcs) {
  const H = tiles.length, W = tiles[0].length;
  const seen = Array.from({ length: H }, () => new Uint8Array(W));
  const blocked = new Set();
  for (const n of (npcs || [])) blocked.add(n.x + ',' + n.y);
  if (sy < 0 || sy >= H || sx < 0 || sx >= W) return seen;
  const q = [[sx, sy]]; seen[sy][sx] = 1;
  while (q.length) {
    const [x, y] = q.pop();
    for (const [dx, dy] of [[0,1],[0,-1],[1,0],[-1,0]]) {
      const nx = x+dx, ny = y+dy;
      if (nx<0||ny<0||nx>=W||ny>=H||seen[ny][nx]) continue;
      if (!walk(tiles[ny][nx]) || blocked.has(nx+','+ny)) continue;
      seen[ny][nx] = 1; q.push([nx, ny]);
    }
  }
  return seen;
}

// Where each key is handed out, from the code and the town data.
const src = id => fs.readFileSync(R + id, 'utf8');
const dungeonCode = src('src/main/java/io/cannonforge/retroquest/controller/DungeonController.java');
const quests = (() => { const d = JSON.parse(src('data/quests.json')); return Array.isArray(d) ? d : d.quests; })();

function keyGrants(key) {
  const out = [];
  if (dungeonCode.includes('addKey("' + key + '")')) out.push('DungeonController grants it');
  for (const q of quests) if (q.itemRewardId === key) out.push('quest ' + q.id + ' (giver ' + (q.giverName || '?') + ')');
  for (const dir of ['towns', 'overworlds']) {
    for (const f of fs.readdirSync(R + 'data/' + dir).filter(x => x.endsWith('.rfmap'))) {
      const raw = fs.readFileSync(R + 'data/' + dir + '/' + f, 'utf8');
      if (raw.includes('"' + key + '"') && raw.includes('GIVE_ITEM')) out.push(dir + '/' + f.replace('.rfmap',''));
    }
  }
  return out;
}

let fatal = 0, warn = 0;
for (const isle of CHAIN) {
  const m = JSON.parse(src('data/overworlds/' + isle.id + '.rfmap'));
  const tiles = m.tiles;
  const seen = flood(tiles, isle.arrival[0], isle.arrival[1], m.npcs);
  const count = seen.reduce((s, r) => s + r.reduce((a, b) => a + b, 0), 0);
  console.log('\n=== ' + isle.id.toUpperCase() + ' === arrival (' + isle.arrival + '), '
      + count + ' tiles on foot');

  // towns
  for (const te of (m.townEntrances || [])) {
    const ok = seen[te.worldY] && seen[te.worldY][te.worldX];
    if (!ok) { fatal++; console.log('  !! town ' + te.townName + ' (' + te.worldX + ',' + te.worldY + ') UNREACHABLE'); }
    else console.log('     town ' + te.townName + ' (' + te.worldX + ',' + te.worldY + ')');
  }

  // dungeons
  for (let y = 0; y < tiles.length; y++) for (let x = 0; x < tiles[0].length; x++) {
    if (tiles[y][x] !== 'D') continue;
    if (!seen[y][x]) { fatal++; console.log('  !! dungeon entrance (' + x + ',' + y + ') UNREACHABLE'); }
    else console.log('     dungeon entrance (' + x + ',' + y + ')');
  }

  // the key
  const grants = keyGrants(isle.key);
  if (!grants.length) { fatal++; console.log('  !! ' + isle.key + ' is granted NOWHERE'); }
  else console.log('     ' + isle.key + ' <- ' + grants.join('; '));

  // the way out
  let exit = null;
  for (let y = 0; y < tiles.length && !exit; y++) for (let x = 0; x < tiles[0].length && !exit; x++) {
    const e = portalOf(tiles[y][x]);
    if (e && e.params.map === isle.next) exit = { x, y, e };
  }
  if (isle.next === 'cradle') {
    console.log('     the Cradle is entered through the key-gated dungeon door, not a portal');
  } else if (!exit) {
    fatal++; console.log('  !! no portal to ' + isle.next + ' anywhere on ' + isle.id);
  } else if (!seen[exit.y][exit.x]) {
    fatal++; console.log('  !! portal to ' + isle.next + ' at (' + exit.x + ',' + exit.y + ') UNREACHABLE');
  } else {
    const want = exit.e.params.requiredKeyId;
    const tag = want === isle.key ? '' : '   (asks for ' + (want || 'nothing') + ')';
    if (want !== isle.key) warn++;
    console.log('     portal to ' + isle.next + ' at (' + exit.x + ',' + exit.y + ')' + tag);
  }

  // a way back, so nothing is a one-way trip
  let back = false;
  for (let y = 0; y < tiles.length && !back; y++) for (let x = 0; x < tiles[0].length && !back; x++) {
    const e = portalOf(tiles[y][x]);
    if (e && e.params.map !== isle.next && seen[y][x]) back = true;
  }
  if (isle.id !== 'lirandel' && !back) { warn++; console.log('  ~  no way back from ' + isle.id + ' (one-way trip)'); }
}

console.log('\n' + (fatal ? fatal + ' FATAL problem(s)' : 'the critical path is walkable end to end')
        + (warn ? ', ' + warn + ' warning(s)' : ''));
process.exit(fatal ? 1 : 0);
