// Does each island's own content carry the player to the level the next one opens at?
// Compares the quest experience available on an island against the experience needed to
// climb from its opening level to the next island's, and reports the shortfall in fights.
const fs = require('fs');
const R = require("path").resolve(__dirname, "..") + "/";
const qd = JSON.parse(fs.readFileSync(R + 'data/quests.json', 'utf8'));
const quests = Array.isArray(qd) ? qd : qd.quests;
const monsters = JSON.parse(fs.readFileSync(R + 'data/monsters.json', 'utf8'));

const xpFor = l => Math.floor(900 * l * l / (l + 4));
const cum = l => { let s = 0; for (let i = 1; i < l; i++) s += xpFor(i); return s; };

// Which island each quest belongs to, by the town or dungeon its giver lives in.
const ISLE_TOWNS = {
  lirandel:  ['moonhaven', 'mooncrest', 'waterfallcave'],
  pyralis:   ['cinderport', 'forge_keep', 'ashfen_village'],
  zephyrion: ['windhaven', 'stormspire', 'galewick', 'cloudrest'],
  sylvandar: ['roothollow', 'mossbridge', 'amber_grove'],
  thalorax:  ['abyssport', 'kelp_towers', 'brightcoral'],
  umbryn:    ['dusthaven', 'the_hollow', 'echo_point'],
  bellorak:  ['gold_guard_camp', 'iron_reckoner_camp', 'neutral_ground'],
};
const OPENS_AT = { lirandel: 1, pyralis: 6, zephyrion: 10, sylvandar: 13,
                   thalorax: 17, umbryn: 21, bellorak: 25, cradle: 29 };
const NEXT = { lirandel: 'pyralis', pyralis: 'zephyrion', zephyrion: 'sylvandar',
               sylvandar: 'thalorax', thalorax: 'umbryn', umbryn: 'bellorak',
               bellorak: 'cradle' };
// Typical monster level and XP on each island, from its spawn band.
const BAND = { lirandel: [1,6], pyralis: [6,11], zephyrion: [8,13], sylvandar: [12,16],
               thalorax: [16,20], umbryn: [20,24], bellorak: [24,28] };

const questXpByIsle = {};
for (const [isle, towns] of Object.entries(ISLE_TOWNS)) {
  let total = 0;
  const seen = new Set();
  for (const t of towns) {
    const p = R + 'data/towns/' + t + '.rfmap';
    if (!fs.existsSync(p)) { console.log('  (no map for ' + t + ')'); continue; }
    const raw = fs.readFileSync(p, 'utf8');
    for (const q of quests)
      if (raw.includes('"' + q.id + '"') && !seen.has(q.id)) { seen.add(q.id); total += q.xpReward || 0; }
  }
  questXpByIsle[isle] = { xp: total, n: seen.size };
}

console.log('island      opens  next opens   XP needed   quest XP (n)   shortfall   fights to cover');
for (const isle of Object.keys(ISLE_TOWNS)) {
  const from = OPENS_AT[isle], to = OPENS_AT[NEXT[isle]];
  const need = cum(to) - cum(from);
  const { xp, n } = questXpByIsle[isle];
  const gap = Math.max(0, need - xp);
  const [lo, hi] = BAND[isle];
  const inBand = monsters.filter(m => m.level >= lo && m.level <= hi);
  const avg = inBand.length ? inBand.reduce((s, m) => s + m.xpValue, 0) / inBand.length : 1;
  console.log(isle.padEnd(11)
      + String(from).padStart(4) + String(to).padStart(12)
      + String(need).padStart(12) + (' ' + xp + ' (' + n + ')').padStart(15)
      + String(gap).padStart(12) + String(Math.ceil(gap / Math.max(1, avg))).padStart(18));
}
