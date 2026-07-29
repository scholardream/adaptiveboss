"""Week 3 decision service for the AdaptiveBoss Fabric mod.

Zero-dependency asyncio TCP server. Protocol: NDJSON — one JSON object per
line, request and reply. Field names mirror the Java side
(com.scholardream.adaptiveboss.bridge.BattleStateJson) one to one.

This week only proves the link: it picks a random skill from
``available_skills``. The RL policy replaces ``decide()`` in later weeks.

Requires Python 3.10+.
"""

import asyncio
import json
import logging
import random
from dataclasses import dataclass, field

HOST = "127.0.0.1"
PORT = 25575

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("decision_server")


@dataclass
class BossState:
    hp: float
    max_hp: float
    x: float
    y: float
    z: float
    yaw: float


@dataclass
class PlayerState:
    uuid: str
    hp: float
    max_hp: float
    x: float
    y: float
    z: float
    vx: float
    vy: float
    vz: float
    held_item: str = "minecraft:air"
    potion_effects: list[str] = field(default_factory=list)


@dataclass
class BehaviorSummary:
    melee_attacks_5s: int = 0
    ranged_attacks_5s: int = 0
    potion_drinks_5s: int = 0
    move_histogram_5s: dict[str, int] = field(default_factory=dict)


@dataclass
class BattleState:
    tick: int
    boss: BossState
    player: PlayerState
    distance: float
    behavior: BehaviorSummary
    cooldowns: dict[str, int]
    available_skills: list[str]


def parse_state(raw: dict) -> BattleState:
    """Build a BattleState from one decoded NDJSON line."""
    return BattleState(
        tick=raw["tick"],
        boss=BossState(**raw["boss"]),
        player=PlayerState(**raw["player"]),
        distance=raw["distance"],
        behavior=BehaviorSummary(**raw.get("behavior", {})),
        cooldowns=raw.get("cooldowns", {}),
        available_skills=raw.get("available_skills", []),
    )


def decide(state: BattleState) -> tuple[str | None, str]:
    """Random baseline; returns (skill_id, reason)."""
    if not state.available_skills:
        return None, "no-skill-available"
    return random.choice(state.available_skills), "random-baseline"


async def handle_client(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
    peer = writer.get_extra_info("peername")
    log.info("boss connected: %s", peer)
    try:
        while True:
            line = await reader.readline()
            if not line:
                break
            line = line.strip()
            if not line:
                continue
            try:
                state = parse_state(json.loads(line))
            except (json.JSONDecodeError, KeyError, TypeError) as e:
                log.warning("bad state line from %s: %s", peer, e)
                continue
            action, reason = decide(state)
            log.info(
                "tick=%d distance=%.2f action=%s (%s)",
                state.tick, state.distance, action, reason,
            )
            writer.write((json.dumps({"action": action, "reason": reason}) + "\n").encode())
            await writer.drain()
    except (ConnectionResetError, BrokenPipeError):
        pass
    finally:
        log.info("boss disconnected: %s", peer)
        writer.close()
        try:
            await writer.wait_closed()
        except ConnectionResetError:
            pass


async def main() -> None:
    server = await asyncio.start_server(handle_client, HOST, PORT)
    log.info("decision server listening on %s:%d (NDJSON, random baseline)", HOST, PORT)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
