// Every quest in quests.json, checked against the world: is it ever offered, by someone the
// player can actually walk up to, and can its target ever be satisfied?
const fs = require('fs');
const R = require("path").resolve(__dirname, "..") + "/";

const qd = JSON.parse(fs.readFileSync(R + 'data/quests.json', 'utf8'));
const quests = Array.isArray(qd) ? qd : qd.quests;
const id2 = JSON.parse(fs.readFileSync(R + 'data/items.json', 'utf8'));
const items = Array.isArray(id2) ? id2 : id2.items;
const monsters = JSON.parse(fs.readFileSync(R + 'data/monsters.json', 'utf8'));
const monsterNames = new Set(monsters.map(m => m.name));
const itemNames = new Set(items.map(i => i.name));
const itemIds  = new Set(items.map(i => i.id));
const balance = JSON.parse(fs.readFileSync(R + 'data/balance.json', 'utf8'));
const PROCEDURAL_MAX_DEPTH = (balance.dungeon || {}).maxDepth || 50;

// How deep each authored dungeon goes: <name>_1.rfmap, _2, ...
const dungeonLevels = new Map();
for (const f of fs.readdirSync(R + 'data/dungeons').filter(x => x.endsWith('.rfmap'))) {
  const m = /^(.*)_(\d+)\.rfmap$/.exec(f);
  if (!m) continue;
  if (+m[2] > (dungeonLevels.get(m[1]) || 0)) dungeonLevels.set(m[1], +m[2]);
}

// Which towns are reachable at all, who is in them, and how deep each island can be descended.
// A 'D' tile with no authored dungeonName is a procedural dungeon, which runs all the way to
// maxDepth; an authored one stops at its last .rfmap.
const REACHABLE_TOWNS = new Set();
const islandOf = new Map();        // town or overworld base name -> the overworld it belongs to
const islandDepth = new Map();     // overworld -> deepest level its own dungeons reach
const islandDungeons = new Map();  // overworld -> dungeon names below it ('procedural' included)
for (const f of fs.readdirSync(R + 'data/overworlds').filter(x => x.endsWith('.rfmap'))) {
  const island = f.replace('.rfmap', '');
  const m = JSON.parse(fs.readFileSync(R + 'data/overworlds/' + f, 'utf8'));
  islandOf.set(island, island);
  for (const te of (m.townEntrances || [])) {
    REACHABLE_TOWNS.add(te.townName.toLowerCase());
    islandOf.set(te.townName.toLowerCase(), island);
  }
  const states = m.initialTileStates || {};
  let depth = 0;
  const names = [];
  for (let y = 0; y < (m.tiles || []).length; y++) {
    for (let x = 0; x < m.tiles[y].length; x++) {
      if (m.tiles[y][x] !== 'D') continue;
      const name = ((states[x + ',' + y] || {}).data || {}).dungeonName;
      if (name) {
        names.push(name);
        depth = Math.max(depth, dungeonLevels.get(name) || 0);
      } else {
        names.push('procedural');
        depth = Math.max(depth, m.maxDungeonDepth > 0 ? m.maxDungeonDepth : PROCEDURAL_MAX_DEPTH);
      }
    }
  }
  islandDepth.set(island, depth);
  islandDungeons.set(island, names);
}
const WORLD_DEPTH = Math.max(...islandDepth.values());

const offered = new Map();       // questId -> [where]
const offeredIn = new Map();     // questId -> Set(map base name), reachable givers only
const forceComplete = new Set(); // questIds a dialogue COMPLETE_QUEST action closes outright
const npcNames = new Set();
function walkActions(o, fn) {
  if (Array.isArray(o)) { o.forEach(v => walkActions(v, fn)); return; }
  if (o && typeof o === 'object') {
    if (typeof o.type === 'string' && typeof o.target === 'string') fn(o);
    for (const k of Object.keys(o)) walkActions(o[k], fn);
  }
}
for (const dir of ['towns', 'overworlds', 'dungeons']) {
  for (const f of fs.readdirSync(R + 'data/' + dir).filter(x => x.endsWith('.rfmap'))) {
    const base = f.replace('.rfmap', '');
    const live = dir !== 'towns' || REACHABLE_TOWNS.has(base.toLowerCase());
    const m = JSON.parse(fs.readFileSync(R + 'data/' + dir + '/' + f, 'utf8'));
    for (const n of (m.npcs || [])) {
      if (live) npcNames.add(n.name);
      if (live) walkActions(n, a => { if (a.type === 'COMPLETE_QUEST') forceComplete.add(a.target); });
      const s = JSON.stringify(n);
      for (const q of quests) {
        if (!s.includes('"' + q.id + '"')) continue;
        if (!/GIVE_QUEST/.test(s)) continue;
        if (!offered.has(q.id)) offered.set(q.id, []);
        offered.get(q.id).push((live ? '' : 'ORPHAN ') + base + '/' + n.name);
        if (live) {
          if (!offeredIn.has(q.id)) offeredIn.set(q.id, new Set());
          offeredIn.get(q.id).add(base.toLowerCase());
        }
      }
    }
  }
}
// Some quests are handed out from code.
const code = fs.readdirSync(R + 'src/main/java/io/cannonforge/retroquest/controller')
  .map(f => fs.readFileSync(R + 'src/main/java/io/cannonforge/retroquest/controller/' + f, 'utf8')).join('\n');

