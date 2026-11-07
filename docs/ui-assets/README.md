# Strinova UI 素材索引（skowt dump）

素材来自 skowt.cc 的 Strinova 资产包（用户下载后放在 `F:\xiangmu\2dai\strinova`，**不在本仓库内**）：

| 分类 | 数量 | 说明 |
|---|---|---|
| strinova-characters | 1,542 | 立绘 / 皮肤 / 角色头像 |
| strinova-icons | 1,186 | 技能 / 武器 / 界面图标 |
| strinova-id-cards | 572 | 基板 / 名片 |
| strinova-items | 377 | 道具图 |
| strinova-logos-names | 24 | Logo / 文字 |
| strinova-other | 4,239 | UI 杂项（大部分 HUD 元素） |

> 站点还有 `textures` 分类（5,541 张，5.69GB zip），下载接口限速且不支持断点续传，暂未纳入。

## 索引文件

- `strinova-assets.csv`：全部 7,940 张，列：`name, section, width, height, bytes, rel_path`
- `strinova-hud-assets.csv`：HUD/UI 相关 2,107 张（按名称关键词筛：hud/score/playerinfo/growth/dp_ 等）

`rel_path` 相对 `F:\xiangmu\2dai\strinova`，可直接定位文件。

## 已接入模组的贴图

| 模组资源 | 来源文件 |
|---|---|
| `assets/strinova/textures/gui/hud/growth_square.png` | `strinova-other/strinova/other/T_UI_HUD_Square · Strinova.png` |
| `assets/strinova/textures/gui/hud/growth_circle.png` | `T_UI_HUD_Circle · Strinova.png` |
| `assets/strinova/textures/gui/hud/growth_triangle.png` | `T_UI_HUD_Triangle · Strinova.png` |

用途：TAB 计分板「成长等级」列的蓝方块 / 黄圆 / 绿三角（次数文字仍用系统字体白色绘制）。

## 下一步可用的关键素材

- `T_Dynamic_RoleScoreboard_*`（17 张，56×32）：角色在计分板中的头像裁切；编号与角色 id 的映射尚未确认。
- `DP_Num`（1870×256）：0–9 数码数字精灵表，适合倒计时/比分。
- `SS_UI_HUDPC`（1619×1633）：HUD 精灵表（排名 1st–10th、击杀/技能图标、准星等），需按 UV 切图。
- `T_UI_HUD_2020/2021`：发光方框 / 白色圆角框，可做选中框、状态框。
- `T_UI_HUDPC_SPR_UI_HUDPC_ScoreLine`：斜线装饰。
- `T_UI_RechargePC_top_img`（1920×74）：细长顶部条。

## 重新生成索引

```powershell
# 需要把素材放在 F:\xiangmu\2dai\strinova
python tools/...  # 或在仓库根执行等价脚本
```

（当前索引由一次性 Python 脚本生成，未入库脚本；如需可再补一个 `tools/ui-asset-index.mjs`。）
