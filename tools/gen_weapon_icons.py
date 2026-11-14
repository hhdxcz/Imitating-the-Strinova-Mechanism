# -*- coding: utf-8 -*-
"""重新生成武器/投掷物剪影贴图：直接使用旧版 T_Dynamic_WeaponWhite_* 原始像素。

旧版贴图（270x90 / 72x72）本身是清晰的白模剪影，之前把它们重采样塞进
256x128 画布（放大 1.3 倍），导致增幅网络 / 选角界面的图标发糊。
这里改为：
  * 有旧版贴图的（主武器 18 把 / 副武器 4 把 / 近战 / 投掷物 9 个）→ 原图按
    alpha 外接框裁剪，**零重采样**；
  * 旧版没有的（潮音、枫鸣、静风、绝对执行、雨鸥、校准仪、夜镰）→ 用已有的
    卡面白模（同样是官方白模，只是分辨率不同）裁剪，静风从 wiki 卡面提取。

输出统一为「内容外接框」大小的 PNG，渲染端按贴图原始宽高比等比放入卡片。
"""
import os
import shutil
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "strinova", "textures", "gui", "weapons")

IDIR = r"F:\xiangmu\2dai\strinova\strinova-icons\strinova\icons"
CROP = r"F:\xiangmu\2dai\strinova\_slices\_weapons_cards_cropped"
WIKI = r"F:\xiangmu\2dai\strinova\_slices\_weapons_wiki"

# 主武器顺序必须与 StrinovaWeaponIcons.FILES 保持一致
MAIN = ["北极星", "彩绘", "潮音", "齿锋", "独舞", "枫鸣", "幻霜", "静风", "警探",
        "绝对执行", "空境", "鸣火", "逆焰", "破晓", "欺诈师", "审判官", "隼", "卫冕",
        "校准仪", "谢幕曲", "夜镰", "影袭", "雨鸥", "绽放", "自由意志"]

# 用户确认过的旧版贴图 id
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

# wiki 卡面集里没有的武器，用官方卡面原图（白色剪影）自己提取
QIANG = r"F:\xiangmu\2dai\strinova\_slices\枪"
CARD_WHITE = {
    "雨鸥": os.path.join(QIANG, "c1e570324b58f31088c68d6caf1bb5cd.png"),
}


def old_path(oid):
    return os.path.join(IDIR, "T_Dynamic_WeaponWhite_%s · Strinova.png" % oid)


def load_old(oid):
    return Image.open(old_path(oid)).convert("RGBA")


def crop_alpha(im, pad=0):
    """按 alpha 外接框裁剪（不缩放）。"""
    box = im.split()[3].getbbox()
    if box is None:
        return im
    x0, y0, x1, y1 = box
    if pad:
        x0 = max(0, x0 - pad)
        y0 = max(0, y0 - pad)
        x1 = min(im.width, x1 + pad)
        y1 = min(im.height, y1 + pad)
    return im.crop((x0, y0, x1, y1))