// Literal EXPLORE targets the controllers tick directly, e.g. "corrupted_shrine".
const codeExplore = new Set();
for (const m of code.matchAll(/progressQuest\(\s*Quest\.Type\.EXPLORE\s*,\s*"([^"]+)"/g)) codeExplore.add(m[1]);

// An EXPLORE target can only ever tick from one of four places, so check all four:
//   NavigationController  progressQuest(EXPLORE, townName)          entering a town
//   DungeonController     progressQuest(EXPLORE, "Dungeon Level N") ANY dungeon, on any island
//   DungeonController     progressQuest(EXPLORE, "<name> Level 1")  entering an authored dungeon
//   DialogueOverlay       a COMPLETE_QUEST action force-completes an EXPLORE quest outright
// The depth counter is global, so the interesting failure is not "impossible" but "impossible
// where it is handed out": a target deeper than anything under the giver's own island quietly
// sends the player back to another island. That is how Voss asked for level 5 of a 3-level
// Ember Caverns and nothing noticed.
function exploreNotes(q) {
  const notes = [];
  const target = String(q.target);
  if (forceComplete.has(q.id)) return notes;          // dialogue closes it regardless of target
  if (codeExplore.has(target)) return notes;
  if (REACHABLE_TOWNS.has(target.toLowerCase())) return notes;

  const homes = [...(offeredIn.get(q.id) || [])].map(b => islandOf.get(b)).filter(Boolean);
  const home = homes.length ? homes[0] : null;

  let m = /^Dungeon Level (\d+)$/.exec(target);
  if (m) {
    const need = +m[1];
    if (need > WORLD_DEPTH)
      notes.push('EXPLORE target "' + target + '" is deeper than any dungeon in the game (max ' +
                 WORLD_DEPTH + ')');
    else if (home && need > islandDepth.get(home))
      notes.push('EXPLORE target "' + target + '" is deeper than anything on ' + home + ' (' +
                 islandDungeons.get(home).join('/') + ' reaches ' + islandDepth.get(home) +
                 ') - only satisfiable by leaving the island');
    return notes;
  }

  m = /^(.*) Level (\d+)$/.exec(target);
  if (m) {
    const name = m[1], lvl = +m[2];
    if (!dungeonLevels.has(name))
      notes.push('EXPLORE target "' + target + '" names no authored dungeon');
    else if (lvl > dungeonLevels.get(name))
      notes.push('EXPLORE target "' + target + '" is past the last level of ' + name + ' (' +
                 dungeonLevels.get(name) + ')');
    else if (lvl !== 1)
      notes.push('EXPLORE target "' + target + '" never ticks - the "<name> Level N" form is only ' +
                 'emitted on entry, so N must be 1');
    else if (home && !(islandDungeons.get(home) || []).includes(name))
      notes.push('EXPLORE target "' + target + '" is not on ' + home + ', where the quest is given');
    return notes;
  }

  notes.push('EXPLORE target "' + target + '" is not a town, an authored dungeon level, a code ' +
             'milestone or a dialogue completion - nothing can ever tick it');
  return notes;
}

let dead = 0, soft = 0;
for (const q of quests) {
  const where = offered.get(q.id) || [];
  const live = where.filter(w => !w.startsWith('ORPHAN'));
  const notes = [];
  if (!where.length && !code.includes('"' + q.id + '"')) notes.push('never offered by anyone');
  else if (!live.length && !code.includes('"' + q.id + '"')) notes.push('only offered in an orphan town: ' + where.join(', '));

  // 'any' is a documented wildcard: Player.progressQuest treats it as 'kill anything'.
  if (q.type === 'KILL' && q.target !== 'any' && !monsterNames.has(q.target) && !code.includes('"' + q.target + '"'))
    notes.push('KILL target "' + q.target + '" is not a monster and is not spawned in code');
  if (q.type === 'COLLECT' && !itemNames.has(q.target))
    notes.push('COLLECT target "' + q.target + '" is not an item name');
  if (q.type === 'TALK' && !npcNames.has(q.target))
    notes.push('TALK target "' + q.target + '" is not a reachable NPC');
  if (q.type === 'DELIVER' && !npcNames.has(q.target))
    notes.push('DELIVER target "' + q.target + '" is not a reachable NPC');
  if (q.type === 'EXPLORE') notes.push(...exploreNotes(q));
  if (q.itemRewardId && !itemIds.has(q.itemRewardId))
    notes.push('reward item "' + q.itemRewardId + '" does not exist');
  if (q.prereqQuestId && !quests.some(z => z.id === q.prereqQuestId))
    notes.push('prerequisite "' + q.prereqQuestId + '" is not a quest');

  if (notes.length) {
    const fatal = notes.some(n => /never offered|orphan town|nothing can ever tick it|never ticks|deeper than any dungeon in the game/.test(n));
    if (fatal) dead++; else soft++;
    console.log((fatal ? '!! ' : ' ~ ') + q.id.padEnd(26) + notes.join('; '));
  }
}
console.log('\n' + quests.length + ' quests: ' + dead + ' unobtainable, ' + soft + ' with a questionable target');
