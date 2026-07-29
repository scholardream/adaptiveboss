# Adaptive Boss — 会学习玩家打法的 Minecraft Boss

> **它的技能是我手工设计的，但它的战术是学你的。**
> Its skills are hand-designed; its tactics are learned from you.

一个带强化学习能力的 Minecraft Boss：**外貌、技能、数值由人设计，"在什么局势下、对什么类型的玩家、放哪个技能"由 AI 学**。同一个玩家用同一招打赢第一场，第二场 Boss 会针对性反制——"你赢不了第二次"。

## 架构

```
Minecraft (Java, Fabric mod)
 ├── Boss 实体 + 技能系统 + 行为树兜底
 ├── 战斗数据采集器 (状态/动作/结果 → NDJSON)
 └── 通信桥 (TCP socket, 每 5 tick 发状态, 收动作)
          ↕
Python 服务
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
| 2 | 技能系统框架 (冲锋/震地/弹幕 + RandomPolicy) | ✅ 本次更新 |
| 3 | Java↔Python 通信桥 (断线降级兜底) | ⬜ |
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
- 决策来源是可插拔的 `DecisionPolicy`，当前为 `RandomPolicy`，第 3 周换成 Python 通信桥；
- 前摇期间播粒子预警（圈/线），玩家看得见就能反制——"何时放"才是真正的决策；
- **所有数值外置**：首次启动生成 `config/adaptiveboss.json`，改完重启生效。

## 环境搭建

前置：**JDK 21**（`java -version` 确认），Git。

```powershell
git clone https://github.com/scholardream/adaptiveboss.git
cd adaptiveboss

# 补齐 gradle wrapper jar（二进制文件不进 git）
Invoke-WebRequest "https://raw.githubusercontent.com/FabricMC/fabric-example-mod/1.21/gradle/wrapper/gradle-wrapper.jar" -OutFile "gradle\wrapper\gradle-wrapper.jar"

# 启动开发客户端（首次会下载 MC 和依赖，较久）
.\gradlew.bat runClient
```

进游戏后创建一个世界（开作弊），执行：

```
/summon_adaptive_boss
```

## 当前进度说明

- `AdaptiveBossEntity`：500 血、攻击 12、护甲 8、移速 0.28、抗击退 0.9，索敌范围 64，原版近战 AI 追人平 A 兜底。
- 技能系统已上线：3 个技能 + 前摇预警 + 冷却 + RandomPolicy 调度。
- 模型/动画为占位文件（`assets/adaptiveboss/geo|animations`），贴图缺失会显示紫黑方块——等 Blockbench 模型做好后替换同名文件即可，Java 侧无需改动。

## License

MIT
