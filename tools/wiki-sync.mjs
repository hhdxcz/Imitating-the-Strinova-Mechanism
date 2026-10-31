#!/usr/bin/env node
/**
 * 卡拉彼丘（Strinova）biligame wiki → 中文主数据同步脚本（数据契约 v2）。
 *
 * 目标：生成 `wiki_zh.json`，为模组「弦能增幅网络」与「选角界面」提供真实中文文本：
 *   每名角色 = 技能 4 项 + 觉醒 3 项（含三色需求）+ 弦能增幅网络 8 组（武器 4 + 角色 4）
 *   + 档案（身份 / 简介 / 语录 / 特性）+ 主武器 + 基础数值。
 *
 * 数据来源（biligame wiki 的 Lua 数据模块与页面 wikitext，权威且结构化）：
 *   - 模块:角色/SkillData                    → 主动/被动/终极/战术技能名+描述、3 个技能觉醒名+描述
 *   - 模块:弦能增幅网络3/BombData            → 角色网络 4 组（主动/被动/护甲/弦化）每项描述+成本、觉醒三色需求
 *   - 模块:弦能增幅网络3/BombWeaponData      → 主武器网络 4 组（Parts1/2/4/5）名称/描述/成本
 *   - 超弦体定位                              → 6 个定位及各自角色（2026-09-15 新增「突击」，权威）
 *   - api.php?action=ask                      → SMW 定位/阵营（与信息框交叉校验）
 *   - 各角色页 {{超弦体}}                     → 英文名 / 身份 / 简介 / 语录 / 特性 / 主武器 / 武器介绍
 *   - 各角色页 {{弦能增幅网络3}}（爆破模式 tab）→ 网络每项「属性变化」文案 + 护甲值/移动速度
 *   - 各武器页 {{弦能增幅网络3/武器}}          → 武器网络每组每项「属性变化」文案
 *
 * 契约：docs/wiki-research/wiki-data-schema.md（v2，字段/顺序/校验要求全部以它为准）。
 *
 * 用法：
 *   node tools/wiki-sync.mjs            # 全量抓取（约 50+ 次请求，限速 150ms/次，失败重试 3 次）
 *   node tools/wiki-sync.mjs --cache    # 复用系统临时目录里的 wikitext 缓存（调试/离线复跑）
 *                                       # 缓存目录：<系统临时目录>/strinova-wiki-cache
 *
 * 输出（两份内容必须完全一致）：
 *   - docs/wiki-research/wiki-zh.json
 *   - common/src/main/resources/assets/strinova/data/wiki_zh.json
 */

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const API = 'https://wiki.biligame.com/klbq/api.php';
const USER_AGENT = 'StrinovaMCMod-WikiSync/2.0 (local dev tool)';
const REQUEST_GAP_MS = 150; // 请求间隔（契约要求 ≥120ms）
const MAX_ATTEMPTS = 3; // 失败重试次数
const USE_CACHE = process.argv.includes('--cache') || process.env.WIKI_SYNC_CACHE === '1';
const CACHE_DIR = path.join(os.tmpdir(), 'strinova-wiki-cache');
const FETCHED_AT = new Date().toISOString().slice(0, 10);

/** 与 StrinovaAgentRoster 顺序完全一致的 24 名超弦体（id ↔ 中文名）。id 不得改动。 */
const ROSTER = [
  ['michele', '米雪儿·李'],
  ['xin', '信'],
  ['xinxia', '心夏'],
  ['yvette', '伊薇特'],
  ['flavia', '芙拉薇娅'],
  ['youwu', '忧雾'],
  ['leona', '蕾欧娜'],
  ['chiyo', '千代'],
  ['ming', '明'],
  ['lawi', '拉薇'],
  ['meridith', '梅瑞狄斯'],
  ['ling', '令'],
  ['kanami', '香奈美'],
  ['aika', '艾卡'],
  ['fagrans', '珐格兰丝'],
  ['mala', '玛拉'],
  ['nono', '诺诺'],
  ['audrey', '奥黛丽·格罗夫'],
  ['madeleine', '玛德蕾娜·利里'],
  ['freesia', '绯莎'],
  ['xinghui', '星绘'],
  ['baimo', '白墨'],
  ['galatea', '加拉蒂亚·利里'],
  ['xi', '汐'],
];

const ROLE_ORDER = ['突击', '决斗', '守护', '支援', '先锋', '控场'];
const FACTIONS = ['欧泊', '剪刀手', '乌尔比诺'];

/** 技能：契约 key → wiki 模块字段名（顺序 = 输出 JSON 字段顺序）。 */
const SKILL_KINDS = [
  ['active', '主动技能'],
  ['passive', '被动技能'],
  ['ultimate', '终极技能'],
  ['tactical', '战术技能'],
];

