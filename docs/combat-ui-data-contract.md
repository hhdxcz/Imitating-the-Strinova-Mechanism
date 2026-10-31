# 战斗 HUD 数据契约（v0.1）

本文档定义「战斗 HUD」与未来「战斗系统」之间的数据接口。HUD 先按本契约接模拟数据，
战斗系统落地后只需按契约填充 `StrinovaHudState`（或由 S2C 包写入），UI 层不用改。

## 1. 数据流

```
战斗系统（服务端权威判定）
    │  ① 事件包：命中/击杀/受击/技能触发（低频）
    │  ② 状态包：生命/护甲/弹药/换弹/技能冷却/终极点数（变化时 + 10Hz 补发）
    ▼
S2C: strinova:s2c_combat_hud  →  common/.../client/hud/StrinovaHudState  →  StrinovaHudRenderer → 各 Widget
    ▲
    └─ 客户端预测仅用于：准星扩散、命中标记、伤害数字（本地即时节流），
       最终数值以服务端下发为准（防作弊）
```

当前实现（战斗系统未完成）：
- 生命值：取玩家 MC 真实血量映射到 0~100（`StrinovaHudRenderer.syncFromPlayer`）
- 护甲/弹药/技能/击杀播报：`StrinovaHudMockData` 模拟，`/strinova_client hud mock` 开关
- 弦化/贴墙/飘飞/段跳：使用现有真实状态（`WaPaperState` / `StrinovaAirJumpClientState`）

## 2. 字段契约

### 2.1 生命 / 护甲

| 字段 | 类型 | 说明 | 数据源（计划） |
|---|---|---|---|
| `health` / `maxHealth` | float | 生命值（HUD 显示 0~100 标度） | 服务端 S2C |
| `armor` / `maxArmor` | float | 护甲值/上限（角色差异，如 80） | 服务端 S2C |
| `armorRegen`（P1） | float | 每秒护甲回复，用于 HUD 回复动画 | 服务端 S2C 或客户端按值推算 |
| `lowHealthPulse`（P1） | boolean | 低血量告警脉冲 | 客户端由 health 推导 |

### 2.2 武器 / 弹药

| 字段 | 类型 | 说明 |
|---|---|---|
| `weaponName` | String | 当前武器显示名（未装备为空） |
| `fireMode` | enum | AUTO/SEMI/BURST/PUMP/BOLT/MELEE |
| `magazine` / `reserve` | int | 弹匣/备弹 |
| `reloadStartMs` / `reloadDurationMs` | long | 换弹进度（0 表示未换弹） |
| `selectedSlot` | int | 当前武器槽位（0~3） |
| `weaponSlots` | List<WeaponSlot> | 槽位列表（P1 显示武器图标） |

### 2.3 技能 / 终极

| 字段 | 类型 | 说明 |
|---|---|---|
| `skillCooldown[3]` | float[] | Q/E/X 冷却剩余比例 0~1（0=就绪） |
| `skillReady[3]` | boolean[] | 是否可用（含储存次数>0） |
| `ultimatePoints` / `ultimateMax` | int | 终极点数（爆破 7 点 / 其他模式 6500 分可映射为进度） |

### 2.4 反馈事件（低频，建议独立包）

| 字段 | 类型 | 说明 |
|---|---|---|
| `killFeed` | List<KillFeedEntry> | killer / weapon / victim / headshot / 时间戳；保留 8 秒 |
| `damageNumbers` | List<DamageNumber> | amount / headshot / mitigated / 时间戳；保留 1.1 秒 |
| `damageIndicators` | List<DamageIndicator> | 受击方向角（相对屏幕，0=正前）；保留 0.9 秒 |
| `hitMarker` | 时间戳 + kill 标记 | 命中标记，保留 220ms |

### 2.5 状态（已实现，来自现有状态机）

弦化 / 贴墙 / 飘飞 / 剩余段跳：直接读 `WaPaperState`、`StrinovaAirJumpClientState`，无需新协议。

### 2.6 队伍（P2，暂未实现）

`teammates: [{uuid, name, health, armor, role, outlineColor, alive, ultimatePoints}]` + 队伍计分。

## 3. 协议建议

- 通道：`strinova:s2c_combat_hud`（状态）、`strinova:s2c_combat_event`（事件，可合并）
- 状态包：全量 12 个数值字段（生命/护甲/弹药/换弹/技能/终极）体积极小，直接全量 10Hz + 变化立即发
- 事件包：击杀播报/伤害数字/受击方向按事件发送，客户端做去重与节流
- 服务端权威：伤害数字与击杀播报必须由服务端下发，客户端不得自报
- 兼容：`StrinovaHudState` 已按“服务端可写”设计，接入时在包处理器里调用对应 setter 即可

