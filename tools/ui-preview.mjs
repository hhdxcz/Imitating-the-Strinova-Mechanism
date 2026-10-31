#!/usr/bin/env node
/**
 * 界面预览生成器：读 wiki 双语数据 → 生成与 Java 实现一致的 HTML 预览（1600×900）。
 *
 * 用法：
 *   node tools/ui-preview.mjs --screen growth --lang zh
 *   node tools/ui-preview.mjs --screen select --lang en
 *   node tools/ui-preview.mjs            # 生成全部 4 张
 *
 * 输出：docs/ui/growth-real-<lang>.html / select-real-<lang>.html
 * 渲染 PNG：用 Edge 无头截图（见 docs/wiki-research/README 或交付说明）。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const DATA = path.join(ROOT, 'common/src/main/resources/assets/strinova/data');
const OUT = path.join(ROOT, 'docs/ui');

const zh = JSON.parse(fs.readFileSync(path.join(DATA, 'wiki_zh.json'), 'utf8'));
let en = null;
try { en = JSON.parse(fs.readFileSync(path.join(DATA, 'wiki_en.json'), 'utf8')); } catch { /* 可选 */ }

const ROLE_COLORS = {
  突击: '#BC3B47', 决斗: '#DEB639', 守护: '#4B77DC', 支援: '#35D6E8', 先锋: '#FF4FA3', 控场: '#9B6BFF',
};
const ROLE_EN = {
  Assault: '#BC3B47', Duelist: '#DEB639', Sentinel: '#4B77DC', Support: '#35D6E8', Vanguard: '#FF4FA3', Controller: '#9B6BFF',
};
const FACTION_ZH = { 欧泊: '欧泊', 剪刀手: '剪刀手', 乌尔比诺: '乌尔比诺' };
const esc = (s) => String(s == null ? '' : s)
  .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

function agentData(id, lang) {
  const base = zh.agents.find((agent) => agent.id === id);
  const overlay = en && en.agents.find((agent) => agent.id === id);
  const pick = (enValue, zhValue) => (enValue != null && enValue !== '' ? enValue : zhValue);
  const isEn = lang === 'en';
  return {
    id,
    name: isEn ? pick(overlay && overlay.name, base.name) : base.name,
    enName: base.enName,
    role: isEn ? (overlay && overlay.role) || base.role : base.role,
    faction: isEn ? (overlay && overlay.faction) || base.faction : FACTION_ZH[base.faction] || base.faction,
    identity: isEn ? pick(overlay && overlay.identity, base.identity) : base.identity,
    intro: isEn ? pick(overlay && overlay.intro, base.intro) : base.intro,
    quote: isEn ? pick(overlay && overlay.quote, base.quote) : base.quote,
    weapon: {
      name: isEn ? pick(overlay && overlay.weapon && overlay.weapon.name, base.weapon.name) : base.weapon.name,
      type: isEn ? pick(overlay && overlay.weapon && overlay.weapon.type, base.weapon.type) : base.weapon.type,
      desc: isEn ? pick(overlay && overlay.weapon && overlay.weapon.desc, base.weapon.desc) : base.weapon.desc,
    },
    stats: base.stats,
    skills: Object.fromEntries(['active', 'passive', 'ultimate', 'tactical'].map((key) => {
      const enSkill = overlay && overlay.skills && overlay.skills[key];
      return [key, {
        name: isEn ? pick(enSkill && enSkill.name, base.skills[key].name) : base.skills[key].name,
        desc: isEn ? pick(enSkill && enSkill.desc, base.skills[key].desc) : base.skills[key].desc,
      }];
    })),
    awakenings: base.awakenings.map((awakening, index) => {
      const enAwakening = overlay && overlay.awakenings && overlay.awakenings[index];
      return {
        name: isEn ? pick(enAwakening && enAwakening.name, awakening.name) : awakening.name,
        desc: isEn ? pick(enAwakening && enAwakening.desc, awakening.desc) : awakening.desc,
        needWeapon: awakening.needWeapon,
        needSkill: awakening.needSkill,
        needDefense: awakening.needDefense,
        active: index === 0,
      };
    }),
    network: base.network.map((group, index) => {
      const enGroup = overlay && overlay.network && overlay.network[index];
      const items = group.items.map((item, itemIndex) => {
        const enItem = enGroup && enGroup.items && enGroup.items[itemIndex];
        return {
          text: isEn ? pick(enItem && enItem.text, item.text) : item.text,
          cost: item.cost,
          change: item.change,
        };
      });
      return { slot: group.slot, kind: group.kind, label: isEn ? pick(enGroup && enGroup.label, group.label) : group.label, items };
    }),
  };
}

