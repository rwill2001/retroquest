#!/bin/sh
# Content health check over the shipped data in data/.
#
#     sh tools/check-content.sh
#
# Fails (non-zero) if anything on the critical path is broken. Run it after editing maps,
# tiles, quests or NPC placement — every one of these checks exists because a scripted
# playthrough walked into the problem it looks for.
#
#   critical-path   arrival -> towns -> dungeons -> key -> exit portal, island by island
#   quest-audit     every quest offered by a reachable NPC, with a target that exists
#   npc-audit       every NPC reachable, and not hidden behind one standing in front
#   dungeon-audit   every walkable feature reachable from its own level's entry
#   hazard-audit    the stairs reachable without stepping on a pit, chute or teleporter
#   boss-audit      every bottom-of-dungeon boss sits on a real bottom, with a reachable altar
#   forage-audit    every hunting ground reachable and running a real hunt; food in vs food out
#
# xp-ladder.js is informational: it prints how much of each island's climb its quests pay
# for, and how many fights make up the rest.
cd "$(dirname "$0")/.." || exit 1
fail=0
for check in critical-path quest-audit npc-audit dungeon-audit hazard-audit boss-audit forage-audit; do
  echo ""
  echo "======== $check ========"
  node "tools/$check.js" || fail=1
done
echo ""
echo "======== spawn ladder (informational) ========"
node tools/xp-ladder.js
echo ""
if [ $fail -eq 0 ]; then echo "content checks passed"; else echo "CONTENT CHECKS FAILED"; fi
exit $fail
