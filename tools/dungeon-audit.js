// Every authored dungeon level, walked from its own entry point: can the player reach the
// stairs, the way out, and the things placed on the level?
const fs = require('fs');
const R = require("path").resolve(__dirname, "..") + "/";
const tj = JSON.parse(fs.readFileSync(R + 'data/tiles.json', 'utf8'));
const TL = Array.isArray(tj) ? tj : tj.tiles;
const T = {}; for (const x of TL) if (!(x.id in T)) T[x.id] = x;
const walk = c => { const d = T[c]; return d ? !!d.walkable : false; };
const NAME = c => T[c] ? T[c].name : '?';

const SPECIAL = 'sDABfHtPIRnkcqMg';
const files = fs.readdirSync(R + 'data/dungeons').filter(f => f.endsWith('.rfmap')).sort();
let problems = 0;
for (const f of files) {
  const m = JSON.parse(fs.readFileSync(R + 'data/dungeons/' + f, 'utf8'));
  const t = m.tiles, H = t.length, W = t[0].length;
  const ex = m.interiorEntryX, ey = m.interiorEntryY;
  const msgs = [];
  if (ex == null || ey == null) { msgs.push('no interiorEntry'); }
  else if (!walk(t[ey][ex])) msgs.push('entry (' + ex + ',' + ey + ') is ' + NAME(t[ey][ex]) + ' — not walkable');

  const seen = Array.from({ length: H }, () => new Uint8Array(W));
  if (ex != null && ey != null && ex >= 0 && ey >= 0 && ex < W && ey < H) {
    const q = [[ex, ey]]; seen[ey][ex] = 1;
    while (q.length) {
      const [x, y] = q.pop();
      for (const [dx, dy] of [[0,1],[0,-1],[1,0],[-1,0]]) {
        const nx = x+dx, ny = y+dy;
        if (nx<0||ny<0||nx>=W||ny>=H||seen[ny][nx]) continue;
        if (!walk(t[ny][nx])) continue;
        seen[ny][nx] = 1; q.push([nx, ny]);
      }
    }
  }
  const missing = {};
  let stairsSeen = 0, stairsTotal = 0, exitSeen = 0, exitTotal = 0;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const c = t[y][x];
    if (!SPECIAL.includes(c)) continue;
    if (c === 's') { stairsTotal++; if (seen[y][x]) stairsSeen++; }
    if (c === 'D') { exitTotal++;   if (seen[y][x]) exitSeen++; }
    if (!seen[y][x] && walk(c)) missing[c] = (missing[c] || 0) + 1;
  }
  if (stairsTotal > 0 && stairsSeen === 0) msgs.push('NO reachable stairs (' + stairsTotal + ' on the level)');
  if (exitTotal   > 0 && exitSeen   === 0) msgs.push('NO reachable exit (' + exitTotal + ' on the level)');
  const orphan = Object.entries(missing).filter(([c]) => c !== 's' && c !== 'D');
  if (orphan.length) msgs.push('walled off: ' + orphan.map(([c,n]) => n + '×' + NAME(c)).join(', '));

  if (msgs.length) { problems++; console.log(f.padEnd(26) + msgs.join('; ')); }
}
console.log('\n' + files.length + ' levels checked, ' + problems + ' with something unreachable');
process.exit(problems ? 1 : 0);
