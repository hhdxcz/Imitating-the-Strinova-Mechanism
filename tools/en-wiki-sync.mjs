#!/usr/bin/env node
/**
 * 英文 wiki 数据离线合并脚本（strinova.org）。
 *
 * 输入（均为 web_fetch 抓取后保存的本地文件，node 直连会被 Miraheze 反爬拦截）：
 *   docs/wiki-research/raw-en/characters-extracted.json  角色页解析结果（信息框 / 武器 / 增幅网络 / 觉醒）
 *   docs/wiki-research/raw-en/skill-names.json           4 个技能英文名（GetSkillStringById 抓取）
 *   common/src/main/resources/assets/strinova/data/wiki_zh.json  中文主数据（用于对齐结构/数值/成本/需求）
 *
 * 输出（两份必须完全一致）：
 *   docs/wiki-research/wiki-en.json
 *   common/src/main/resources/assets/strinova/data/wiki_en.json
 *
 * 说明：英文 wiki 没有逐项升级的「属性变化」细节，也没有可批量抓取的技能一句话描述，
 * 因此 change / skills[].desc 留空，运行时按字段回退中文；其余文本（角色档案 / 武器 / 网络标题与节点 / 觉醒）均为英文。
 *
 * 用法：node tools/en-wiki-sync.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const RAW = path.join(ROOT, 'docs/wiki-research/raw-en');
const ZH_PATH = path.join(ROOT, 'common/src/main/resources/assets/strinova/data/wiki_zh.json');

const zh = JSON.parse(fs.readFileSync(ZH_PATH, 'utf8'));
const characters = JSON.parse(fs.readFileSync(path.join(RAW, 'characters-extracted.json'), 'utf8'));
const skillNames = JSON.parse(fs.readFileSync(path.join(RAW, 'skill-names.json'), 'utf8'));

/** 技能名称 + 描述：由 web_fetch 抓取的角色技能卡 HTML 解析后固化（见 raw-en/skills-descs.json）。 */
const skillDescriptions = JSON.parse(fs.readFileSync(path.join(RAW, 'skills-descs.json'), 'utf8'));


const warnings = [];
const agents = [];

for (const zhAgent of zh.agents) {
  const en = characters[zhAgent.id];
  if (!en) {
    warnings.push(`${zhAgent.id}: 缺少英文角色页数据`);
    continue;
  }
  if (!en.network || en.network.length !== 8) {
    warnings.push(`${zhAgent.id}: 英文网络组数=${en.network ? en.network.length : 0}`);
  }
  const network = [];
  for (let i = 0; i < zhAgent.network.length; i++) {
    const zhGroup = zhAgent.network[i];
    const enGroup = en.network[i];
    if (!enGroup) {
      warnings.push(`${zhAgent.id}.slot${zhGroup.slot}: 英文缺失，回退中文`);
      network.push({
        slot: zhGroup.slot,
        kind: zhGroup.kind,
        label: zhGroup.label,
        items: zhGroup.items.map((item) => ({ text: item.text, cost: item.cost, change: '' })),
      });
      continue;
    }
    const items = [];
    for (let k = 0; k < zhGroup.items.length; k++) {
      const enItem = enGroup.items[k];
      if (!enItem) {
        warnings.push(`${zhAgent.id}.slot${zhGroup.slot}: 第 ${k + 1} 项英文缺失，回退中文`);
        items.push({ text: zhGroup.items[k].text, cost: zhGroup.items[k].cost, change: '' });
        continue;
      }
      if (enItem.cost !== zhGroup.items[k].cost) {
        warnings.push(`${zhAgent.id}.slot${zhGroup.slot}: 第 ${k + 1} 项成本不一致 zh=${zhGroup.items[k].cost} en=${enItem.cost}`);
      }
      items.push({ text: enItem.text, cost: zhGroup.items[k].cost, change: '' });
    }
    if (enGroup.items.length > zhGroup.items.length) {
      warnings.push(`${zhAgent.id}.slot${zhGroup.slot}: 英文项数 ${enGroup.items.length} 多于中文 ${zhGroup.items.length}，已忽略多余项`);
    }
    network.push({ slot: zhGroup.slot, kind: zhGroup.kind, label: enGroup.name || zhGroup.label, items });
  }

  const awakenings = en.awakenings.map((awakening, index) => {
    const zhAwakening = zhAgent.awakenings[index] || {};
    if (awakening.needWeapon !== zhAwakening.needWeapon
      || awakening.needSkill !== zhAwakening.needSkill
      || awakening.needDefense !== zhAwakening.needDefense) {
      warnings.push(`${zhAgent.id}: 觉醒${index + 1} 三色需求不一致 zh=${zhAwakening.needWeapon}/${zhAwakening.needSkill}/${zhAwakening.needDefense} en=${awakening.needWeapon}/${awakening.needSkill}/${awakening.needDefense}`);
    }
    return {
      name: awakening.name || (zhAgent.awakenings[index] || {}).name || '',
      desc: awakening.desc || '',
      needWeapon: zhAwakening.needWeapon || 0,
      needSkill: zhAwakening.needSkill || 0,
      needDefense: zhAwakening.needDefense || 0,
    };
  });

  const names = skillNames[zhAgent.id] || {};
  const parsed = skillDescriptions[en.page] || {};
  const skill = (key) => {
    const fromWiki = parsed[key] || {};
    const name = names[key] || fromWiki.name || '';
    const desc = fromWiki.desc || '';
    if (!name) {
      warnings.push(`${zhAgent.id}: 缺少技能名 ${key}`);
    }
    if (!desc) {
      warnings.push(`${zhAgent.id}: 技能 ${key} 缺少英文描述（运行时回退中文）`);
    }
    return { name, desc };
  };

  agents.push({
    id: zhAgent.id,
    name: en.name || zhAgent.name,
    enName: en.name || zhAgent.enName,
    role: en.role || '',
    roleRaw: en.role || '',
    faction: en.camp || '',
    factionRaw: en.camp || '',
    identity: en.identity || '',
    intro: en.intro || '',
    quote: en.quote || '',
    traits: [],
    weapon: en.weapon || { name: '', type: '', desc: '' },
    skills: {
      active: skill('active'),
      passive: skill('passive'),
      ultimate: skill('ultimate'),
      tactical: skill('tactical'),
    },
    awakenings,
    network,
  });
}

