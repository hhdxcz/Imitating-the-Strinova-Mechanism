# 顶部面板 UI 素材模板

这个目录是给「自己做顶部面板图片表」用的模板。推荐用 **分件 PNG + manifest.csv**，比一整张大图更稳：我按文件名/坐标接入，不需要猜切图边界。

## 方式一（推荐）：分件 PNG + manifest.csv

目录结构：

```
topbar/
  manifest.csv
  center_plate.png
  score_red.png
  score_blue.png
  frame_left.png
  frame_right.png
  portrait_frame.png
  faction_badge.png
```

`manifest.csv` 列说明：

| 列 | 说明 |
|---|---|
| name | 资源名（代码里用） |
| file | 文件名 |
| x,y,w,h | 如果是一张图集，填图内坐标；分件 PNG 留空 |
| target | 接到哪个部件（center_plate / score_red / score_blue / frame / portrait / faction） |
| stretch | 拉伸方式：none / horizontal / vertical / both / nine_slice |
| slice | 九宫格边距，如 `8,8,8,8`（左,上,右,下） |
| tint | 是否需要代码染色（white / team / none） |
| notes | 备注 |

示例：

```csv
name,file,x,y,w,h,target,stretch,slice,tint,notes
center_plate,center_plate.png,,,,center_plate,none,,none,中央黑色斜角底板
score_red,score_red.png,,,,score_red,horizontal,,white,红队比分底条
score_blue,score_blue.png,,,,score_blue,horizontal,,white,蓝队比分底条
frame_left,frame_left.png,,,,frame,none,,white,左侧白色斜线
frame_right,frame_right.png,,,,frame,none,,white,右侧白色斜线
portrait_frame,portrait_frame.png,,,,portrait,none,,white,头像白框
faction_badge,faction_badge.png,,,,faction,none,,white,头像下小徽章
```

## 方式二：一张图集 + JSON

- 用 **Aseprite / TexturePacker / Photopea** 导出 atlas PNG + JSON（Frame 名建议直接叫 `center_plate`、`score_red`…）；
- 图集尺寸建议 2048×2048 以内、透明背景、不要有网格线；
- 每个 frame 四周留 1~2px 透明边，避免采样串色；
- 导出后把 PNG + JSON 一起给我即可，我按 frame 名接。

## 绘制规格

- **PNG-24 带 alpha**，不要 JPG，不要带背景色；
- 建议按 **2x 或 4x** 分辨率画（代码里会缩到逻辑尺寸）；
- **不要烘焙文字/数字**（目标回合、倒计时、比分是动态的，由代码绘制）；
- 需要横向拉伸的部件（比分条、底板）请留可拉伸中段，或标注 `stretch=horizontal`；
- 建议逻辑尺寸（当前 560×60 顶栏）：
  - 中央底板：150×54 左右
  - 单个比分条：70×22 左右
  - 斜线/边框：按实际画
  - 头像框：24×14（RoleScoreboard 56×32 比例）
- 颜色尽量用白色/灰度 + 代码染色（`tint=white/team`），以后换红蓝/主题不用重画。

## 交付方式

把素材放到本地目录（例如 `F:\xiangmu\2dai\strinova\_ui_src\topbar\`），告诉我目录路径即可；我会：
1. 读取 manifest/图集；
2. 复制到 `assets/strinova/textures/gui/topbar/`；
3. 在 `StrinovaMatchHeaderWidget` 里接入并构建。
