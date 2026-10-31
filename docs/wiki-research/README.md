# wiki 数据研究目录

本目录保存「卡拉彼丘」wiki 数据抓取与校对产物，供模组运行时数据（`StrinovaWikiData`）与人工审阅使用。
数据契约见 [wiki-data-schema.md](wiki-data-schema.md)。

## 数据来源

| 语言 | 站点 | 用途 |
|---|---|---|
| 中文 | [biligame 卡拉彼丘 wiki](https://wiki.biligame.com/klbq) | 游戏内官方中文：角色档案 / 技能 / 觉醒 / 弦能增幅网络（含逐项「属性变化」文案）/ 数值 |
| 英文 | [Strinova Wiki (strinova.org)](https://strinova.org/wiki) | 官方英文本地化：角色档案 / 技能名与描述 / 武器 / 增幅网络 / 觉醒 |

## 产物

| 文件 | 说明 |
|---|---|
| `wiki-zh.json` | 中文主数据（24 角色 × 8 增幅网络分支 × 1~2 项 + 技能 / 觉醒 / 档案 / 数值） |
| `wiki-en.json` | 英文文本覆盖层（结构、数值、成本、需求与中文主数据一致；英文缺失字段运行时回退中文） |
| `raw-en/characters-extracted.json` | 英文角色页解析结果（信息框 / 主武器 / 增幅网络 / 觉醒） |
| `raw-en/skills-descs.json` | 英文 4 技能名称 + 描述（`skills-batch*.raw.txt` 解析后固化） |
| `raw-en/skill-names.json` | 英文技能名兜底表 |

运行时读取的两份文件在
`common/src/main/resources/assets/strinova/data/wiki_zh.json` 与 `wiki_en.json`（与上面 `wiki-*.json` 字节一致）。

## 重新生成

```bash
# 中文：直连 biligame MediaWiki API（node fetch 可用），限速 150ms/次
node tools/wiki-sync.mjs

# 英文：stratnova.org 有反爬，node/curl 直连会 403，需用 web_fetch 工具抓取：
#   1) 角色 raw wikitext（每次 4~5 页）
#      https://strinova.org/w/api.php?action=query&prop=revisions&rvprop=content&rvslots=main&titles=Michele%7CNobunaga&format=json&formatversion=2
#   2) 技能卡 HTML（每次 5~6 页 × 4 技能，URL 上限 2048 字符）
#      https://strinova.org/w/api.php?action=expandtemplates&text={{#invoke:Skill|GetSkillBox|Michele|1}}...&prop=wikitext&format=json&formatversion=2
#   抓到后解析为 characters-extracted.json / skills-descs.json，再离线合并：
node tools/en-wiki-sync.mjs
```

## 已知缺口

- 生命值：biligame 角色页无「生命值」字段，24 人统一按游戏基础值 130 兜底（`wiki-zh.json` warnings 有记录）。
- 8 个老角色（米雪儿·李 / 信 / 心夏 / 伊薇特 / 芙拉薇娅 / 忧雾 / 蕾欧娜 / 千代）的**战术技能英文描述**在英文 wiki 的模块数据中缺失，运行时回退中文描述；其余技能的英文名与描述均已补齐。
- 英文 wiki 没有逐项升级的「属性变化」明细（只有节点短文本），因此英文模式下悬停提示的属性变化文案沿用中文；节点短文本为英文。
- 定位差异：明 / 玛拉 / 汐 / 芙拉薇娅 在 biligame「定位页=突击」与角色页「决斗」不一致，已按定位页取「突击」（与游戏内 2026-09-15 职能调整一致）。
