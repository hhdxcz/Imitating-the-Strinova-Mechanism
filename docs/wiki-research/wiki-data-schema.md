# wiki 数据契约 v2（增幅网络 / 选角界面用）

> 目标：给模组运行时提供**中英双语、逐角色**的技能 / 觉醒 / 弦能增幅网络 / 档案 / 武器文本。
> 中文来源：`https://wiki.biligame.com/klbq`（游戏内官方中文，权威）。
> 英文来源：`https://strinova.org/wiki`（官方英文本地化，权威）。
> 运行时由 `StrinovaWikiData` 读取；中文文件是主数据，英文文件是按 `id` 的**文本覆盖层**。

## 文件

| 文件 | 语言 | 说明 |
|---|---|---|
| `common/src/main/resources/assets/strinova/data/wiki_zh.json` | 中文 | 主数据：结构、数值、成本、需求 + 中文文本 |
| `common/src/main/resources/assets/strinova/data/wiki_en.json` | 英文 | 文本覆盖：与 zh 相同 `id` 顺序；数字字段可省略 |
| `docs/wiki-research/wiki-zh.json` / `wiki-en.json` | — | 与上面两份完全相同（供审阅对照） |

## 顶层结构（两份文件相同）

```jsonc
{
  "source": { "wiki": "https://...", "fetchedAt": "2026-10-31", "lang": "zh" },
  "roleOrder": ["突击", "决斗", "守护", "支援", "先锋", "控场"],   // en 可为英文名或同样 6 个
  "agents": [ /* 24 个，顺序必须与 StrinovaAgentRoster 一致 */ ],
  "warnings": ["xxx: 说明"]          // 覆盖不到 / 资料冲突时必填，禁止静默
}
```

`StrinovaAgentRoster` 固定顺序（`id` 必须完全一致，不得增删改名）：

`michele, xin, xinxia, yvette, flavia, youwu, leona, chiyo, ming, lawi, meridith, ling, kanami, aika, fagrans, mala, nono, audrey, madeleine, freesia, xinghui, baimo, galatea, xi`

## agent 字段

```jsonc
{
  "id": "michele",
  "name": "米雪儿·李",          // zh 中文名；en 英文名（如 "Michele"）
  "enName": "Michele Lee",      // 官方英文名（两文件都填）
  "role": "守护",               // zh：6 定位之一；en："Sentinel" 等官方英文
  "roleRaw": "守护",            // 原始资料值（zh）；en 可同 role
  "faction": "欧泊",            // zh：欧泊/剪刀手/乌尔比诺；en："P.U.S"/"The Scissors"/"Urbino"
  "factionRaw": "欧泊",
  "identity": "萌新搜查官",     // 身份 / EN Title
  "intro": "欧泊小队新锐搜查官，努力守望着美好的理想乡。",  // 角色简介 / EN Description
  "quote": "打起精神来，游戏还没有结束呢！", // 个性语录 / EN Quote
  "traits": ["透视", "单体减速", "召唤物"],  // 超弦体特性（zh 必填；en 可空数组）
  "weapon": { "name": "警探", "type": "自动步枪", "desc": "满足以猛烈的火力压制战场…" },
  "stats": { "hp": 130, "armor": 80, "armorRegen": 1, "moveSpeed": 55, "stringMove": 66, "stringDr": 33 },  // zh 必填；en 可省略
  "skills": {
    "active":   { "name": "喵喵卫士", "desc": "部署喵喵卫士，自动攻击一定范围内的敌人，并使其减速" },
    "passive":  { "name": "猫踪喵迹", "desc": "…" },
    "ultimate": { "name": "火力大喵", "desc": "…" },
    "tactical": { "name": "陷阱喵喵", "desc": "…" }
  },
  "awakenings": [
    { "name": "猫眼锁定", "desc": "…", "needWeapon": 1, "needSkill": 0, "needDefense": 1 },
    { "name": "…", "desc": "…", "needWeapon": 1, "needSkill": 2, "needDefense": 1 },
    { "name": "…", "desc": "…", "needWeapon": 3, "needSkill": 1, "needDefense": 1 }
  ],
  "network": [ /* 恰好 8 组，顺序固定 */ ]
}
```

## network（弦能增幅网络，8 组固定顺序）

| slot | kind | 展示标题（zh / en） | 来源 |
|---|---|---|---|
| 1 | `weapon` | 输出 / Firing | 武器增幅网络第 1 组（名字随武器而变，如 核心/机能） |
| 2 | `weapon` | 暴击 / Critical Hit | 第 2 组 |
| 3 | `weapon` | 续航 / Capacity | 第 3 组 |
| 4 | `weapon` | 精准 / Accuracy | 第 4 组 |
| 5 | `active` | 主动技能名 / Active skill name | 角色网络第 1 组 |
| 6 | `passive` | 被动技能名 / Passive skill name | 角色网络第 2 组 |
| 7 | `armor` | 护甲 / Armor | 角色网络第 3 组 |
| 8 | `string` | 弦化 / Stringification | 角色网络第 4 组 |

```jsonc
{ "slot": 1, "kind": "weapon", "label": "输出",
  "items": [
    { "text": "基础伤害提升", "cost": 150, "change": "…" },   // zh：change 尽量填完整「属性变化」文案
    { "text": "基础射速提升", "cost": 150, "change": "" }
  ] }
```

规则：
- `label` = 该分支的**真实标题**（武器组：wiki 的组名；主动/被动组：对应技能名；护甲/弦化：护甲/弦化）。
- `items` 长度 1~2，**按 wiki 顺序**；`cost` 为整数（武器 150 / 角色 250）。
- zh：`change` 为 wiki「属性变化」列去掉颜色模板后的纯文本（如 `伤害：10点/秒→15点/秒 射程：7米→10.5米`）；确实没有就写 `""`，并记 warning。
- en：`change` 通常没有资料，写 `""`；`text` 取英文原文（去掉 FIXME/占位条目）。
- 8 组必须齐全；某组在 wiki 只有 1 项时保持 1 项，**不要编造第 2 项**。

## 校验要求（交付前自测）

1. JSON 合法、UTF-8、2 空格缩进；
2. `agents` 恰好 24 个，id 顺序与清单一致；
3. 每个 agent：`skills` 4 个全部有 `name`（`desc` 尽量非空）；`awakenings` 恰好 3 个且 `name`/`desc` 非空；`network` 恰好 8 组，每项 `text` 非空、`cost` > 0；
4. 数字交叉校验：中文觉醒需求 vs 英文 `icon1/icon2/icon3`，不一致记 warning；
5. 输出一个覆盖统计（24×8 组、item 总数、空 change 数、空 desc 数、warning 列表），附在任务完成消息里。