const L = {
  zh: {
    growthTitle: '弦能增幅网络', points: '拥有能量点', ready: '准备阶段', close: '✕',
    kindActive: '主动技能', kindPassive: '被动技能', kindWeapon: '', armor: '护甲', stringify: '弦化',
    weaponSlots: ['主武器', '副武器', '近战武器', '战术道具', '战术道具'],
    awakening: '觉醒', activated: '已激活', inactive: '未激活',
    statHp: '生命', statArmor: '护甲', statSpeed: '移速',
    energy: '能量点', need: '需求', selected: '已选',
    source: '数据来源：biligame 卡拉彼丘 wiki · 中文文本',
    selectTitle: '选择角色', phaseAlly: '我方进攻', phaseEnemy: '敌方防守', lock: '锁定角色',
    teammates: '队友', skills: '技能', skillLabels: { active: 'Q 主动', passive: 'P 被动', ultimate: 'X 终极', tactical: 'E 战术' },
    weaponTab: '武器选择', appearanceTab: '外观选择', identity: '身份', quote: '语录', p1: 'P1',
  },
  en: {
    growthTitle: 'String Energy Network', points: 'Energy Points', ready: 'Prep Time', close: '✕',
    kindActive: 'Active Skill', kindPassive: 'Passive Skill', kindWeapon: '', armor: 'Armor', stringify: 'Stringify',
    weaponSlots: ['Primary', 'Secondary', 'Melee', 'Tactical', 'Tactical'],
    awakening: 'Awakening', activated: 'Active', inactive: 'Inactive',
    statHp: 'HP', statArmor: 'Armor', statSpeed: 'Speed',
    energy: 'Energy', need: 'Requires', selected: 'Selected',
    source: 'Source: Strinova Wiki (strinova.org) · English text',
    selectTitle: 'Select Agent', phaseAlly: 'Our Attack', phaseEnemy: 'Enemy Defense', lock: 'Lock Agent',
    teammates: 'Teammates', skills: 'Skills', skillLabels: { active: 'Q Active', passive: 'P Passive', ultimate: 'X Ultimate', tactical: 'E Tactical' },
    weaponTab: 'Weapons', appearanceTab: 'Appearance', identity: 'Title', quote: 'Quote', p1: 'P1',
  },
};

const BASE_CSS = `
* { box-sizing: border-box; margin: 0; padding: 0; }
html, body { width: 1600px; height: 900px; overflow: hidden; }
body { font-family: "Microsoft YaHei", "Segoe UI", sans-serif; color: #EAF2FA; background: #05080D;
  -webkit-font-smoothing: antialiased; }
.screen { position: relative; width: 1600px; height: 900px;
  background: linear-gradient(180deg, #0A1018 0%, #141D2B 100%); }
.source { position: absolute; left: 16px; bottom: 2px; font-size: 10px; color: #8fa0b1; letter-spacing: .3px;
    background: rgba(5,8,13,.9); border-left: 2px solid rgba(53,214,232,.55); padding: 2px 8px; z-index: 20; }
`;

function headerPattern(accent) {
  return `background: linear-gradient(180deg, rgba(22,31,44,.98), rgba(16,24,35,.96));
    border-top: 2px solid ${accent}; border-bottom: 1px solid rgba(53,214,232,.25);`;
}

