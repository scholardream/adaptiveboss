# Adaptive Boss — 会学习玩家打法的 Minecraft Boss

> **它的技能是我手工设计的，但它的战术是学你的。**
> Its skills are hand-designed; its tactics are learned from you.

一个带强化学习能力的 Minecraft Boss：**外貌、技能、数值由人设计，"在什么局势下、对什么类型的玩家、放哪个技能"由 AI 学**。同一个玩家用同一招打赢第一场，第二场 Boss 会针对性反制——"你赢不了第二次"。

Boss 正式命名 **Vealorny（维洛尼）**，二阶段 Boss：一阶段"观测骑士"在战斗中收集你的行为数据，被击败后根据数据决定二阶段的针对性形态。一阶段建模规划书见 [docs/design/PHASE1_MODEL_SPEC.md](docs/design/PHASE1_MODEL_SPEC.md)。

## 架构

```
Minecraft (Java, Fabric mod)
 ├── Boss 实体 + 技能系统 + 行为树兜底
 ├── 战斗数据采集器 (状态/动作/结果 → NDJSON)
 └── 通信桥 (TCP socket, 每 5 tick 发状态, 收动作)   ✅ 已上线
          ↕
Python 服务 (python/)
 ├── 战术决策器 (Phase 1: contextual bandit / Phase 2: RL)
 ├── 玩家画像库 (按 UUID 持久化到存档)
 └── 训练脚本 (离线复盘战斗日志, 更新策略)
```

## 技术栈

| 层 | 选型 |
|---|---|
| Mod | Fabric Loader + Fabric API, Minecraft **1.21.1**, Java **21** |
| 建模/动画 | Blockbench + **GeckoLib 4** |
| 决策 | Python asyncio TCP server → LinUCB contextual bandit → Stable Baselines3 (stretch) |
| 构建 | Gradle (Groovy DSL) + Fabric Loom |

## 八周里程碑

| 周 | 目标 | 状态 |
|---|---|---|
| 1 | Fabric 骨架 + 自定义 Boss 实体 | ✅ |
| 2 | 技能系统框架 (冲锋/震地/弹幕 + RandomPolicy) | ✅ |
| 3 | Java↔Python 通信桥 (NDJSON 协议, 100ms 超时降级 RandomPolicy, 自动重连) | ✅ |
| 4 | 战斗数据采集 (NDJSON, 可回放) | ⬜ |
| 5 | Bandit 战术决策器 (胜率显著高于随机) | ⬜ |
| 6 | 适应性闭环 ("复仇"可演示) | ⬜ |
| 7 | 打磨 + 录 demo | ⬜ |
| 8 | 发布 (Release + B 站视频) | ⬜ |

## 技能设计（克制关系是 AI 可学习的前提）

| 技能 | 克制 | 前摇 telegraph | 冷却 |
|---|---|---|---|
| `charge` 冲锋 | 远程风筝 | 15 tick，地面红色预警线指向目标 | 60 tick |
| `area_slam` 范围震地 | 绕背贴脸 | 20 tick，橙色预警圈标出爆炸半径 | 100 tick |
| `projectile_volley` 弹幕 | 站桩输出 | 10 tick，紫色贴身预警圈 | 80 tick |

- 技能接口：`Skill`（id / 冷却 / 前摇 / `canCast` / `cast`），由 `SkillScheduler` 每 5 tick 驱动一次决策；
- 决策来源是可插拔的 `DecisionPolicy`，当前默认 `SocketPolicy`（问 Python），超时/断线自动降级 `RandomPolicy`；
- 前摇期间播粒子预警（圈/线），玩家看得见就能反制——"何时放"才是真正的决策；
- **所有数值外置**：首次启动生成 `config/adaptiveboss.json`（技能数值 + 通信桥参数），改完重启生效。

## 通信桥协议（第 3 周）

- Java 端每 5 tick 把战斗状态（Boss/玩家状态、5 秒行为摘要、技能冷却、可用技能）序列化为 NDJSON 经 TCP 发到 `127.0.0.1:25575`；
- Python 端 `python/decision_server.py`（零依赖 asyncio）回 `{"action": "skill_id", "reason": "..."}`；
- 100ms 无回复或断线 → 本次决策降级 RandomPolicy，后台线程指数退避自动重连，降级/恢复均写日志；
- 协议字段一一对应见 `python/README.md`。

## 环境搭建

前置：**JDK 21**（`java -version` 确认），Git，Python 3.10+。

```powershell
git clone https://github.com/scholardream/adaptiveboss.git
cd adaptiveboss

# 补齐 gradle wrapper jar（二进制文件不进 git）
Invoke-WebRequest "https://raw.githubusercontent.com/FabricMC/fabric-example-mod/1.21/gradle/wrapper/gradle-wrapper.jar" -OutFile "gradle\wrapper\gradle-wrapper.jar"

# 终端 1：起 Python 决策服务
python python/decision_server.py

# 终端 2：启动开发客户端（首次会下载 MC 和依赖，较久）
.\gradlew.bat runClient
```

进游戏后创建一个世界（开作弊），执行：

```
/summon_adaptive_boss
```

不起 Python 服务也能玩——Boss 会自动降级到本地随机策略（日志可见降级记录）。

## 当前进度说明

- `AdaptiveBossEntity`（Vealorny）：500 血、攻击 12、护甲 8、移速 0.28、抗击退 0.9，索敌范围 64，原版近战 AI 追人平 A 兜底。
- 技能系统：3 个技能 + 前摇预警 + 冷却 + 可插拔策略调度。
- 通信桥：战斗状态 NDJSON 上报 + 决策回执 + 行为统计（近战/远程/喝药/位移直方图 5 秒滚动窗口）。
- 模型/动画为占位文件（`assets/adaptiveboss/geo|animations`），Vealorny 一阶段模型按规划书制作中，做好后替换同名文件即可，Java 侧无需改动。

## License

MIT
