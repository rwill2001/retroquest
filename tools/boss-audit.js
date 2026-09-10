// Every bottom-of-dungeon boss in data/dungeon_bosses.json, checked against the maps it claims:
// does its dungeon exist, is it really the bottom, is there an altar to trigger it, and can the
// player actually walk to that altar from the level entry?
//
// This exists because the boss is invisible until you are standing on the altar. A boss on the
// wrong level, or on a level whose altar is walled off, is a set piece nobody ever sees and
// nothing else in the build would complain about.
const fs = require('fs');
const R = require("path").resolve(__dirname, "..") + "/";

const bosses = JSON.parse(fs.readFileSync(R + 'data/dungeon_bosses.json', 'utf8'));
const tileDefs = JSON.parse(fs.readFileSync(R + 'data/tiles.json', 'utf8'));
const walkable = new Map(tileDefs.map(t => [t.id, !!t.walkable]));
const GODS = new Set(['LIRANDEL','PYRALIS','ZEPHYRION','SYLVANDAR','THALORAX','UMBRYN','BELLORAK']);

// The four dungeons whose bottom level is a hand-written trial in DungeonController. A data
// boss on one of these would be shadowed by the trial's earlier return and never run.
const TRIAL_LEVELS = {
  pressure_temple: 3, archive_of_tears: 3, iron_pit: 3, cradle_of_shards: 8,
};

// How deep each authored dungeon goes.
const lastLevel = new Map();
for (const f of fs.readdirSync(R + 'data/dungeons').filter(x => x.endsWith('.rfmap'))) {
  const m = /^(.*)_(\d+)\.rfmap$/.exec(f);
  if (!m) continue;
  if (+m[2] > (lastLevel.get(m[1]) || 0)) lastLevel.set(m[1], +m[2]);
}

/** Altars reachable on foot from the level's authored entry. */
function reachableAltars(md) {
  const T = md.tiles, H = T.length, W = T[0].length;
  const seen = new Set();
  const start = md.interiorEntryX + ',' + md.interiorEntryY;
  const q = [[md.interiorEntryX, md.interiorEntryY]];
  seen.add(start);
  const found = [], all = [];
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (T[y][x] === 'A') all.push(x + ',' + y);
  while (q.length) {
    const [cx, cy] = q.shift();
    if (T[cy][cx] === 'A') found.push(cx + ',' + cy);
    for (const [dx, dy] of [[0,-1],[0,1],[-1,0],[1,0]]) {
      const nx = cx + dx, ny = cy + dy, k = nx + ',' + ny;
      if (nx < 0 || ny < 0 || nx >= W || ny >= H || seen.has(k)) continue;
      if (!walkable.get(T[ny][nx])) continue;
      seen.add(k);
      q.push([nx, ny]);
    }
  }
  return { found, all };
}

let bad = 0, warn = 0;
const claimed = new Set(), flags = new Set();

for (const b of bosses) {
  const id = (b.dungeon || '?') + '_' + b.level;
  const notes = [];

  if (!lastLevel.has(b.dungeon)) {
    notes.push('names no authored dungeon');
  } else if (b.level !== lastLevel.get(b.dungeon)) {
    notes.push('sits on level ' + b.level + ' but ' + b.dungeon +
               ' bottoms out at ' + lastLevel.get(b.dungeon));
  }
  if (TRIAL_LEVELS[b.dungeon] === b.level) {
    notes.push(b.dungeon + ' level ' + b.level + ' is a hand-written trial — the boss can never fire');
  }
  if (claimed.has(id)) notes.push('two bosses claim ' + id);
  claimed.add(id);

  if (!b.flag) notes.push('no flag — it would fight again every time the altar is touched');
  else if (flags.has(b.flag)) notes.push('flag "' + b.flag + '" is shared with another boss');
  flags.add(b.flag);

  if (b.god && !GODS.has(String(b.god).toUpperCase())) notes.push('god "' + b.god + '" is not one of the seven');
  if (b.favor > 0 && !b.god) notes.push('awards favor but names no god');
  if (!b.phases || !b.phases.length) notes.push('has no phases');
  else b.phases.forEach((p, i) => {
    if (!p.name)  notes.push('phase ' + (i + 1) + ' has no name');
    if (!(p.hp > 0)) notes.push('phase ' + (i + 1) + ' has no HP');
    if (i < b.phases.length - 1 && !p.transition)
      notes.push('phase ' + (i + 1) + ' is not the last but has no transition text');
  });
  if (!b.intro) notes.push('no intro prompt');
  if (!b.revelation || !b.revelation.body) notes.push('no revelation body — the reward IS the text');

  // The altar it triggers from
  if (lastLevel.has(b.dungeon)) {
    const file = R + 'data/dungeons/' + b.dungeon + '_' + b.level + '.rfmap';
    if (fs.existsSync(file)) {
      const md = JSON.parse(fs.readFileSync(file, 'utf8'));
      const { found, all } = reachableAltars(md);
      if (!all.length)        notes.push('level has no altar tile to trigger from');
      else if (!found.length) notes.push('all ' + all.length + ' altars are walled off from the entry');
    }
  }

  if (notes.length) {
    const fatal = notes.some(n => /never fire|no authored dungeon|no altar|walled off|no phases|no revelation body|two bosses|shared with/.test(n));
    if (fatal) bad++; else warn++;
    console.log((fatal ? '!! ' : ' ~ ') + id.padEnd(22) + notes.join('; '));
  }
}

console.log('\n' + bosses.length + ' bottom-of-dungeon bosses: ' + bad + ' broken, ' + warn + ' questionable');
if (bad) process.exit(1);
