// A content fingerprint per island: everything a playtest would exercise there, hashed.
//
// The point is test tracking. Once an island has been walked end to end there is no reason
// to walk it again — until its content changes. This hashes exactly the files and records
// that make up an island, so adding a quest to Umbryn invalidates Umbryn and nothing else.
//
//   node tools/fingerprint.js            print every island's fingerprint
//   node tools/fingerprint.js --json     machine-readable
const fs = require('fs');
const crypto = require('crypto');
const R = require('path').resolve(__dirname, '..') + '/';

/** Towns and dungeons belonging to each island, derived from the overworld itself. */
function islandContent(isle) {
  const map = JSON.parse(fs.readFileSync(R + 'data/overworlds/' + isle + '.rfmap', 'utf8'));
  const towns = (map.townEntrances || []).map(t => t.townName);
  const dungeons = new Set();
  for (const s of Object.values(map.initialTileStates || {}))
    if (s && s.data && s.data.dungeonName) dungeons.add(s.data.dungeonName);
  // Older maps carry the name on the state object itself.
  for (const s of Object.values(map.initialTileStates || {}))
    if (s && s.dungeonName) dungeons.add(s.dungeonName);
  return { towns, dungeons: [...dungeons].sort() };
}

/** Quests whose giver lives in one of this island's towns. */
function islandQuests(towns) {
  const qd = JSON.parse(fs.readFileSync(R + 'data/quests.json', 'utf8'));
  const quests = Array.isArray(qd) ? qd : qd.quests;
  const ids = new Set();
  for (const t of towns) {
    const p = R + 'data/towns/' + t + '.rfmap';
    if (!fs.existsSync(p)) continue;
    const raw = fs.readFileSync(p, 'utf8');
    for (const q of quests) if (raw.includes('"' + q.id + '"')) ids.add(q.id);
  }
  return [...ids].sort();
}

const ISLANDS = ['lirandel','pyralis','zephyrion','sylvandar','thalorax','umbryn','bellorak'];

function fingerprint(isle) {
  const { towns, dungeons } = islandContent(isle);
  const h = crypto.createHash('sha1');
  const parts = [];
  const add = (label, path) => {
    if (!fs.existsSync(R + path)) return;
    h.update(label).update(fs.readFileSync(R + path));
    parts.push(path);
  };
  add('overworld', 'data/overworlds/' + isle + '.rfmap');
  for (const t of towns.slice().sort()) add('town', 'data/towns/' + t + '.rfmap');
  for (const d of dungeons) {
    for (let lvl = 1; lvl <= 12; lvl++) {
      const p = 'data/dungeons/' + d + '_' + lvl + '.rfmap';
      if (fs.existsSync(R + p)) add('dungeon', p);
    }
  }
  // The quests that belong here, by content rather than by whole-file hash, so an edit to
  // another island's quest does not invalidate this one.
  const qd = JSON.parse(fs.readFileSync(R + 'data/quests.json', 'utf8'));
  const quests = Array.isArray(qd) ? qd : qd.quests;
  const mine = islandQuests(towns);
  for (const id of mine) h.update(JSON.stringify(quests.find(q => q.id === id) || {}));
  // Tiles and items shape every island, so they count everywhere.
  h.update(fs.readFileSync(R + 'data/tiles.json'));
  h.update(fs.readFileSync(R + 'data/items.json'));

  return { isle, fingerprint: h.digest('hex').slice(0, 16),
           towns: towns.sort(), dungeons, quests: mine, files: parts.length };
}

const all = ISLANDS.map(fingerprint);
if (process.argv.includes('--json')) {
  console.log(JSON.stringify(Object.fromEntries(all.map(a => [a.isle, a])), null, 2));
} else {
  for (const a of all)
    console.log(a.isle.padEnd(11) + a.fingerprint + '   '
      + a.towns.length + ' town(s), ' + a.dungeons.length + ' dungeon(s), '
      + a.quests.length + ' quest(s), ' + a.files + ' map file(s)');
}