/** 武器网络 4 组的 wiki 键 → slot（顺序固定，对应契约 1~4）。 */
const WEAPON_PARTS = [
  ['Parts1', 1],
  ['Parts2', 2],
  ['Parts4', 3],
  ['Parts5', 4],
];
const WEAPON_LABEL_FALLBACK = ['输出', '暴击', '续航', '精准'];
const WEAPON_COST_FALLBACK = 150;

/** 角色网络 4 组 → slot 5~8（顺序固定）。护甲/弦化 label 固定，主动/被动 label 用技能名。 */
const CHARACTER_GROUPS = [
  { slot: 5, kind: 'active', group: '主动技能', fixedLabel: '' },
  { slot: 6, kind: 'passive', group: '被动技能', fixedLabel: '' },
  { slot: 7, kind: 'armor', group: '护甲', fixedLabel: '护甲' },
  { slot: 8, kind: 'string', group: '弦化', fixedLabel: '弦化' },
];
const CHARACTER_COST_FALLBACK = 250;

/** 契约未收录生命值时（biligame 角色页确实没有「生命值」字段）的统一回退值。 */
const HP_FALLBACK = 130;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// ---------------------------------------------------------------------------
// 抓取（限速 + 重试；--cache 时复用系统临时目录缓存）
// ---------------------------------------------------------------------------
let lastRequestAt = 0;

function pageUrl(title) {
  return `${API}?action=parse&page=${encodeURIComponent(title)}&prop=wikitext&format=json&formatversion=2`;
}

async function requestJson(url, attempt = 1) {
  const wait = REQUEST_GAP_MS - (Date.now() - lastRequestAt);
  if (wait > 0) await sleep(wait);
  lastRequestAt = Date.now();
  try {
    const response = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  } catch (error) {
    if (attempt >= MAX_ATTEMPTS) throw error;
    await sleep(800 * attempt);
    return requestJson(url, attempt + 1);
  }
}

async function fetchWikitext(title) {
  if (USE_CACHE) {
    const cacheFile = path.join(CACHE_DIR, `${encodeURIComponent(title)}.txt`);
    if (fs.existsSync(cacheFile)) return fs.readFileSync(cacheFile, 'utf8');
  }
  const json = await requestJson(pageUrl(title));
  const wikitext = json?.parse?.wikitext;
  if (typeof wikitext !== 'string' || wikitext.length === 0) {
    throw new Error(`页面不存在或没有 wikitext：${title}`);
  }
  if (USE_CACHE) {
    fs.mkdirSync(CACHE_DIR, { recursive: true });
    fs.writeFileSync(path.join(CACHE_DIR, `${encodeURIComponent(title)}.txt`), wikitext, 'utf8');
  }
  return wikitext;
}

// ---------------------------------------------------------------------------
// 极简 Lua 数据表解析（wiki 数据模块是纯 Lua table 字面量）
// ---------------------------------------------------------------------------
function parseLuaTable(source) {
  let i = 0;
  const len = source.length;
  const skipWs = () => {
    while (i < len) {
      const ch = source[i];
      if (ch === ' ' || ch === '\t' || ch === '\r' || ch === '\n') {
        i++;
        continue;
      }
      if (ch === '-' && source[i + 1] === '-') {
        while (i < len && source[i] !== '\n') i++;
        continue;
      }
      break;
    }
  };
  const parseString = () => {
    const quote = source[i++];
    let out = '';
    while (i < len && source[i] !== quote) {
      if (source[i] === '\\') {
        i++;
        const escaped = source[i++];
        if (escaped === 'n') out += '\n';
        else if (escaped === 't') out += '\t';
        else out += escaped;
      } else {
        out += source[i++];
      }
    }
    i++;
    return out;
  };
  const parseValue = () => {
    skipWs();
    const ch = source[i];
    if (ch === '{') return parseTable();
    if (ch === '"' || ch === "'") return parseString();
    const num = /^-?\d+(?:\.\d+)?/.exec(source.slice(i));
    if (num) {
      i += num[0].length;
      return Number(num[0]);
    }
    const ident = /^[A-Za-z_][A-Za-z0-9_]*/.exec(source.slice(i));
    if (ident) {
      i += ident[0].length;
      if (ident[0] === 'true') return true;
      if (ident[0] === 'false') return false;
      return null;
    }
    throw new Error(`Lua 解析失败 @${i}: ${source.slice(i, i + 60)}`);
  };
  const parseTable = () => {
    i++; // {
    const array = [];
    const object = {};
    let named = false;
    for (;;) {
      skipWs();
      if (i >= len) throw new Error('Lua 表未闭合');
      if (source[i] === '}') {
        i++;
        break;
      }
      if (source[i] === '[') {
        i++;
        skipWs();
        let key;
        if (source[i] === '"' || source[i] === "'") {
          key = parseString();
        } else {
          const num = /^\d+/.exec(source.slice(i));
          if (!num) throw new Error(`Lua 键解析失败 @${i}`);
          i += num[0].length;
          key = Number(num[0]);
        }
        skipWs();
        if (source[i] === ']') i++;
        skipWs();
        if (source[i] === '=') i++;
        object[key] = parseValue();
        named = true;
      } else if (/[A-Za-z_]/.test(source[i])) {
        const ident = /^[A-Za-z_][A-Za-z0-9_]*/.exec(source.slice(i))[0];
        const save = i;
        i += ident.length;
        skipWs();
        if (source[i] === '=') {
          i++;
          object[ident] = parseValue();
          named = true;
        } else {
          i = save;
          array.push(parseValue());
        }
      } else {
        array.push(parseValue());
      }
      skipWs();
      if (source[i] === ',' || source[i] === ';') i++;
    }
    if (named) {
      for (let k = 0; k < array.length; k++) object[k + 1] = array[k];
      return object;
    }
    return array;
  };
  skipWs();
  if (source.startsWith('return')) i += 6;
  skipWs();
  return parseValue();
}

