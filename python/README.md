# AdaptiveBoss — Python decision service

Week 3 bridge counterpart: receives the live battle state from the Fabric mod
over TCP (NDJSON: one JSON object per line) and replies with the skill to use.

## Run

```bash
python decision_server.py
```

Requires **Python 3.10+**. Zero dependencies — standard library only.

The server listens on `127.0.0.1:25575` (must match `bridge.host` /
`bridge.port` in the mod's `config/adaptiveboss.json`).

## Protocol

One request line per decision (every 5 ticks by default):

```json
{
  "tick": 12345,
  "boss": { "hp": 480.0, "max_hp": 500.0, "x": 1.0, "y": 64.0, "z": 2.0, "yaw": 90.0 },
  "player": {
    "uuid": "...", "hp": 20.0, "max_hp": 20.0,
    "x": 5.0, "y": 64.0, "z": 3.0,
    "vx": 0.1, "vy": 0.0, "vz": -0.2,
    "held_item": "minecraft:bow",
    "potion_effects": ["minecraft:speed"]
  },
  "distance": 5.2,
  "behavior": {
    "melee_attacks_5s": 3,
    "ranged_attacks_5s": 1,
    "potion_drinks_5s": 0,
    "move_histogram_5s": { "N": 10, "NE": 2, "E": 0, "SE": 0, "S": 5, "SW": 0, "W": 1, "NW": 0, "STILL": 40 }
  },
  "cooldowns": { "charge": 0, "area_slam": 12, "projectile_volley": 0 },
  "available_skills": ["charge", "projectile_volley"]
}
```

Reply (one line):

```json
{ "action": "charge", "reason": "random-baseline" }
```

`action` must be one of `available_skills`, or `null` to let the boss keep
basic melee/chase. `reason` is optional, for debug display.

## Notes

- This week `decide()` is a **random baseline** over `available_skills` — it
  only proves the link. The RL policy lands here in later weeks.
- If the server is down or slow (> `bridge.timeoutMs`, default 100 ms), the
  mod degrades to its local `RandomPolicy` automatically and reconnects with
  exponential backoff — the boss never freezes.