// ------------------------------------------------------------------
// 成长网络
// ------------------------------------------------------------------
function growthHtml(agent, lang) {
  const t = L[lang];
  const accent = ROLE_COLORS[agent.role] || ROLE_EN[agent.role] || '#35D6E8';
  const network = agent.network;
  const left = network.slice(0, 4);
  const right = network.slice(4);
  const weaponName = agent.weapon.name;
  const kindTag = (kind) => (kind === 'active' ? t.kindActive : kind === 'passive' ? t.kindPassive : '');
  const block = (group) => `
    <div class="nblock">
      <div class="nhead"><span class="accent" style="background:${accent}"></span>
        <span class="nlabel">${esc(group.label)}</span>
        ${kindTag(group.kind) ? `<span class="nkind">${esc(kindTag(group.kind))}</span>` : ''}
      </div>
      <div class="nline" style="background:linear-gradient(90deg, ${accent}55, transparent)"></div>
      <div class="nitems">
        ${group.items.map((item) => `
          <div class="nitem${item.cost > 330 ? ' dim' : ''}">
            <span class="ntext">${esc(item.text)}</span>
            <span class="ncost"><i class="gem"></i>${item.cost}</span>
          </div>`).join('')}
      </div>
    </div>`;

  // 示例 tooltip：取第一个有 change 文案的项
  let tip = null;
  outer:
  for (let g = 0; g < network.length; g++) {
    for (let i = 0; i < network[g].items.length; i++) {
      const item = network[g].items[i];
      if (item.change && lang === 'zh') { tip = { group: g, item: i, text: item.text, change: item.change, cost: item.cost }; break outer; }
    }
  }
  const tipHtml = tip ? `
    <div class="tooltip" style="left:26px; top:104px">
      <div class="tt-title">${esc(tip.text)}</div>
      <div class="tt-cost"><i class="gem"></i>${tip.cost}</div>
      <div class="tt-change">${esc(tip.change)}</div>
    </div>` : '';

  return `<!DOCTYPE html><html lang="${lang}"><head><meta charset="utf-8"><style>${BASE_CSS}
  .head { position:absolute; left:16px; right:16px; top:14px; height:56px; ${headerPattern(accent)}
    display:flex; align-items:center; padding:0 18px; gap:24px; }
  .head .title { font-size:21px; font-weight:700; letter-spacing:.5px; }
  .head .title em { font-style:normal; color:${accent}; }
  .head .points { margin-left:auto; margin-right:auto; font-size:15px; color:#FFD166; }
  .head .points b { font-size:20px; margin-left:6px; }
  .head .ready { font-size:14px; color:#35D6E8; }
  .head .close { width:34px; height:34px; border:1px solid #40546a; color:#9AA8B8;
    display:flex; align-items:center; justify-content:center; font-size:16px; }
  .main { position:absolute; left:16px; right:16px; top:84px; bottom:214px; display:flex; gap:16px; }
  .portrait { width:262px; flex:none; position:relative; overflow:hidden;
    background:linear-gradient(160deg, ${accent}55 0%, #101a26 55%, #0A1018 100%);
    border:1px solid ${accent}44; }
  .portrait .giant { position:absolute; left:50%; top:44%; transform:translate(-50%,-50%);
    font-size:220px; font-weight:800; color:${accent}88; line-height:1; }
  .portrait .edge { position:absolute; left:0; right:0; top:0; height:3px; background:${accent}; }
  .portrait .pname { position:absolute; left:0; right:0; bottom:92px; text-align:center;
    font-size:24px; font-weight:700; letter-spacing:1px; }
  .portrait .role { position:absolute; left:50%; transform:translateX(-50%); bottom:60px;
    padding:2px 12px; font-size:13px; color:#0A111C; background:${accent}; font-weight:700; }
  .portrait .identity { position:absolute; left:8px; right:8px; bottom:40px; text-align:center;
    font-size:12px; color:#9AA8B8; }
  .portrait .stats { position:absolute; left:6px; right:6px; bottom:14px; text-align:center;
    font-size:12px; color:#7f8c9a; letter-spacing:.4px; }
  .network { flex:1; display:grid; grid-template-columns:1fr 1fr; grid-template-rows:repeat(4,1fr); grid-auto-flow:column; gap:12px 14px; }
  .nblock { background:rgba(16,24,35,.78); border:1px solid rgba(53,214,232,.16); padding:8px 10px 8px; }
  .nhead { display:flex; align-items:center; gap:7px; }
  .nhead .accent { width:3px; height:14px; display:inline-block; }
  .nlabel { font-size:15px; font-weight:700; }
  .nkind { margin-left:auto; font-size:11px; color:#5E6874; }
  .nline { height:1px; margin:5px 0 6px; }
  .nitems { display:flex; flex-direction:column; gap:5px; }
  .nitem { display:flex; align-items:center; height:27px; padding:0 8px;
    background:linear-gradient(90deg, rgba(255,255,255,.055), rgba(0,0,0,.12)); }
  .nitem.dim { opacity:.55; }
  .ntext { font-size:13px; color:#DCE6F0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .ncost { margin-left:auto; font-size:13px; color:#4FA3FF; display:flex; align-items:center; gap:5px; }
  .gem { width:9px; height:9px; background:#4FA3FF; display:inline-block; transform:rotate(45deg); }
  .weapons { width:176px; flex:none; background:rgba(12,18,26,.9); border-left:1px solid rgba(53,214,232,.2);
    padding:8px; display:flex; flex-direction:column; gap:8px; }
  .wslot { flex:1; display:flex; align-items:center; gap:8px; padding:0 8px;
    background:rgba(255,255,255,.035); border:1px solid transparent; position:relative; }
  .wslot.active { background:rgba(53,214,232,.14); border-color:rgba(53,214,232,.55); }
  .wicon { width:26px; height:26px; flex:none; position:relative; }
  .wicon::before { content:''; position:absolute; inset:6px 2px; background:${accent}; opacity:.85;
    clip-path:polygon(0 35%, 78% 35%, 78% 45%, 100% 45%, 100% 58%, 72% 58%, 72% 80%, 52% 80%, 52% 58%, 0 58%); }
  .wslot.grenade .wicon::before { clip-path:polygon(50% 15%, 88% 50%, 50% 85%, 12% 50%); inset:4px; }
  .wslot.knife .wicon::before { clip-path:polygon(8% 92%, 78% 12%, 92% 26%, 22% 100%); inset:2px; }
  .wname { font-size:12px; color:#C8D4E0; max-width:96px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .wkey { position:absolute; right:6px; bottom:4px; font-size:10px; color:#7f8c9a; }
  .wtype { font-size:10px; color:#6f7d8b; }
  .awaken { position:absolute; left:16px; right:16px; bottom:16px; height:186px; display:flex; gap:14px; }
  .acard { flex:1; background:rgba(16,24,35,.82); border:1px solid rgba(255,209,102,.22); position:relative;
    padding:12px 14px; }
  .acard.on { border-color:rgba(255,209,102,.75); box-shadow:inset 0 0 26px rgba(255,209,102,.08); }
  .acard .no { font-size:12px; color:#9AA8B8; letter-spacing:1px; }
  .acard .aname { font-size:17px; font-weight:700; color:${'#FFD166'}; margin:3px 0 2px; }
  .acard.off .aname { color:#EAF2FA; }
  .acard .astatus { position:absolute; right:14px; top:12px; font-size:12px; color:#54E0A0; }
  .acard.off .astatus { color:#5E6874; }
  .acard .adesc { font-size:12.5px; line-height:1.55; color:#B4C0CC; margin-top:6px;
    display:-webkit-box; -webkit-line-clamp:3; -webkit-box-orient:vertical; overflow:hidden; }
  .dots { position:absolute; right:14px; top:36px; display:flex; gap:5px; }
  .dot { width:20px; height:18px; display:flex; align-items:center; justify-content:center;
    font-size:11px; font-weight:700; color:#0A111C; }
  .dot.met { background:#4FA3FF; } .dot.met.y { background:#FFD166; } .dot.met.g { background:#54E0A0; }
  .dot.miss { background:rgba(86,98,110,.35); color:#B4C0CC; }
  .tooltip { position:absolute; z-index:9; width:248px; background:#0C121A; border:1px solid #2A3542;
    padding:9px 11px; box-shadow:0 6px 26px rgba(0,0,0,.6); }
  .tooltip .tt-title { font-size:14px; font-weight:700; }
  .tooltip .tt-cost { font-size:13px; color:#4FA3FF; margin:5px 0 6px; display:flex; align-items:center; gap:6px; }
  .tooltip .tt-change { font-size:12.5px; line-height:1.6; color:#9FD0E8; border-top:1px solid #22303e; padding-top:6px; }
  </style></head><body><div class="screen">
    <div class="head">
      <div class="title">${t.growthTitle} <em>· ${esc(agent.name)}</em></div>
      <div class="points">${t.points}<b>330</b></div>
      <div class="ready">${t.ready} 00:25</div>
      <div class="close">${t.close}</div>
    </div>
    <div class="main">
      <div class="portrait">
        <div class="edge"></div>
        <div class="giant">${esc((agent.name || '?').slice(0, 1))}</div>
        <div class="pname">${esc(agent.name)}</div>
        <div class="role">${esc(agent.role)}</div>
        <div class="identity">${esc(agent.identity || '')}</div>
        <div class="stats">${t.statHp} ${agent.stats.hp}　${t.statArmor} ${agent.stats.armor}　${t.statSpeed} ${agent.stats.moveSpeed}</div>
      </div>
      <div class="network">
        ${left.concat(right).map(block).join('')}
      </div>
      <div class="weapons">
        ${t.weaponSlots.map((slot, index) => `
          <div class="wslot ${index === 0 ? 'active' : index === 2 ? 'knife' : index === 1 ? '' : 'grenade'}">
            <div class="wicon"></div>
            <div>
              <div class="wname">${index === 0 ? esc(weaponName) : esc(slot)}</div>
              <div class="wtype">${index === 0 ? esc(agent.weapon.type) : ''}</div>
            </div>
            <div class="wkey">${index + 1}</div>
          </div>`).join('')}
      </div>
    </div>
    <div class="awaken">
      ${agent.awakenings.map((awakening, index) => `
        <div class="acard ${awakening.active ? 'on' : 'off'}">
          <div class="no">${t.awakening} ${index + 1}</div>
          <div class="aname">${esc(awakening.name)}</div>
          <div class="astatus">${awakening.active ? t.activated : t.inactive}</div>
          <div class="dots">
            ${[[awakening.needWeapon, 'met'], [awakening.needSkill, 'met y'], [awakening.needDefense, 'met g']]
              .filter(([need]) => need > 0)
              .map(([need, cls]) => `<div class="dot ${cls}">${need}</div>`).join('')}
          </div>
          <div class="adesc">${esc(awakening.desc)}</div>
        </div>`).join('')}
    </div>
    ${tipHtml}
    <div class="source">${t.source}</div>
  </div></body></html>`;
}