// ---------------------------------------------------------------------------
// 页面 wikitext 小工具
// ---------------------------------------------------------------------------
const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&');
const stripComments = (text) => text.replace(/<!--[\s\S]*?-->/g, '');

/** 抽取所有 `{{name ...}}` 模板块（花括号按层配对；name 后须为空白或 `|`，避免匹配同名前缀模板）。 */
function extractTemplates(wikitext, name) {
  const blocks = [];
  const re = new RegExp(`\\{\\{\\s*${escapeRegExp(name)}(?=[\\s|}])`, 'g');
  let match;
  while ((match = re.exec(wikitext)) !== null) {
    const start = match.index;
    let depth = 0;
    let end = -1;
    for (let p = start; p < wikitext.length - 1; p++) {
      if (wikitext[p] === '{' && wikitext[p + 1] === '{') {
        depth++;
        p++;
        continue;
      }
      if (wikitext[p] === '}' && wikitext[p + 1] === '}') {
        depth--;
        p++;
        if (depth === 0) {
          end = p + 1; // 右花括号后一位（slice 上界，不含）
          break;
        }
      }
    }
    if (end < 0) break;
    blocks.push(wikitext.slice(start, end));
    re.lastIndex = end;
  }
  return blocks;
}

/** 按顶层分隔符切分（忽略 {{...}} / [[...]] 内部的分隔符）。 */
function splitTopLevel(text, separator = '|') {
  const parts = [];
  let depth = 0;
  let last = 0;
  for (let i = 0; i < text.length; i++) {
    const two = text.slice(i, i + 2);
    if (two === '{{' || two === '[[') {
      depth++;
      i++;
      continue;
    }
    if (two === '}}' || two === ']]') {
      depth = Math.max(0, depth - 1);
      i++;
      continue;
    }
    if (depth === 0 && text[i] === separator) {
      parts.push(text.slice(last, i));
      last = i + 1;
    }
  }
  parts.push(text.slice(last));
  return parts;
}

/** 解析模板块的具名参数 `|key=value`（value 可跨行、可嵌套模板）。 */
function parseTemplateParams(block) {
  if (!block) return {};
  const inner = block.slice(2, block.length - 2);
  const pieces = splitTopLevel(inner).slice(1); // 第 1 段是模板名
  const params = {};
  for (const piece of pieces) {
    const index = piece.indexOf('=');
    if (index < 0) continue;
    const key = piece.slice(0, index).trim();
    if (!key || /[{}[\]\n]/.test(key)) continue;
    params[key] = piece.slice(index + 1).trim();
  }
  return params;
}

/** 模板的位置参数（不含模板名），用于 {{NavChara|图片|角色名|...}} 这类调用。 */
function templatePositionalParams(block) {
  if (!block) return [];
  const inner = block.slice(2, block.length - 2);
  return splitTopLevel(inner).slice(1);
}

