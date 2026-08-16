# Imitating the Strinova Mechanism

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)](https://www.minecraft.net)
[![Loader](https://img.shields.io/badge/Loader-Fabric%20%7C%20Forge-orange)](https://architectury.dev)
[![Version](https://img.shields.io/badge/Version-1.2.0-blue)](https://github.com/hhdxcz/strinova)
[![Java](https://img.shields.io/badge/Java-17-red)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

> 在 Minecraft 里还原《卡拉彼丘》的弦化机制喵~

---

## 目录

- [功能特性](#功能特性)
- [操作说明](#操作说明)
- [命令一览](#命令一览)
- [安装](#安装)
- [从源码构建](#从源码构建)
- [技术架构](#技术架构)
- [致谢](#致谢)

---

## 功能特性

### 弦化

按住 `Ctrl` 进入弦化状态，将玩家碰撞箱压缩为薄片，降低受击面积。

- **伤害减免**：可配置 0.0~1.0 的减伤倍率，默认减免 40%

### 贴墙

弦化状态下按 `R` 贴附到前方墙壁上，沿墙面自由移动。

- **贴墙黑名单**：可配置禁止贴附的方块列表

### 飘飞

弦化状态下按空格键触发飘飞，在空中滑翔。

- 落地、入水、撞墙时自动取消
- 每次离地仅可飘飞一次

### 多段跳

在空中按空格实现多段跳跃，默认<u>额外</u> 1 段（共 2 段）。

- 每人可单独配置跳跃次数（1~N 段，或无限）
- 落地自动重置次数

### 自定义碰撞箱

可视化编辑弦化和飘飞状态下的碰撞箱。

- **通用模式**：整体偏移 + 尺寸微调
- **分体模式**：头/身体/四肢独立配置碰撞箱
- **3D 实时预览**：拖拽旋转、滚轮缩放

### 贴墙黑名单

管理不允许贴附的方块，图形化界面操作。

- 搜索方块（支持中文名 / 命名空间 ID）
- 左键点击切换禁用状态
- **分享/导入**：一键生成压缩分享码，跨存档还原黑名单

### 玩家描边

给指定玩家添加发光描边，方便在人群中定位。支持 16 种颜色。

---

## 操作说明

| 按键 | 功能 | 说明 |
|------|------|------|
| `Ctrl`（按住） | 弦化 | 变身纸片人，碰撞箱缩小 |
| `R` | 贴墙 | 弦化状态下贴附到前方墙壁，可自定义按键 |
| `空格` | 多段跳 / 飘飞 | 空中按跳跃触发多段跳；弦化状态下触发飘飞 |

> 按键绑定可在游戏设置中修改。

---

## 命令一览

> [!IMPORTANT]
> **以下所有命令都集成在 `/strinova_client edit_collision` 图形化菜单中（以下只解释说明）**

### 描边

| 命令 | 权限 | 说明 |
|------|:---:|------|
| `/strinova outline <颜色>` | 玩家 | 设置自己的描边颜色 |
| `/strinova outline clear` | 玩家 | 清除自己的描边 |
| `/strinova outline set <玩家> <颜色>` | OP | 设置他人描边颜色 |

> 颜色支持：dark_red（深红）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjQUEwMDAwIi8+PC9zdmc+" alt="■">、red（红色）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjRkY1NTU1Ii8+PC9zdmc+" alt="■">、gold（金色）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjRkZBQTAwIi8+PC9zdmc+" alt="■">、yellow（黄色）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjRkZGRjU1Ii8+PC9zdmc+" alt="■">、dark_green（深绿）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjMDBBQTAwIi8+PC9zdmc+" alt="■">、green（绿色）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjNTVGRjU1Ii8+PC9zdmc+" alt="■">、dark_aqua（深青）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjMDBBQUFBIi8+PC9zdmc+" alt="■">、aqua（青色）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjNTVGRkZGIi8+PC9zdmc+" alt="■">、dark_blue（深蓝）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjMDAwMEFBIi8+PC9zdmc+" alt="■">、blue（蓝色）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjNTU1NUZGIi8+PC9zdmc+" alt="■">、dark_purple（深紫）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjQUEwMEFBIi8+PC9zdmc+" alt="■">、light_purple（浅紫）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjRkY1NUZGIi8+PC9zdmc+" alt="■">、black（黑色）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjMDAwMDAwIi8+PC9zdmc+" alt="■">、dark_gray（深灰）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjNTU1NTU1Ii8+PC9zdmc+" alt="■">、gray（灰色）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjQUFBQUFBIi8+PC9zdmc+" alt="■">、white（白色）<img src="data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxNiIgaGVpZ2h0PSIxNiI+PHJlY3Qgd2lkdGg9IjE2IiBoZWlnaHQ9IjE2IiBmaWxsPSIjRkZGRkZGIiBzdHJva2U9IiNjY2NjY2MiIHN0cm9rZS13aWR0aD0iMSIvPjwvc3ZnPg==" alt="■">
### 贴墙黑名单

| 命令 | 权限 | 说明 |
|------|:---:|------|
| `/strinova wall blacklist add <方块>` | OP | 添加禁止贴墙的方块 |
| `/strinova wall blacklist remove <方块>` | OP | 移除 |
| `/strinova wall blacklist clear` | OP | 清空黑名单 |
| `/strinova wall blacklist list` | OP | 查看黑名单 |
| `/strinova wall blacklist import <分享码>` | OP | 导入黑名单分享码 |

### 弦化

| 命令 | 权限 | 说明 |
|------|:---:|------|
| `/strinova paper damage_reduction get` | OP | 查看当前减伤倍率 |
| `/strinova paper damage_reduction <0~1>` | OP | 设置减伤倍率 |

### 跳跃

| 命令 | 权限 | 说明 |
|------|:---:|------|
| `/strinova jump get` | 玩家 | 查看自己的段跳次数 |
| `/strinova jump set <段数>` | 玩家 | 设置自己的段跳次数 |
| `/strinova jump set <玩家> <段数>` | OP | 设置他人段跳次数 |
| `/strinova jump set <玩家> ALL` | OP | 设置他人无限跳跃 |

### 碰撞箱微调

| 命令 | 权限 | 说明 |
|------|:---:|------|
| `/strinova boxpos sync <x> <y> <z>` | 玩家 | 设置弦化碰撞箱偏移 |
| `/strinova boxpos fly <x> <y> <z>` | 玩家 | 设置飘飞碰撞箱偏移 |
| `/strinova boxlen sync <x> <y> <z>` | 玩家 | 设置弦化碰撞箱尺寸增量 |
| `/strinova boxlen fly <x> <y> <z>` | 玩家 | 设置飘飞碰撞箱尺寸增量 |
| `/strinova boxpos reset` | 玩家 | 恢复默认碰撞箱位置 |

---

## 安装

### 前置依赖

| 模组 | 说明 |
|------|------|
| [Cloth Config API](https://modrinth.com/mod/cloth-config) | 配置界面 |

---

## 从源码构建

### 环境要求

- JDK 17
- Git

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/hhdxcz/Imitating-the-Strinova-Mechanism.git

# 构建 Fabric 版本
./gradlew :fabric:build

# 构建 Forge 版本
./gradlew :forge:build

# 构建产物位于 fabric/build/libs/ 和 forge/build/libs/

# 在开发环境中运行
./gradlew :fabric:runClient     # Fabric 客户端
./gradlew :forge:runClient      # Forge 客户端
```


---

## 技术架构

本模组基于 [Architectury API](https://architectury.dev/) 实现跨平台（Fabric + Forge），核心代码在 `common` 模块中共享。

```
Imitating the Strinova Mechanism/
├── common/          # 核心逻辑（共享代码）
│   ├── gameplay/    # 游戏机制：弦化、贴墙、飘飞、多段跳
│   ├── mixin/       # Mixin 注入（17 个注入点）
│   ├── net/         # 网络通信（10 个通道）
│   ├── config/      # 配置管理
│   ├── command/     # 命令系统
│   ├── client/      # 客户端 GUI / 3D 预览
│   ├── collision/   # 碰撞箱系统
│   ├── paper/       # 状态管理
│   ├── outline/     # 描边系统
│   └── render/      # 渲染辅助
├── fabric/          # Fabric 平台适配
└── forge/           # Forge 平台适配
```

---


## 致谢

- 灵感来源：《卡拉彼丘》(Strinova / 卡拉比丘)
- 祝卡拉彼丘永远不似喵！
- 作者：[hhdxcz](https://github.com/hhdxcz)
- 特别感谢所有在开发过程中提供帮助的朋友们喵~

