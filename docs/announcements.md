# 公告中心（进入世界自动弹出）

模组在玩家进入世界（单人 / 联机）后，会后台检测联网并拉取公告；有**未读**公告时自动弹出
「公告中心」界面（分类页签：全部 / 更新公告 / 游玩说明 / 版本说明 / 活动公告 / 系统公告…）。

- 同一条公告（按 `id`）只会自动弹一次；模组更新或在线公告新增 `id` 后会再次弹出。
- 关闭界面即把当前公告记为已读（存在 `config/strinova-client.json` 的 `announcementSeenIds`）。
- 设置页（`/strinova_client hud` → 战斗HUD设置）底部有「进入世界公告弹窗: 开/关」（默认开）与
  「查看公告…」手动入口。
- 手动弹出**不**消耗首次自动弹出（只有自动弹窗 / 点「我已阅读」才记已读）。

## 内容来源

1. **在线**（优先）：按顺序尝试以下地址，取第一个成功的 JSON：
   - `https://cdn.jsdelivr.net/gh/hhdxcz/Imitating-the-Strinova-Mechanism@main/announcements.json`
   - `https://raw.githubusercontent.com/hhdxcz/Imitating-the-Strinova-Mechanism/main/announcements.json`
   - 可在 `config/strinova-client.json` 用 `announcementUrls`（字符串数组）覆盖。
2. **内置回退**：`common/src/main/resources/assets/strinova/announcements.json`（随 jar 打包）。
   在线拉取失败但设备联网（探测 baidu / bing / mozilla）时显示内置内容；**设备离线时不自动弹出**。

仓库根目录的 `announcements.json` 是线上文件，改完 `git push origin main` 后客户端联网即可拉到
（jsDelivr 有缓存，急用可改用 raw 或临时改 `announcementUrls`）。内置文件建议同步更新一份，
这样离线玩家和拉取失败的玩家也能看到内容。

## JSON 格式

```json
{
  "_type": "strinova_announcements",
  "updated": "2026-11-21",
  "announcements": [
    {
      "id": "v1.3.24-update1",
      "category": "update",
      "title": "更新公告标题",
      "version": "1.3.24",
      "date": "2026-11-21",
      "link": "",
      "body": [
        "正文第一段（自动按宽度换行）",
        "正文第二段"
      ]
    }
  ]
}
```

- `id`：唯一即可，新增 id = 新公告（已读记录按 id 去重）；缺省时用 `category|version|title` 生成。
- `category`：`update` 更新公告 / `guide` 游玩说明 / `version` 版本说明 / `event` 活动公告 /
  `notice` 系统公告；填其它字符串会作为自定义分类显示。
- `title` 必填；`version`、`date` 可留空（只影响右上角元信息与排序，日期倒序）。
- `body`：字符串数组，每项一段；也兼容 `body` 为单个字符串（按换行拆分）或字段名 `content`。
- `link`：可选，当前界面只做数据保留（未渲染成可点链接）。

## 调试命令

- `/strinova_client announcement`：手动打开公告中心。
- `/strinova_client announcement reset`：清除已读记录（下次进世界会重新自动弹）。
- `/strinova_client announcement fetch`：立即联网刷新在线公告。

## 自测建议

1. 进世界 → 等约 3 秒应自动弹出；关闭后重进世界不再弹。
2. `/strinova_client announcement reset` → 重进世界应再次弹出。
3. 设置里关闭「进入世界公告弹窗」→ 重进世界不弹；开启后恢复。
4. 断网进世界 → 不弹；`/strinova_client announcement` 仍可查看内置内容。
