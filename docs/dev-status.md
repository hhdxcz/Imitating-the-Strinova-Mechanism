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

### 3. 选角 / 成长界面（已接 wiki 真实文本）
- `/strinova_client agent` 选角（StrinovaAgentSelectScreen）：阶段条 / 5 名队友 / 24 格头像 / 中央立绘+角色简介+语录 / 右侧定位·阵营 chip、身份、**4 个技能卡（Q 主动 / P 被动 / X 终极 / E 战术，可点选切换下方名称+描述）**、武器/外观页签、**5 行武器（第 1 行为角色真实主武器名·类型，悬停显示武器介绍）**、锁定角色；
- `B` 键或 `/strinova_client growth` 弦能增幅网络（StrinovaGrowthScreen）：按当前角色 **8 个分支**（左列 4 武器网络 / 右列 主动技能 / 被动技能 / 护甲 / 弦化），每分支 1~2 项（150/250 能量点），悬停显示 wiki「属性变化」文案；底部 3 个**角色专属觉醒**（名称/描述/三色需求点，按已购数量点亮）；左侧立绘显示生命/护甲/移速/身份；
- 数据层：`StrinovaWikiData` 懒加载 `assets/strinova/data/wiki_zh.json`（主数据）+ `wiki_en.json`（英文文本覆盖），按游戏语言合并、缺字段回退；数据不可用时界面回退占位文案，不崩溃；
- 布局按真实游戏截图对齐（2026-10-31）：成长网络分支带类型图标（蓝菱形武器 / 金圆主动被动 / 绿盾护甲 / 绿三角弦化）、已升级项显示「已升级」、价格暖红+蓝宝石、右侧武器大卡（序号+类型+名称，战术槽提示「现在可装备战术技能」）、觉醒三色需求用「形状重复」表示（数量=需求）；选角界面顶部地图/阶段条+比分、右侧金色角色头衔块（定位图标+角色名）、主武器大卡 + 副武器/近战/战术 2×2 小卡、页签固定在面板底部、锁定按钮居中于头像网格上方；
- **可视化自定义（与 TAB 计分板同类体验）**：
  - `/strinova_client agent edit`（或界面右上角「自定义」）打开选角界面编辑器：拖拽队友列宽/右侧面板宽/锁定条高，逐区开关（顶部阶段条/队友列/立绘/简介/语录/右侧面板/技能/武器/页签/头像网格/锁定按钮/提示），不透明度，重置/保存/Esc 放弃；配置存 `config/strinova-client.json` 的 `agentSelectUi`，可 `agent export|import|values` 导入导出 JSON（`config/strinova-agent-select-ui.json`）；
  - `/strinova_client growth edit` 打开增幅网络界面编辑器：拖拽立绘宽/武器栏宽/觉醒区高、列/行间距、逐区开关、不透明度、配色覆盖、能量点 -/+（含重置购买）；配置存 `growthUi`，可 `growth export|import|values`（`config/strinova-growth-ui.json`）；
  - 玩法：**每个分支只能选一项**（选中后同类其余项灰显不可点）；分支标题右上角绿色**刷新图标**可返还该分支点数并重新选择；左侧立绘满高、觉醒区在立绘右侧底部、武器栏 1/2/3 整行 + 4/5 并排（对齐真实截图）；TAB 计分板「成长等级」对本地玩家读取客户端加点进度（1+已选数，激活觉醒时金色），加点/刷新后即时更新；
  - 分界线可调（编辑器）：标题栏高度、网络左右列分界、网络 4 行行高权重、立绘宽、武器栏宽、觉醒区高共 8 条线全部可拖拽；
  - 觉醒 2 / 3 二选一：两卡之间「⇄ 切换激活」按钮，两者需求都满足时可切换；觉醒 1 自动激活；
  - TAB 成长等级列（本地玩家）：蓝色方块（武器网络点数）/ 黄色圆（主动+被动技能点数）/ 绿色三角（护甲+弦化点数）计数 + ">" + 已激活觉醒的橙色八芒星（星内为觉醒序号），加点/刷新/切换实时更新；
  - 能量点可设置：命令 `/strinova_client growth points [数值]`（0~9999，存 `StrinovaHudConfig.growthEnergyPoints`，立即生效；重置购买后恢复该值）、`/strinova_client growth reset`（清空购买并恢复）；
- 角色 id/定位/阵营/主题色仍以 `StrinovaAgentRoster` 为权威；客户端配置 `config/strinova-client.json`。

### 4. 对局/战斗数据层（M1）
- 服务端 `StrinovaCombatManager`（护甲 80、KDA、绝招、击杀事件）、`StrinovaMatchManager`（分队/比分/回合/90s/回合记录）；
- S2C 同步 `s2c_match_state`（500ms 节流 + 登录单发），客户端写 MatchState/ScoreboardState/HudState；
- 命令：`/strinova match start|stop|status`（OP 2）；
- 平台钩子：伤害吸收（先纸片减伤）、死亡去重（Tag）、登录/登出/重生、server tick 广播。

### 5. wiki 文本数据层（本次新增）
- 契约：`docs/wiki-research/wiki-data-schema.md`；
- 数据：`common/src/main/resources/assets/strinova/data/wiki_zh.json`（24 角色 × 8 分支 × 1~2 项 = 192 组 / 365 项，全部带成本与属性变化文案；技能/觉醒/档案/数值齐全）与 `wiki_en.json`（英文文本覆盖层，成本/需求与中文逐项校验一致）；
- 生成脚本：`tools/wiki-sync.mjs`（biligame 中文全量抓取）、`tools/en-wiki-sync.mjs`（strinova.org 英文离线合并 + validator）；英文原始抓取经 web_fetch 获取后解析为 `docs/wiki-research/raw-en/*.json`；
- 说明与缺口：`docs/wiki-research/README.md`（含两站数据来源、重新生成步骤、已知缺口）；
- 界面预览：`tools/ui-preview.mjs` 生成 `docs/ui/growth-real-{zh,en}.png` 与 `select-real-{zh,en}.png`（1600×900，Edge 无头截图）。

## 已知 TODO / 下一阶段

1. **选角锁定下发服务端**：目前锁定只写客户端配置，计分板角色名仍空白；
2. **枪械/命中部位系统**：TACZ 伤害接入护甲、爆头、弹药 HUD（`KillEvent.headshot` 仍为 false）；
3. **炸弹/目标点系统**：回合记录里「炸弹爆炸/拆除」只有数据 key，尚未产生；
4. **对局 UI 数据**：成长等级/输出分/技能分目前为 KDA 派生或占位；
5. **英文数据补缺**：8 个老角色（米雪儿·李/信/心夏/伊薇特/芙拉薇娅/忧雾/蕾欧娜/千代）的战术技能英文描述在英文 wiki 模块数据中缺失（运行时回退中文）；英文无逐项「属性变化」明细（悬停文案沿用中文）；
6. **数据校验自动化**：把 `node tools/en-wiki-sync.mjs` 与中文 validator 挂到 CI，防止 wiki 更新后数据漂移。

## 约定

- 用户偏好中文回复；希望有可视化预览（截图/示意图）后再交付；
- 不要问“是否继续”这类确认，直接做；
- 大文件改动前用 grep/read 精确定位，避免整文件重复读入（控制上下文）。
