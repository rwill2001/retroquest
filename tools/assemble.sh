#!/usr/bin/env bash
# assemble.sh — cut recorded shots together into one sequence.
#
#   sh tools/assemble.sh <edl> <out.mp4>
#
# An EDL is one `<shot-id> <seconds>` per line, in running order; `#` comments and
# blank lines are ignored. Each shot is trimmed from its head to that length and the
# results are concatenated.
#
# Trimming re-encodes rather than stream-copying. `-c copy` can only cut on a
# keyframe, so a 1.75s trim of a 4s clip silently comes back 2s long or starts on a
# grey frame — and four wall-style clips that are each a beat too long is exactly the
# kind of error nobody sees until the music no longer lines up.
#
# This produces an assembly to watch, not a delivery. Titles, lower thirds, the grade
# and the audio are the edit's job — see docs/video/01-SCRIPT.md.
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${RQ_RECORD_OUT:-$REPO/out}"

die() { printf 'assemble: %s\n' "$*" >&2; exit 1; }

# MSYS rewrites paths handed to a Windows binary as arguments, but not paths written
# into a file that binary then reads. Only the concat list needs this.
native_path() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi
}

[ $# -eq 2 ] || die "usage: sh tools/assemble.sh <edl> <out.mp4>"
edl="$1"; final="$2"
[ -f "$edl" ] || die "no such edl: $edl"
command -v ffmpeg >/dev/null 2>&1 || die "ffmpeg is not on PATH"

work="$OUT/_assembly"
rm -rf "$work"; mkdir -p "$work" || die "cannot create $work"
list="$work/list.txt"; : > "$list"

n=0; total=0
while read -r id secs _; do
  case "$id" in ''|'#'*) continue ;; esac
  src="$OUT/$id/$id.mp4"
  [ -f "$src" ] || die "missing clip for '$id' — run: sh tools/record.sh $id"

  have="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$src")"
  if awk -v h="$have" -v w="$secs" 'BEGIN{exit !(h + 0.05 < w)}'; then
    printf 'assemble: WARNING — %s is %.2fs but the edl asks for %ss\n' "$id" "$have" "$secs" >&2
  fi

  n=$((n + 1))
  seg="$(printf '%s/%02d-%s.mp4' "$work" "$n" "$id")"
  ffmpeg -y -loglevel error -i "$src" -t "$secs" \
    -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p -an "$seg" \
    || die "trim failed for $id"
  # ffmpeg here is a native Windows build, so a POSIX /c/... path in the concat list
  # reaches it as a *relative* path and it prepends the drive: C:/c/Development/...
  # Every other path in this script is an argument, which MSYS rewrites on the way
  # through; the ones inside the list file are not, so they get converted by hand.
  printf "file '%s'\n" "$(native_path "$seg")" >> "$list"
  total="$(awk -v t="$total" -v s="$secs" 'BEGIN{printf "%.2f", t + s}')"
  printf 'assemble: %2d. %-18s %ss\n' "$n" "$id" "$secs"
done < "$edl"

[ "$n" -gt 0 ] || die "edl listed no shots"

mkdir -p "$(dirname "$final")"
ffmpeg -y -loglevel error -f concat -safe 0 -i "$list" -c copy "$final" \
  || die "concat failed"

printf 'assemble: %s — %s clips, %ss, %s\n' "$final" "$n" "$total" "$(du -h "$final" | cut -f1)"
