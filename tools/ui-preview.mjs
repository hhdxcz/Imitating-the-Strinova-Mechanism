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
    upgraded: '已升级', weaponHint: '现在可装备战术技能', map: '88区', mode: '爆破入侵',
    notice: '进攻方无法选择欧泊角色',
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
    upgraded: 'Upgraded', weaponHint: 'Tactical Skill equippable', map: 'Area 88', mode: 'Demolition',
    notice: 'Attackers cannot pick P.U.S agents',
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

  // 演示「每类只能选一项」：第一类选了第一项，第二项灰显；其余类未选
  const lockedSlot = agent.network[0] ? agent.network[0].slot : 1;

  const swapIcon = '<span class="swap"></span>';
  const weaponCard = (index, extraClass) => {
    const slotLabel = t.weaponSlots[index];
    const name = index === 0 ? agent.weapon.name : slotLabel;
    const iconClass = index === 0 ? 'rifle' : index === 1 ? 'pistol' : index === 2 ? 'knife' : 'grenade';
    return `
      <div class="wcard ${extraClass || ''}${index === 0 ? ' sel' : ''}">
        <div class="wtop"><span class="wnum">${index + 1}</span><span class="wtype">${esc(slotLabel)}</span></div>
        <div class="wicon ${iconClass}"></div>
        <div class="wname">${esc(name)}</div>
        ${swapIcon}
      </div>`;
  };

  const branches = agent.network.map((group) => {
    const kind = group.kind;
    const iconClass = kind === 'weapon' ? 'ico-diamond' : (kind === 'active' || kind === 'passive') ? 'ico-circle'
      : kind === 'armor' ? 'ico-shield' : 'ico-triangle';
    const color = kind === 'weapon' ? '#5A9BFF' : (kind === 'active' || kind === 'passive') ? '#E8B54D' : '#57C785';
    const locked = group.slot === lockedSlot;
    const items = group.items.map((item, index) => {
      const owned = locked && index === 0;
      const grayed = locked && index > 0;
      return `<div class="bitem${owned ? ' owned' : ''}${grayed ? ' grayed' : ''}">
        <span class="btext">${esc(item.text)}</span>
        ${owned
          ? `<span class="bowned">${esc(t.upgraded)}</span>`
          : `<span class="bprice"><em>${item.cost}</em><i class="gem"></i></span>`}
      </div>`;
    }).join('');
    return `
      <div class="branch">
        <div class="bhead">
          <i class="bicon ${iconClass}" style="background:${color}"></i>
          <span>${esc(group.label)}</span>
          <i class="refresh${locked ? '' : ' dim'}"></i>
        </div>
        <div class="bitems">${items}</div>
      </div>`;
  }).join('');

  const needShapes = (awakening) => {
    const groups = [
      [awakening.needWeapon, 'sq', '#4FA3FF'],
      [awakening.needSkill, 'ci', '#FFD166'],
      [awakening.needDefense, 'tri', '#54E0A0'],
    ];
    let html = '';
    for (const [count, shape, color] of groups) {
      for (let i = 0; i < count; i++) {
        html += `<i class="needneed ${shape}" style="background:${color}"></i>`;
      }
    }
    return html;
  };
  const awakenCards = agent.awakenings.map((awakening, index) => {
    // 演示：觉醒1 与 觉醒3 激活（二觉/三觉二选一）
    const active = index !== 1;
    return `
    <div class="acard${active ? ' active' : ''}">
      <div class="aneed">${needShapes(awakening)}</div>
      <div class="atop">
        <span class="alabel">${t.awakening}${index + 1}：</span>
        <span class="status${active ? ' on' : ''}">${active ? t.activated : t.inactive}</span>
      </div>
      <div class="aname">${esc(awakening.name)}</div>
      <div class="adesc">${esc(awakening.desc)}</div>
    </div>`;
  }).join('');

  let tip = null;
  if (lang === 'zh') {
    outer:
    for (const group of agent.network) {
      for (const item of group.items) {
        if (item.change) { tip = item; break outer; }
      }
    }
  }
  const tipHtml = tip ? `
    <div class="tooltip">
      <div class="tt-title">${esc(tip.text)}</div>
      <div class="tt-cost"><em>${tip.cost}</em><i class="gem"></i></div>
      <div class="tt-change">${esc(tip.change)}</div>
    </div>` : '';

  return `<!DOCTYPE html><html lang="${lang}"><head><meta charset="utf-8"><style>${BASE_CSS}
  .head { position:absolute; left:0; right:0; top:0; height:62px; display:flex; align-items:center;
    padding:0 30px; background:linear-gradient(180deg, rgba(18,24,34,.97), rgba(12,18,26,.94)); }
  .head::after { content:''; position:absolute; left:0; right:0; bottom:0; height:1px;
    background:linear-gradient(90deg, rgba(53,214,232,.5), rgba(255,79,163,.4)); }
  .head .title { font-size:21px; font-weight:700; letter-spacing:.5px; }
  .head .title em { font-style:normal; color:${accent}; margin-left:4px; }
  .head .points { position:absolute; left:50%; transform:translateX(-50%); font-size:17px;
    color:#EAF2FA; display:flex; align-items:center; gap:7px; }
  .head .points b { font-size:23px; color:#fff; font-weight:800; }
  .head .ready { margin-left:auto; font-size:15px; color:#C8D4E0; }
  .head .close { width:38px; height:38px; margin-left:22px; display:flex; align-items:center;
    justify-content:center; font-size:23px; color:#EAF2FA; border:1px solid rgba(255,255,255,.28); }
  .main { position:absolute; left:18px; right:18px; top:80px; bottom:14px; display:flex; gap:16px; }
  .portrait { width:252px; flex:none; height:100%; position:relative; overflow:hidden;
    background:linear-gradient(165deg, ${accent}66 0%, #0e1621 58%, #080d14 100%);
    border:1px solid rgba(255,255,255,.14); }
  .portrait .giant { position:absolute; left:50%; top:40%; transform:translate(-50%,-50%);
    font-size:190px; font-weight:800; color:rgba(255,255,255,.14); line-height:1; }
  .portrait .pname { position:absolute; left:14px; bottom:66px; font-size:30px; font-weight:800;
    letter-spacing:2px; text-shadow:0 2px 12px #000; }
  .portrait .rolechip { position:absolute; left:14px; bottom:40px; display:flex; align-items:center;
    gap:6px; font-size:14px; font-weight:700; color:#EAF2FA; }
  .portrait .rolechip i { width:12px; height:12px; background:${accent}; transform:rotate(45deg); display:inline-block; }
  .portrait .stats { position:absolute; left:14px; right:10px; bottom:16px; font-size:12px;
    color:#9AA8B8; letter-spacing:.5px; }
  .rightSide { flex:1; min-width:0; display:flex; flex-direction:column; gap:10px; }
  .topArea { flex:1; min-height:0; display:flex; gap:14px; }
  .network { flex:1; min-height:0; display:grid; grid-template-columns:1fr 1fr; grid-template-rows:repeat(4,1fr);
    grid-auto-flow:column; gap:8px 22px; }
  .branch { padding:2px 2px 4px; }
  .bhead { display:flex; align-items:center; gap:8px; margin-bottom:6px; }
  .bicon { width:14px; height:14px; flex:none; display:inline-block; }
  .ico-diamond { transform:rotate(45deg) scale(.82); }
  .ico-circle { border-radius:50%; }
  .ico-shield { clip-path:polygon(50% 0, 100% 18%, 100% 62%, 50% 100%, 0 62%, 0 18%); }
  .ico-triangle { clip-path:polygon(50% 0, 100% 100%, 0 100%); }
  .bhead span { font-size:15px; font-weight:700; letter-spacing:.3px; }
  .refresh { margin-left:auto; width:23px; height:17px; flex:none; position:relative;
    background:linear-gradient(135deg, #7FE3A8, #3FA96F);
    clip-path:polygon(16% 0, 100% 0, 84% 100%, 0 100%); }
  .refresh.dim { opacity:.55; }
  .refresh::before { content:''; position:absolute; left:5px; top:5px; width:10px; height:8px;
    border:2px solid #fff; border-right-color:transparent; border-radius:50%; }
  .refresh::after { content:''; position:absolute; left:12px; top:2px; border-left:5px solid #fff;
    border-top:3.5px solid transparent; border-bottom:3.5px solid transparent; transform:rotate(18deg); }
  .bitems { display:flex; flex-direction:column; }
  .bitem { height:30px; display:flex; align-items:center; padding:0 10px;
    border-top:1px solid rgba(255,255,255,.055); background:rgba(0,0,0,.22); }
  .bitem:last-child { border-bottom:1px solid rgba(255,255,255,.055); }
  .bitem.grayed { opacity:.45; }
  .btext { font-size:13px; color:#D8E2EC; }
  .bprice { margin-left:auto; display:flex; align-items:center; gap:6px; }
  .bprice em { font-style:normal; font-size:14px; font-weight:700; color:#FF8A7A; }
  .bowned { margin-left:auto; font-size:12.5px; color:#B4C0CC; }
  .bitem.owned { background:rgba(120,170,220,.16); }
  .gem { width:9px; height:9px; background:#4FA3FF; display:inline-block; transform:rotate(45deg); }
  .weapons { width:186px; flex:none; display:flex; flex-direction:column; gap:8px; }
  .wcard { flex:1; position:relative; background:rgba(255,255,255,.045);
    border:1px solid rgba(255,255,255,.14); padding:5px 7px; }
  .wcard.sel { border-color:rgba(53,214,232,.75); background:rgba(53,214,232,.12); }
  .wrowlow { display:flex; gap:8px; flex:1; }
  .wrowlow .wcard { flex:1; }
  .wtop { display:flex; align-items:center; gap:6px; font-size:11px; color:#9AA8B8; }
  .wnum { font-weight:700; color:#C8D4E0; }
  .wicon { position:absolute; left:50%; top:42%; transform:translate(-50%,-50%); width:74px; height:32px; }
  .wicon::before { content:''; position:absolute; inset:0; background:#C8D4E0; opacity:.9; }
  .wicon.rifle::before { clip-path:polygon(0 42%, 78% 42%, 78% 30%, 92% 30%, 92% 55%, 70% 55%, 70% 78%, 46% 78%, 46% 55%, 0 55%); }
  .wicon.pistol::before { clip-path:polygon(4% 34%, 78% 34%, 78% 45%, 96% 45%, 96% 56%, 72% 56%, 72% 82%, 50% 82%, 50% 56%, 4% 56%); }
  .wicon.knife::before { clip-path:polygon(2% 92%, 72% 4%, 90% 22%, 20% 100%); }
  .wicon.grenade::before { clip-path:polygon(50% 12%, 92% 52%, 50% 92%, 8% 52%); }
  .wname { position:absolute; left:7px; bottom:5px; font-size:12px; color:#D8E2EC;
    max-width:112px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .swap { position:absolute; right:5px; bottom:4px; width:15px; height:10px; opacity:.9; }
  .swap::before { content:''; position:absolute; left:0; top:2px; width:11px; height:2px; background:#FFC94A; }
  .swap::after { content:''; position:absolute; left:0; top:0; border-right:4px solid #FFC94A;
    border-top:2px solid transparent; border-bottom:2px solid transparent; transform:rotate(180deg); }
  .awakenPanel { height:188px; flex:none; position:relative; display:flex; align-items:stretch;
    background:rgba(255,255,255,.035); border:1px solid rgba(255,255,255,.12); border-radius:5px;
    padding:27px 0 6px; }
  .awtoggle { position:absolute; right:8px; top:5px; z-index:3; height:19px; padding:0 8px;
    background:rgba(0,0,0,.55); border:1px solid rgba(255,255,255,.45); color:#EAF2FA;
    font-size:11px; font-weight:700; display:flex; align-items:center; gap:5px; font-family:inherit; }
  .awswap { width:12px; height:9px; position:relative; display:inline-block; }
  .awswap::before { content:''; position:absolute; left:0; top:1px; width:10px; height:2px; background:#EAF2FA; }
  .awswap::after { content:''; position:absolute; left:0; top:4px; width:10px; height:2px;
    background:#EAF2FA; transform:scaleX(-1); }
  .acard { flex:1; min-width:0; padding:0 12px; position:relative; }
  .acard + .acard { border-left:1px solid rgba(255,255,255,.10); }
  .aneed { display:flex; align-items:center; gap:4px; height:14px; }
  .needneed { width:9px; height:9px; display:inline-block; }
  .needneed.sq { border-radius:1px; }
  .needneed.ci { border-radius:50%; }
  .needneed.tri { clip-path:polygon(50% 0, 100% 100%, 0 100%); }
  .atop { display:flex; align-items:center; gap:6px; font-size:12px; color:#C8D4E0; margin-top:3px; }
  .status { padding:0 6px; border-radius:8px; font-size:10.5px; line-height:15px;
    background:rgba(255,255,255,.10); color:#9AA8B8; }
  .status.on { background:rgba(84,224,160,.18); color:#54E0A0; }
  .aname { font-size:15px; font-weight:800; margin:4px 0 3px; color:#EAF2FA; }
  .acard.active .aname { color:#FFD166; }
  .adesc { font-size:11.5px; line-height:1.5; color:#B4C0CC;
    display:-webkit-box; -webkit-line-clamp:3; -webkit-box-orient:vertical; overflow:hidden; }
  .tooltip { position:absolute; left:24px; top:96px; width:250px; background:#0C121A;
    border:1px solid #2A3542; padding:9px 11px; box-shadow:0 6px 26px rgba(0,0,0,.6); z-index:9; }
  .tooltip .tt-title { font-size:14px; font-weight:700; }
  .tooltip .tt-cost { font-size:13px; color:#FF8A7A; margin:5px 0 6px; display:flex; align-items:center; gap:6px; }
  .tooltip .tt-change { font-size:12.5px; line-height:1.6; color:#9FD0E8; border-top:1px solid #22303e; padding-top:6px; }
  </style></head><body><div class="screen">
    <div class="head">
      <div class="title">${t.growthTitle}<em>· ${esc(agent.name)}</em></div>
      <div class="points">${t.points}：<i class="gem" style="width:11px;height:11px"></i><b>330</b></div>
      <div class="ready">${t.ready}：00:13</div>
      <div class="close">✕</div>
    </div>
    <div class="main">
      <div class="portrait">
        <div class="giant">${esc(agent.name.slice(0, 1))}</div>
        <div class="pname">${esc(agent.name)}</div>
        <div class="rolechip"><i></i>${esc(agent.role)}</div>
        <div class="stats">${t.statHp} ${agent.stats.hp}　${t.statArmor} ${agent.stats.armor}　${t.statSpeed} ${agent.stats.moveSpeed}</div>
      </div>
      <div class="rightSide">
        <div class="topArea">
          <div class="network">${branches}</div>
          <div class="weapons">
            ${weaponCard(0)}
            ${weaponCard(1)}
            ${weaponCard(2)}
            <div class="wrowlow">${weaponCard(3)}${weaponCard(4)}</div>
          </div>
        </div>
        <div class="awakenPanel">
          <button class="awtoggle"><i class="awswap"></i>切换激活</button>
          ${awakenCards}
        </div>
      </div>
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
  const allAgents = zh.agents.map((item) => lang === 'en'
    ? ((en && en.agents.find((agent2) => agent2.id === item.id) || {}).name || item.name)
    : item.name);
  const skillOrder = ['active', 'passive', 'ultimate', 'tactical'];
  const keyLetters = ['Q', 'P', 'X', 'E'];
  const selected = agent.skills.active;
  const weaponTypeLabels = t.weaponSlots;

  return `<!DOCTYPE html><html lang="${lang}"><head><meta charset="utf-8"><style>${BASE_CSS}
  .map { position:absolute; left:24px; top:20px; display:flex; align-items:center; gap:9px; z-index:6; }
  .mapicon { width:34px; height:34px; border:1px solid rgba(255,255,255,.4);
    background:rgba(255,255,255,.12); }
  .map b { display:block; font-size:15px; letter-spacing:1px; text-shadow:0 1px 6px #000; }
  .map span { font-size:12px; color:#D8E2EC; opacity:.85; text-shadow:0 1px 6px #000; }
  .phasebar { position:absolute; left:152px; right:96px; top:16px; height:56px; display:flex; z-index:3; }
  .slab { width:330px; display:flex; align-items:center; justify-content:center; font-size:17px;
    font-weight:800; letter-spacing:2px; color:#fff; text-shadow:0 2px 8px #000; }
  .slab.ally { background:linear-gradient(90deg, #A8323D, rgba(168,50,61,.15)); clip-path:polygon(0 0, 100% 0, 93% 100%, 0 100%); }
  .slab.enemy { background:linear-gradient(270deg, #3B5FA8, rgba(59,95,168,.15)); clip-path:polygon(7% 0, 100% 0, 100% 100%, 0 100%); margin-left:auto; }
  .phasecenter { flex:1; margin:0 8px; background:rgba(12,18,26,.86); border:1px solid rgba(255,255,255,.18);
    display:flex; flex-direction:column; align-items:center; justify-content:center; gap:2px; }
  .phasebar .center .row1 { font-size:14px; color:#D8E2EC; letter-spacing:2px; }
  .phasebar .row2 { display:flex; align-items:baseline; gap:28px; }
  .phasebar .timer { font-size:25px; font-weight:800; color:#fff; letter-spacing:2px; }
  .phasebar .score { font-size:17px; color:#FF8A7A; }
  .phasebar .score.enemy { color:#6FA0FF; }
  .gear { position:absolute; right:28px; top:24px; width:30px; height:30px; border:1px solid rgba(255,255,255,.45);
    border-radius:50%; z-index:3; display:flex; align-items:center; justify-content:center; font-size:15px; color:#EAF2FA; }
  .team { position:absolute; left:26px; top:96px; width:224px; display:flex; flex-direction:column; gap:12px; z-index:3; }
  .mate { display:flex; align-items:center; gap:10px; min-height:44px; }
  .mate .avatar { width:40px; height:40px; flex:none; background:rgba(20,30,42,.9);
    border:1px solid rgba(255,255,255,.35); display:flex; align-items:center; justify-content:center;
    font-size:17px; color:#EAF2FA; }
  .mate.me .avatar { border-color:#FFD24A; box-shadow:0 0 0 1px #FFD24A55; }
  .mate .mname { font-size:15px; line-height:1.25; color:#fff; text-shadow:0 1px 8px #000; font-weight:700; }
  .mate.me .mname { color:#FFD24A; }
  .mate .mrole { font-size:11.5px; line-height:1.2; margin-top:2px; color:#C8D4E0; text-shadow:0 1px 6px #000; }
  .mate .arrow { width:18px; height:9px; margin-top:4px; opacity:.7;
    background:#EAF2FA; clip-path:polygon(0 0, 100% 50%, 0 100%); }
  .center { position:absolute; left:264px; right:432px; top:88px; bottom:156px;
    overflow:hidden; }
  .center .giant { position:absolute; left:50%; top:46%; transform:translate(-50%,-50%);
    font-size:280px; font-weight:800; color:rgba(255,255,255,.10); line-height:1; }
  .center .intro { position:absolute; left:26px; right:26px; bottom:86px; font-size:14px; line-height:1.65;
    color:#fff; text-shadow:0 2px 10px #000; }
  .center .intro .quote { margin-top:5px; font-size:12.5px; color:#E6EEF6; opacity:.9; font-style:italic; }
  .center .namebar { position:absolute; left:0; right:0; bottom:0; height:74px; padding:8px 20px;
    background:linear-gradient(0deg, rgba(5,8,13,.92), rgba(5,8,13,.15)); }
  .center .namebar .an { font-size:32px; font-weight:900; letter-spacing:2px; color:#fff; text-shadow:0 2px 10px #000; }
  .center .namebar .roleline { font-size:13px; color:#FFD24A; letter-spacing:1px; }
  .center .p1 { position:absolute; left:420px; top:-4px; font-size:10px; font-weight:800; color:#FFD24A;
    background:#2A2318; border:1px solid #FFD24A; padding:1px 5px; display:none; }
  .right { position:absolute; right:26px; top:88px; bottom:156px; width:390px;
    background:linear-gradient(180deg, rgba(16,22,30,.92), rgba(10,14,20,.9));
    border:1px solid rgba(255,255,255,.16); padding:0; display:flex; flex-direction:column; }
  .rhead { display:flex; align-items:center; gap:10px; height:56px; padding:0 12px;
    background:linear-gradient(90deg, rgba(120,86,20,.55), rgba(20,16,10,.25));
    border-bottom:1px solid rgba(255,209,102,.45); }
  .rhead .ricon { width:26px; height:26px; background:#FFD24A; transform:rotate(45deg); flex:none; }
  .rhead .rrole { font-size:13px; color:#FFD24A; letter-spacing:1px; }
  .rhead .rname { margin-left:auto; font-size:27px; font-weight:900; color:#fff; letter-spacing:2px;
    text-shadow:0 2px 10px #000; }
  .rbody { padding:10px 12px; display:flex; flex-direction:column; flex:1; min-height:0; }
  .identity { font-size:12.5px; color:#C8D4E0; }
  .identity b { color:#7f8c9a; font-weight:400; }
  .skills { margin-top:10px; display:flex; gap:8px; }
  .scard { width:52px; height:52px; border:1px solid rgba(255,255,255,.28); background:rgba(0,0,0,.35);
    display:flex; flex-direction:column; align-items:center; justify-content:center; gap:1px; position:relative; }
  .scard.sel { border-color:${accent}; background:${accent}33; }
  .scard .k { font-size:15px; font-weight:800; color:#EAF2FA; }
  .scard.sel .k { color:${accent}; }
  .scard .sname { font-size:8.5px; color:#9AA8B8; max-width:48px; text-align:center; line-height:1.1;
    display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; }
  .qline { margin-top:10px; font-size:14px; font-weight:800; color:#fff; }
  .qline em { font-style:normal; color:#FFD24A; }
  .qdesc { margin-top:4px; font-size:12.5px; line-height:1.55; color:#C8D4E0;
    display:-webkit-box; -webkit-line-clamp:3; -webkit-box-orient:vertical; overflow:hidden; }
  .wpanel { margin-top:12px; }
  .wmain { height:92px; position:relative; border:1px solid rgba(255,255,255,.2);
    background:rgba(255,255,255,.05); margin-bottom:6px; }
  .wmain .wtag { position:absolute; left:6px; top:4px; font-size:10.5px; color:#9AA8B8; }
  .wmain .wicon { position:absolute; left:50%; top:52%; transform:translate(-50%,-50%); width:186px; height:42px; }
  .wmain .wicon::before { content:''; position:absolute; inset:0; background:#DCE6F0; opacity:.92;
    clip-path:polygon(0 42%, 76% 42%, 76% 30%, 92% 30%, 92% 55%, 68% 55%, 68% 78%, 44% 78%, 44% 55%, 0 55%); }
  .wmain .wname { position:absolute; left:6px; bottom:4px; font-size:12px; color:#EAF2FA; }
  .wmain .wtype { position:absolute; right:6px; bottom:4px; font-size:11px; color:#9AA8B8; }
  .wgrid { display:grid; grid-template-columns:1fr 1fr; gap:6px; }
  .wcell { height:66px; position:relative; border:1px solid rgba(255,255,255,.16);
    background:rgba(255,255,255,.04); }
  .wcell .wtag { position:absolute; left:5px; top:3px; font-size:10px; color:#9AA8B8; }
  .wcell .wicon { position:absolute; left:50%; top:56%; transform:translate(-50%,-50%); width:64px; height:26px; }
  .wcell .wicon::before { content:''; position:absolute; inset:0; background:#C8D4E0; opacity:.9; }
  .wcell .wicon.pistol::before { clip-path:polygon(4% 34%, 78% 34%, 78% 45%, 96% 45%, 96% 56%, 72% 56%, 72% 82%, 50% 82%, 50% 56%, 4% 56%); }
  .wcell .wicon.knife::before { clip-path:polygon(2% 92%, 72% 4%, 90% 22%, 20% 100%); }
  .wcell .wicon.grenade::before { clip-path:polygon(50% 12%, 92% 52%, 50% 92%, 8% 52%); }
  .wcell .wname { position:absolute; left:5px; bottom:3px; font-size:10.5px; color:#C8D4E0; }
  .tabs { display:flex; gap:4px; margin-top:auto; padding-top:8px; }
  .tab { flex:1; height:26px; display:flex; align-items:center; justify-content:center; font-size:12.5px;
    border:1px solid rgba(255,255,255,.25); background:rgba(0,0,0,.35); color:#C8D4E0; }
  .tab.on { background:#EAF2FA; color:#10161e; font-weight:800; }
  .lockbtn { position:absolute; left:264px; right:432px; bottom:124px; height:30px; z-index:4;
    background:linear-gradient(90deg, rgba(170,40,48,.92), rgba(120,24,30,.92)); color:#fff;
    font-size:15px; font-weight:800; letter-spacing:4px; display:flex; align-items:center;
    justify-content:center; border:1px solid rgba(255,255,255,.25); }
  .grid { position:absolute; left:26px; right:432px; bottom:20px; height:92px; display:grid;
    grid-template-columns:repeat(12, 1fr); grid-template-rows:1fr 1fr; gap:5px 6px; }
  .cell { background:rgba(10,16,24,.82); border:1px solid rgba(255,255,255,.16); display:flex;
    align-items:center; justify-content:center; font-size:15px; color:#D8E2EC; position:relative; }
  .cell.sel { border-color:#FFD24A; background:rgba(60,52,26,.7); color:#fff; font-weight:800; }
  .cell.sel::before { content:'P1'; position:absolute; left:2px; top:0; font-size:8px; color:#FFD24A; }
  .notice { position:absolute; left:264px; right:432px; bottom:4px; text-align:center;
    font-size:11px; color:#FFB4B4; text-shadow:0 1px 6px #000; }
  </style></head><body><div class="screen">
    <div class="map"><div class="mapicon"></div><div><b>${t.map}</b><span>${t.mode}</span></div></div>
    <div class="phasebar">
      <div class="slab ally">${t.phaseAlly}</div>
      <div class="phasecenter">
        <div class="row1">${t.selectTitle}</div>
        <div class="row2"><span class="score">00</span><span class="timer">00:16</span><span class="score enemy">00</span></div>
      </div>
      <div class="slab enemy">${t.phaseEnemy}</div>
    </div>
    <div class="gear">⚙</div>
    <div class="team">
      ${teammate.map((mate, index) => `
        <div class="mate${index === 0 ? ' me' : ''}">
          <div class="avatar">${esc(mate.name.slice(0, 1))}</div>
          <div>
            <div class="mname">${esc(mate.name)}</div>
            <div class="mrole">${esc(mate.role)}</div>
            <div class="arrow"></div>
          </div>
        </div>`).join('')}
    </div>
    <div class="center">
      <div class="giant">${esc(agent.name.slice(0, 1))}</div>
      <div class="intro">${esc(agent.intro)}<div class="quote">「${esc(agent.quote)}」</div></div>
      <div class="namebar">
        <div class="an">${esc(agent.name)}</div>
        <div class="roleline">${esc(agent.role)} · ${esc(agent.faction)}</div>
      </div>
      <div class="p1">${t.p1}</div>
    </div>
    <div class="right">
      <div class="rhead"><div class="ricon"></div><div class="rrole">${esc(agent.role)}</div>
        <div class="rname">${esc(agent.name)}</div></div>
      <div class="rbody">
        <div class="identity"><b>${t.identity} ·</b> ${esc(agent.identity || '')}</div>
        <div class="skills">
          ${skillOrder.map((key, index) => `
            <div class="scard${index === 0 ? ' sel' : ''}">
              <div class="k">${keyLetters[index]}</div>
              <div class="sname">${esc(agent.skills[key].name)}</div>
            </div>`).join('')}
        </div>
        <div class="qline"><em>${esc(t.skillLabels.active)}</em> ${esc(selected.name)}</div>
        <div class="qdesc">${esc(selected.desc)}</div>
        <div class="wpanel">
          <div class="wmain">
            <span class="wtag">1 ${esc(weaponTypeLabels[0])}</span>
            <div class="wicon"></div>
            <span class="wname">${esc(agent.weapon.name)}</span>
            <span class="wtype">${esc(agent.weapon.type)}</span>
          </div>
          <div class="wgrid">
            ${[1, 2, 3, 4].map((index) => `
              <div class="wcell">
                <span class="wtag">${index + 1} ${esc(weaponTypeLabels[index])}${index >= 3 ? ' ' + (index - 2) : ''}</span>
                <div class="wicon ${index === 1 ? 'pistol' : index === 2 ? 'knife' : 'grenade'}"></div>
                <span class="wname">${esc(weaponTypeLabels[index])}</span>
              </div>`).join('')}
          </div>
        </div>
        <div class="tabs"><div class="tab on">${t.weaponTab}</div><div class="tab">${t.appearanceTab}</div></div>
      </div>
    </div>
    <div class="lockbtn">${t.lock}</div>
    <div class="grid">
      ${allAgents.map((name, index) => `<div class="cell${index === 0 ? ' sel' : ''}">${esc(name.slice(0, 1))}</div>`).join('')}
    </div>
    <div class="notice">${t.notice}</div>
    <div class="source">${t.source}</div>
  </div></body></html>`;
}

function starPolygon(cx, cy, outer, inner, points) {
  const parts = [];
  for (let i = 0; i < points * 2; i++) {
    const radius = i % 2 === 0 ? outer : inner;
    const angle = Math.PI * i / points - Math.PI / 2;
    parts.push((cx + Math.cos(angle) * radius).toFixed(1) + '% ' + (cy + Math.sin(angle) * radius).toFixed(1) + '%');
  }
  return 'polygon(' + parts.join(',') + ')';
}

/** TAB 计分板「成长等级」列效果预览（形色计数 > 八芒星）。 */
function growthChipHtml(lang) {
  const title = lang === 'zh' ? '成长等级' : 'Growth';
  const star = starPolygon(50, 50, 50, 21, 8);
  const shape = (cls, text) => `<div class="shape ${cls}"><span>${text}</span></div>`;
  return `<!DOCTYPE html><html lang="${lang}"><head><meta charset="utf-8"><style>
  * { box-sizing:border-box; margin:0; padding:0; }
  html,body { width:900px; height:170px; overflow:hidden; }
  body { background:#140d04; font-family:"Microsoft YaHei","Segoe UI",sans-serif; }
  .wrap { width:900px; height:170px; display:flex; flex-direction:column; align-items:center;
    justify-content:center; gap:10px;
    background:linear-gradient(90deg,#2a1f0c 0%,#7a6222 22%,#8a6f28 50%,#7a6222 78%,#2a1f0c 100%); }
  .title { font-size:17px; color:#F0DFB0; letter-spacing:2px; }
  .row { display:flex; align-items:center; gap:9px; }
  .shape { width:46px; height:46px; display:flex; align-items:center; justify-content:center; }
  .shape span { font-size:27px; font-weight:800; color:#fff; text-shadow:0 1px 3px rgba(0,0,0,.55); }
  .sq { background:#4FA3FF; border-radius:5px; }
  .ci { background:#F2B21B; border-radius:50%; }
  .tri { background:#4CAF50; clip-path:polygon(50% 0,100% 100%,0 100%); }
  .tri span { padding-top:12px; font-size:23px; }
  .gt { font-size:32px; font-weight:800; color:#F0DFB0; margin:0 2px; }
  .star { width:52px; height:52px; background:#E8833A; display:flex; align-items:center;
    justify-content:center; clip-path:${star}; }
  .star span { font-size:26px; font-weight:800; color:#fff; }
  </style></head><body><div class="wrap">
    <div class="title">${title}</div>
    <div class="row">
      ${shape('sq', 4)}${shape('ci', 1)}${shape('tri', 0)}
      <div class="gt">&gt;</div>
      <div class="star"><span>1</span></div>
    </div>
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
const runScreens = screens === 'all' ? ['growth', 'select', 'growth-chip'] : [screens];
const runLangs = langs === 'all' ? ['zh', 'en'] : [langs];

fs.mkdirSync(OUT, { recursive: true });
for (const screen of runScreens) {
  for (const lang of runLangs) {
    if (screen === 'growth-chip') {
      const html = growthChipHtml(lang);
      const file = path.join(OUT, `scoreboard-growth-real-${lang}.html`);
      fs.writeFileSync(file, html, 'utf8');
      console.log('written', path.relative(ROOT, file), html.length, 'bytes');
      continue;
    }
    const agent = screen === 'growth' ? agentData('nono', lang) : agentData('michele', lang);
    const html = screen === 'growth' ? growthHtml(agent, lang) : selectHtml(agent, lang);
    const file = path.join(OUT, `${screen}-real-${lang}.html`);
    fs.writeFileSync(file, html, 'utf8');
    console.log('written', path.relative(ROOT, file), html.length, 'bytes');
  }
}
