# -*- coding: utf-8 -*-
"""同步武器/投掷物剪影贴图：直接整张复制官方 `T_Dynamic_WeaponWhite_*` 原图。

**不做任何裁剪 / 缩放 / 重采样**。旧版贴图本身就是 270x90（投掷物 72x72）的
白模，自带留白；之前把它裁剪到内容外接框再放大铺满卡片，等于把贴图放大 2~4 倍，
才是「增幅网络/选角糊」的根因。

缺旧版贴图的武器（潮音、枫鸣、静风、绝对执行、校准仪、夜镰、雨鸥）不生成文件，
渲染端会退回程序化绘制的仿制剪影（`drawWeaponIcon`）。

用法：`python tools/gen_weapon_icons.py`
"""
import os
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "strinova", "textures", "gui", "weapons")
IDIR = r"F:\xiangmu\2dai\strinova\strinova-icons\strinova\icons"

# 主武器顺序必须与 StrinovaWeaponIcons.FILES 保持一致
MAIN = ["北极星", "彩绘", "潮音", "齿锋", "独舞", "枫鸣", "幻霜", "静风", "警探",
        "绝对执行", "空境", "鸣火", "逆焰", "破晓", "欺诈师", "审判官", "隼", "卫冕",
        "校准仪", "谢幕曲", "夜镰", "影袭", "雨鸥", "绽放", "自由意志"]

# 用户确认过的旧版贴图 id；没列出的就是没有旧版贴图，走程序化仿制
OLD_MAIN = {
    "警探": "10101001", "逆焰": "10102001", "影袭": "10104001", "隼": "10105001",
    "北极星": "10106001", "齿锋": "10108001", "绽放": "10111001", "欺诈师": "10112001",
    "谢幕曲": "10201001", "空境": "10202001", "彩绘": "10301001", "破晓": "10303001",
    "审判官": "10304001", "卫冕": "10403001", "幻霜": "10501001", "独舞": "10502001",
    "自由意志": "10602001", "鸣火": "10603001",
}

OLD_SECONDARY = [("小蜜蜂", "12101001"), ("雪鸮", "12201001"),
                 ("焚焰者", "12303001"), ("重焰", "12307001")]
OLD_KNIFE = ("忍锋", "15201001")
OLD_GADGETS = [("手雷", "18101001"), ("闪光弹", "18201001"), ("烟雾弹", "18301001"),
               ("风场雷", "18401001"), ("减速雷", "18501001"), ("治疗雷", "18601001"),
               ("报警器", "18701001"), ("拦截器", "18801001"), ("雪球", "18901001")]


def old_path(oid):
    return os.path.join(IDIR, "T_Dynamic_WeaponWhite_%s · Strinova.png" % oid)


# 武器剪影是白色细线条，缩小绘制时用线性过滤才不会断线（渲染端已限制「只缩不放」，
# 所以不会因为线性过滤而放大发糊）。
MCMETA = '{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n'


def copy_old(oid, filename):
    src = old_path(oid)
    dst = os.path.join(OUT, filename + ".png")
    shutil.copyfile(src, dst)
    with open(dst + ".mcmeta", "w", encoding="utf-8", newline="\n") as fp:
        fp.write(MCMETA)
    return dst


def main():
    if not os.path.isdir(OUT):
        os.makedirs(OUT, exist_ok=True)
    report = []
    for i, name in enumerate(MAIN):
        filename = "m%02d" % i
        oid = OLD_MAIN.get(name)
        if oid is None:
            # 没有旧版贴图：删掉旧的抠图产物，让渲染端走程序化仿制
            for suffix in (".png", ".png.mcmeta"):
                stale = os.path.join(OUT, filename + suffix)
                if os.path.exists(stale):
                    os.remove(stale)
            report.append((filename, name, "procedural", "-"))
            continue
        copy_old(oid, filename)
        report.append((filename, name, "old:" + oid, "270x90"))

    for i, (name, oid) in enumerate(OLD_SECONDARY):
        copy_old(oid, "s%02d" % i)
        report.append(("s%02d" % i, name, "old:" + oid, "270x90"))

    copy_old(OLD_KNIFE[1], "knife")
    report.append(("knife", OLD_KNIFE[0], "old:" + OLD_KNIFE[1], "270x90"))

    for i, (name, oid) in enumerate(OLD_GADGETS):
        copy_old(oid, "g%02d" % i)
        report.append(("g%02d" % i, name, "old:" + oid, "72x72"))

    for row in report:
        print("%-6s %-6s %-16s %s" % row)
    return 0


if __name__ == "__main__":
    sys.exit(main())
