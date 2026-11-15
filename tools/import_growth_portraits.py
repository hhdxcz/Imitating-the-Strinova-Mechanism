# -*- coding: utf-8 -*-
"""导入「增幅网络」左侧角色基础立绘（T_Dynamic_RoleGrowth_*）。

把官方立绘按 alpha 外接框裁掉透明边，然后不缩放地贴到统一画布（顶部对齐、水平居中），
这样模组里按固定尺寸画布做 cover 裁切即可，角色之间身高比例保持一致。

输出：common/src/main/resources/assets/strinova/textures/gui/hud/growth/<agentId>.png
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = r"F:\xiangmu\2dai\strinova\strinova-characters\strinova\characters"
OUT = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "strinova",
                   "textures", "gui", "hud", "growth")

# 角色 id → skowt 数字 ID（与 StrinovaHudTextures.NUM_BY_AGENT 保持一致）
NUM_BY_AGENT = {
    "michele": "101", "audrey": "105", "madeleine": "107", "xin": "108",
    "ling": "109", "baimo": "110", "freesia": "112", "flavia": "115",
    "aika": "119", "fagrans": "120", "xinxia": "124", "lawi": "128",
    "yvette": "131", "ming": "132", "meridith": "133", "kanami": "137",
    "xinghui": "146",
}

CANVAS_W = 700
CANVAS_H = 725


def main():
    os.makedirs(OUT, exist_ok=True)
    report = []
    for agent, num in NUM_BY_AGENT.items():
        src = os.path.join(SRC, "T_Dynamic_RoleGrowth_%s \u00b7 Strinova.png" % num)
        if not os.path.exists(src):
            report.append((agent, num, "missing", "-"))
            continue
        im = Image.open(src).convert("RGBA")
        bbox = im.split()[3].getbbox()
        if bbox is None:
            report.append((agent, num, "empty", "-"))
            continue
        art = im.crop(bbox)
        # 只缩小不放大：立绘比画布大时才等比缩
        if art.width > CANVAS_W or art.height > CANVAS_H:
            scale = min(CANVAS_W / art.width, CANVAS_H / art.height)
            art = art.resize((max(1, round(art.width * scale)), max(1, round(art.height * scale))),
                             Image.LANCZOS)
        canvas = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
        canvas.alpha_composite(art, ((CANVAS_W - art.width) // 2, 0))
        dst = os.path.join(OUT, agent + ".png")
        canvas.save(dst, optimize=True)
        report.append((agent, num, canvas.size, os.path.getsize(dst)))

    for row in sorted(report):
        if isinstance(row[2], tuple):
            print("%-12s %-4s %sx%s %6.1fKB" % (row[0], row[1], row[2][0], row[2][1], row[3] / 1024.0))
        else:
            print("%-12s %-4s %s" % (row[0], row[1], row[2]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
