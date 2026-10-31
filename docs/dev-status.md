# 开发状态交接（给新会话用）

> 目的：新会话只需读本文件即可恢复上下文，不必携带历史对话。
> 仓库：`F:\xiangmu\2dai\Imitating-the-Strinova-Mechanism`
> 当前版本：1.2.2（Minecraft 1.20.1 / Fabric + Forge / Architectury）

## 构建

```bash
.\gradlew.bat --offline --console=plain build -x test
```

产物：`fabric/build/libs/...-fabric-1.2.2.jar`、`forge/build/libs/...-forge-1.2.2.jar`。
提交前注意：仓库已配置 git 用户；推送 `git push origin main`。

## 已完成的模块

### 1. 原版核心机制
- 弦化（纸片）状态、贴墙、飘飞、多段跳，键位 Ctrl / R / Space；
- 碰撞箱分体/微调、描边颜色、TACZ 兼容（第一人称/退出状态/持枪屏蔽贴墙键）；
- 配置 `config/strinova.json`（服务端权威，启动自动启用 `autoEnableOnStart`）。

### 2. 战斗 HUD（P0，程序化绘制）
- 包：`common/.../client/hud/`，Painter 抽象（后续可换贴图实现）；
- 部件：顶部双方比分、左下血甲+增益、Q/X/C 技能、右下 5 格武器栏（TACZ 读弹匣/备弹）、击杀播报、伤害数字、准星、状态；
- TAB 计分板：`StrinovaScoreboardOverlay`，屏占比 66%×51.5%，无大背景/无外框，回合记录带在双方中间（我方/敌方 + 爆/拆/灭/时）；
- 计分板编辑器：`/strinova_client scoreboard`（拖动/列宽/行高/字体）、`scoreboard style`（逐部件可见/字号/颜色/居中/透明度）、`scoreboard export|import`（JSON+剪贴板）、`scoreboard values`（复制数值）；
- 默认值来自用户导出配置（列宽/行高/字号见 `StrinovaHudConfig`）；
- 字体：`StrinovaSystemFontRenderer` 运行时加载文泉驿微米黑（优先 config/strinova-fonts 与系统 wqy），失败回退；`useYaheiFont` 可关闭；
- UI 高斯模糊：本模组界面打开时用自定义 post shader（半径 8），`uiBlurEnabled` 可关。

### 3. 选角 / 成长界面
- `/strinova_client agent` 选角（StrinovaAgentSelectScreen，真实布局：阶段条/队友/24 格/立绘占位/技能武器/锁定）；
- `B` 键或 `/strinova_client growth` 弦能增幅网络（7 类×2 项购买、5 武器槽、3 觉醒）；
- 角色数据：`StrinovaAgentRoster`（24 人、定位、阵营、主题色）；
- 客户端配置：`config/strinova-client.json`（HUD、布局、计分板、字体开关）。

### 4. 对局/战斗数据层（M1）
- 服务端 `StrinovaCombatManager`（护甲 80、KDA、绝招、击杀事件）、`StrinovaMatchManager`（分队/比分/回合/90s/回合记录）；
- S2C 同步 `s2c_match_state`（500ms 节流 + 登录单发），客户端写 MatchState/ScoreboardState/HudState；
- 命令：`/strinova match start|stop|status`（OP 2）；
- 平台钩子：伤害吸收（先纸片减伤）、死亡去重（Tag）、登录/登出/重生、server tick 广播。

## 已知 TODO / 下一阶段

1. **wiki 文本校对**：用 biligame / strinova.org 核对 24 名超弦体的名字、定位、阵营、技能名，以及增幅网络类别/觉醒文案（用户最近需求）；
2. 枪械/命中部位系统（TACZ 伤害接入护甲、爆头、弹药 HUD）；
3. 炸弹/目标点系统（回合记录里的“炸弹爆炸/拆除”目前只有数据 key，未产生）；
4. 对局 UI 数据：成长等级/输出分/技能分目前是 KDA 派生或占位；
5. 选角锁定未下发服务端；计分板角色名空白（服务端不知道客户端选角）。

## 约定

- 用户偏好中文回复；希望有可视化预览（截图/示意图）后再交付；
- 不要问“是否继续”这类确认，直接做；
- 大文件改动前用 grep/read 精确定位，避免整文件重复读入（控制上下文）。
