# HUD 字体（bundled fonts）

模组内置两套 OFL 开源字体，运行时按字符自动选择：

| 文件 | 用途 | 来源 | 许可 |
|---|---|---|---|
| `NotoSansSC-Medium.ttf` | 中文（及 CJK 标点） | 卡拉彼丘官网 CDN（Google Noto Sans SC 官方发布） | SIL Open Font License 1.1 |
| `Saira-SemiBold.ttf` | 数字、拉丁字母、英文符号 | 卡拉彼丘官网 CDN（Google Saira 官方发布） | SIL Open Font License 1.1 |

- 两者均已用 `fonttools` 按模组实际用字子集化（Noto 约 509 KB / 1853 字符，含 1409 个汉字；Saira 约 40 KB），避免把 10 MB+ 全量 CJK 字体打进 jar；
- 缺失字符会回退到 `config/strinova-fonts/` 或系统字体（微软雅黑/文泉驿）；
- 游戏客户端自带字体是 **Microsoft YaHei 6.25（MSYH）**，属于微软商业字体，不能随模组分发；如需完全一致，可把游戏 `font.ttf` 放到 `config/strinova-fonts/` 本地使用。

官方字体资源（OFL）：
- Noto Sans SC: https://klbq-web-cms.strinova.com/prod/strinova_static/fonts/Noto_Sans_SC/NotoSansSC-Medium.ttf
- Saira: https://klbq-web-cms.strinova.com/prod/strinova_static/fonts/Saira/ttf/Saira-SemiBold.ttf
