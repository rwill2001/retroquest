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

// Which towns are reachable at all, and who is in them.
const REACHABLE_TOWNS = new Set();
for (const f of fs.readdirSync(R + 'data/overworlds').filter(x => x.endsWith('.rfmap'))) {
  const m = JSON.parse(fs.readFileSync(R + 'data/overworlds/' + f, 'utf8'));
  for (const te of (m.townEntrances || [])) REACHABLE_TOWNS.add(te.townName.toLowerCase());
}

const offered = new Map();     // questId -> [where]
const npcNames = new Set();
for (const dir of ['towns', 'overworlds', 'dungeons']) {
  for (const f of fs.readdirSync(R + 'data/' + dir).filter(x => x.endsWith('.rfmap'))) {
    const base = f.replace('.rfmap', '');
    const live = dir !== 'towns' || REACHABLE_TOWNS.has(base.toLowerCase());
    const m = JSON.parse(fs.readFileSync(R + 'data/' + dir + '/' + f, 'utf8'));
    for (const n of (m.npcs || [])) {
      if (live) npcNames.add(n.name);
      const s = JSON.stringify(n);
      for (const q of quests) {
        if (!s.includes('"' + q.id + '"')) continue;
        if (!/GIVE_QUEST/.test(s)) continue;
        if (!offered.has(q.id)) offered.set(q.id, []);
        offered.get(q.id).push((live ? '' : 'ORPHAN ') + base + '/' + n.name);
      }
    }
  }
}
// Some quests are handed out from code.
const code = fs.readdirSync(R + 'src/main/java/io/cannonforge/retroquest/controller')
  .map(f => fs.readFileSync(R + 'src/main/java/io/cannonforge/retroquest/controller/' + f, 'utf8')).join('\n');

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
  if (q.itemRewardId && !itemIds.has(q.itemRewardId))
    notes.push('reward item "' + q.itemRewardId + '" does not exist');
  if (q.prereqQuestId && !quests.some(z => z.id === q.prereqQuestId))
    notes.push('prerequisite "' + q.prereqQuestId + '" is not a quest');

  if (notes.length) {
    const fatal = notes.some(n => /never offered|orphan town/.test(n));
    if (fatal) dead++; else soft++;
    console.log((fatal ? '!! ' : ' ~ ') + q.id.padEnd(26) + notes.join('; '));
  }
}
console.log('\n' + quests.length + ' quests: ' + dead + ' unobtainable, ' + soft + ' with a questionable target');