const result = {
  source: {
    wiki: 'https://strinova.org/wiki',
    fetchedAt: new Date().toISOString().slice(0, 10),
    lang: 'en',
    note: '文本覆盖层：结构/数值/成本/需求以中文主数据为准；技能描述与升级明细英文 wiki 无批量数据，留空并回退中文。',
  },
  roleOrder: zh.roleOrder,
  agents,
  warnings,
};

const text = JSON.stringify(result, null, 2) + '\n';
fs.writeFileSync(path.join(ROOT, 'docs/wiki-research/wiki-en.json'), text, 'utf8');
fs.writeFileSync(path.join(ROOT, 'common/src/main/resources/assets/strinova/data/wiki_en.json'), text, 'utf8');

// ---------------- validator ----------------
const problems = [];
if (agents.length !== 24) problems.push(`agents=${agents.length}`);
for (const agent of agents) {
  if (agent.network.length !== 8) problems.push(`${agent.id}: network=${agent.network.length}`);
  if (agent.awakenings.length !== 3) problems.push(`${agent.id}: awakenings=${agent.awakenings.length}`);
  for (const group of agent.network) {
    if (!group.label) problems.push(`${agent.id}.${group.slot}: 空 label`);
    for (const item of group.items) {
      if (!item.text) problems.push(`${agent.id}.${group.slot}: 空 text`);
      if (!(item.cost > 0)) problems.push(`${agent.id}.${group.slot}: cost=${item.cost}`);
    }
  }
  for (const key of ['active', 'passive', 'ultimate', 'tactical']) {
    if (!agent.skills[key].name) problems.push(`${agent.id}: 空技能名 ${key}`);
  }
  for (const awakening of agent.awakenings) {
    if (!awakening.name || !awakening.desc) problems.push(`${agent.id}: 空觉醒 ${awakening.name}`);
  }
  if (!agent.identity || !agent.intro) problems.push(`${agent.id}: 档案缺失`);
  if (!agent.weapon.name) problems.push(`${agent.id}: 武器缺失`);
}
const itemTotal = agents.reduce((sum, a) => sum + a.network.reduce((s, g) => s + g.items.length, 0), 0);
console.log('=== 英文 wiki 数据同步完成 ===');
console.log(`agents=${agents.length} networkGroups=${agents.reduce((s, a) => s + a.network.length, 0)} items=${itemTotal}`);
console.log(`warnings=${warnings.length}`);
for (const warning of warnings) console.log('  -', warning);
console.log(`problems=${problems.length}`);
for (const problem of problems) console.log('  !', problem);
if (problems.length) process.exit(1);
