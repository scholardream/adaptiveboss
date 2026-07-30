"""Week 4 replay tool: pretty-prints one fight log (.ndjson) as a timeline.

Zero dependencies — standard library only (json / argparse / collections).
Python 3.10+. requirements.txt is unchanged: replay.py needs nothing new.

Usage:
    python replay.py run/saves/<world>/adaptive_boss_logs/fight_<id>.ndjson
"""

import argparse
import json
import sys
from collections import Counter
from pathlib import Path


def fmt_hp(hp: float, max_hp: float) -> str:
    return f"{hp:6.1f}/{max_hp:.1f}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Replay an AdaptiveBoss fight log (NDJSON).")
    parser.add_argument("logfile", type=Path, help="path to a fight_*.ndjson file")
    args = parser.parse_args()

    if not args.logfile.is_file():
        print(f"error: no such file: {args.logfile}", file=sys.stderr)
        return 1

    meta = None
    summary = None
    frames = []
    for lineno, raw in enumerate(args.logfile.read_text(encoding="utf-8").splitlines(), 1):
        raw = raw.strip()
        if not raw:
            continue
        try:
            line = json.loads(raw)
        except json.JSONDecodeError as e:
            print(f"warning: skipping malformed line {lineno}: {e}", file=sys.stderr)
            continue
        kind = line.get("type")
        if kind == "meta":
            meta = line
        elif kind == "frame":
            frames.append(line)
        elif kind == "summary":
            summary = line
        else:
            print(f"warning: unknown line type at {lineno}: {kind!r}", file=sys.stderr)

    if meta:
        print(f"fight    : {meta.get('fight_id')}")
        print(f"started  : {meta.get('start_time')}")
        print(f"world    : {meta.get('world')} ({meta.get('dimension')}), difficulty {meta.get('difficulty')}")
        print(f"boss     : {meta.get('boss_uuid')}")
        print(f"target   : {meta.get('target_uuid')}")
    else:
        print("warning: no meta line found", file=sys.stderr)
    print()

    if not frames:
        print("(no decision frames)")
    total_boss_hurt = 0.0
    total_player_hurt = 0.0
    action_counts: Counter[str | None] = Counter()
    source_counts: Counter[str] = Counter()

    header = f"{'frame':>5} {'dist':>6} {'boss hp':>14} {'player hp':>14} {'action':<18} {'src':<16} {'boss_hurt':>9} {'player_hurt':>11}"
    print(header)
    print("-" * len(header))
    for frame in frames:
        state = frame.get("state", {})
        boss = state.get("boss", {})
        player = state.get("player", {})
        action = frame.get("action")
        boss_hurt = float(frame.get("boss_hurt", 0.0))
        player_hurt = float(frame.get("player_hurt", 0.0))
        total_boss_hurt += boss_hurt
        total_player_hurt += player_hurt
        action_counts[action] += 1
        source_counts[frame.get("source", "?")] += 1
        print(
            f"{frame.get('frame', '?'):>5} "
            f"{state.get('distance', 0.0):>6.2f} "
            f"{fmt_hp(boss.get('hp', 0.0), boss.get('max_hp', 0.0)):>14} "
            f"{fmt_hp(player.get('hp', 0.0), player.get('max_hp', 0.0)):>14} "
            f"{action if action else '-':<18} "
            f"{frame.get('source', '?'):<16} "
            f"{boss_hurt:>9.1f} "
            f"{player_hurt:>11.1f}"
        )
    print()

    if summary:
        duration_ticks = summary.get("duration_ticks", 0)
        print(f"winner        : {summary.get('winner')}")
        print(f"duration      : {duration_ticks} ticks ({duration_ticks / 20.0:.1f} s)")
        print(f"boss hp left  : {summary.get('boss_hp_remaining')}")
        print(f"player hp left: {summary.get('player_hp_remaining')}")

        behavior = summary.get("behavior", {})
        print(f"behavior      : melee={behavior.get('melee_attacks', 0)} "
              f"ranged={behavior.get('ranged_attacks', 0)} "
              f"potions={behavior.get('potion_drinks', 0)}")
        histogram = behavior.get("move_histogram", {})
        if histogram:
            moves = " ".join(f"{k}:{v}" for k, v in histogram.items() if v)
            print(f"move histogram: {moves or '(no movement)'}")

        skill_usage = summary.get("skill_usage", {})
        if skill_usage:
            total_casts = sum(skill_usage.values())
            print("skill casts   :")
            for skill_id, count in sorted(skill_usage.items(), key=lambda kv: -kv[1]):
                print(f"  {skill_id:<20} {count:>3}  ({count / total_casts * 100:.0f}%)")
        else:
            print("skill casts   : (none)")
    else:
        print("warning: no summary line found (fight may have been cut off)", file=sys.stderr)
    print()

    print("frame stats")
    print(f"  frames           : {len(frames)}")
    print(f"  boss damage taken: {total_boss_hurt:.1f}")
    print(f"  damage dealt     : {total_player_hurt:.1f}")
    decisions = ", ".join(f"{a if a else '-'} x{n}" for a, n in action_counts.most_common())
    print(f"  decisions        : {decisions or '(none)'}")
    sources = ", ".join(f"{s} x{n}" for s, n in source_counts.most_common())
    print(f"  sources          : {sources or '(none)'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