def extract_wiki_art(name, th=130):
    """旧版贴图缺失时，从 wiki 武器卡面提取武器本体（保留卡面的明暗，不重采样）。"""
    im = Image.open(os.path.join(WIKI, name + ".png")).convert("RGBA")
    a = np.array(im)
    h, w = a.shape[:2]
    rgb = a[:, :, :3].astype(np.int32)
    mn = rgb.min(axis=2)
    sat = rgb.max(axis=2) - mn
    raw = (mn >= th) & (sat <= 55)
    mask = np.zeros_like(raw)
    mask[int(h * 0.16):int(h * 0.93), 8:w - 8] = raw[int(h * 0.16):int(h * 0.93), 8:w - 8]
    labels, count = ndimage.label(mask)
    comps = []
    for idx in range(1, count + 1):
        comp = labels == idx
        area = int(comp.sum())
        if area < 300:
            continue
        ys, xs = np.where(comp)
        comps.append((area, ys.min(), ys.max(), comp))
    if not comps:
        raise RuntimeError("无法从 wiki 卡面提取武器: " + name)
    comps.sort(reverse=True, key=lambda c: c[0])
    _, y0, y1, base = comps[0]
    keep = base.copy()
    for area, cy0, cy1, comp in comps[1:]:
        # 只保留与主体垂直范围重叠的部件（枪身/弹匣/枪托），丢掉卡面高光条
        if cy0 <= y1 + 2 and cy1 >= y0 - 2:
            keep |= comp
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[:, :, :3] = a[:, :, :3]
    out[:, :, 3] = (keep * 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def extract_card_white(path, th=200):
    """从官方武器卡面原图提取白色剪影（去掉卡面白边框、标题、角标）。"""
    im = Image.open(path).convert("RGBA")
    a = np.array(im)
    h, w = a.shape[:2]
    rgb = a[:, :, :3].astype(np.int32)
    sat = rgb.max(axis=2) - rgb.min(axis=2)
    raw = (rgb.min(axis=2) >= th) & (sat <= 40)
    mask = np.zeros_like(raw)
    y0, y1 = int(h * 0.26), int(h * 0.94)
    mask[y0:y1, int(w * 0.03):w - int(w * 0.03)] = raw[y0:y1, int(w * 0.03):w - int(w * 0.03)]
    # 卡面剪影内部有细描边，闭合一次把碎块连成整体
    mask = ndimage.binary_closing(mask, structure=np.ones((3, 3)), iterations=1)
    labels, count = ndimage.label(mask)
    comps = []
    for idx in range(1, count + 1):
        comp = labels == idx
        area = int(comp.sum())
        if area < 120:
            continue
        ys, xs = np.where(comp)
        if ys.min() <= y0 + 1 or xs.min() <= int(w * 0.03) or xs.max() >= w - int(w * 0.03) - 1:
            continue  # 卡面边框残留
        comps.append((area, ys.min(), ys.max(), comp))
    if not comps:
        raise RuntimeError("无法从卡面提取白色剪影: " + path)
    comps.sort(reverse=True, key=lambda c: c[0])
    _, by0, by1, base = comps[0]
    keep = base.copy()
    for area, cy0, cy1, comp in comps[1:]:
        if cy0 <= by1 + 4 and cy1 >= by0 - 6:
            keep |= comp
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[:, :, :3] = 255
    out[:, :, 3] = (keep * 255).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def source_for_main(name):
    if name in OLD_MAIN:
        return load_old(OLD_MAIN[name]), "old:" + OLD_MAIN[name]
    crop_path = os.path.join(CROP, name + ".png")
    if name in CARD_WHITE:
        return extract_card_white(CARD_WHITE[name]), "card-white"
    if os.path.exists(crop_path):
        return Image.open(crop_path).convert("RGBA"), "crop"
    if os.path.exists(os.path.join(WIKI, name + ".png")):
        return extract_wiki_art(name), "wiki"
    return None, "missing"


def save(im, filename):
    path = os.path.join(OUT, filename + ".png")
    im.save(path, optimize=True)
    return path


def main():
    if not os.path.isdir(OUT):
        os.makedirs(OUT, exist_ok=True)
    problems = []
    report = []
    for i, name in enumerate(MAIN):
        src, kind = source_for_main(name)
        if src is None:
            problems.append(name)
            continue
        im = crop_alpha(src)
        save(im, "m%02d" % i)
        report.append(("m%02d" % i, name, kind, im.size))

    for i, (name, oid) in enumerate(OLD_SECONDARY):
        im = crop_alpha(load_old(oid))
        save(im, "s%02d" % i)
        report.append(("s%02d" % i, name, "old:" + oid, im.size))

    im = crop_alpha(load_old(OLD_KNIFE[1]))
    save(im, "knife")
    report.append(("knife", OLD_KNIFE[0], "old:" + OLD_KNIFE[1], im.size))

    for i, (name, oid) in enumerate(OLD_GADGETS):
        im = crop_alpha(load_old(oid))
        save(im, "g%02d" % i)
        report.append(("g%02d" % i, name, "old:" + oid, im.size))

    # selected_line.png 保持原样
    for row in report:
        print("%-6s %-6s %-16s %s" % (row[0], row[1], row[2], row[3]))
    if problems:
        print("MISSING:", problems)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
