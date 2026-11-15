# -*- coding: utf-8 -*-
"""检查所有界面 render 方法：是否压入了设计空间 pose、是否有提前 return（会导致 pose 不平衡）。"""
import io
import glob
import re

SIG = 'public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {'


def method_bounds(src, sig):
    idx = src.find(sig)
    if idx < 0:
        return None
    brace = src.find('{', idx)
    depth = 0
    i = brace
    while i < len(src):
        c = src[i]
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return brace + 1, i
        i += 1
    return None


for f in sorted(glob.glob('common/src/main/java/com/hhdxcz/strinova/client/**/*.java', recursive=True)):
    s = io.open(f, encoding='utf-8').read()
    if 'StrinovaScaledScreen' not in s and 'StrinovaUiScale' not in s:
        continue
    b = method_bounds(s, SIG)
    if not b:
        continue
    body = s[b[0]:b[1]]
    returns = [ln.strip() for ln in body.split('\n') if re.search(r'^\s*return\b', ln)]
    has_push = 'pushDesignSpace' in body
    has_pop = 'popDesignSpace' in body
    print('%-90s push=%s pop=%s returns=%s' % (
        f.replace('common/src/main/java/com/hhdxcz/strinova/', ''), has_push, has_pop,
        returns if returns else '-'))