## 4. UI 文件清单（本次交付）

```
common/.../client/hud/
├── StrinovaHudRenderer.java          // 注册 RENDER_HUD、逐帧渲染、编辑器预览 API
├── StrinovaHudLayout.java            // 元素/锚点/偏移布局模型
├── StrinovaHudPainter.java           // 绘制抽象（程序化 → 贴图可替换）
├── StrinovaProgrammaticPainter.java  // 程序化绘制实现（零贴图资源）
├── StrinovaHudWidget.java            // 部件接口
├── StrinovaHudState.java             // 本契约的数据容器
├── StrinovaHudConfig.java            // 客户端 HUD 配置（config/strinova-client.json）
├── StrinovaHudMockData.java          // 模拟数据源
├── StrinovaVitalsWidget.java         // 生命/护甲
├── StrinovaAmmoWidget.java           // 武器/弹药
├── StrinovaSkillsWidget.java         // Q/E/X/终极
├── StrinovaStateWidget.java          // 弦化/贴墙/飘飞/段跳
├── StrinovaCrosshairWidget.java      // 动态准星 + hitmarker
├── StrinovaKillFeedWidget.java       // 击杀播报
└── StrinovaDamageNumbersWidget.java  // 伤害数字 + 受击方向
common/.../client/
├── StrinovaCombatSettingsScreen.java // HUD 设置界面
└── StrinovaHudEditorScreen.java      // HUD 布局编辑器
docs/ui/hud-preview.html              // 静态布局预览（给非游戏环境确认用）
```

## 5. 迁移到贴图版（A → B）步骤

1. 新增 `StrinovaTexturePainter implements StrinovaHudPainter`（图集 + 九宫格面板 + 图标 + 自定义字体）
2. 在 `StrinovaHudRenderer` 构造 Painter 的地方切换实现（1 处）
3. 各 Widget 只依赖 Painter 接口，布局/数据/设置界面无需改动
4. 新增 `assets/strinova/textures/gui/hud.png` 等资源与 lang 不变

## 6. 对局状态 / 计分板 / 选角（第二批 UI）

### 6.1 StrinovaMatchState（顶部比分面板）

| 字段 | 类型 | 说明 |
|---|---|---|
| `active` | boolean | 是否有进行中的对局（无对局时顶部面板不显示） |
| `modeKey` | String | 模式 lang key（当前为爆破入侵） |
| `phase` | enum | WARMUP / ROUND / ROUND_END / MATCH_END |
| `round` | int | 当前回合 |
| `roundSecondsLeft` | int | 回合倒计时（秒） |
| `alliesScore` / `enemiesScore` | int | 双方比分 |
| `alliesAttacking` | boolean | 我方进攻/防守 |

### 6.2 StrinovaScoreboardState（TAB 计分板）

`Entry`：`name`、`agentName`、`ally`、`self`、`kills`、`deaths`、`assists`、`health`（0~100）、`armor`（0~80）、`status`（ALIVE / DOWNED / DEAD）。

- 按住 TAB 且存在进行中对局时显示自定义全屏计分板；
- 自定义计分板激活时由 `PlayerTabOverlayMixin` 取消原版玩家列表渲染，避免两套列表叠加；
- 无对局时保持原版行为。

### 6.3 StrinovaAgentRoster（超弦体选角）

24 名超弦体，字段：`id` / `name` / `role`（突击、决斗、守护、支援、先锋、控场）/ `faction`（欧泊、剪刀手、乌尔比诺）/ `accentColor`。
`roleNameKey()`、`roleDescKey()`、`factionNameKey()` 返回 lang key；选角结果保存在客户端配置 `strinova-client.json` 的 `selectedAgentId`。

### 6.4 后续协议建议

- 对局状态：`strinova:s2c_match_state`（变化时 + 1Hz 全量，字段同 6.1）
- 计分板：`strinova:s2c_scoreboard`（每 2~5 秒或击杀/倒地事件触发，字段同 6.2）
- 选角：进入对局前由服务端确认并下发本局角色；客户端本地选择仅用于 UI 预览
- 顶部比分面板与 TAB 计分板在没有对局数据时自动隐藏，不影响原版玩法

### 6.5 第二批新增文件