// ------------------------------------------------------------------
// 选角界面
// ------------------------------------------------------------------
function selectHtml(agent, lang) {
  const t = L[lang];
  const accent = ROLE_COLORS[agent.role] || ROLE_EN[agent.role] || '#DEB639';
  const teammateAgents = zh.agents.filter((item) => item.id !== agent.id).slice(0, 4);
  const teammate = [{ name: agent.name, role: agent.role }].concat(teammateAgents.map((item) => {
    const overlay = en && en.agents.find((agent2) => agent2.id === item.id);
    return {
      name: lang === 'en' ? ((overlay && overlay.name) || item.name) : item.name,
      role: lang === 'en' ? ((overlay && overlay.role) || item.role) : item.role,
    };
  }));
  const allAgents = zh.agents.map((item) => lang === 'en' ? ((en && en.agents.find((agent2) => agent2.id === item.id) || {}).name || item.name) : item.name);
  const skillOrder = ['active', 'passive', 'ultimate', 'tactical'];
  const keyLetters = ['Q', 'P', 'X', 'E'];
  const selected = agent.skills.active;
  return `<!DOCTYPE html><html lang="${lang}"><head><meta charset="utf-8"><style>${BASE_CSS}
  .phasebar { position:absolute; left:16px; right:16px; top:14px; height:60px; display:flex; align-items:stretch; gap:10px; }
  .slab { width:340px; display:flex; align-items:center; justify-content:center; font-size:16px; font-weight:700;
    letter-spacing:1px; clip-path:polygon(0 0, 100% 0, 94% 100%, 0 100%); }
  .slab.ally { background:linear-gradient(90deg, #BC3B47, rgba(188,59,71,.55)); }
  .slab.enemy { background:linear-gradient(270deg, #4B77DC, rgba(75,119,220,.55)); clip-path:polygon(6% 0, 100% 0, 100% 100%, 0 100%); }
  .phasebar .center { flex:1; background:rgba(10,16,24,.92); border:1px solid rgba(53,214,232,.28);
    display:flex; align-items:center; justify-content:center; gap:26px; }
  .phasebar .center .mode { font-size:15px; color:#9AA8B8;letter-spacing:1px; }
  .phasebar .center .timer { font-size:26px; font-weight:800; color:#35D6E8; letter-spacing:2px; }
  .main { position:absolute; left:16px; right:16px; top:90px; bottom:158px; display:flex; gap:14px; }
  .team { width:196px; flex:none; display:flex; flex-direction:column; gap:8px; }
  .team .thead { font-size:12px; color:#9AA8B8; letter-spacing:1px; padding:2px 4px; }
  .mate { flex:1; display:flex; align-items:center; gap:9px; padding:0 10px; background:rgba(16,24,35,.8);
    border-left:3px solid ${accent}88; }
  .mate.me { border-left-color:#FFD24A; background:rgba(40,36,20,.55); }
  .mate .avatar { width:30px; height:30px; background:#1b2635; border:1px solid rgba(255,255,255,.18);
    display:flex; align-items:center; justify-content:center; font-size:14px; color:#DCE6F0; }
  .mate .mname { font-size:13px; color:#DCE6F0; }
  .mate .mrole { font-size:11px; color:#6f7d8b; }
  .center { flex:1; position:relative; overflow:hidden; border:1px solid rgba(53,214,232,.16);
    background:linear-gradient(170deg, ${accent}33 0%, #0b121b 60%, #05080D 100%); }
  .center .giant { position:absolute; left:50%; top:42%; transform:translate(-50%,-50%);
    font-size:270px; font-weight:800; color:rgba(255,255,255,.075); line-height:1; }
  .center .intro { position:absolute; left:34px; right:34px; bottom:112px; font-size:14px; line-height:1.7;
    color:#C6D2DE; text-shadow:0 2px 12px #000; }
  .center .intro .quote { margin-top:8px; font-size:13px; color:#8fa0b1; font-style:italic; }
  .center .namebar { position:absolute; left:0; right:0; bottom:0; height:86px; padding:10px 20px;
    background:linear-gradient(0deg, rgba(5,8,13,.96), rgba(5,8,13,.35)); border-top:1px solid ${accent}66; }
  .center .namebar .an { font-size:34px; font-weight:800; letter-spacing:2px; }
  .center .namebar .roleline { font-size:13px; color:${accent}; margin-top:2px; letter-spacing:1px; }
  .center .p1 { position:absolute; left:16px; top:14px; font-size:13px; font-weight:800; color:#FFD24A;
    border:1px solid #FFD24A; padding:1px 7px; }
  .right { width:404px; flex:none; background:rgba(12,18,26,.94); border:1px solid rgba(53,214,232,.2);
    padding:14px 16px; position:relative; }
  .rolechip { display:flex; align-items:center; gap:10px; }
  .chip { padding:2px 12px; background:${accent}; color:#0A111C; font-size:13px; font-weight:800; letter-spacing:1px; }
  .faction { font-size:13px; color:#9AA8B8; }
  .identity { margin-top:8px; font-size:13px; color:#C6D2DE; }
  .identity b { color:#6f7d8b; font-weight:400; }
  .skills { margin-top:14px; display:flex; align-items:center; gap:8px; }
  .skills .lab { font-size:12px; color:#9AA8B8; letter-spacing:1px; margin-right:2px; }
  .scard { width:58px; height:58px; border:1px solid rgba(53,214,232,.35); background:rgba(10,17,25,.9);
    display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px; padding:0 2px; }
  .scard.sel { background:${accent}33; border-color:${accent}; }
  .scard .k { font-size:16px; font-weight:800; color:#DCE6F0; }
  .scard.sel .k { color:${accent}; }
  .scard .sname { font-size:9px; line-height:1.1; color:#8fa0b1; max-width:54px; text-align:center;
    display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }
  .skilldetail { margin-top:10px; border-left:3px solid ${accent}; padding:2px 0 2px 10px; }
  .skilldetail .sl { font-size:13px; font-weight:700; color:${accent}; }
  .skilldetail .sd { font-size:12.5px; line-height:1.55; color:#B4C0CC; margin-top:4px;
    display:-webkit-box; -webkit-line-clamp:3; -webkit-box-orient:vertical; overflow:hidden; }
  .tabs { display:flex; gap:6px; margin-top:14px; }
  .tab { flex:1; height:26px; display:flex; align-items:center; justify-content:center; font-size:12.5px;
    border:1px solid rgba(255,255,255,.22); background:rgba(255,255,255,.06); color:#9AA8B8; }
  .tab.on { background:${accent}CC; border-color:${accent}; color:#0A111C; font-weight:700; }
  .wrow { display:flex; align-items:center; gap:10px; height:36px; margin-top:6px;
    background:rgba(255,255,255,.045); padding:0 10px; }
  .wrow .wicon { width:22px; height:22px; position:relative; }
  .wrow .wicon::before { content:''; position:absolute; inset:5px 1px; background:${accent}bb;
    clip-path:polygon(0 35%, 78% 35%, 78% 45%, 100% 45%, 100% 58%, 72% 58%, 72% 80%, 52% 80%, 52% 58%, 0 58%); }
  .wrow.grenade .wicon::before { clip-path:polygon(50% 15%, 88% 50%, 50% 85%, 12% 50%); inset:4px; }
  .wrow.knife .wicon::before { clip-path:polygon(8% 92%, 78% 12%, 92% 26%, 22% 100%); inset:2px; }
  .wrow .wname { font-size:13px; color:#DCE6F0; }
  .wrow .wtype { margin-left:auto; font-size:11px; color:#7f8c9a; }
  .lockbtn { position:absolute; right:16px; bottom:16px; height:72px; width:404px; background:${accent};
    color:#0A111C; font-size:19px; font-weight:800; letter-spacing:4px; display:flex; align-items:center;
    justify-content:center; border:1px solid #00000055; }
  .grid { position:absolute; left:16px; bottom:16px; height:72px; display:grid;
    grid-template-columns:repeat(12, 1fr); grid-template-rows:1fr 1fr; gap:5px 6px; width:1150px; }
  .wdesc { margin-top:12px; padding-top:10px; border-top:1px solid rgba(255,255,255,.12);
    font-size:12px; line-height:1.65; color:#8fa0b1; }
  .wdesc b { display:block; color:#C6D2DE; font-size:12.5px; margin-bottom:4px; }
  .cell { background:rgba(16,24,35,.85); border:1px solid rgba(255,255,255,.09); display:flex;
    align-items:center; justify-content:center; font-size:14px; color:#C6D2DE; position:relative; }
  .cell.sel { border-color:${accent}; background:${accent}33; color:#fff; font-weight:700; }
  .cell.sel::before { content:'P1'; position:absolute; left:3px; top:1px; font-size:8px; color:#FFD24A; }
  </style></head><body><div class="screen">
    <div class="phasebar">
      <div class="slab ally">${t.phaseAlly}</div>
      <div class="center"><span class="mode">${t.selectTitle}</span><span class="timer">00:24</span></div>
      <div class="slab enemy">${t.phaseEnemy}</div>
    </div>
    <div class="main">
      <div class="team">
        <div class="thead">${t.teammates}</div>
        ${teammate.map((mate, index) => `
          <div class="mate ${index === 0 ? 'me' : ''}">
            <div class="avatar">${esc(mate.name.slice(0, 1))}</div>
            <div><div class="mname">${esc(mate.name)}</div><div class="mrole">${esc(mate.role)}</div></div>
          </div>`).join('')}
      </div>
      <div class="center">
        <div class="giant">${esc(agent.name.slice(0, 1))}</div>
        <div class="p1">${t.p1}</div>
        <div class="intro">${esc(agent.intro)}<div class="quote">「${esc(agent.quote)}」</div></div>
        <div class="namebar">
          <div class="an">${esc(agent.name)}</div>
          <div class="roleline">${esc(agent.role)} · ${esc(agent.faction)}</div>
        </div>
      </div>
      <div class="right">
        <div class="rolechip"><span class="chip">${esc(agent.role)}</span><span class="faction">${esc(agent.faction)}</span></div>
        <div class="identity"><b>${t.identity} ·</b> ${esc(agent.identity || '')}</div>
        <div class="skills">
          <span class="lab">${t.skills}</span>
          ${skillOrder.map((key, index) => `
            <div class="scard ${index === 0 ? 'sel' : ''}">
              <div class="k">${keyLetters[index]}</div>
              <div class="sname">${esc(agent.skills[key].name)}</div>
            </div>`).join('')}
        </div>
        <div class="skilldetail">
          <div class="sl">${esc(t.skillLabels.active)} · ${esc(selected.name)}</div>
          <div class="sd">${esc(selected.desc)}</div>
        </div>
        <div class="tabs"><div class="tab on">${t.weaponTab}</div><div class="tab">${t.appearanceTab}</div></div>
        ${t.weaponSlots.map((slot, index) => `
          <div class="wrow ${index === 0 ? '' : index === 2 ? 'knife' : index === 1 ? '' : 'grenade'}">
            <div class="wicon"></div>
            <div class="wname">${index === 0 ? esc(agent.weapon.name) : esc(slot)}</div>
            <div class="wtype">${index === 0 ? esc(agent.weapon.type) : ''}</div>
          </div>`).join('')}
        <div class="wdesc"><b>${esc(agent.weapon.name)} · ${esc(agent.weapon.type)}</b>${esc(agent.weapon.desc)}</div>
      </div>
    </div>
    <div class="lockbtn">${t.lock}</div>
    <div class="grid">
      ${allAgents.map((name, index) => `<div class="cell${index === 0 ? ' sel' : ''}">${esc(name.slice(0, 1))}</div>`).join('')}
    </div>
    <div class="source">${t.source}</div>
  </div></body></html>`;
}

// ------------------------------------------------------------------
const args = process.argv.slice(2);
const getArg = (name, fallback) => {
  const index = args.indexOf('--' + name);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
};
const screens = getArg('screen', 'all');
const langs = getArg('lang', 'all');
const runScreens = screens === 'all' ? ['growth', 'select'] : [screens];
const runLangs = langs === 'all' ? ['zh', 'en'] : [langs];

fs.mkdirSync(OUT, { recursive: true });
for (const screen of runScreens) {
  for (const lang of runLangs) {
    const agent = screen === 'growth' ? agentData('nono', lang) : agentData('michele', lang);
    const html = screen === 'growth' ? growthHtml(agent, lang) : selectHtml(agent, lang);
    const file = path.join(OUT, `${screen}-real-${lang}.html`);
    fs.writeFileSync(file, html, 'utf8');
    console.log('written', path.relative(ROOT, file), html.length, 'bytes');
  }
}