/** 把 {{ul|a|b}} 列表模板展开成一行文本（支持嵌套）。 */
function flattenLists(text) {
  let out = text;
  for (;;) {
    const match = /\{\{\s*ul\b/i.exec(out);
    if (!match) break;
    const start = match.index;
    let depth = 0;
    let end = -1;
    for (let p = start; p < out.length - 1; p++) {
      if (out[p] === '{' && out[p + 1] === '{') {
        depth++;
        p++;
        continue;
      }
      if (out[p] === '}' && out[p + 1] === '}') {
        depth--;
        p++;
        if (depth === 0) {
          end = p + 1;
          break;
        }
      }
    }
    if (end < 0) break;
    const inner = out.slice(start + 2, end - 2).replace(/^\s*ul\b/i, '');
    const items = splitTopLevel(inner)
      .map((part) => part.trim())
      .filter((part) => part.length > 0);
    out = `${out.slice(0, start)} ${items.join(' ')} ${out.slice(end)}`;
  }
  return out;
}

/** 「属性变化」文案 → 单行纯文本：去 {{Color|...}}、展开 {{ul}}、<br/> 变空格、合并空白。 */
function flattenChange(raw) {
  if (!raw) return '';
  let text = stripComments(String(raw));
  text = text.replace(/\{\{\s*Color\s*\|[^|{}]*\|([^{}]*)\}\}/gi, '$1');
  text = text.replace(/\{\{\s*Color\s*\|[^{}]*\}\}/gi, '');
  text = flattenLists(text);
  text = text.replace(/\{\{\s*#info\s*:[^{}]*\}\}/gi, ' ');
  text = text.replace(/\{\{[^{}]*\}\}/g, ' ');
  text = text.replace(/'''?/g, '');
  text = text.replace(/\[\[[^|\]]*\|([^\]]*)\]\]/g, '$1');
  text = text.replace(/\[\[([^\]]*)\]\]/g, '$1');
  text = text.replace(/<br\s*\/?>/gi, '\n');
  text = text.replace(/^[\s|]+/gm, '');
  text = text.replace(/\|/g, ' ');
  return text.replace(/\s+/g, ' ').trim();
}

/** 一般文本清洗（标题/名称/描述/档案字段）。 */
function cleanInline(raw) {
  if (raw === undefined || raw === null) return '';
  let text = stripComments(String(raw));
  text = text.replace(/\{\{\s*Color\s*\|[^|{}]*\|([^{}]*)\}\}/gi, '$1');
  text = text.replace(/\{\{[^{}]*\}\}/g, ' ');
  text = text.replace(/'''?/g, '');
  text = text.replace(/\[\[[^|\]]*\|([^\]]*)\]\]/g, '$1');
  text = text.replace(/\[\[([^\]]*)\]\]/g, '$1');
  text = text.replace(/<br\s*\/?>/gi, ' ');
  text = text.replace(/&nbsp;/gi, ' ');
  return text.replace(/\s+/g, ' ').trim();
}

/** Lua 数据里的值统一成数组（单值 / 空值都能处理）。 */
function toArray(value) {
  if (value === undefined || value === null) return [];
  return Array.isArray(value) ? value : [value];
}

function toInt(value) {
  if (value === undefined || value === null) return null;
  const num = Number(String(value).replace(/[^\d.-]/g, ''));
  return Number.isFinite(num) ? Math.round(num) : null;
}

/** 从 change 文案里解析基础数值，例如「护甲回复值：1点/秒→4点/秒」→ 1。 */
function statFromChange(change, pattern) {
  const match = pattern.exec(change ?? '');
  return match ? Number(match[1]) : null;
}

// ---------------------------------------------------------------------------
// 页面结构解析
// ---------------------------------------------------------------------------

/** 角色页 {{超弦体}} 信息框参数（含多行值的健壮解析）。 */
function parseInfobox(wikitext) {
  const block = extractTemplates(stripComments(wikitext), '超弦体')[0];
  return block ? parseTemplateParams(block) : {};
}

/** 「超弦体定位」页：定位 → 角色名列表。 */
function parseRolePage(wikitext) {
  const text = stripComments(wikitext);
  const roles = {};
  const headings = [...text.matchAll(/^==\s*([^\n]*?)\s*==\s*$/gm)];
  for (let index = 0; index < headings.length; index++) {
    const heading = headings[index];
    const role = cleanInline(heading[1]);
    if (!ROLE_ORDER.includes(role)) continue;
    const sectionStart = heading.index + heading[0].length;
    const sectionEnd = index + 1 < headings.length ? headings[index + 1].index : text.length;
    const section = text.slice(sectionStart, sectionEnd);
    const names = [];
    for (const block of extractTemplates(section, 'NavChara')) {
      const positional = templatePositionalParams(block);
      const name = cleanInline(positional[1] ?? ''); // positional[0] = 半身图文件名
      if (name) names.push(name);
    }
    roles[role] = names;
  }
  return roles;
}

/** 角色页的「弦能增幅网络3」爆破模式参数（第 1 个块；未写 `模式` 时默认就是爆破模式）。 */
function parseCharacterNetwork(wikitext) {
  const blocks = extractTemplates(stripComments(wikitext), '弦能增幅网络3');
  if (blocks.length === 0) return {};
  const explosion = blocks.find((block) => parseTemplateParams(block)['模式'] === '爆破模式');
  return parseTemplateParams(explosion ?? blocks[0]);
}

/** 武器页的「弦能增幅网络3/武器」参数。 */
function parseWeaponNetwork(wikitext) {
  const block = extractTemplates(stripComments(wikitext), '弦能增幅网络3/武器')[0];
  return block ? parseTemplateParams(block) : {};
}

/** SMW 查询：定位 / 阵营（仅交叉校验，失败不影响主流程）。 */
async function fetchSmwAttributes() {
  const query = '[[分类:超弦体]]|?定位|?阵营|limit=50';
  const json = await requestJson(`${API}?action=ask&query=${encodeURIComponent(query)}&format=json`);
  const result = {};
  for (const [name, row] of Object.entries(json?.query?.results ?? {})) {
    const printouts = row.printouts ?? {};
    const pick = (key) => {
      if (Array.isArray(printouts[key])) return printouts[key][0] ?? '';
      return printouts[key] ?? '';
    };
    result[name] = {
      roleRaw: cleanInline(String(pick('定位'))),
      factionRaw: cleanInline(String(pick('阵营'))),
    };
  }
  return result;
}

// ---------------------------------------------------------------------------
// 主流程
// ---------------------------------------------------------------------------
async function main() {
  const warnings = [];
  const warn = (message) => warnings.push(message);

  // [1/6] 数据模块 + 定位分类页
  console.log('[1/6] 抓取数据模块与「超弦体定位」页…');
  const skillData = parseLuaTable(await fetchWikitext('模块:角色/SkillData'));
  const bombData = parseLuaTable(await fetchWikitext('模块:弦能增幅网络3/BombData'));
  const weaponData = parseLuaTable(await fetchWikitext('模块:弦能增幅网络3/BombWeaponData'));
  const rolePage = parseRolePage(await fetchWikitext('超弦体定位'));
  console.log(
    `      SkillData ${Object.keys(skillData).length} 条 / BombData ${Object.keys(bombData).length} 条 / ` +
      `BombWeaponData ${Object.keys(weaponData).length} 条 / 定位页 ` +
      Object.entries(rolePage)
        .map(([role, names]) => `${role}${names.length}`)
        .join(' '),
  );

  const roleByName = {};
  for (const [role, names] of Object.entries(rolePage)) {
    for (const name of names) roleByName[name] = role;
  }

  // [2/6] SMW 交叉校验
  let smw = {};
  try {
    smw = await fetchSmwAttributes();
    console.log(`[2/6] SMW 定位/阵营 ${Object.keys(smw).length} 条`);
  } catch (error) {
    warn(`SMW: 查询定位/阵营失败（${error.message}），已跳过该交叉校验`);
    console.log(`[2/6] SMW 查询失败，已跳过：${error.message}`);
  }

  // [3/6] 24 个角色页
  console.log('[3/6] 抓取 24 名角色页（信息框 + 增幅网络属性变化 / 数值）…');
  const characterPages = {};
  for (const [, zhName] of ROSTER) {
    characterPages[zhName] = await fetchWikitext(zhName);
    process.stdout.write('.');
  }
  console.log(' 完成');

  // [4/6] 主武器页（去重后抓取）
  const weaponNames = [...new Set(
    ROSTER.map(([, zhName]) => cleanInline(parseInfobox(characterPages[zhName])['武器名称'])).filter((name) => name),
  )];
  console.log(`[4/6] 抓取 ${weaponNames.length} 个主武器页（武器网络属性变化文案）…`);
  const weaponPages = {};
  for (const weaponName of weaponNames) {
    try {
      weaponPages[weaponName] = await fetchWikitext(weaponName);
      process.stdout.write('.');
    } catch (error) {
      weaponPages[weaponName] = '';
      warn(`武器「${weaponName}」: 页面抓取失败（${error.message}），武器网络 change 将留空`);
    }
  }
  console.log(' 完成');

  // [5/6] 合并每名角色的数据
  console.log('[5/6] 合并角色数据…');
  let hpFallbackCount = 0;
  let emptyChangeCount = 0;
  let emptyDescCount = 0;
  let itemTotal = 0;

  const agents = [];
  for (const [id, zhName] of ROSTER) {
    const page = characterPages[zhName] ?? '';
    const infobox = parseInfobox(page);
    const params = parseCharacterNetwork(page);
    const skillEntry = skillData[zhName] ?? {};
    const bombEntry = bombData[zhName] ?? {};

    if (!Object.keys(skillEntry).length) warn(`${zhName}: 模块:角色/SkillData 缺少该角色（技能将为空）`);
    if (!Object.keys(bombEntry).length) warn(`${zhName}: 模块:弦能增幅网络3/BombData 缺少该角色（角色网络将为 0 项）`);

    // --- 技能 ---
    const skills = {};
    for (const [key, kindLabel] of SKILL_KINDS) {
      const entry = toArray(skillEntry[kindLabel])[0] ?? {};
      const name = cleanInline(entry.name);
      const desc = cleanInline(entry.desc);
      if (!name) warn(`${zhName}: ${kindLabel}名称为空（模块:角色/SkillData）`);
      if (!desc) {
        emptyDescCount++;
        warn(`${zhName}: ${kindLabel}描述为空（模块:角色/SkillData）`);
      }
      skills[key] = { name, desc };
    }

    // --- 觉醒 ---
    const awakenSources = toArray(skillEntry['技能觉醒']);
    const awakeningNeeds = bombEntry['技能觉醒'] ?? {};
    const awakenings = [0, 1, 2].map((index) => {
      const entry = awakenSources[index] ?? {};
      const name = cleanInline(entry.name);
      const desc = cleanInline(entry.desc);
      if (!name) warn(`${zhName}: 觉醒${index + 1}名称为空`);
      if (!desc) {
        emptyDescCount++;
        warn(`${zhName}: 觉醒${index + 1}描述为空`);
      }
      const needRaw = toArray(awakeningNeeds[`Active${index + 1}Need`]);
      if (needRaw.length < 3) warn(`${zhName}: 觉醒${index + 1}三色需求缺失，needWeapon/needSkill/needDefense 回退 0`);
      return {
        name,
        desc,
        needWeapon: toInt(needRaw[0]) ?? 0,
        needSkill: toInt(needRaw[1]) ?? 0,
        needDefense: toInt(needRaw[2]) ?? 0,
      };
    });
    if (awakenSources.length !== 3) warn(`${zhName}: 技能觉醒应为 3 个，wiki 实际 ${awakenSources.length} 个`);

    // --- 档案 ---
    const identity = cleanInline(infobox['身份']);
    const intro = cleanInline(infobox['角色简介']);
    const quote = cleanInline(infobox['个性语录']);
    const traits = cleanInline(infobox['超弦体特性'])
      .split(/[、,，]/)
      .map((trait) => trait.trim())
      .filter((trait) => trait.length > 0);
    const enName = cleanInline(infobox['英文名']);
    const weaponName = cleanInline(infobox['武器名称']);
    const weaponType = cleanInline(infobox['武器类型']);
    const weaponDesc = cleanInline(infobox['武器介绍']);
    for (const [field, value] of [
      ['身份', identity],
      ['角色简介', intro],
      ['个性语录', quote],
      ['英文名', enName],
      ['武器名称', weaponName],
      ['武器类型', weaponType],
      ['武器介绍', weaponDesc],
    ]) {
      if (!value) warn(`${zhName}: 角色页 {{超弦体}} 缺少「${field}」`);
    }
    if (traits.length === 0) warn(`${zhName}: 超弦体特性为空`);

    // --- 定位 / 阵营 ---
    const roleRaw = cleanInline(infobox['定位']);
    const smwRole = smw[zhName]?.roleRaw ?? '';
    const role = roleByName[zhName] || smwRole || roleRaw;
    if (!role) warn(`${zhName}: 缺少定位数据`);
    else if (!ROLE_ORDER.includes(role)) warn(`${zhName}: 定位「${role}」不在 6 类定位（${ROLE_ORDER.join('/')}）中`);
    if (roleRaw && roleRaw !== role) {
      const sources = [`角色页信息框「${roleRaw}」`];
      if (smwRole && smwRole !== role) sources.push(`SMW「${smwRole}」`);
      warn(
        `${zhName}: 定位以「超弦体定位」页「${role}」为准；${sources.join('、')}未同步 2026-09-15 职能调整` +
          `（wiki 自身数据新旧不一致）`,
      );
    } else if (smwRole && smwRole !== role) {
      warn(`${zhName}: 定位「${role}」与 SMW「${smwRole}」不一致（以「超弦体定位」页为准）`);
    }
    const faction = cleanInline(infobox['阵营']);
    if (!faction) warn(`${zhName}: 缺少阵营数据`);
    else if (!FACTIONS.includes(faction)) warn(`${zhName}: 阵营「${faction}」不在 ${FACTIONS.join('/')} 中`);
    const smwFaction = smw[zhName]?.factionRaw ?? '';
    if (faction && smwFaction && smwFaction !== faction) {
      warn(`${zhName}: 阵营信息框「${faction}」与 SMW「${smwFaction}」不一致（以角色页信息框为准）`);
    }

    // --- 角色网络（4 组，slot 5~8）：属性变化文案先整理好 ---
    const characterChanges = {};
    for (const { group } of CHARACTER_GROUPS) {
      characterChanges[group] = [];
      for (let k = 1; k <= 2; k++) {
        const raw = params[`${group}组${k}属性变化`];
        characterChanges[group].push(raw === undefined ? null : flattenChange(raw));
      }
    }

    // --- 基础数值 ---
    const armor = toInt(params['护甲值']);
    const moveSpeed = toInt(params['移动速度']);
    if (armor === null) warn(`${zhName}: 角色页 {{弦能增幅网络3}} 缺少「护甲值」，stats.armor 回退 100`);
    if (moveSpeed === null) warn(`${zhName}: 角色页 {{弦能增幅网络3}} 缺少「移动速度」，stats.moveSpeed 回退 0`);

    const armorRegen = statFromChange(characterChanges['护甲'][0], /护甲回复值[：:]\s*([\d.]+)/);
    const stringMove = statFromChange(characterChanges['弦化'][0], /弦化移速[：:]\s*([\d.]+)/);
    const stringDr = statFromChange(characterChanges['弦化'][1], /弦化减伤比例[：:]\s*([\d.]+)/);
    if (armorRegen === null) warn(`${zhName}: 无法从护甲组1属性变化解析「护甲回复值」，stats.armorRegen 回退 1`);
    if (stringMove === null) warn(`${zhName}: 无法从弦化组1属性变化解析「弦化移速」，stats.stringMove 回退 66`);
    if (stringDr === null) warn(`${zhName}: 无法从弦化组2属性变化解析「弦化减伤比例」，stats.stringDr 回退 33`);

    const stats = {
      hp: HP_FALLBACK,
      armor: armor ?? 100,
      armorRegen: armorRegen ?? 1,
      moveSpeed: moveSpeed ?? 0,
      stringMove: stringMove ?? 66,
      stringDr: stringDr ?? 33,
    };
    hpFallbackCount++;

    // --- 武器网络（4 组，slot 1~4） ---
    const weaponEntry = weaponData[weaponName];
    if (!weaponEntry) warn(`${zhName}: 未找到主武器「${weaponName}」的武器网络（模块:弦能增幅网络3/BombWeaponData）`);
    const weaponPage = weaponPages[weaponName] ?? '';
    const weaponParams = weaponPage ? parseWeaponNetwork(weaponPage) : {};
    const hasWeaponNetwork = Object.keys(weaponParams).length > 0;
    if (weaponPage && !hasWeaponNetwork) {
      warn(`武器「${weaponName}」: 武器页缺少 {{弦能增幅网络3/武器}}，武器网络 change 将留空`);
    }

    const weaponGroups = WEAPON_PARTS.map(([part, slot], index) => {
      const group = weaponEntry?.[part];
      const label = cleanInline(group?.name) || WEAPON_LABEL_FALLBACK[index];
      if (!group) {
        warn(`${zhName}: 武器「${weaponName}」的武器网络缺少 ${part}（slot ${slot}）`);
        return { slot, kind: 'weapon', label, items: [] };
      }
      const descs = toArray(group.desc);
      const needs = toArray(group.need);
      const items = descs.map((descRaw, k) => {
        const text = cleanInline(String(descRaw));
        if (!text) warn(`${zhName}: 武器网络「${label}」第 ${k + 1} 项描述为空`);
        let cost = toInt(needs[k]);
        if (!(cost > 0)) {
          cost = toInt(needs[needs.length - 1]) || WEAPON_COST_FALLBACK;
          warn(`${zhName}: 武器网络「${label}」第 ${k + 1} 项缺少成本，回退 ${cost}`);
        }
        let change = '';
        const rawChange = weaponParams[`${part}组${k + 1}属性变化`];
        if (rawChange !== undefined) {
          change = flattenChange(rawChange);
          if (!change) {
            emptyChangeCount++;
            warn(`${zhName}: 武器网络「${label}」第 ${k + 1} 项「${text}」的属性变化为空`);
          }
        } else if (hasWeaponNetwork) {
          emptyChangeCount++;
          warn(
            `${zhName}: 武器页「${weaponName}」缺少参数 ${part}组${k + 1}属性变化，武器网络「${label}」第 ${k + 1} 项 change 留空`,
          );
        }
        return { text, cost, change };
      });
      return { slot, kind: 'weapon', label, items };
    });

    // --- 角色网络（4 组，slot 5~8） ---
    const characterGroups = CHARACTER_GROUPS.map(({ slot, kind, group, fixedLabel }) => {
      const entry = bombEntry[group] ?? {};
      const descs = toArray(entry.desc);
      const needs = toArray(entry.need);
      const entryName = cleanInline(entry.name);
      let label = fixedLabel;
      if (!label) {
        // 主动 / 被动技能组：label = 技能名（技能表为准，模块组名交叉校验）
        const skillName = kind === 'active' ? skills.active.name : skills.passive.name;
        if (entryName && skillName && entryName !== skillName) {
          warn(
            `${zhName}: 网络「${group}」组名「${entryName}」与技能表「${skillName}」不一致（以技能表为准）`,
          );
        }
        label = skillName || entryName || group;
      }
      if (!Object.keys(entry).length) {
        warn(`${zhName}: 角色网络缺少「${group}」组（slot ${slot}）`);
        return { slot, kind, label, items: [] };
      }
      const items = descs.map((descRaw, k) => {
        const text = cleanInline(String(descRaw));
        if (!text) warn(`${zhName}: 角色网络「${label}」第 ${k + 1} 项描述为空`);
        let cost = toInt(needs[k]);
        if (!(cost > 0)) {
          cost = toInt(needs[needs.length - 1]) || CHARACTER_COST_FALLBACK;
          warn(`${zhName}: 角色网络「${label}」第 ${k + 1} 项缺少成本，回退 ${cost}`);
        }
        let change = characterChanges[group][k] ?? '';
        if (change === null) {
          // 该组在 wiki 没有对应的「属性变化」参数：legacy 数值留空并记录（不静默）
          change = '';
          emptyChangeCount++;
          warn(`${zhName}: 角色页缺少参数 ${group}组${k + 1}属性变化，网络「${label}」第 ${k + 1} 项 change 留空`);
        } else if (!change) {
          emptyChangeCount++;
          warn(`${zhName}: 角色网络「${label}」第 ${k + 1} 项「${text}」的属性变化为空`);
        }
        return { text, cost, change };
      });
      return { slot, kind, label, items };
    });

    const network = [...weaponGroups, ...characterGroups];
    itemTotal += network.reduce((sum, group) => sum + group.items.length, 0);

    agents.push({
      id,
      name: zhName,
      enName,
      role,
      roleRaw,
      faction,
      factionRaw: faction,
      identity,
      intro,
      quote,
      traits,
      weapon: { name: weaponName, type: weaponType, desc: weaponDesc },
      stats,
      skills,
      awakenings,
      network,
    });
  }

  // 生命值在 biligame 角色页没有字段：统一回退并汇总为一条 warning（不逐角色刷屏）
  if (hpFallbackCount > 0) {
    warn(
      `数据缺口: ${hpFallbackCount} 名角色的 wiki 页面均无「生命值」字段，stats.hp 统一回退 ${HP_FALLBACK}` +
        `（契约要求；不是抓取失败）`,
    );
  }

  // --- 契约硬性不变量（脚本自检，失败即中止，避免写出坏数据） ---
  if (agents.length !== ROSTER.length) {
    throw new Error(`角色数量异常：期望 ${ROSTER.length}，实际 ${agents.length}`);
  }
  agents.forEach((agent, index) => {
    if (agent.id !== ROSTER[index][0]) {
      throw new Error(`角色顺序与 StrinovaAgentRoster 不一致：第 ${index + 1} 个为 ${agent.id}，期望 ${ROSTER[index][0]}`);
    }
    if (agent.network.length !== 8) {
      throw new Error(`${agent.name}: network 应为 8 组，实际 ${agent.network.length} 组`);
    }
    if (agent.awakenings.length !== 3) {
      throw new Error(`${agent.name}: awakenings 应为 3 个，实际 ${agent.awakenings.length} 个`);
    }
    for (const group of agent.network) {
      if (group.items.length < 1 || group.items.length > 2) {
        throw new Error(`${agent.name}: 网络 slot ${group.slot}（${group.label}）items 数量 ${group.items.length} 不在 1~2 之间`);
      }
      for (const item of group.items) {
        if (!item.text) throw new Error(`${agent.name}: 网络 slot ${group.slot} 存在空 text`);
        if (!(item.cost > 0)) throw new Error(`${agent.name}: 网络 slot ${group.slot} 存在非正成本 ${item.cost}`);
      }
    }
  });

  const result = {
    source: {
      wiki: 'https://wiki.biligame.com/klbq',
      fetchedAt: FETCHED_AT,
      lang: 'zh',
    },
    roleOrder: ROLE_ORDER,
    agents,
    warnings,
  };

  // [6/6] 写出 JSON（docs 与 resources 两份内容完全一致）
  console.log('[6/6] 写出 JSON…');
  const docsJson = path.join(ROOT, 'docs', 'wiki-research', 'wiki-zh.json');
  const resourceJson = path.join(ROOT, 'common', 'src', 'main', 'resources', 'assets', 'strinova', 'data', 'wiki_zh.json');
  fs.mkdirSync(path.dirname(docsJson), { recursive: true });
  fs.mkdirSync(path.dirname(resourceJson), { recursive: true });
  const text = `${JSON.stringify(result, null, 2)}\n`;
  fs.writeFileSync(docsJson, text, 'utf8');
  fs.writeFileSync(resourceJson, text, 'utf8');
  console.log(`      ${docsJson}（${Buffer.byteLength(text, 'utf8')} 字节）`);
  console.log(`      ${resourceJson}`);

  // 覆盖统计
  const networkGroupTotal = agents.reduce((sum, agent) => sum + agent.network.length, 0);
  const filledChangeCount = agents.reduce(
    (sum, agent) => sum + agent.network.reduce((n, group) => n + group.items.filter((item) => item.change).length, 0),
    0,
  );
  const emptySkillDesc = agents.reduce(
    (sum, agent) => sum + Object.values(agent.skills).filter((skill) => !skill.desc).length,
    0,
  );
  console.log('\n覆盖统计');
  console.log(`  角色            ${agents.length}`);
  console.log(`  网络组          ${networkGroupTotal}（24 × 8）`);
  console.log(`  网络 item 总数  ${itemTotal}（有 change ${filledChangeCount}，空 change ${emptyChangeCount}）`);
  console.log(`  空技能描述      ${emptySkillDesc}`);
  console.log(`  定位分布        ${ROLE_ORDER.map((role) => `${role}${agents.filter((a) => a.role === role).length}`).join(' ')}`);
  console.log(`  warning         ${warnings.length} 条`);
  for (const warning of warnings) console.log(`    - ${warning}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