```
common/.../client/hud/StrinovaMatchState.java          // 对局状态契约
common/.../client/hud/StrinovaScoreboardState.java     // 计分板契约
common/.../client/hud/StrinovaAgentRoster.java         // 24 名超弦体图鉴数据
common/.../client/hud/StrinovaMatchHeaderWidget.java   // 顶部双方比分面板
common/.../client/hud/StrinovaScoreboardOverlay.java   // TAB 全屏计分板
common/.../mixin/client/PlayerTabOverlayMixin.java     // 隐藏原版玩家列表
common/.../client/StrinovaAgentSelectScreen.java       // 超弦体选角界面
common/.../client/StrinovaCombatSettingsScreen.java    // 新增顶部面板/计分板开关
docs/ui/agent-select-preview.html / .png               // 选角预览
docs/ui/scoreboard-preview.html / .png                 // 计分板预览
```

## 7. P0 对局 HUD 对齐真实布局（补充契约）

依据真实游戏截图（见 `docs/ui/strinova-ui-reference.md`）对以下字段/布局做了调整：

| 项 | 调整 |
|---|---|
| 生命/护甲标度 | 生命标度改为 **0~150**（截图显示 130），护甲 **0~80**（截图显示 80）；HUD 显示当前值而非 0~100 百分比 |
| 增益提示 | `StrinovaHudState.BuffEntry(name, color)` + `getBuffs()/setBuffs()`；显示在血甲面板下方（如「护甲恢复」「静心凝势」） |
| 武器栏 | `WeaponSlot` 扩展为 **5 个槽位**（主武器/副武器/近战/战术道具×2），HUD 改为右下竖排 1~5，当前槽展开武器名 + 弹匣/备弹 |
| 技能栏 | 改为真实布局 **Q / X / C** 三个菱形：Q=战术技能冷却、X=绝招点数（0/7）、C=第三槽 |
| 顶部比分面板 | 左右各 5 个角色头像（存活绿勾）+ 中央「目标 N 回合 / 倒计时 / 红蓝比分」；宽度自适应（408px，适配小 GUI 缩放） |
| 默认布局 | 顶部比分 y=6、击杀播报下移避开比分面板、左下血甲/状态、右下武器栏、底部技能栏同步调整 |

后续接入真实对局系统时，只需用服务端 S2C 填充上述字段，HUD 布局与绘制不用再改。

## 8. M1 已接入真实数据（战斗/对局数据层）

### 8.1 服务端状态

- `gameplay/StrinovaCombatManager`：护甲（80/80，远程伤害吸收 50%，1 MC 伤害=7.5 护甲点；脱战 3 秒后每秒 +1 回甲）、KDA（含 10 秒内伤人助攻）、绝招点（实际伤害每 40 点 +1，上限 7）、受击归属与 `KillEvent` 队列。
- `gameplay/StrinovaMatchManager`：在线玩家交替分两队、比分 0:0、回合与 90 秒倒计时；计时归零敌方 +1 分并开下一回合；退出时移除队伍。

### 8.2 同步协议

新增 `strinova:s2c_match_state`（500ms 节流 + 变化即时，登录时单独发一次）：

```text
active: bool
[modeKey, round, secondsLeft, alliesScore, enemiesScore,
 alliesTeamKey, enemiesTeamKey]
killEvents: [killer, weapon, victim, headshot] × N
players: [uuid, name, ally, kills, deaths, assists,
          health(0~150), armor(0~80), ultimatePoints, ultimateMax,
          alive, weaponName, growthLevel, damageScore, skillScore, assistScore, ping] × N
```

客户端 `StrinovaNetwork` 收到后写入 `StrinovaMatchState` / `StrinovaScoreboardState` / `StrinovaHudState`（自身生命/护甲/绝招）+ 击杀播报；`StrinovaHudState.isServerMatchActive()` 为真时 HUD 优先使用服务端数据（3 秒没收到同步才回退 MC 血量/模拟数据）。

### 8.3 钩子与命令

- 伤害：`PlayerMixin` 的 `hurt` `@ModifyVariable`（先弦化减伤 → 再护甲吸收）；死亡：`die(DamageSource)` HEAD 记录 + 临时 Tag 去重（平台死亡事件仅兜底）。
- 平台：Fabric `END_SERVER_TICK` / Forge `ServerTickEvent(Phase.END)` 调用两个 Manager tick + `broadcastMatchState`；登录 ensure + 发送快照；登出清理；重生回满护甲并保留 KDA。
- 命令：`/strinova match start|stop|status`（OP）。

### 8.4 仍为占位（后续接战斗系统时填充）

武器/弹药/技能冷却（暂无枪械系统，HUD 显示未装备/空槽）、角色名（服务端不知道客户端选角）、倒地/救援状态、经济/购买；计分板的输出分/技能分/协作分为 KDA 派生值，`KillEvent.headshot` 暂为 false。
