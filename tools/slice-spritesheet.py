#!/usr/bin/env python3
"""Slice a sprite sheet with transparency into individual sprites.

用法:
  python tools/slice-spritesheet.py <input.png> <output_dir> [--mode grouped] [--gap 1]
                                   [--pad 2] [--min-size 4] [--min-area 8]

模式:
  components  每个 alpha 连通块一张图（保留所有碎片）
  grouped     先按连通块，再把 bbox 相交/相邻(gap) 的块合并成整个图标，再过滤小碎片
"""
import argparse
import json
import os
from collections import deque

import numpy as np
from PIL import Image, ImageDraw, ImageFont


def components(im):
    rgba = np.array(im.convert('RGBA'))
    h, w = rgba.shape[:2]
    alpha = rgba[:, :, 3]
    mask = alpha > 8
    visited = np.zeros((h, w), dtype=bool)
    result = []
    for y in range(h):
        for x in range(w):
            if not mask[y, x] or visited[y, x]:
                continue
            q = deque([(x, y)])
            visited[y, x] = True
            pts = []
            while q:
                cx, cy = q.popleft()
                pts.append((cx, cy))
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        if dx == 0 and dy == 0:
                            continue
                        nx, ny = cx + dx, cy + dy
                        if 0 <= nx < w and 0 <= ny < h and mask[ny, nx] and not visited[ny, nx]:
                            visited[ny, nx] = True
                            q.append((nx, ny))
            if len(pts) < 2:
                continue
            x0 = min(p[0] for p in pts); x1 = max(p[0] for p in pts)
            y0 = min(p[1] for p in pts); y1 = max(p[1] for p in pts)
            result.append({'x': x0, 'y': y0, 'w': x1 - x0 + 1, 'h': y1 - y0 + 1, 'area': len(pts)})
    return result


def group(rects, gap):
    parent = list(range(len(rects)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    def near(a, b):
        return not (a['x'] + a['w'] - 1 + gap < b['x']
                    or b['x'] + b['w'] - 1 + gap < a['x']
                    or a['y'] + a['h'] - 1 + gap < b['y']
                    or b['y'] + b['h'] - 1 + gap < a['y'])

    for i in range(len(rects)):
        for j in range(i + 1, len(rects)):
            if near(rects[i], rects[j]):
                union(i, j)
    groups = {}
    for i, r in enumerate(rects):
        groups.setdefault(find(i), []).append(r)
    merged = []
    for items in groups.values():
        x0 = min(r['x'] for r in items); y0 = min(r['y'] for r in items)
        x1 = max(r['x'] + r['w'] - 1 for r in items); y1 = max(r['y'] + r['h'] - 1 for r in items)
        merged.append({'x': x0, 'y': y0, 'w': x1 - x0 + 1, 'h': y1 - y0 + 1,
                       'area': sum(r['area'] for r in items), 'parts': len(items)})
    return merged


def make_contact(im, items, out_path, cols=8, cell=110):
    rows = max(1, (len(items) + cols - 1) // cols)
    sheet = Image.new('RGBA', (cols * cell, rows * (cell + 18)), (24, 28, 34, 255))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 13)
    except Exception:
        font = ImageFont.load_default()
    for i, item in enumerate(items):
        preview = item['image']
        preview.thumbnail((cell - 14, cell - 14), Image.LANCZOS)
        cx = (i % cols) * cell
        cy = (i // cols) * (cell + 18)
        for yy in range(0, cell, 16):
            for xx in range(0, cell, 16):
                color = (44, 48, 56, 255) if ((xx // 16 + yy // 16) % 2 == 0) else (34, 38, 45, 255)
                draw.rectangle([cx + xx, cy + yy, cx + min(cell, xx + 16), cy + min(cell, yy + 16)], fill=color)
        sheet.alpha_composite(preview, (cx + (cell - preview.width) // 2, cy + (cell - preview.height) // 2))
        draw.text((cx + 3, cy + cell + 1), f"{i:02d} {item['w']}x{item['h']}",
                  fill=(230, 238, 248, 255), font=font)
    sheet.convert('RGB').save(out_path)


def main():
    parser = argparse.ArgumentParser(description='Slice sprite sheet with alpha into PNGs')
    parser.add_argument('input')
    parser.add_argument('output_dir')
    parser.add_argument('--mode', choices=['components', 'grouped'], default='grouped')
    parser.add_argument('--gap', type=int, default=1, help='grouped 模式下合并 bbox 的间隔像素')
    parser.add_argument('--pad', type=int, default=2, help='每个切片四周留白')
    parser.add_argument('--min-size', type=int, default=4, help='过滤掉宽高都小于等于该值的碎片')
    parser.add_argument('--min-area', type=int, default=8, help='过滤掉面积小于该值的碎片')
    args = parser.parse_args()

    im = Image.open(args.input).convert('RGBA')
    rects = components(im)
    items = group(rects, args.gap) if args.mode == 'grouped' else rects

    filtered = []
    for r in items:
        if min(r['w'], r['h']) <= args.min_size and max(r['w'], r['h']) <= args.min_size:
            continue
        if r['area'] < args.min_area:
            continue
        filtered.append(r)

    filtered.sort(key=lambda r: (r['y'] // 16, r['x']))
    os.makedirs(args.output_dir, exist_ok=True)
    manifest = []
    contact_items = []
    for i, r in enumerate(filtered):
        x0 = max(0, r['x'] - args.pad); y0 = max(0, r['y'] - args.pad)
        x1 = min(im.width, r['x'] + r['w'] + args.pad); y1 = min(im.height, r['y'] + r['h'] + args.pad)
        crop = im.crop((x0, y0, x1, y1))
        name = f'sprite_{i:02d}.png'
        crop.save(os.path.join(args.output_dir, name))
        r = dict(r)
        r['name'] = name
        r['image'] = crop
        contact_items.append(r)
        manifest.append({k: v for k, v in r.items() if k != 'image'})
    with open(os.path.join(args.output_dir, 'manifest.json'), 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    make_contact(im, contact_items, os.path.join(args.output_dir, '_contact.png'))
    print(f'sliced {len(manifest)} sprites -> {args.output_dir}')


if __name__ == '__main__':
    main()
