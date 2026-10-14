/* Pokemon Randomizer YAO, local web interface. Part of a fork of UPR-FVX (GPL-3.0-or-later). */
'use strict';

// ------------------------------------------------------------------ helpers
// append and replaceChildren turn null into the text "null"; the interface passes null for "nothing here", so skip those
(function () {
  const keep = (kids) => kids.flat().filter((k) => k !== null && k !== undefined && k !== false);
  for (const proto of [Element.prototype, DocumentFragment.prototype]) {
    const append = proto.append; const replace = proto.replaceChildren;
    proto.append = function (...kids) { return append.apply(this, keep(kids)); };
    proto.replaceChildren = function (...kids) { return replace.apply(this, keep(kids)); };
  }
})();

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

function h(tag, props, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v === false || v === null || v === undefined) continue;
    if (k === 'class') el.className = v;
    else if (k === 'html') el.innerHTML = v;
    else if (k === 'text') el.textContent = v;
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === 'style' && typeof v === 'object') Object.assign(el.style, v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat()) {
    if (kid === null || kid === undefined || kid === false) continue;
    el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

const ICON = {
  dice: '<svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><rect x="13" y="2" width="9" height="9" rx="2"/><rect x="2" y="12" width="10" height="10" rx="2"/><g fill="currentColor" stroke="none"><circle cx="15.4" cy="4.4" r="1"/><circle cx="17.5" cy="6.5" r="1"/><circle cx="19.6" cy="8.6" r="1"/><circle cx="4.6" cy="14.6" r="1"/><circle cx="9.4" cy="14.6" r="1"/><circle cx="7" cy="17" r="1"/><circle cx="4.6" cy="19.4" r="1"/><circle cx="9.4" cy="19.4" r="1"/></g></svg>',
  search: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg>',
  folder: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M3 6a2 2 0 012-2h4l2 2h8a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2z"/></svg>',
  rom: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 7h8M8 11h8M8 15h4"/></svg>',
  plus: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
  x: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>',
  play: '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M7 4l13 8-13 8z"/></svg>',
  up: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5M5 12l7-7 7 7"/></svg>',
};
const icon = (name) => h('span', { html: ICON[name], style: { display: 'inline-flex' } });

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtPct = (n) => (Math.round(n * 10) / 10).toString();
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const sameJson = (a, b) => JSON.stringify(a) === JSON.stringify(b);

function safeStorage(op, key, value) {
  try {
    if (op === 'get') return window.localStorage.getItem(key);
    if (op === 'set') window.localStorage.setItem(key, value);
    if (op === 'del') window.localStorage.removeItem(key);
  } catch (e) { /* storage can be blocked, the page works without it */ }
  return null;
}

function toast(message, kind = 'info', ms = 4800) {
  const t = h('div', { class: 'toast ' + kind, text: message });
  $('#toasts').append(t);
  setTimeout(() => t.remove(), ms);
}

// ------------------------------------------------------------------ api
const TOKEN = (() => {
  const fromUrl = new URLSearchParams(location.search).get('t');
  if (fromUrl) {
    try { sessionStorage.setItem('yao.token', fromUrl); } catch (e) { /* ignore */ }
    history.replaceState(null, '', location.pathname);
    return fromUrl;
  }
  try { return sessionStorage.getItem('yao.token') || ''; } catch (e) { return ''; }
})();

async function api(path, body) {
  const opts = { headers: { 'X-YAO-Token': TOKEN } };
  if (body !== undefined) {
    opts.method = 'POST';
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  }
  let res;
  try { res = await fetch(path, opts); } catch (e) { throw new Error('The program is not answering. Is its window still open?'); }
  let data = {};
  try { data = await res.json(); } catch (e) { /* not json */ }
  if (res.status === 401) throw new Error('This page was opened without the launcher. Start the program again and use the address it prints.');
  if (data && data.ok === false && data.error) {
    const err = new Error(data.error);
    err.data = data;
    throw err;
  }
  return data;
}

// Added (11/10/2026): streams a dropped file's bytes to the server, which saves it to a real path on this
// machine and hands that path back. A browser never exposes a dropped file's actual filesystem path (only its
// name and content), but the randomizer works with paths, so this is how drag-and-drop gets it one. `body: file`
// lets fetch() stream the File object straight to the network without first reading it into a JS byte array or
// string, which matters here since ROMs (especially decrypted 3DS dumps) can be several gigabytes.
async function uploadFile(file) {
  let res;
  try {
    res = await fetch('/api/upload', {
      method: 'POST',
      headers: { 'X-YAO-Token': TOKEN, 'X-File-Name': encodeURIComponent(file.name), 'Content-Type': 'application/octet-stream' },
      body: file,
    });
  } catch (e) { throw new Error('The program is not answering. Is its window still open?'); }
  let data = {};
  try { data = await res.json(); } catch (e) { /* not json */ }
  if (res.status === 401) throw new Error('This page was opened without the launcher. Start the program again and use the address it prints.');
  if (data && data.ok === false && data.error) throw new Error(data.error);
  return data;
}

// Added (11/10/2026): opens the OS's own file dialog on the server (this is a local-only program, so the server
// and the browser are the same machine), replacing the old in-browser custom file picker the user reported
// showed broken paths. `mode` is 'open' or 'save'; `filter` is 'rom', 'png' or '' for none. Falls back to telling
// the caller to use the plain manual path text field (always present next to every "Browse" button) when no
// display is available, for example a genuinely headless/remote run of this program.
async function nativeBrowse({ mode, filter, title, startDir, onPick }) {
  let data;
  try {
    data = await api('/api/native-dialog', { mode: mode || 'open', filter: filter || '', title: title || '', startDir: startDir || '' });
  } catch (e) { toast(e.message, 'bad'); return; }
  if (!data.ok) { toast((data.error || 'The native file dialog could not be opened.') + ' Type the path in the field instead.', 'bad', 8000); return; }
  if (data.path) onPick(data.path);
}

// Added (13/10/2026, web UI feedback): a plain `input.value = path` leaves the text field scrolled to its start,
// so a long path only ever shows its beginning ("C:\Users\..."), the part that is the same for every file, instead
// of the end that actually tells folders and files apart. Called right after setting the value of any path field.
function showPathEnd(input) {
  input.scrollLeft = input.scrollWidth;
  try { input.setSelectionRange(input.value.length, input.value.length); } catch (e) { /* not a text-like input */ }
}

// ------------------------------------------------------------------ state
const S = {
  info: null, rom: null, schema: null, defaults: {}, values: {}, yao: null,
  items: [], species: [], moves: [], evoTypes: [],
  view: 'game', section: 'general', search: '', openGroups: new Set(),
  job: null, run: { output: '', seed: '', guide: false, log: true, consent: false },
};

function defaultYao() {
  return {
    shinyPercent: 0, locke: 'none', keepBasicShopItems: false, protectedItems: [], shopItems: [],
    boostFloor: 0, boostPercent: 0, statEdits: [], megaFinale: { gymLeaders: false, league: false, evilBosses: false, otherBosses: false, everyone: false, replace: false, fromLevel: 0 }, battleStyleRules: [], playerSprites: { male: '', female: '' }, evolutions: [], removals: [], evolutionScale: { enabled: false, minLevel: 30, percent: 20 }, fullyEvolvedByLevel: 0, stoneEvolutionsAlsoLevel: false,
    incrementalMovesets: true, trainerItems: 'tiered', regularTrainersKeepItems: false,
    eggBox: { count: 0, legendary: 'any', stages: [], basicOnly: false, duplicates: false },
  };
}

function persist() {
  safeStorage('set', 'yao.state.v1', JSON.stringify({ values: S.values, yao: S.yao, run: { seed: S.run.seed, guide: S.run.guide, log: S.run.log } }));
}

function restore() {
  const raw = safeStorage('get', 'yao.state.v1');
  if (!raw) return;
  try {
    const saved = JSON.parse(raw);
    for (const [k, v] of Object.entries(saved.values || {})) if (k in S.defaults) S.values[k] = v;
    if (saved.yao) { S.yao = Object.assign(defaultYao(), saved.yao); S.yao.megaFinale = Object.assign(defaultYao().megaFinale, S.yao.megaFinale || {}); if (!Array.isArray(S.yao.battleStyleRules)) S.yao.battleStyleRules = []; }
    if (saved.run) Object.assign(S.run, saved.run);
  } catch (e) { /* damaged saved state is ignored */ }
}

function isHiddenField(key) { return !!(S.schema && (S.schema.hiddenFields || []).includes(key)); }
function isHiddenGroup(id) { return !!(S.schema && (S.schema.hiddenGroups || []).includes(id)); }

function allFields() {
  const out = [];
  if (!S.schema) return out;
  for (const sec of S.schema.sections) for (const g of sec.groups) { if (g.main) out.push(g.main); out.push(...g.fields); }
  return out;
}

function changedCount(section) {
  let n = 0;
  for (const sec of S.schema.sections) {
    if (section && sec.id !== section) continue;
    for (const g of sec.groups) for (const f of [g.main, ...g.fields]) {
      if (f && f.key in S.defaults && !isHiddenGroup(g.id) && !isHiddenField(f.key) && !sameJson(S.values[f.key], S.defaults[f.key])) n++;
    }
  }
  return n;
}

// rows that are still half filled in are not sent and do not count
function cleanYao() {
  const y = JSON.parse(JSON.stringify(S.yao));
  y.shopItems = y.shopItems.filter((i) => i.id > 0);
  y.protectedItems = y.protectedItems.filter((i) => i > 0);
  y.statEdits = y.statEdits.filter((e) => e.species > 0);
  y.removals = y.removals.filter((r) => r.from > 0 && r.to > 0);
  y.evolutions = y.evolutions.filter((e) => e.from > 0 && e.to > 0);
  // Added (11/10/2026, web UI feedback item 28): the evolution level scale now has its own explicit toggle
  // instead of implicitly being "off" only at 0%, so a disabled toggle must zero the percentage sent to the
  // server too, the same way a disabled checkbox's sub-options are left out everywhere else in this interface.
  if (!y.evolutionScale.enabled) y.evolutionScale.percent = 0;
  return y;
}

function megaAny(m) { return !!(m.gymLeaders || m.league || m.evilBosses || m.otherBosses || m.everyone); }

function yaoCount() {
  const y = cleanYao(); let n = 0;
  if (y.shinyPercent > 0) n++;
  if (y.locke !== 'none') n++;
  if (y.shopItems.length) n++;
  if (y.keepBasicShopItems || y.protectedItems.length) n++;
  if (y.boostFloor > 0 || y.boostPercent > 0 || y.statEdits.length) n++;
  if (y.evolutions.length || y.removals.length || y.evolutionScale.percent > 0 || y.fullyEvolvedByLevel > 0 || y.stoneEvolutionsAlsoLevel) n++;
  if (y.eggBox.count > 0) n++;
  if (y.incrementalMovesets === false || y.trainerItems === 'unchanged' || y.regularTrainersKeepItems) n++;
  if (megaAny(y.megaFinale) && S.rom.features.megas) n++;
  if (y.battleStyleRules.length && S.rom.features.battleStyles && S.rom.features.battleStyles.length) n++;
  if ((y.playerSprites.male || y.playerSprites.female) && S.rom.features.playerSprites.available) n++;
  return n;
}

// ------------------------------------------------------------------ tooltips
const tip = { el: null, timer: null };
function showTip(target, text) {
  if (!text) return;
  const el = tip.el || (tip.el = $('#tip'));
  el.textContent = text;
  el.classList.add('show');
  const r = target.getBoundingClientRect();
  const w = el.offsetWidth, hgt = el.offsetHeight;
  let x = r.left + r.width / 2 - w / 2;
  let y = r.bottom + 10;
  if (y + hgt > innerHeight - 8) y = r.top - hgt - 10;
  el.style.left = clamp(x, 8, innerWidth - w - 8) + 'px';
  el.style.top = clamp(y, 8, innerHeight - hgt - 8) + 'px';
}
function hideTip() { (tip.el || $('#tip')).classList.remove('show'); }
// Added (12/10/2026, web UI feedback round 3): settings the upstream project documents on its wiki (which
// evolutions change, which items count as bad, how "similar strength" is decided...) open that page when the
// "i" is clicked, and say so in the tooltip. Settings with no wiki page behave exactly as before. The map is
// keyed by the native setting key; every anchor was checked against the live upstream site.
const WIKI_BASE = 'https://upr-fvx.github.io/universal-pokemon-randomizer-fvx/wikipages/';
const WIKI = (() => {
  const m = {};
  const set = (keys, page) => keys.forEach((k) => { m[k] = WIKI_BASE + page; });
  set(['changeImpossibleEvolutions'], 'evolutions.html#change-impossible-evolutions');
  set(['makeEvolutionsEasier', 'makeEvolutionsEasierLvl'], 'evolutions.html#make-evolutions-easier');
  set(['removeTimeBasedEvolutions'], 'evolutions.html#remove-time-based-evolutions');
  set(['bstFollowEvolutions', 'baseStatsFollowEvolutions', 'abilitiesFollowEvolutions', 'tmsFollowEvolutions', 'tutorFollowEvolutions'], 'evolutions.html#follow-evolutions-settings');
  set(['estimateLevelForEvolutionImprovements'], 'estimatedLevels.html');
  set(['updateMoves', 'updateMovesToGeneration'], 'moves.html#updated-moves');
  set(['updateBaseStats', 'updateBaseStatsToGeneration'], 'pokemon_base_statistics.html#updated-base-stats');
  set(['movesetsForceGoodDamaging', 'movesetsGoodDamagingPercent', 'tmsForceGoodDamaging', 'tmsGoodDamagingPercent', 'tutorsForceGoodDamaging', 'tutorsGoodDamagingPercent'], 'movesets.html#good-damaging-moves');
  set(['trainersUsePokemonOfSimilarStrength', 'similarStrengthEncounters', 'evosSimilarStrength'], 'pokemon_base_statistics.html#similar-strength');
  set(['limitMainGameLegendaries'], 'static_pokemon.html#limit-main-game-legendaries');
  set(['useMinimumCatchRate', 'minimumCatchRateLevel'], 'wild_pokemon.html#minimum-catch-rate-odds');
  set(['banBadRandomFieldItems', 'banBadRandomShopItems', 'banBadRandomPickupItems'], 'items.html#bad-items');
  set(['randomizeMoveNames'], 'new_feature_summary.html#move-name-randomization');
  set(['battleStyle'], 'new_feature_summary.html#battle-style-randomization');
  set(['typeEffectivenessMod'], 'new_feature_summary.html#type-effectiveness-randomization');
  set(['pokemonPalettesMod'], 'new_feature_summary.html#pokemon-palette-randomization');
  set(['trainersUseLocalPokemon'], 'new_feature_summary.html#use-local-pokemon');
  return m;
})();

function infoButton(text, wiki) {
  if (!text && !wiki) return null;
  // Renamed from the generic "info" class (bug fix, 11/10/2026): it collided with toast(message, 'info'),
  // whose toast elements are also styled with a bare ".info" class token, which squashed the toast into
  // this tiny 18x18 round button shape instead of the normal toast box. See .infobtn in app.css.
  const shown = wiki ? (text ? text + '\n\n' : '') + 'Click the "i" to open the wiki page that explains this in detail.' : text;
  const b = h('button', { class: 'infobtn' + (wiki ? ' wiki' : ''), type: 'button', 'aria-label': wiki ? 'More information (opens the wiki)' : 'More information', text: 'i' });
  b.addEventListener('mouseenter', () => showTip(b, shown));
  b.addEventListener('mouseleave', hideTip);
  b.addEventListener('focus', () => showTip(b, shown));
  b.addEventListener('blur', hideTip);
  b.addEventListener('click', (e) => {
    e.preventDefault();
    if (wiki) window.open(wiki, '_blank', 'noopener');
    else showTip(b, shown);
  });
  return b;
}

// ------------------------------------------------------------------ effects (background, cursor, card spotlight and tilt)
const FX = { on: true, mx: -999, my: -999, rx: -999, ry: -999 };

function initEffects() {
  const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const saved = safeStorage('get', 'yao.fx');
  FX.on = saved === null ? !reduced : saved === '1';
  applyFxFlag();
  $('#fxBtn').addEventListener('click', () => {
    FX.on = !FX.on; safeStorage('set', 'yao.fx', FX.on ? '1' : '0'); applyFxFlag();
    toast(FX.on ? 'Mouse effects on' : 'Mouse effects off');
  });

  const ring = $('.cursor-ring'), dot = $('.cursor-dot');
  window.addEventListener('mousemove', (e) => { FX.mx = e.clientX; FX.my = e.clientY; if (FX.rx < -500) { FX.rx = e.clientX; FX.ry = e.clientY; } });
  window.addEventListener('mousedown', () => ring.classList.add('down'));
  window.addEventListener('mouseup', () => ring.classList.remove('down'));
  document.addEventListener('mouseover', (e) => {
    const hot = e.target.closest && e.target.closest('button, a, input, select, textarea, label.switch, .listrow, .pickbtn');
    ring.classList.toggle('hot', !!hot);
  });

  // spotlight on cards and a gentle tilt on the ones marked as tilt
  document.addEventListener('mousemove', (e) => {
    const card = e.target.closest && e.target.closest('.card, .drop');
    if (!card) return;
    const r = card.getBoundingClientRect();
    card.style.setProperty('--mx', (e.clientX - r.left) + 'px');
    card.style.setProperty('--my', (e.clientY - r.top) + 'px');
    if (FX.on && card.classList.contains('tilt')) {
      const px = (e.clientX - r.left) / r.width - 0.5, py = (e.clientY - r.top) / r.height - 0.5;
      card.style.transform = `perspective(900px) rotateX(${(-py * 5).toFixed(2)}deg) rotateY(${(px * 6).toFixed(2)}deg) translateZ(0)`;
    }
  });
  document.addEventListener('mouseout', (e) => {
    const card = e.target.closest && e.target.closest('.card.tilt');
    if (card && !card.contains(e.relatedTarget)) card.style.transform = '';
  });

  // ripple on buttons
  document.addEventListener('click', (e) => {
    const b = e.target.closest && e.target.closest('.btn');
    if (!b || !FX.on) return;
    const r = b.getBoundingClientRect();
    const d = Math.max(r.width, r.height);
    const rip = h('span', { class: 'ripple', style: { width: d + 'px', height: d + 'px', left: (e.clientX - r.left - d / 2) + 'px', top: (e.clientY - r.top - d / 2) + 'px' } });
    b.append(rip); setTimeout(() => rip.remove(), 650);
  });

  // background field of points that bend toward the pointer
  const canvas = $('#bg'), ctx = canvas.getContext && canvas.getContext('2d');
  if (!ctx) return;
  let W = 0, H = 0, pts = [], dpr = 1, last = 0;
  const GAP = 44;
  const resize = () => {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    W = canvas.width = Math.floor(innerWidth * dpr); H = canvas.height = Math.floor(innerHeight * dpr);
    canvas.style.width = innerWidth + 'px'; canvas.style.height = innerHeight + 'px';
    pts = [];
    for (let y = GAP / 2; y < innerHeight + GAP; y += GAP) for (let x = GAP / 2; x < innerWidth + GAP; x += GAP) {
      pts.push({ x, y, ox: x, oy: y, ph: Math.random() * 6.28 });
    }
  };
  resize();
  window.addEventListener('resize', resize);
  const frame = (t) => {
    requestAnimationFrame(frame);
    if (document.hidden) return;
    if (t - last < 30) return; // about 30 frames per second is plenty
    last = t;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, innerWidth, innerHeight);
    FX.rx += (FX.mx - FX.rx) * 0.18; FX.ry += (FX.my - FX.ry) * 0.18;
    const ring = $('.cursor-ring'), dotEl = $('.cursor-dot');
    if (FX.on) {
      ring.style.transform = `translate3d(${FX.rx}px,${FX.ry}px,0)`;
      dotEl.style.transform = `translate3d(${FX.mx}px,${FX.my}px,0)`;
    }
    const light = document.documentElement.dataset.theme === 'light';
    const R = 190;
    for (const p of pts) {
      const dx = FX.on ? FX.rx - p.ox : 9999, dy = FX.on ? FX.ry - p.oy : 9999;
      const d = Math.hypot(dx, dy);
      let k = 0;
      if (d < R) { k = (1 - d / R); p.x = p.ox + dx * k * 0.22; p.y = p.oy + dy * k * 0.22; } else { p.x += (p.ox - p.x) * 0.2; p.y += (p.oy - p.y) * 0.2; }
      const breathe = FX.on ? (Math.sin(t / 1400 + p.ph) + 1) * 0.5 : 0.5;
      const a = light ? 0.12 + breathe * 0.1 + k * 0.6 : 0.10 + breathe * 0.1 + k * 0.75;
      const size = 1.1 + k * 2.6;
      ctx.fillStyle = k > 0.01 ? (k > 0.55 ? `rgba(255,63,168,${a})` : `rgba(38,230,255,${a})`) : (light ? `rgba(70,90,190,${a})` : `rgba(124,146,255,${a})`);
      ctx.beginPath(); ctx.arc(p.x, p.y, size, 0, 6.2832); ctx.fill();
    }
    // a glow under the pointer
    if (FX.on && FX.rx > -500) {
      const g = ctx.createRadialGradient(FX.rx, FX.ry, 0, FX.rx, FX.ry, 260);
      g.addColorStop(0, light ? 'rgba(10,143,181,0.10)' : 'rgba(38,230,255,0.10)'); g.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = g; ctx.fillRect(FX.rx - 260, FX.ry - 260, 520, 520);
    }
  };
  requestAnimationFrame(frame);
}
function applyFxFlag() { document.body.classList.toggle('no-cursor-fx', !FX.on); }

// ------------------------------------------------------------------ modal and pickers
function openModal({ title, body, footer, onClose, wide }) {
  const root = $('#modal-root');
  const close = () => { back.remove(); document.removeEventListener('keydown', onKey); if (onClose) onClose(); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  const back = h('div', { class: 'modal-back', onclick: (e) => { if (e.target === back) close(); } });
  const modal = h('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true', 'aria-label': title, style: wide ? { width: 'min(980px,100%)' } : null },
    h('header', null, h('h2', { text: title }), h('button', { class: 'icon-btn', 'aria-label': 'Close', onclick: close }, icon('x'))),
    h('div', { class: 'body' }, body),
    footer ? h('footer', null, footer(close)) : null);
  back.append(modal);
  root.append(back);
  document.addEventListener('keydown', onKey);
  const first = $('input, button.listrow', modal); if (first) first.focus();
  return { close, modal };
}

function openPicker({ title, items, label, sub, onPick, placeholder }) {
  const input = h('input', { type: 'search', placeholder: placeholder || 'Search', 'aria-label': 'Search' });
  const list = h('div', { class: 'stack', style: { gap: '2px', marginTop: '10px' } });
  let handle;
  const draw = () => {
    const q = input.value.trim().toLowerCase();
    list.replaceChildren();
    let shown = 0;
    for (const it of items) {
      const text = label(it);
      if (q && !text.toLowerCase().includes(q) && !String(sub ? sub(it) : '').toLowerCase().includes(q)) continue;
      list.append(h('button', { class: 'listrow', type: 'button', onclick: () => { onPick(it); handle.close(); } }, text, sub ? h('span', { class: 'sub', text: sub(it) }) : null));
      if (++shown >= 250) { list.append(h('div', { class: 'muted', style: { padding: '8px 12px' }, text: 'Keep typing to narrow the list.' })); break; }
    }
    if (!shown) list.append(h('div', { class: 'muted', style: { padding: '8px 12px' }, text: 'Nothing matches.' }));
  };
  input.addEventListener('input', draw);
  handle = openModal({ title, body: h('div', null, input, list) });
  draw();
  input.focus();
}

function speciesName(n) { const s = S.species.find((x) => x.n === n); return s ? s.name : '#' + n; }
function itemName(id) { const s = S.items.find((x) => x.id === id); return s ? s.name : '#' + id; }
function moveName(id) { const s = S.moves.find((x) => x.id === id); return s ? s.name : '#' + id; }

function speciesButton(value, onPick, placeholder) {
  const b = h('button', { class: 'pickbtn', type: 'button' }, h('span', { class: value ? '' : 'ph', text: value ? speciesName(value) : (placeholder || 'Choose a Pokemon') }), h('span', { class: 'faint', text: '▾' }));
  b.addEventListener('click', () => openPicker({ title: 'Choose a Pokemon', items: S.species, label: (s) => s.name, sub: (s) => '#' + s.n, onPick: (s) => onPick(s.n), placeholder: 'Search by name or number' }));
  return b;
}
// Added (11/10/2026, web UI feedback item 29): `stoneOnly` narrows the picker to this game's own real evolution
// stones (RomSession collects them from the game's own vanilla STONE-family evolutions, see "stone" on each
// item from /api/items), used for the "stone" evolution method so it cannot offer an ordinary consumable.
function itemButton(value, onPick, placeholder, stoneOnly) {
  const pool = stoneOnly ? S.items.filter((i) => i.stone) : S.items;
  const b = h('button', { class: 'pickbtn', type: 'button' }, h('span', { class: value ? '' : 'ph', text: value ? itemName(value) : (placeholder || 'Choose an item') }), h('span', { class: 'faint', text: '▾' }));
  b.addEventListener('click', () => openPicker({ title: stoneOnly ? 'Choose an evolution stone' : 'Choose an item', items: pool, label: (s) => s.name, sub: (s) => '#' + s.id, onPick: (s) => onPick(s.id), placeholder: 'Search by name or number' }));
  return b;
}
function moveButton(value, onPick) {
  const b = h('button', { class: 'pickbtn', type: 'button' }, h('span', { class: value ? '' : 'ph', text: value ? moveName(value) : 'Choose a move' }), h('span', { class: 'faint', text: '▾' }));
  b.addEventListener('click', () => openPicker({ title: 'Choose a move', items: S.moves, label: (s) => s.name, sub: (s) => '#' + s.id, onPick: (s) => onPick(s.id), placeholder: 'Search by name or number' }));
  return b;
}

// ------------------------------------------------------------------ navigation
function go(view) {
  S.view = view;
  $$('.view').forEach((v) => v.classList.toggle('active', v.id === 'view-' + view));
  $$('#steps button').forEach((b) => b.classList.toggle('active', b.dataset.view === view));
  const render = { game: renderGame, rand: renderRandomizer, run: renderRun }[view];
  if (render) render();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function enableSteps() {
  $$('#steps button').forEach((b) => { b.disabled = b.dataset.view !== 'game' && !S.rom; });
}

// ------------------------------------------------------------------ view 1: game
function renderGame() {
  const root = $('#view-game');
  root.replaceChildren();
  const recent = (() => { try { return JSON.parse(safeStorage('get', 'yao.recent') || '[]'); } catch (e) { return []; } })();

  if (!S.rom) {
    const pathInput = h('input', { type: 'text', placeholder: 'Path of your game file, for example C:\\roms\\SoulSilver.nds', 'aria-label': 'Path of the game', value: recent[0] || '' });
    const loadFromInput = () => loadRom(pathInput.value.trim());
    pathInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') loadFromInput(); });
    // Added (11/10/2026): replaces the old in-browser custom file browser (reported broken) with the OS's own
    // file dialog, opened server side, plus dragging a ROM file straight onto this zone. The manual path field
    // above/below always works too, including when no display is available for the native dialog.
    const dropZone = h('div', { class: 'drop', style: { marginTop: '28px' } },
      h('div', { class: 'stack', style: { alignItems: 'center', width: 'min(640px,100%)' } },
        h('div', { class: 'game-name', text: 'Choose your game' }),
        h('div', { class: 'muted', text: 'Game Boy, Game Boy Color, Game Boy Advance, DS and 3DS (decrypted .3ds or .cxi) games' }),
        h('div', { class: 'muted', style: { fontSize: '13px' }, text: 'Drag a game file here, or:' }),
        h('button', { class: 'btn primary big', type: 'button', onclick: () => nativeBrowse({ mode: 'open', filter: 'rom', startDir: recent[0] ? recent[0].replace(/[\\/][^\\/]*$/, '') : '', title: 'Choose a game', onPick: (p) => loadRom(p) }) }, icon('folder'), 'Browse for a game'),
        h('div', { class: 'row', style: { width: '100%', marginTop: '6px' } }, pathInput, h('button', { class: 'btn', onclick: loadFromInput, text: 'Load' })),
        recent.length ? h('div', { class: 'row wrap', style: { justifyContent: 'center' } }, h('span', { class: 'faint', text: 'Recent:' }), ...recent.slice(0, 4).map((p) => h('button', { class: 'chip', type: 'button', title: p, onclick: () => loadRom(p), text: p.split(/[\\/]/).pop() }))) : null,
      ));
    ['dragenter', 'dragover'].forEach((evt) => dropZone.addEventListener(evt, (e) => { e.preventDefault(); dropZone.classList.add('dragging'); }));
    ['dragleave', 'dragend'].forEach((evt) => dropZone.addEventListener(evt, () => dropZone.classList.remove('dragging')));
    dropZone.addEventListener('drop', async (e) => {
      e.preventDefault();
      dropZone.classList.remove('dragging');
      const file = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
      if (!file) return;
      root.replaceChildren(h('div', { class: 'card stack', style: { alignItems: 'center', padding: '60px 20px' } },
        h('div', { class: 'big-number', text: 'Copying ' + file.name + '…' }),
        h('div', { class: 'progress', style: { width: '260px' } }, h('i')),
        h('div', { class: 'muted', text: 'Big 3DS games can take a little while to copy.' })));
      try {
        const up = await uploadFile(file);
        await loadRom(up.path);
      } catch (err) {
        toast(err.message, 'bad', 8000);
        renderGame();
      }
    });
    root.append(
      h('h1', { class: 'hero', html: 'Randomize your <em>Pokemon game</em>.' }),
      h('p', { class: 'lead', text: 'Gen 1 to Gen 7, English and Spanish, with real shiny chances, egg boxes, Locke rules (experimental), your own shop items, stat boosts and editable evolutions. Everything runs on your computer and your original game file is never changed.' }),
      dropZone,
      h('div', { class: 'grid three', style: { marginTop: '22px' } },
        h('div', { class: 'card tilt' }, h('h3', { text: '3DS games' }), h('p', { class: 'muted', text: 'X, Y, Omega Ruby, Alpha Sapphire, Sun, Moon, Ultra Sun and Ultra Moon must be decrypted dumps. They need about 6 GB of memory, which the launcher asks Java for.' })),
        h('div', { class: 'card tilt' }, h('h3', { text: 'English and Spanish' }), h('p', { class: 'muted', text: 'Names of Pokemon, items and moves always come from your game, so everything works in the language of the file you choose.' })),
        h('div', { class: 'card tilt' }, h('h3', { text: 'Nothing leaves your computer' }), h('p', { class: 'muted', text: 'This page talks only to the program on your own machine, including a game dropped onto it. No game data is uploaded anywhere else.' }))),
    );
    return;
  }

  const g = S.rom;
  const f = g.features;
  const feature = (name, a, onText, offText) => h('div', { class: 'feature' }, h('span', { class: 'dot ' + (a.available ? 'on' : 'off') }),
    h('div', null, h('b', { text: name }), h('div', { class: 'muted', style: { fontSize: '13.5px' }, text: a.available ? onText : (a.reason || offText) })));
  root.append(
    h('h1', { class: 'hero', html: g.name ? esc(g.name) + ' <em>is ready</em>.' : 'Your game <em>is ready</em>.' }),
    h('div', { class: 'row wrap', style: { margin: '6px 0 22px' } },
      h('span', { class: 'pill cyan', text: 'Generation ' + g.generation }),
      /^[A-Z0-9]{4}$/.test(g.code || '') ? h('span', { class: 'pill', text: g.code }) : null,
      h('span', { class: 'pill', text: g.english ? 'English text' : 'Other language' }),
      h('span', { class: 'pill', text: '.' + g.extension })),
    !g.integrity.clean ? h('div', { class: 'notice', style: { marginBottom: '16px', whiteSpace: 'pre-wrap' } }, h('b', { text: 'This game file is not an unmodified retail dump. ' }), g.integrity.text) : h('span'),
    h('div', { class: 'grid two' },
      h('div', { class: 'card tilt stack' },
        h('h2', { text: 'What this game can do' }),
        feature('Real shiny chance', f.shiny, 'Any percentage from 0.1 to 100 for wild, static, gift, egg and trainer Pokemon.', 'Not available for this game.'),
        feature('Locke (experimental): Pokemon Centers skip fainted Pokemon', f.lockeCenters, 'Pokemon Centers and whiting out no longer revive fainted Pokemon.', 'Not available for this game. The item part of Locke still works.'),
        feature('Egg box', f.eggBox, 'The game hands you a box of randomized eggs.', 'Not available for this game yet.'),
        feature('Extra shop items', { available: true, reason: '' }, f.shopsResizable ? 'Your extra items are added to the shops, with the price you choose.' : 'Your extra items are put in the shops with the price you choose. This game\'s shops have a fixed number of slots, so each extra item takes the place of a less important one (extra items never replace everyday supplies).', ''),
        feature('Custom evolutions', { available: f.evolutionEditing, reason: 'Not available for this game.' }, g.generation <= 2 ? 'In this generation only the level of an existing level evolution can change.' : 'Change how a Pokemon evolves, remove evolutions or lower evolution levels.', ''),
      ),
      h('div', { class: 'card tilt stack' },
        h('h2', { text: 'The file' }),
        h('div', { class: 'mono', style: { overflowWrap: 'anywhere', color: 'var(--muted)', fontSize: '13px' }, text: g.path }),
        S.info && g.threeDs && !S.info.threeDsReady ? h('div', { class: 'notice bad', text: 'This is a 3DS game and this run of the program has less than 4 GB of memory. Use the launcher, or start it with -Xmx6g, before randomizing.' }) : null,
        h('div', { class: 'row wrap', style: { marginTop: 'auto' } },
          h('button', { class: 'btn', onclick: () => nativeBrowse({ mode: 'open', filter: 'rom', startDir: g.path.replace(/[\\/][^\\/]*$/, ''), title: 'Choose a game', onPick: (p) => loadRom(p) }) }, icon('folder'), 'Choose another game'),
          h('button', { class: 'btn primary', onclick: () => go('rand'), text: 'Continue to the randomizer →' })),
      )),
  );
}

async function loadRom(path) {
  if (!path) { toast('Type or choose the path of a game first.', 'bad'); return; }
  const root = $('#view-game');
  root.replaceChildren(h('div', { class: 'card stack', style: { alignItems: 'center', padding: '60px 20px' } }, h('div', { class: 'big-number', text: 'Reading the game…' }), h('div', { class: 'progress', style: { width: '260px' } }, h('i')), h('div', { class: 'muted', text: 'Big 3DS games can take half a minute.' })));
  try {
    const r = await api('/api/rom', { path });
    S.rom = r.game;
    const [schema, items, species, moves, evo] = await Promise.all([api('/api/schema'), api('/api/items'), api('/api/species'), api('/api/moves'), api('/api/evolution-types')]);
    S.schema = schema; S.items = items.items; S.species = species.items; S.moves = moves.items; S.evoTypes = evo.items;
    // Added (11/10/2026, web UI feedback item 17): Locke and Soft-locke do not belong to any upstream settings
    // panel (they touch both items and Pokemon Center behaviour), so unlike every other YAO feature this one
    // gets a section of its own rather than a spot inside an existing one. The server's schema only describes
    // upstream Settings fields, so this section is added purely on the client with no native groups of its own;
    // yaoCardsForSection() below fills it with the Locke card.
    S.schema.sections.push({ id: 'gamemodes', title: 'Game Modes', groups: [] });
    // Added (12/10/2026, web UI feedback round 3): "Every Pokemon is fully evolved by level" used to be a slider
    // that only worked while "Make Evolutions Easier" was ticked. It changes the game's evolution levels for
    // everyone (rivals and every other trainer included, not only the player's Pokemon), so it now stands on its
    // own as a YAO control (fullEvoCard) and the native slider is hidden to avoid two controls for one thing.
    S.schema.hiddenFields = (S.schema.hiddenFields || []).concat(['makeEvolutionsEasierLvl']);
    buildDefaults();
    restore();
    S.run.output = '';
    const recent = (() => { try { return JSON.parse(safeStorage('get', 'yao.recent') || '[]'); } catch (e) { return []; } })().filter((p) => p !== path);
    recent.unshift(path); safeStorage('set', 'yao.recent', JSON.stringify(recent.slice(0, 6)));
    enableSteps();
    renderGame();
    toast('Game loaded: ' + (S.rom.name || path.split(/[\\/]/).pop()), 'ok');
  } catch (e) {
    S.rom = null; enableSteps(); renderGame();
    toast(e.message, 'bad', 9000);
    const inp = $('input[type=text]', $('#view-game')); if (inp) { inp.value = path; showPathEnd(inp); }
  }
}

function buildDefaults() {
  S.defaults = {}; S.values = {};
  for (const sec of S.schema.sections) for (const g of sec.groups) for (const f of [g.main, ...g.fields]) {
    if (f && f.key) { S.defaults[f.key] = JSON.parse(JSON.stringify(f.default === undefined ? null : f.default)); S.values[f.key] = JSON.parse(JSON.stringify(S.defaults[f.key])); }
  }
  S.yao = defaultYao();
}

// ------------------------------------------------------------------ view 2: randomizer
// Redesigned (11/10/2026, task 5): the previous four presets never touched any of this fork's own features
// (shiny chance, Locke, mega finale, egg boxes, custom/protected shop items, the stat editors), so they read
// as a plain copy of the original randomizer's own presets rather than something that shows what this fork
// adds. "Nuzlocke-ready" and "Shiny hunt" are new and lean on YAO features specifically; the other four keep
// their original spirit, with "Total chaos" now also ending every trainer battle in a Mega Evolution. Every
// `values` key is a native Settings field (same as before); `yao` keys match YaoSettings/defaultYao() above,
// and are shallow-merged onto a fresh defaultYao() by applyPreset() so a preset never half-applies leftover
// YAO state from whatever was configured before it.
const PRESETS = {
  reset: { label: 'Reset everything', hint: 'All options, including this fork\'s own features, back to unchanged.', values: {} },
  gentle: { label: 'Gentle', hint: 'Wild Pokemon, trainers and starters change, at a similar strength to the original.', values: { startersMod: 'RANDOM_BASIC', randomizeWildPokemon: true, similarStrengthEncounters: true, trainersMod: 'RANDOM', trainersUsePokemonOfSimilarStrength: true, rivalCarriesStarterThroughout: true, staticPokemonMod: 'SIMILAR_STRENGTH' } },
  classic: { label: 'Classic random', hint: 'The usual randomizer run: Pokemon, types, abilities, moves, TMs, trainers and items.', values: { startersMod: 'COMPLETELY_RANDOM', speciesTypesMod: 'RANDOM_FOLLOW_EVOLUTIONS', abilitiesMod: 'RANDOMIZE', movesetsMod: 'RANDOM_PREFER_SAME_TYPE', trainersMod: 'RANDOM', trainersUsePokemonOfSimilarStrength: true, randomizeWildPokemon: true, similarStrengthEncounters: true, staticPokemonMod: 'RANDOM_MATCHING', tmsMod: 'RANDOM', tmsHmsCompatibilityMod: 'RANDOM_PREFER_TYPE', moveTutorMovesMod: 'RANDOM', fieldItemsMod: 'SHUFFLE', pickupItemsMod: 'RANDOM' } },
  chaos: { label: 'Total chaos', hint: 'Everything random, including base stats, evolutions, type effectiveness, and a Mega Evolution to end every trainer\'s last Pokemon.', values: { startersMod: 'COMPLETELY_RANDOM', baseStatisticsMod: 'RANDOM', speciesTypesMod: 'COMPLETELY_RANDOM', abilitiesMod: 'RANDOMIZE', evolutionsMod: 'RANDOM', movesetsMod: 'COMPLETELY_RANDOM', trainersMod: 'RANDOM', randomizeWildPokemon: true, staticPokemonMod: 'COMPLETELY_RANDOM', tmsMod: 'RANDOM', tmsHmsCompatibilityMod: 'COMPLETELY_RANDOM', moveTutorMovesMod: 'RANDOM', fieldItemsMod: 'RANDOM', shopItemsMod: 'RANDOM', pickupItemsMod: 'RANDOM', typeEffectivenessMod: 'RANDOM' }, yao: { megaFinale: { everyone: true, replace: true } } },
  nuzlocke: { label: 'Nuzlocke-ready', hint: 'Soft-locke mode (fainted Pokemon can\'t be revived at a Pokemon Center), similar-strength trainers and wild encounters for a fair run, and the regular shops kept as they are.', values: { startersMod: 'RANDOM_BASIC', randomizeWildPokemon: true, similarStrengthEncounters: true, trainersMod: 'RANDOM', trainersUsePokemonOfSimilarStrength: true, rivalCarriesStarterThroughout: true, staticPokemonMod: 'SIMILAR_STRENGTH' }, yao: { locke: 'soft' } },
  shinyhunt: { label: 'Shiny hunt', hint: 'A high real shiny chance (about 1 Pokemon in 7, wild, static, gift, egg and trainer alike) with the rest of the game left original, so a shiny genuinely stands out.', values: {}, yao: { shinyPercent: 15 } },
};

function applyPreset(key) {
  const p = PRESETS[key];
  for (const k of Object.keys(S.defaults)) S.values[k] = JSON.parse(JSON.stringify(S.defaults[k]));
  for (const [k, v] of Object.entries(p.values)) if (k in S.defaults) S.values[k] = v;
  // Reset every YAO feature to its default first, then apply only what this preset asks for, so a preset
  // never leaves behind leftover shiny/Locke/mega/etc state from whatever was configured before it. Nested
  // objects (eggBox, evolutionScale, playerSprites) are shallow-merged rather than replaced outright, so a
  // preset only needs to name the sub-keys it actually cares about.
  const freshYao = defaultYao();
  if (p.yao) {
    for (const [k, v] of Object.entries(p.yao)) {
      if (v && typeof v === 'object' && !Array.isArray(v) && freshYao[k] && typeof freshYao[k] === 'object') {
        Object.assign(freshYao[k], v);
      } else {
        freshYao[k] = v;
      }
    }
  }
  S.yao = freshYao;
  persist(); renderRandomizer();
  toast('Preset applied: ' + p.label, 'ok');
}

function groupOn(g) {
  if (!g.main) return true;
  const v = S.values[g.main.key];
  if (g.main.type === 'bool') return !!v;
  if (g.main.type === 'enum') return v !== g.main.options[0].value;
  return true;
}

function matches(f, q) {
  if (!q) return true;
  const text = ((f.label || '') + ' ' + (f.tooltip || '') + ' ' + (f.options ? f.options.map((o) => o.label).join(' ') : '')).toLowerCase();
  return q.split(/\s+/).every((w) => text.includes(w));
}

function hl(text, q) {
  if (!q || !text) return text;
  const words = q.split(/\s+/).filter(Boolean).map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  if (!words.length) return text;
  const re = new RegExp('(' + words.join('|') + ')', 'ig');
  const out = document.createDocumentFragment();
  let last = 0; let m;
  while ((m = re.exec(text))) { out.append(text.slice(last, m.index), h('mark', { text: m[0] })); last = m.index + m[0].length; if (m[0].length === 0) re.lastIndex++; }
  out.append(text.slice(last));
  return out;
}

function setVal(key, v, rerenderGroup) {
  S.values[key] = v; persist();
  if (rerenderGroup) rerenderGroup();
  updateSideCounts();
}

function fieldLabel(f, q) {
  return h('div', { class: 'name' }, h('span', { class: 't' }, hl(f.label || f.key, q)), infoButton(f.tooltip, WIKI[f.key]));
}

function segControl(options, value, onChange, tipsOn, disabled) {
  const seg = h('div', { class: 'seg', role: 'radiogroup' });
  options.forEach((o) => {
    const b = h('button', { type: 'button', role: 'radio', 'aria-checked': String(o.value === value), class: o.value === value ? 'on' : '', text: o.label, disabled: !!disabled });
    if (o.tooltip && tipsOn !== false) { b.addEventListener('mouseenter', () => showTip(b, o.tooltip)); b.addEventListener('mouseleave', hideTip); }
    b.addEventListener('click', () => { onChange(o.value); });
    seg.append(b);
  });
  return seg;
}

// Added (11/10/2026, web UI feedback item 27): SettingsSchema.java carries an optional "dependsOn" (the key of
// another bool field in the same group) through from settings-meta.json. A field with no effect while its
// parent is off (e.g. "Force % of Good Damaging Moves" and the percentage slider under it) used to stay fully
// interactive even then, implying it was doing something when it was not. Every renderField branch below
// disables its control and dims the row when this returns false; rerenderGroup already re-runs this on every
// change, including a parent toggle, since setVal re-draws the whole group.
function fieldEnabled(f) {
  // dependsOnValue (13/10/2026): the parent is a drop-down and this field only matters for one of its choices.
  if (f.dependsOn && f.dependsOnValue !== undefined) return S.values[f.dependsOn] === f.dependsOnValue;
  return !f.dependsOn || !!S.values[f.dependsOn];
}

// Added (12/10/2026, web UI feedback round 3): a drop-down hides its choices, so nothing explained what each one
// does (the trainer Pokemon mode was the example). Under every drop-down this shows what the CURRENT choice
// does, plus a collapsible "what does each option do?" list of all of them, taken from the options' own
// tooltips in settings-meta.json (segmented controls already show theirs on hover).
function optionHelp(options, value) {
  const withTip = options.filter((o) => o.tooltip);
  if (!withTip.length) return null;
  const cur = options.find((o) => o.value === value) || options[0];
  return h('div', { class: 'option-help' },
    cur && cur.tooltip ? h('div', { class: 'faint', style: { fontSize: '13px', whiteSpace: 'pre-line' }, text: (cur.label ? cur.label + ': ' : '') + cur.tooltip }) : null,
    h('details', null, h('summary', { text: 'What does each option do?' }),
      h('ul', null, ...options.map((o) => h('li', null, h('b', { text: o.label || '(custom)' }), o.tooltip ? ' ' + o.tooltip : h('span', { class: 'faint', text: ' (no extra detail)' }))))));
}

function renderField(f, q, rerenderGroup) {
  const t = f.type;
  const enabled = fieldEnabled(f);
  const depClass = enabled ? '' : ' dep-off';
  if (t === 'bool') {
    const input = h('input', { type: 'checkbox', 'aria-label': f.label || f.key, disabled: !enabled });
    input.checked = !!S.values[f.key];
    input.addEventListener('change', () => setVal(f.key, input.checked, rerenderGroup));
    return h('div', { class: 'opt' + depClass }, fieldLabel(f, q), h('label', { class: 'switch' }, input, h('span', { class: 'track' }), h('span', { class: 'thumb' })));
  }
  if (t === 'enum') {
    const options = f.options;
    let ctl;
    if (options.length <= 4) ctl = segControl(options, S.values[f.key], (v) => setVal(f.key, v, rerenderGroup), true, !enabled);
    else {
      ctl = h('select', { 'aria-label': f.label || f.key, style: { maxWidth: '320px' }, disabled: !enabled }, options.map((o) => h('option', { value: o.value, text: o.label })));
      ctl.value = S.values[f.key] ?? options[0].value;
      ctl.addEventListener('change', () => setVal(f.key, ctl.value, rerenderGroup));
    }
    const help = options.length > 4 ? optionHelp(options, S.values[f.key]) : null;
    return h('div', { class: 'opt' + depClass + ((help || (options.length <= 4 && options.some((o) => o.label.length > 18))) ? ' stacked' : '') }, fieldLabel(f, q), ctl, help);
  }
  if (t === 'int') {
    const out = h('output', { text: S.values[f.key] });
    const input = h('input', { type: 'range', min: f.min, max: f.max, step: f.step || 1, value: S.values[f.key], 'aria-label': f.label || f.key, disabled: !enabled });
    const fill = () => input.style.setProperty('--fill', ((input.value - f.min) / Math.max(1, f.max - f.min) * 100) + '%');
    fill();
    input.addEventListener('input', () => { out.textContent = input.value; fill(); S.values[f.key] = Number(input.value); persist(); updateSideCounts(); });
    return h('div', { class: 'opt stacked' + depClass }, fieldLabel(f, q), h('div', { class: 'range' }, input, out));
  }
  // Changed from a drag slider to a segmented picker (11/10/2026, style polish item 4.1): a generation number
  // like "6" or "7" is a handful of named options, not a continuous range, so this reuses the same segControl()
  // already used for small enum choices rather than a range input.
  if (t === 'genPicker') {
    const options = f.options.map((g) => ({ value: g, label: 'Gen ' + g }));
    const ctl = segControl(options, S.values[f.key], (v) => setVal(f.key, Number(v), rerenderGroup), false, !enabled);
    return h('div', { class: 'opt' + depClass }, fieldLabel(f, q), ctl);
  }
  if (t === 'genRestrictions') return renderGens(f, q, rerenderGroup);
  if (t === 'customStarters') return renderStarters(f, q);
  if (t === 'miscTweaks') return renderTweaks(f, q);
  if (t === 'battleStyle') return renderBattleStyle(f, q);
  return h('div');
}

function renderGens(f, q) {
  const v = S.values[f.key];
  const box = h('div', { class: 'row wrap' });
  const draw = () => {
    box.replaceChildren(...v.gens.map((on, i) => {
      const supported = i + 1 <= 7;
      return h('button', { class: 'chip' + (on ? ' on' : ''), type: 'button', disabled: !supported, text: 'Gen ' + (i + 1), onclick: () => {
        v.gens[i] = !v.gens[i];
        if (!v.gens.some(Boolean)) { v.gens[i] = true; toast('At least one generation has to stay selected.', 'bad'); }
        S.values[f.key] = v; persist(); draw(); updateSideCounts();
      } });
    }), h('label', { class: 'row', style: { marginLeft: '10px' } }, h('span', { class: 'muted', text: 'Include evolutionary relatives' }), (() => {
      const input = h('input', { type: 'checkbox' }); input.checked = v.relatives;
      input.addEventListener('change', () => { v.relatives = input.checked; S.values[f.key] = v; persist(); });
      return h('span', { class: 'switch' }, input, h('span', { class: 'track' }), h('span', { class: 'thumb' }));
    })()));
  };
  draw();
  return h('div', { class: 'opt stacked' }, fieldLabel(f, q), box);
}

function renderStarters(f, q) {
  const v = S.values[f.key];
  const row = h('div', { class: 'grid three' });
  const draw = () => {
    row.replaceChildren(...v.map((n, i) => speciesButton(n, (num) => { v[i] = num; S.values[f.key] = v; persist(); draw(); }, 'Starter ' + (i + 1))));
  };
  draw();
  return h('div', { class: 'opt stacked' }, fieldLabel(f, q), row);
}

function renderTweaks(f, q) {
  const wrap = h('div', { class: 'stack', style: { gap: '2px' } });
  if (!f.tweaks.length) { wrap.append(h('div', { class: 'muted', text: 'This game has no extra tweaks.' })); }
  f.tweaks.forEach((tw) => {
    const input = h('input', { type: 'checkbox', 'aria-label': tw.label });
    input.checked = (S.values[f.key] || []).includes(tw.id);
    input.addEventListener('change', () => {
      const set = new Set(S.values[f.key] || []);
      if (input.checked) set.add(tw.id); else set.delete(tw.id);
      S.values[f.key] = Array.from(set); persist(); updateSideCounts();
    });
    if (q && !((tw.label || '') + ' ' + (tw.tooltip || '')).toLowerCase().includes(q)) return;
    wrap.append(h('div', { class: 'opt' }, h('div', { class: 'name' }, h('span', { class: 't' }, hl(tw.label, q)), infoButton(tw.tooltip)), h('label', { class: 'switch' }, input, h('span', { class: 'track' }), h('span', { class: 'thumb' }))));
  });
  return wrap;
}

function renderBattleStyle(f, q) {
  const v = S.values[f.key];
  const box = h('div', { class: 'row wrap' });
  const draw = () => {
    box.replaceChildren(segControl(f.mods, v.mod, (m) => { v.mod = m; S.values[f.key] = v; persist(); draw(); updateSideCounts(); }));
    if (v.mod === 'SINGLE_STYLE') box.append(segControl(f.styles, v.style, (s) => { v.style = s; S.values[f.key] = v; persist(); draw(); }));
  };
  draw();
  return h('div', { class: 'opt stacked' }, fieldLabel(f, q), box);
}

// Added (12/10/2026, web UI feedback round 3): "Movesets, items and variety" was one flat list of twelve
// switches where "Boss Trainers / Important Trainers / Regular Trainers" repeated three times with nothing to
// say which belonged to moves, held items or type variety, and "Levels and extra Pokemon" mixed three unrelated
// ideas. Both groups are now split into titled blocks that each say in one sentence what they change in the game,
// with the three trainer tiers shown side by side and a plain-language "currently" line. Every control is still
// the same native setting (same keys, same values), so nothing about saving or running changed. Search results
// keep the plain flat list.
const TIER_LEGEND = [
  ['Boss', 'Gym leaders, Kahunas, team leaders, the Elite Four and Champions.'],
  ['Important', 'Rivals and friends, team admins and other key story battles (for example Colress in Black 2 and Sycamore in X and Y).'],
  ['Regular', 'Every other trainer.'],
];
const SUBGROUPS = {
  trainermoves: {
    intro: true,
    blocks: [
      { title: 'Better movesets', desc: 'Trainer Pokemon get moves chosen to work together, including TM, tutor, egg and earlier-evolution moves, instead of whatever their level-up list gives them. Makes fights harder and less predictable.',
        details: [
          'What it does: for every Pokemon of the tiers you turn on, the program builds the list of moves it could know at its level (level-up, TM, tutor, egg and earlier-evolution moves), throws out useless ones, and then picks four that fit together: moves of its own type (STAB), moves that match its Attack or Special Attack, and moves that work with its ability and with each other.',
          'Incremental (the switch below, on by default): every move gets a likelihood from how its power fits the stage of the game, so trainers are competitive from the first gym without every Pokemon using its strongest move. The power that fits is 45 + 0.8 x level (about 49 at level 5, 57 at level 15, 69 at level 30, 81 at level 45), plus 2 for gym leaders, 3 for other bosses and 4 for the Elite Four and Champion, never more than 85. Moves close to that power are the most likely, moves up to 40% above it about half as likely, stronger ones rare (an occasional Earthquake early, a Hyper Beam in the Elite Four), and nothing is above roughly twice that power (a bit more at very low levels). Weaker moves are half as likely early and fade out by level 50; moves under half of the fitting power are dropped as long as four better ones remain, and that limit rises to three quarters between level 25 and 45, so the Elite Four never has Tackle but can have a Fang move. Multi-hit moves count every hit, charge and recharge moves count in full (Hyper Beam is 150). TM and tutor moves are also allowed in earlier than upstream allowed (up to 1.4 times the fitting power). STAB, ability and stat preferences are not changed, they are multiplied with this.',
          'Separate from your moves: if you also randomize the Pokemon movesets (the Movesets setting), your Pokemon get one randomized set of learnable moves and the trainers get a different, independently randomized one, which the rules here then narrow down. So the moves a trainer uses are not the ones your own Pokemon of that species can learn. This only happens while one of the tiers below is on.',
          'Physical or special: a Pokemon whose Attack is clearly higher than its Special Attack (1.25 times or more) only gets physical attacks, the reverse only special ones, and mixed attackers keep both. This only applies when at least two moves of the right kind exist.',
          'Status moves: allowed for every Pokemon at every level, trainer tier included, but less likely than attacks, so Swords Dance or Spore show up on some Pokemon and not on all. A set never has more than two status moves and always has at least two attacking moves, so it can have two, three or four attacks.',
          'Example: a level 15 Pokemon of the first gym leader (fitting power about 59) most often gets moves of 45 to 65 power, Bite or Water Pulse for instance, sometimes a 70 to 80 power one, and only rarely something like Earthquake or Thunderbolt if it can learn it. The Elite Four at level 55 (fitting power 85) mostly gets 65 to 95 power moves, Fang moves at the low end, and rarely Hyper Beam. Only trainers are affected, never wild or caught Pokemon. If a rule would leave a Pokemon without attacks, the rule is skipped for it.',
        ],
        yaoToggle: ['incrementalMovesets', 'Moves grow with the level and the stage of the game', 'On: moves are picked from what the Pokemon can learn (level-up, TM, tutor, egg, earlier evolutions), favouring a power that fits the stage of the game: around 50 to 60 at the first gyms, mostly 65 to 95 for the Elite Four, strong moves (120, 150) rare. If a Pokemon has no move of that power the best it can learn is used. Status moves are possible for every Pokemon at every level but rare, and a set has at least two attacking moves. Off: the old behaviour, upstream limits on TM and tutor power and no preference for a power.'],
        tiers: ['betterBossTrainerMovesets', 'betterImportantTrainerMovesets', 'betterRegularTrainerMovesets'] },
      { title: 'Held items', desc: 'Trainer Pokemon get a held item in battle (or a different one than before). Turn on the tiers you want, then narrow down what kind of items they may get.',
        tiers: ['randomizeHeldItemsForBossTrainerPokemon', 'randomizeHeldItemsForImportantTrainerPokemon', 'randomizeHeldItemsForRegularTrainerPokemon'],
        extras: [
          ['consumableItemsOnlyForTrainerPokemon', 'Only items that are used up (berries and the like), never items that stay equipped.'],
          ['sensibleItemsOnlyForTrainerPokemon', 'Only items that suit the Pokemon, chosen from its species and moves instead of at random.'],
          ['highestLevelOnlyGetsItemsForTrainerPokemon', 'Only the highest-level Pokemon on each trainer\'s team gets an item.'],
        ] },
      { title: 'Type variety', desc: 'A trainer will not use two Pokemon of the same type on one team. Ignored for trainers that have a type theme.',
        tiers: ['diverseTypesForBossTrainers', 'diverseTypesForImportantTrainers', 'diverseTypesForRegularTrainers'] },
    ],
  },
  trainerlevels: {
    blocks: [
      { title: 'Battle level', desc: 'Raise or lower the level every trainer Pokemon fights at, by a percentage.', keys: ['trainersLevelModified', 'trainersLevelModifier'] },
      { title: 'Evolution', desc: 'Make trainer Pokemon evolved as far as their level allows. This never changes their battle level, only which stage they are.', keys: ['trainersEvolveTheirPokemon', 'trainersEvolutionLevelModifier'] },
      { title: 'Extra Pokemon', desc: 'Add more Pokemon to teams, per tier. Very long teams can mean long fights with no chance to heal, so keep an eye on gauntlet battles.',
        keys: ['additionalBossTrainerPokemon', 'additionalImportantTrainerPokemon', 'additionalRegularTrainerPokemon', 'eliteFourUniquePokemonNumber'] },
    ],
  },
};

function renderSubgroups(g, rerender) {
  const cfg = SUBGROUPS[g.id];
  const fieldByKey = (k) => g.fields.find((f) => f.key === k && !isHiddenField(f.key));
  const tierNames = ['Boss', 'Important', 'Regular'];
  const body = h('div', { class: 'stack', style: { gap: '18px' } });
  if (cfg.intro) {
    body.append(h('div', { class: 'tier-legend' }, ...TIER_LEGEND.map(([name, text]) => h('div', null, h('b', { text: name }), ' ' + text))));
  }
  for (const block of cfg.blocks) {
    const el = h('div', { class: 'subblock stack', style: { gap: '10px' } }, h('div', null, h('h4', { text: block.title }), h('div', { class: 'faint', style: { fontSize: '13px' }, text: block.desc })));
    if (block.details) {
      el.append(h('div', { class: 'stack', style: { gap: '8px' } }, ...block.details.map((t) => h('div', { class: 'muted', style: { fontSize: '14px' }, text: t }))));
    }
    if (block.yaoToggle) {
      const [key, label, tip] = block.yaoToggle;
      const i = h('input', { type: 'checkbox', 'aria-label': label }); i.checked = S.yao[key] !== false;
      i.addEventListener('change', () => { S.yao[key] = i.checked; persist(); });
      el.append(h('div', { class: 'opt' }, h('div', { class: 'name' }, h('span', { class: 't', text: label }), infoButton(tip)), h('label', { class: 'switch' }, i, h('span', { class: 'track' }), h('span', { class: 'thumb' }))));
    }
    if (block.tiers) {
      const fields = block.tiers.map(fieldByKey);
      const cells = fields.map((f, i) => {
        if (!f) return null;
        const input = h('input', { type: 'checkbox', 'aria-label': block.title + ' for ' + tierNames[i] + ' trainers' });
        input.checked = !!S.values[f.key];
        input.addEventListener('change', () => setVal(f.key, input.checked, rerender));
        return h('div', { class: 'tier-cell' }, h('div', { class: 'name' }, h('span', { class: 't', text: tierNames[i] }), infoButton(f.tooltip, WIKI[f.key])), h('label', { class: 'switch' }, input, h('span', { class: 'track' }), h('span', { class: 'thumb' })));
      });
      el.append(h('div', { class: 'tier-row' }, ...cells));
      const onFor = fields.map((f, i) => (f && S.values[f.key] ? tierNames[i] : null)).filter(Boolean);
      el.append(h('div', { class: 'now' + (onFor.length ? ' on' : ''), text: onFor.length ? 'On for: ' + onFor.join(', ') + ' trainers.' : 'Off: nobody is changed.' }));
      if (block.extras) {
        const anyOn = fields.some((f) => f && S.values[f.key]);
        const extras = block.extras.map(([k, text]) => {
          const f = fieldByKey(k); if (!f) return null;
          const input = h('input', { type: 'checkbox', 'aria-label': f.label || k, disabled: !anyOn });
          input.checked = !!S.values[k];
          input.addEventListener('change', () => setVal(k, input.checked, rerender));
          return h('div', { class: 'opt' + (anyOn ? '' : ' dep-off') }, h('div', { class: 'name' }, h('span', { class: 't', text: f.label || k }), infoButton(text)), h('label', { class: 'switch' }, input, h('span', { class: 'track' }), h('span', { class: 'thumb' })));
        });
        el.append(h('div', { class: 'stack', style: { gap: '2px' } }, ...extras));
      }
    } else {
      const parts = (block.keys || []).map(fieldByKey).filter(Boolean).map((f) => renderField(f, '', rerender));
      el.append(h('div', { class: 'stack', style: { gap: '2px' } }, ...parts));
    }
    body.append(el);
  }
  return body;
}

// Added (13/10/2026, web UI feedback item 50): the wild Pokemon group was a flat list of eighteen options whose names
// ("1 Per Encounter Set", "Keep Set/Zone Themes", "Similar Strength"...) read as near duplicates. This presents the
// same native settings as plain choices: ONE "how are Pokemon replaced" picker (mapped onto randomizeWildPokemon plus
// wildPokemonZoneMod, so saved settings and settings strings are untouched and old files load exactly as before),
// then separate, titled blocks for "which Pokemon may appear", "collecting rules", catch rate, held items and levels.
const WILD_MODES = [
  { id: 'ORIGINAL', title: 'Original', short: 'Wild Pokemon stay as in the game.',
    long: 'Nothing about who lives where is changed. You can still change their levels, catch rate or held items below.',
    example: 'Route 1 still has Pidgey and Rattata.' },
  { id: 'NONE', title: 'Completely random', short: 'Every slot of every area gets its own random Pokemon.',
    long: 'The most chaotic choice. Each slot is rolled on its own, so two slots that held the same Pokemon in the game can become two unrelated ones, even on the same route. Each area ends up with a handful of different Pokemon. In Omega Ruby and Alpha Sapphire this is approximated per map, because the exact version crashes the DexNav.',
    example: 'The two Pidgey slots of Route 1 become Gengar and Magikarp.' },
  { id: 'SWAP', title: 'Swap each Pokemon for another', short: 'Every Pokemon is replaced by one other Pokemon and always the same one. Choose how wide that swap applies.',
    long: 'Each original Pokemon is exchanged for ONE new Pokemon, wherever the swap applies, so the places stay recognizable: if Pidgey becomes Growlithe, every Pidgey slot there becomes Growlithe. The only choice is how far that swap reaches.',
    example: 'Pidgey becomes Growlithe in every slot the swap covers.' },
];
// How far a swap reaches. Each value is a wildPokemonZoneMod value, from the narrowest area to the widest.
const WILD_SCOPES = [
  { id: 'ENCOUNTER_SET', title: 'Each encounter set', long: 'A set is one list of Pokemon (the grass of a route, its surfing water, one cave floor). The swap is decided separately for each set, so the grass and the water of the same route are unrelated.',
    example: 'Route 1 grass: Pidgey becomes Growlithe. Route 1 surfing: its own, different swap.' },
  { id: 'MAP', title: 'Each map', long: 'A map is a route, or one floor of a cave or tower. The swap is the same for every set on that map (grass, surfing, fishing), and different maps are decided separately.',
    example: 'On Route 1, Pidgey is Growlithe in the grass and when fishing. On Route 2, Pidgey is something else.' },
  { id: 'NAMED_LOCATION', title: 'Each named place', long: 'Maps with the same place name share one swap, so all floors of a cave or all parts of a forest agree.',
    example: 'Every floor of Mt. Moon turns Zubat into the same Pokemon.' },
  { id: 'GAME', title: 'The whole game', long: 'One swap for the entire game, the classic species swap of the original randomizer. Easy to learn: you only have to remember one table.',
    example: 'Pidgey is Growlithe on every route, in every cave, on every shore.' },
];
const WILD_MODE_KEYS = ['wildPokemonZoneMod', 'randomizeWildPokemon'];

function wildModeOf() {
  if (!S.values.randomizeWildPokemon) return 'ORIGINAL';
  const z = S.values.wildPokemonZoneMod || 'GAME';
  return z === 'NONE' ? 'NONE' : 'SWAP';
}

function renderWildBlocks(g, rerender) {
  const fieldByKey = (k) => g.fields.find((f) => f.key === k && !isHiddenField(f.key));
  const zoneField = g.fields.find((f) => f.key === 'wildPokemonZoneMod');
  const zoneValues = zoneField ? zoneField.options.map((o) => o.value) : [];
  const scopes = WILD_SCOPES.filter((m) => zoneValues.includes(m.id));
  const modes = WILD_MODES.filter((m) => m.id === 'ORIGINAL' || (m.id === 'NONE' ? zoneValues.includes('NONE') : scopes.length > 0));
  const mode = wildModeOf();
  const body = h('div', { class: 'stack', style: { gap: '18px' } });

  // 1. the mode picker. It is also the on/off switch of wild randomization (the group's own switch is hidden).
  const picker = h('div', { class: 'stack', style: { gap: '8px' }, role: 'radiogroup', 'aria-label': 'How wild Pokemon are replaced' });
  modes.forEach((m) => {
    const sel = m.id === mode;
    const card = h('button', { type: 'button', role: 'radio', 'aria-checked': String(sel), class: 'mode-card' + (sel ? ' on' : ''), onclick: () => {
      if (m.id === 'ORIGINAL') { S.values.randomizeWildPokemon = false; }
      else if (m.id === 'NONE') { S.values.randomizeWildPokemon = true; S.values.wildPokemonZoneMod = 'NONE'; }
      else { S.values.randomizeWildPokemon = true; S.values.wildPokemonZoneMod = (S.wildScope && scopes.some((x) => x.id === S.wildScope)) ? S.wildScope : scopes[scopes.length - 1].id; }
      persist(); rerender(); updateSideCounts();
    } },
      h('span', { class: 'mode-title', text: m.title }),
      h('span', { class: 'mode-short', text: m.short }),
      sel ? h('span', { class: 'mode-long', text: m.long }) : null,
      sel && m.id !== 'SWAP' ? h('span', { class: 'mode-example', text: 'Example: ' + m.example }) : null);
    picker.append(card);
  });
  const scopeBox = (() => {
    if (mode !== 'SWAP') return null;
    const cur = scopes.find((x) => x.id === S.values.wildPokemonZoneMod) || scopes[scopes.length - 1];
    return h('div', { class: 'stack', style: { gap: '8px' } },
      h('label', { class: 'field', text: 'How far does a swap reach?' }),
      h('div', { class: 'row wrap' }, ...scopes.map((x) => h('button', { class: 'chip' + (x.id === cur.id ? ' on' : ''), type: 'button', text: x.title, onclick: () => { S.wildScope = x.id; S.values.wildPokemonZoneMod = x.id; persist(); rerender(); updateSideCounts(); } }))),
      h('div', { class: 'muted', style: { fontSize: '14px' }, text: cur.long }),
      h('div', { class: 'mode-example', text: 'Example: ' + cur.example }));
  })();
  body.append(h('div', { class: 'subblock stack', style: { gap: '10px' } },
    h('div', null, h('h4', { text: 'How are wild Pokemon replaced?' }), h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'Pick one. This is also the on/off switch: with Original, wild Pokemon are not randomized and only the options at the bottom apply.' })),
    picker, scopeBox));

  const add = (el) => { if (el) body.append(el); };
  const randomized = mode !== 'ORIGINAL';
  const sharesAreas = mode === 'SWAP' && ['MAP', 'NAMED_LOCATION', 'GAME'].includes(S.values.wildPokemonZoneMod);
  const fieldRows = (keys, extraOff) => keys.map(fieldByKey).filter(Boolean).map((f) => {
    const row = renderField(f, '', rerender);
    if (extraOff && extraOff(f)) { row.classList.add('dep-off'); row.querySelectorAll('input,select,button').forEach((c) => { c.disabled = true; }); }
    return row;
  });
  const block = (title, desc, rows, note) => !randomized ? null : h('div', { class: 'subblock stack', style: { gap: '10px' } },
    h('div', null, h('h4', { text: title }), h('div', { class: 'faint', style: { fontSize: '13px' }, text: desc })),
    h('div', { class: 'stack', style: { gap: '2px' } }, ...rows),
    note ? h('div', { class: 'now' + (note.on ? ' on' : ''), text: note.text }) : null);

  // 2. split by encounter type + time of day (only meaningful with a shared area)
  const split = fieldByKey('splitWildZoneByEncounterTypes');
  if (split || fieldByKey('useTimeBasedEncounters')) {
    const splitOn = !!S.values.splitWildZoneByEncounterTypes;
    add(block('Areas', 'What counts as the same place when a species is swapped.',
      [...fieldRows(['splitWildZoneByEncounterTypes'], () => !sharesAreas), ...fieldRows(['useTimeBasedEncounters'])],
      { on: randomized && sharesAreas && splitOn, text: !sharesAreas && randomized ? '"Split by encounter types" only has an effect when a species is replaced the same way across a map, place or the whole game.' : (splitOn ? 'A Tentacool you surf into and a Tentacool you fish up are replaced by different Pokemon.' : 'Walking, surfing and fishing share the same swap.') }));
  }

  // 3. which Pokemon may appear
  const strength = !!S.values.similarStrengthEncounters;
  const typeMod = S.values.wildPokemonTypeMod;
  const evoMod = S.values.wildPokemonEvolutionMod;
  const filterBits = [];
  if (strength) filterBits.push('about as strong as the original');
  if (typeMod === 'RANDOM_THEMES') filterBits.push('one random type per area'); else if (typeMod === 'KEEP_PRIMARY') filterBits.push('same primary type as the original');
  if (S.values.keepWildTypeThemes) filterBits.push('areas that had a type theme keep it');
  if (evoMod === 'BASIC_ONLY') filterBits.push('only Pokemon that do not evolve from another'); else if (evoMod === 'KEEP_STAGE') filterBits.push('same evolution stage as the original');
  if (S.values.keepWildEvolutionFamilies) filterBits.push('evolution families stay together');
  if (S.values.blockWildLegendaries) filterBits.push('no legendaries');
  add(block('Which Pokemon may appear', 'Rules that narrow down the new Pokemon. Each one only filters; none of them changes where Pokemon live. They stack: a replacement has to satisfy all that are on, and if nothing fits the rules the closest choice is used.',
    fieldRows(['similarStrengthEncounters', 'wildPokemonTypeMod', 'keepWildTypeThemes', 'wildPokemonEvolutionMod', 'keepWildEvolutionFamilies', 'blockWildLegendaries', 'allowWildAltFormes']),
    { on: filterBits.length > 0, text: filterBits.length ? 'Replacements are: ' + filterBits.join('; ') + '.' : 'No filter: any Pokemon of the game can appear anywhere.' }));

  // 4. collecting
  add(block('Collecting', 'Rules about being able to complete the Pokedex and about low-level routes.',
    fieldRows(['catchEmAllEncounters', 'balanceShakingGrass']),
    { on: !!S.values.catchEmAllEncounters, text: S.values.catchEmAllEncounters ? 'Every Pokemon is placed somewhere before any is placed twice (as far as the other rules allow).' : 'The same Pokemon may appear in many places while others appear nowhere.' }));

  // 5. these work with or without randomizing
  const always = (title, desc, keys) => h('div', { class: 'subblock stack', style: { gap: '10px' } },
    h('div', null, h('h4', { text: title }), h('div', { class: 'faint', style: { fontSize: '13px' }, text: desc })),
    h('div', { class: 'stack', style: { gap: '2px' } }, ...fieldRows(keys)));
  body.append(always('Catch rate', 'Make Pokemon easier to catch. Works with or without randomizing them.', ['useMinimumCatchRate', 'minimumCatchRateLevel']));
  body.append(always('Held items', 'The items wild Pokemon carry.', ['randomizeWildPokemonHeldItems', 'banBadRandomWildPokemonHeldItems']));
  body.append(always('Levels', 'Raise or lower the level of every wild Pokemon by a percentage. Works with or without randomizing them.', ['wildLevelsModified', 'wildLevelModifier']));
  return body;
}

function renderGroup(g, q) {
  const card = h('div', { class: 'card group' });
  if (isHiddenGroup(g.id)) { card.classList.add('hidden'); return card; }
  const draw = () => {
    card.replaceChildren();
    const on = groupOn(g);
    const visibleFields = g.fields.filter((f) => !isHiddenField(f.key)).filter((f) => !q || matches(f, q) || (f.type === 'miscTweaks' && f.tweaks.some((t) => ((t.label || '') + (t.tooltip || '')).toLowerCase().includes(q))));
    const mainMatches = g.main && matches(g.main, q);
    if (q && !visibleFields.length && !mainMatches && !((g.title || '').toLowerCase().includes(q))) { card.classList.add('hidden'); return; }
    card.classList.remove('hidden');
    let mainHelp = null;
    const head = h('div', { class: 'group-head' }, h('div', { class: 'row' }, h('h3', null, hl(g.title, q)), g.main && (g.main.tooltip || WIKI[g.main.key]) ? infoButton(g.main.tooltip, WIKI[g.main.key]) : null));
    if (g.main) {
      if (g.main.type === 'bool') {
        const input = h('input', { type: 'checkbox', 'aria-label': g.title }); input.checked = !!S.values[g.main.key];
        input.addEventListener('change', () => { setVal(g.main.key, input.checked, draw); });
        // The wild Pokemon group has no switch of its own: its mode picker (Original / random / swap) is the on/off.
        if (g.id !== 'wild') head.append(h('label', { class: 'switch' }, input, h('span', { class: 'track' }), h('span', { class: 'thumb' })));
      } else {
        const opts = g.main.options;
        if (opts.length <= 5) head.append(segControl(opts, S.values[g.main.key], (v) => setVal(g.main.key, v, draw)));
        else {
          const sel = h('select', { style: { maxWidth: '360px' }, 'aria-label': g.title }, opts.map((o) => h('option', { value: o.value, text: o.label })));
          sel.value = S.values[g.main.key]; sel.addEventListener('change', () => setVal(g.main.key, sel.value, draw));
          head.append(sel);
          mainHelp = optionHelp(opts, S.values[g.main.key]);
        }
      }
    }
    card.append(head);
    if (mainHelp) card.append(mainHelp);
    if (visibleFields.length) {
      const body = h('div', { class: 'group-body' + ((on || q || g.id === 'wild') ? '' : ' collapsed') });
      if (!q && g.id === 'wild' && g.fields.some((f) => f.key === 'wildPokemonZoneMod')) body.append(renderWildBlocks(g, draw));
      else if (!q && SUBGROUPS[g.id]) body.append(renderSubgroups(g, draw));
      else visibleFields.forEach((f) => body.append(renderField(f, q, draw)));
      card.append(body);
      if (!on && !q && g.id !== 'wild') card.append(h('div', { class: 'faint', style: { marginTop: '8px', fontSize: '13px' }, text: g.main && g.main.type === 'bool' ? 'Turn this on to see its options.' : 'Choose something other than "' + g.main.options[0].label + '" to see its options.' }));
    }
  };
  draw();
  return card;
}

// Fixed (11/10/2026, style polish item 3): toggles .show (visibility) instead of .hidden (display:none), so
// the count badge's fixed-width slot always stays in the sidebar button's flex layout, present or not. See
// .side .count in app.css.
function updateSideCounts() {
  if (!S.schema) return;
  $$('.side button[data-sec]').forEach((b) => {
    const n = changedCount(b.dataset.sec);
    const c = $('.count', b);
    if (c) { c.textContent = n || ''; c.classList.toggle('show', !!n); }
  });
}

function renderRandomizer() {
  const root = $('#view-rand');
  root.replaceChildren();
  const q = S.search.trim().toLowerCase();
  const sections = S.schema.sections;
  const side = h('div', { class: 'side' }, ...sections.map((s) => h('button', { type: 'button', 'data-sec': s.id, class: s.id === S.section && !q ? 'active' : '', onclick: () => { S.section = s.id; S.search = ''; renderRandomizer(); } }, h('span', { class: 't', text: s.title }), h('span', { class: 'count' }))));
  const searchInput = h('input', { type: 'search', placeholder: 'Search every option…', value: S.search, 'aria-label': 'Search options' });
  searchInput.addEventListener('input', () => { S.search = searchInput.value; const pos = searchInput.selectionStart; renderRandomizerContent(); });
  const content = h('div', { id: 'rand-content' });
  const presets = h('div', { class: 'row wrap' }, ...Object.entries(PRESETS).map(([k, p]) => {
    const b = h('button', { class: 'chip', type: 'button', text: p.label, onclick: () => applyPreset(k) });
    b.addEventListener('mouseenter', () => showTip(b, p.hint)); b.addEventListener('mouseleave', hideTip);
    return b;
  }), h('span', { style: { flex: 1 } }),
    h('button', { class: 'btn small', type: 'button', onclick: openSettingsIO, text: 'Import / export settings' }));
  root.append(
    h('div', { class: 'row spread wrap', style: { marginBottom: '16px' } }, h('div', null, h('h2', { text: 'The randomizer' }), h('div', { class: 'muted', text: 'Everything here is the original randomizer. Options that are off are hidden until you turn their topic on.' }))),
    h('div', { class: 'card', style: { marginBottom: '16px' } }, h('div', { class: 'stack' }, h('div', { class: 'search' }, h('span', { html: ICON.search }), searchInput), presets)),
    h('div', { class: 'rand-layout' }, side, content),
    h('div', { class: 'row', style: { justifyContent: 'flex-end', marginTop: '26px' } }, h('button', { class: 'btn primary', onclick: () => go('run'), text: 'Continue to the run →' })),
  );
  renderRandomizerContent();
  updateSideCounts();
}

// Which native section each of this program's own features (built by buildYaoCards(), below) belongs in.
// Added (11/10/2026, web UI feedback item 17): these used to all live together on a separate "Extras" step,
// which the project owner asked to stop doing ("I am not trying to add extras, I am trying to do a better
// more complete randomizer"). Placement reasoning, matching upstream's own section split (see
// settings-meta.json): the per-species stat editor and the whole-dex stat booster both edit base stats, so
// they join "pokemon" (which already has a native "Base Statistics"/"Base Stat Totals" group); the custom
// evolution editor joins "pokemon" too, right alongside the native "evolutions" group it extends. Shiny
// chance and egg boxes both concern wild/static/gift Pokemon, so they join "wild" (shiny also quietly covers
// trainer Pokemon; its own card explains that, since trainers do not otherwise get a shiny control of their
// own). Shop items join "items" next to the native shop settings. Mega finale is specifically about trainer
// battles, so it joins "trainers". Locke and Soft-locke touch both items and Pokemon Centers and fit no
// single upstream panel, so they get their own "gamemodes" section (added in loadRom()). Player sprites stay
// out of every section on purpose (hidden from the web UI for this release, see buildYaoCards()).
function yaoCardsForSection(id, cards) {
  switch (id) {
    case 'pokemon': return [cards.boostCard, cards.statEditCard, cards.evoCard, cards.fullEvoCard, cards.stoneLevelCard, cards.evoScaleCard];
    case 'wild': return [cards.shinyCard, cards.eggCard];
    case 'items': return [cards.shopCard];
    case 'trainers': return [cards.itemsCard, cards.battleRulesCard, cards.megaCard];
    case 'gamemodes': return [cards.lockeCard];
    default: return [];
  }
}

// Added (11/10/2026, settings grouping pass): interleaves the Pokemon panel's native groups with this
// program's own base-stat and evolution features, instead of rendering every native group first and every YAO
// card afterwards at the bottom of the section. Two families are built:
//  - "Base Stats": the native "Base stats" and "Base stat total" groups (settings-meta.json ids "basestats"
//    and "bst"), the YAO stat booster, and the YAO per-species stat editor, all under one shared heading, in
//    the order they actually run in GameRandomizer (whole-dex base stats, then the whole-dex booster, then the
//    per-species editor, which is always the final word). "Updates and EXP curves" (id "basestatsupdate") is
//    left out of the family on purpose: it mixes base-stat updating with EXP curve settings that have nothing
//    to do with base stats, so folding it in would group unrelated things together.
//  - The custom evolution editor renders immediately after the native "Evolutions" group, instead of at the
//    very end of the section past "Evolution fixes" and "Colors", since it directly extends that group.
// Every other native group (types, abilities, evolution fixes, colors) renders in its original schema order.
function renderPokemonSection(content, sec, byId, yaoCards) {
  let shown = 0;
  const visible = (c) => c && !c.classList.contains('hidden');
  const append = (c) => { if (visible(c)) { content.append(c); shown++; } };
  let skipBst = false;
  for (const g of sec.groups) {
    if (skipBst && g.id === 'bst') { skipBst = false; continue; }
    if (g.id === 'basestats') {
      const pieces = [byId.basestats, byId.bst, yaoCards.boostCard, yaoCards.statEditCard].filter(visible);
      if (pieces.length) { content.append(h('div', { class: 'family' }, h('h3', { class: 'family-title', text: 'Base Stats' }), ...pieces)); shown += pieces.length; }
      skipBst = true;
      continue;
    }
    // "Evolution timing" family added (11/10/2026, web UI feedback item 28): the native "Evolution fixes" group
    // (which holds "Make Evolutions Easier" and its level slider) and this program's own evolution level scale
    // both change WHEN Pokemon evolve, and the two silently stack (see the comment on evoScaleCard above), so
    // they are grouped under one heading the same way "Base Stats" groups base-stat controls above.
    if (g.id === 'evoeasier') {
      const pieces = [byId.evoeasier, yaoCards.fullEvoCard, yaoCards.stoneLevelCard, yaoCards.evoScaleCard].filter(visible);
      if (pieces.length) { content.append(h('div', { class: 'family' }, h('h3', { class: 'family-title', text: 'Evolution timing' }), ...pieces)); shown += pieces.length; }
      continue;
    }
    append(byId[g.id]);
    if (g.id === 'evolutions') append(yaoCards.evoCard);
  }
  return shown;
}

// Added (11/10/2026, settings grouping pass, lighter check over the other panels): a smaller, generic version
// of the interleaving above for the pairs that turned out to be genuinely tight (one YAO card about exactly
// what one native group covers), without the "Base Stats" family's shared wrapper, which is only worth the
// extra visual weight where several controls (two native groups plus two YAO cards) are involved.
function renderSectionWithAttachments(content, sec, byId, attachAfter, trailing) {
  let shown = 0;
  const append = (c) => { if (c && !c.classList.contains('hidden')) { content.append(c); shown++; } };
  for (const g of sec.groups) {
    append(byId[g.id]);
    (attachAfter[g.id] || []).forEach(append);
  }
  (trailing || []).forEach(append);
  return shown;
}

function renderRandomizerContent() {
  const content = $('#rand-content');
  if (!content) return;
  const q = S.search.trim().toLowerCase();
  content.replaceChildren();
  let shown = 0;
  // This program's own features are rebuilt fresh on every render, same as the native groups just below, so
  // they always reflect the current game and values. They are left out of a text search: they are controls,
  // not searchable option text, and search already narrows down to a single flat list across every section.
  const yaoCards = (!q && S.rom) ? buildYaoCards() : null;
  for (const sec of S.schema.sections) {
    if (!q && sec.id !== S.section) continue;
    const byId = {};
    const cards = sec.groups.map((g) => { const c = renderGroup(g, q); byId[g.id] = c; return c; }).filter((c) => !c.classList.contains('hidden'));
    const extra = yaoCards ? yaoCardsForSection(sec.id, yaoCards).filter(Boolean) : [];
    if (!cards.length && !extra.length) continue;
    if (q) content.append(h('h3', { class: 'faint', style: { margin: '18px 4px 8px', letterSpacing: '.1em', textTransform: 'uppercase', fontSize: '12px' }, text: sec.title }));
    // Grouping pass (11/10/2026): in the Pokemon panel, native and YAO controls that change the same kind of
    // thing used to render far apart (every native group card first, then every YAO card at the very bottom
    // of the section), which read as messy even after the earlier "fold into native sections" work. See
    // renderPokemonSection() below for the interleaved layout; every other section keeps the simple
    // native-cards-then-extras order, since nothing else in this interface has as many related pairs.
    // Lighter pass over the other panels (11/10/2026, settings grouping pass): the egg box is specifically
    // about static/gift Pokemon, so it renders right after the native "Static Pokemon and gifts" group rather
    // than at the very bottom of the Wild panel, past "Totem..." and "In game trades". Shops, custom items
    // likewise render right after the native "Shops" group rather than after every Items group. Real shiny
    // chance is left where it was (the Wild panel's end): it covers wild, static, gift, egg AND trainer
    // Pokemon at once, so it does not belong to any single native group any more than another. Mega finale
    // (Trainers) and Locke (its own Game Modes section) were checked the same way and found to already sit
    // reasonably (neither is about one specific native Trainers group), so they are unchanged.
    if (!q && sec.id === 'pokemon' && yaoCards) {
      shown += renderPokemonSection(content, sec, byId, yaoCards);
    } else if (!q && sec.id === 'wild' && yaoCards) {
      shown += renderSectionWithAttachments(content, sec, byId, { static: [yaoCards.eggCard] }, [yaoCards.shinyCard]);
    } else if (!q && sec.id === 'items' && yaoCards) {
      shown += renderSectionWithAttachments(content, sec, byId, { shops: [yaoCards.shopCard] }, []);
    } else {
      cards.forEach((c) => content.append(c));
      extra.forEach((c) => content.append(c));
      shown += cards.length + extra.length;
    }
  }
  if (!shown) content.append(h('div', { class: 'card muted', text: 'No option matches "' + S.search + '".' }));
  $$('.side button[data-sec]').forEach((b) => b.classList.toggle('active', !q && b.dataset.sec === S.section));
}

// Reworked (12/10/2026, web UI feedback round 3): the dialog used to be one mixed box (load a pasted string, load a
// .rnqs file, copy the string) with nothing to save the settings to a file. It now has two clearly separate
// halves, "Export" (download a .rnqs file, or copy the string) and "Import" (upload a .rnqs file, or paste a
// string), so settings can be kept, moved to another computer and shared.
function openSettingsIO() {
  const area = h('textarea', { rows: 3, placeholder: 'Paste a settings string here', style: { fontFamily: 'var(--mono)', fontSize: '12.5px' } });
  const fileInput = h('input', { type: 'file', accept: '.rnqs,application/octet-stream', class: 'sr', id: 'rnqs-file' });
  fileInput.addEventListener('change', async () => {
    const f = fileInput.files[0]; if (!f) return;
    const buf = new Uint8Array(await f.arrayBuffer());
    let bin = ''; buf.forEach((b) => { bin += String.fromCharCode(b); });
    await importSettings({ file: btoa(bin) }); handle.close();
  });
  const downloadFile = async () => {
    try {
      const r = await api('/api/settings/export-file', { values: S.values, yao: cleanYao() });
      const bin = atob(r.file); const bytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
      const url = URL.createObjectURL(new Blob([bytes], { type: 'application/octet-stream' }));
      const name = (S.rom && S.rom.name ? S.rom.name.replace(/[^A-Za-z0-9._ -]+/g, '').trim().replace(/ +/g, '-') : 'settings') + '.rnqs';
      const a = h('a', { href: url, download: name }); document.body.append(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 4000);
      toast('Settings saved as ' + name + ' (check your downloads folder).', 'ok', 7000);
    } catch (e) { toast(e.message, 'bad'); }
  };
  const handle = openModal({
    title: 'Import or export settings',
    body: h('div', { class: 'stack' },
      h('div', { class: 'card stack', style: { background: 'transparent' } },
        h('h3', { text: 'Export: save your current settings' }),
        h('div', { class: 'muted', style: { fontSize: '14px' }, text: 'Everything you set up, including this program\'s own extras (shiny, Locke, shops, evolutions...), in one file or one line of text. The original randomizer can read the .rnqs file too.' }),
        h('div', { class: 'row wrap' },
          h('button', { class: 'btn primary', type: 'button', text: 'Download a .rnqs file', onclick: downloadFile }),
          h('button', { class: 'btn', type: 'button', text: 'Copy as a settings string', onclick: async () => {
            try {
              const r = await api('/api/settings/export', { values: S.values, yao: cleanYao() });
              try { await navigator.clipboard.writeText(r.text); toast('Settings string copied.', 'ok'); } catch (e) { area.value = r.text; toast('Copy it from the box below.', 'info'); }
            } catch (e) { toast(e.message, 'bad'); }
          } }))),
      h('div', { class: 'card stack', style: { background: 'transparent' } },
        h('h3', { text: 'Import: load settings you saved before' }),
        h('div', { class: 'muted', style: { fontSize: '14px' }, text: 'A .rnqs file or a settings string made by this program or by the original randomizer. It replaces what is set now.' }),
        h('div', { class: 'row wrap' }, h('label', { class: 'btn', for: 'rnqs-file', text: 'Upload a .rnqs file' }), fileInput),
        area,
        h('div', null, h('button', { class: 'btn', type: 'button', text: 'Load the pasted string', onclick: async () => { if (area.value.trim()) { await importSettings({ text: area.value.trim() }); handle.close(); } else toast('Paste a settings string first.', 'bad'); } })))),
  });
}

async function importSettings(payload) {
  try {
    const r = await api('/api/settings/import', payload);
    for (const [k, v] of Object.entries(r.values || {})) if (k in S.defaults) S.values[k] = v;
    S.yao = Object.assign(defaultYao(), r.yao || {});
    S.yao.eggBox = Object.assign(defaultYao().eggBox, S.yao.eggBox || {});
    S.yao.megaFinale = Object.assign(defaultYao().megaFinale, S.yao.megaFinale || {});
    if (!Array.isArray(S.yao.battleStyleRules)) S.yao.battleStyleRules = [];
    S.yao.evolutionScale = Object.assign(defaultYao().evolutionScale, S.yao.evolutionScale || {});
    persist(); renderRandomizer();
    if (r.yao && r.yao.warnings && r.yao.warnings.length) toast(r.yao.warnings.join('\n'), 'info', 9000);
    toast('Settings loaded.', 'ok');
  } catch (e) { toast(e.message, 'bad', 8000); }
}

// ------------------------------------------------------------------ this program's own feature helpers
function availabilityPill(a) {
  return a.available ? h('span', { class: 'pill ok', text: 'Available for this game' }) : h('span', { class: 'pill bad', title: a.reason, text: 'Not for this game' });
}
function sectionHead(title, sub, pill) {
  return h('div', { class: 'row spread wrap' }, h('div', null, h('h2', { text: title }), sub ? h('div', { class: 'muted', style: { fontSize: '14px' }, text: sub }) : null), pill || null);
}
// numberInput added (11/10/2026, web UI feedback item 7): some sliders also need a plain text field
// for typing an exact value instead of dragging. Opt in per call so the plain range/field-driven
// sliders elsewhere keep their original look.
// disabled added (11/10/2026, web UI feedback item 8): lets a caller grey out and disable a slider
// (and its number field) when the game cannot use the setting it controls, instead of leaving it
// interactive with no effect.
// onCommit added (12/10/2026, web UI feedback round 3): onInput fires on every pixel of a drag, and most callers
// answered it by re-drawing their whole card, which destroyed the very slider being dragged (so it could not be
// grabbed and slid). A caller that needs a redraw now does it from onCommit, which only fires when the drag is
// released (the range input's own "change" event), and keeps onInput for cheap live updates (a value label).
function slider({ min, max, step, value, onInput, onCommit, format, numberInput, disabled }) {
  // Fixed (11/10/2026, style polish item 3): numberInput used to add the exact-value number field NEXT TO the
  // slider's own read-only `<output>` readout, so the same value showed twice, once in each style. There should
  // be exactly one editable, correctly formatted value display per slider: when numberInput is set, the number
  // field itself is that display and the separate output is skipped; the plain (non-numberInput) sliders used
  // elsewhere keep their single output readout exactly as before.
  const out = numberInput ? null : h('output', { text: format(value) });
  const input = h('input', { type: 'range', min, max, step, value, disabled: !!disabled });
  const fill = () => input.style.setProperty('--fill', ((input.value - min) / (max - min) * 100) + '%');
  fill();
  const setValue = (v, fromTyping) => { input.value = v; if (out) out.textContent = format(v); fill(); if (numField && !fromTyping) numField.value = v; };
  input.addEventListener('input', () => { const v = Number(input.value); setValue(v); onInput(v); });
  if (onCommit) input.addEventListener('change', () => onCommit(Number(input.value)));
  let numField = null;
  if (numberInput) {
    // Fixed (13/10/2026, web UI feedback): this was type=number, which refuses or rewrites partial decimals such
    // as ".3" or "0," while typing (and depends on the keyboard locale). A text field with a decimal keyboard hint
    // accepts both "." and "," and is never rewritten while the user is typing; it is tidied on Enter or blur.
    numField = h('input', { type: 'text', inputmode: 'decimal', value, disabled: !!disabled, 'aria-label': 'Exact value', style: { width: '76px', flex: 'none' } });
    numField.addEventListener('input', () => {
      const raw = numField.value.trim().replace(',', '.');
      if (raw === '' || raw === '.') return;
      let v = Number(raw);
      if (Number.isNaN(v)) return;
      v = clamp(v, min, max);
      if (step) v = Math.round(v / step) * step;
      v = Math.round(v * 1000) / 1000;
      setValue(v, true);
      onInput(v);
    });
    // a typed value is only committed (and the caller's card redrawn) on Enter or when the field loses focus, so
    // typing never rebuilds the field under the cursor
    numField.addEventListener('change', () => { numField.value = input.value; if (onCommit) onCommit(Number(input.value)); });
    numField.addEventListener('blur', () => { numField.value = input.value; });
  }
  return h('div', { class: 'range' }, input, numField, out);
}

// ------------------------------------------------------------------ this program's own features
// Renamed from renderExtras (11/10/2026, web UI feedback item 17): these cards no longer render their own
// "Extras" view. Each one is still built exactly as before, but is now handed to yaoCardsForSection() by
// renderRandomizerContent(), which places it inside the native settings section it actually belongs to.
function buildYaoCards() {
  const y = S.yao; const f = S.rom.features;
  const save = (rerender) => { persist(); if (rerender) rerender(); };

  // --- shiny
  const shinyCard = (() => {
    const big = h('div', { class: 'big-number' });
    const odds = h('div', { class: 'muted' });
    const sparks = h('div', { class: 'shiny-demo' });
    const draw = () => {
      big.textContent = fmtPct(y.shinyPercent) + '%';
      odds.textContent = y.shinyPercent <= 0 ? 'Off: the game keeps its normal shiny odds.' : (y.shinyPercent >= 100 ? 'Every Pokemon is shiny.' : 'About 1 Pokemon in ' + Math.max(1, Math.round(100 / y.shinyPercent)) + ' is shiny.');
      sparks.replaceChildren(...Array.from({ length: Math.min(14, Math.ceil(y.shinyPercent / 7)) }, (_, i) => h('span', { class: 'spark', style: { left: (4 + Math.random() * 90) + '%', top: (Math.random() * 80) + '%', animationDelay: (i * 0.25) + 's' } })));
    };
    draw();
    // card-disabled + slider(disabled:) added (11/10/2026, web UI feedback item 8): the slider used to
    // stay fully draggable even when f.shiny.available was false, letting you set a value the game can
    // never use. It is now greyed out and non-interactive for those games.
    return h('div', { class: 'card stack' + (f.shiny.available ? '' : ' card-disabled') },
      sectionHead('Real shiny chance', 'Wild, static, gift, egg and trainer Pokemon. 0.1 steps.', availabilityPill(f.shiny)),
      h('div', { class: 'row spread', style: { alignItems: 'flex-end' } }, big, sparks),
      slider({ min: 0, max: 100, step: 0.1, value: y.shinyPercent, format: (v) => fmtPct(v) + '%', numberInput: true, disabled: !f.shiny.available, onInput: (v) => { y.shinyPercent = v; save(); draw(); } }),
      odds,
      !f.shiny.available ? h('div', { class: 'notice', text: 'This game cannot take the shiny patch: ' + f.shiny.reason + ' The setting is ignored for this game.' }) : h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'This changes the game\'s program code, so you confirm it before the run. A shiny made this way is shiny only inside the patched game.' }),
    );
  })();

  // --- locke
  const lockeCard = (() => {
    const box = h('div', { class: 'stack' });
    const draw = () => {
      box.replaceChildren(
        segControl([{ value: 'none', label: 'Off' }, { value: 'soft', label: 'Soft-locke' }, { value: 'full', label: 'Locke' }], y.locke, (v) => { y.locke = v; save(); draw(); }, false),
        h('div', { class: 'muted', style: { fontSize: '14px' }, text: y.locke === 'none' ? 'Normal rules.' : y.locke === 'soft' ? 'Pokemon Centers and whiting out no longer revive fainted Pokemon. Revive items stay.' : 'Like Soft-locke, and Revive, Max Revive, Revival Herb and Sacred Ash are removed from shops, field items and pickup.' }),
        y.locke !== 'none' && !f.lockeCenters.available ? h('div', { class: 'notice', text: 'The Pokemon Center part is not available for this game (' + f.lockeCenters.reason + ') so only the item part will apply.' }) : null,
        y.locke !== 'none' ? h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'Items given by story scripts cannot be changed, and a fainted Pokemon left in a PC box is restored when taken out, so a dead Pokemon must stay boxed.' }) : null);
    };
    draw();
    const lockePill = f.lockeCenters.available ? availabilityPill(f.lockeCenters) : h('span', { class: 'pill warn', title: f.lockeCenters.reason, text: 'Items only for this game' });
    return h('div', { class: 'card stack' }, sectionHead('Locke rules (experimental)', 'Fainted means gone. Beta: this may not fully work yet and is not play tested. It will be finished in the next release.', lockePill), box);
  })();

  // --- egg box
  const eggCard = (() => {
    const e = y.eggBox; const box = h('div', { class: 'stack' });
    const eggCount = h('span', { class: 'mono', style: { color: 'var(--cyan)' }, text: e.count || 'off' });
    const draw = () => {
      eggCount.textContent = e.count || 'off';
      box.replaceChildren(
        h('div', { class: 'row spread' }, h('span', { class: 'muted', text: 'Number of eggs' }), eggCount),
        // disabled added (11/10/2026, web UI feedback item 8): this slider stayed interactive even when
        // f.eggBox.available was false, letting you configure eggs the game can never deliver.
        slider({ min: 0, max: 90, step: 1, value: e.count, format: (v) => v === 0 ? 'off' : String(v), disabled: !f.eggBox.available, onInput: (v) => { e.count = v; save(); eggCount.textContent = v || 'off'; }, onCommit: () => draw() }),
        e.count > 0 ? h('div', { class: 'stack' },
          h('div', null, h('label', { class: 'field', text: 'Legendary Pokemon' }), segControl([{ value: 'any', label: 'Allowed' }, { value: 'none', label: 'None' }, { value: 'only', label: 'Only legendaries' }], e.legendary, (v) => { e.legendary = v; save(); draw(); }, false)),
          h('div', null, h('label', { class: 'field', text: 'Length of the evolution line (none chosen = any)' }), h('div', { class: 'row wrap' }, ...[1, 2, 3].map((n) => h('button', { class: 'chip' + (e.stages.includes(n) ? ' on' : ''), type: 'button', text: n === 1 ? 'Does not evolve' : n + ' stages', onclick: () => { e.stages = e.stages.includes(n) ? e.stages.filter((x) => x !== n) : [...e.stages, n].sort(); save(); draw(); } })))),
          h('div', { class: 'row wrap' },
            h('button', { class: 'chip' + (e.basicOnly ? ' on' : ''), type: 'button', text: 'Only Pokemon that do not evolve from another', onclick: () => { e.basicOnly = !e.basicOnly; save(); draw(); } }),
            h('button', { class: 'chip' + (e.duplicates ? ' on' : ''), type: 'button', text: 'Allow the same species twice', onclick: () => { e.duplicates = !e.duplicates; save(); draw(); } })),
          (() => {
            const pool = S.species.filter((s) => (e.legendary === 'any' || (e.legendary === 'only') === s.legendary) && (!e.basicOnly || s.basic) && (!e.stages.length || e.stages.includes(Math.min(3, s.stages))));
            return h('div', { class: 'faint', style: { fontSize: '13px' }, text: pool.length + ' Pokemon of this game pass these filters.' + (!e.duplicates && pool.length < e.count ? ' The box will have ' + pool.length + ' eggs.' : '') });
          })(),
          !f.eggBox.available ? h('div', { class: 'notice', text: 'The egg box is not available for this game: ' + f.eggBox.reason } ) : h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'In Emerald the egg woman of Lavaridge Town hands them all over in one conversation: first the party, then the PC boxes, starting at the box that is open. A game cannot choose the boxes, so keep room: an egg is lost if every box is full.' }),
        ) : null);
    };
    draw();
    return h('div', { class: 'card stack' + (f.eggBox.available ? '' : ' card-disabled') }, sectionHead('Egg box', 'A box of randomized eggs, delivered by the game.', availabilityPill(f.eggBox)), box);
  })();

  // --- shops
  const shopCard = (() => {
    const box = h('div', { class: 'stack' });
    // Reads the loaded game's own price limits instead of a hardcoded range (11/10/2026, web UI
    // feedback item 14): ShopPriceLimits.java already knows Gen 1 goes up to 999999 with no step, Gen 2
    // to 4 go 1 to 65535 with no step, and Gen 5 to 7 are multiples of 10 up to 655350.
    const priceLimits = f.shopPriceLimits || { min: 1, max: 999999, step: 1 };
    const normalizePrice = (v) => {
      v = clamp(Math.round(Number(v)) || priceLimits.min, priceLimits.min, priceLimits.max);
      if (priceLimits.step > 1) v = Math.min(Math.ceil(v / priceLimits.step) * priceLimits.step, priceLimits.max);
      return v;
    };
    const draw = () => {
      const rows = y.shopItems.map((it, i) => h('tr', null,
        h('td', { style: { width: '62%' } }, itemButton(it.id, (id) => { it.id = id; save(); draw(); })),
        h('td', null, h('input', { type: 'number', min: priceLimits.min, max: priceLimits.max, step: priceLimits.step, value: it.price, 'aria-label': 'Price', onchange: (ev) => { it.price = normalizePrice(ev.target.value); save(); draw(); } })),
        h('td', { style: { width: '40px' } }, h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Remove', onclick: () => { y.shopItems.splice(i, 1); save(); draw(); } }, icon('x')))));
      const prot = h('div', { class: 'row wrap' }, ...y.protectedItems.map((id, i) => h('span', { class: 'chip on' }, itemName(id), h('button', { class: 'infobtn', type: 'button', style: { color: '#04121a', borderColor: '#04121a' }, 'aria-label': 'Remove', onclick: () => { y.protectedItems.splice(i, 1); save(); draw(); }, text: '×' }))),
        h('button', { class: 'chip', type: 'button', onclick: () => openPicker({ title: 'Keep this item in place', items: S.items, label: (s) => s.name, sub: (s) => '#' + s.id, placeholder: 'Search', onPick: (s) => { if (!y.protectedItems.includes(s.id)) { y.protectedItems.push(s.id); save(); draw(); } } }) }, icon('plus'), 'Add an item'));
      box.replaceChildren(
        // Wording clarified (11/10/2026, web UI feedback item 16): 64 is how many new items can be
        // added, not a cap on the shop's total size.
        // Example text added (11/10/2026, upstream options review item 7): this feature replaces upstream's
        // "Add Cheap Rare Candies" checkbox, which is now hidden from this interface.
        h('div', null, h('label', { class: 'field', text: 'Add up to 64 extra items to every regular shop (e.g. add Rare Candy at a cheap price)' }),
          y.shopItems.length ? h('table', { class: 'table' }, h('thead', null, h('tr', null, h('th', { text: 'Item' }), h('th', { text: 'Price' }), h('th'))), h('tbody', null, rows)) : h('div', { class: 'muted', text: 'None. Rare Candy for 10 and Ability Capsule for 5000 are popular choices.' }),
          h('div', { style: { marginTop: '10px' } }, h('button', { class: 'btn small', type: 'button', disabled: y.shopItems.length >= 64, onclick: () => openPicker({ title: 'Add an item to the shops', items: S.items, label: (s) => s.name, sub: (s) => '#' + s.id, onPick: (s) => { y.shopItems.push({ id: s.id, price: normalizePrice(100) }); save(); draw(); } }) }, icon('plus'), 'Add an item'))),
        f.shopsRandomizable === false ? h('div', { class: 'notice note', text: 'In this game, the "Shops" randomization above has nothing to change: it never touches the regular shops, and this game has no other shops it can change. Your extra shop items below still work.' }) : null,
        !f.shopsResizable ? h('div', { class: 'notice note', text: 'Shops cannot grow in this game, so each of your extra items takes the place of a less important one (your extra items never replace everyday supplies). Shop randomization never touches the regular shops (Poke Marts), so their everyday supplies always stay.' }) : null,
        h('div', null, h('label', { class: 'field', text: 'Keep these items in place when shops are randomized' }), prot));
    };
    draw();
    return h('div', { class: 'card stack' }, sectionHead('Shops', 'Your own items, and items that must not move.'), box);
  })();

  // --- mega finale
  // Reworked (12/10/2026, web UI feedback round 3): the finale used to be ONE choice (off, gym leaders + league,
  // boss trainers, all trainers), so gym leaders + league + evil-team bosses could not be combined and there was
  // no way to start later in the game. Tiers are now independent switches that can be mixed, and "from level"
  // only affects trainers whose strongest Pokemon (after every other change) is at that level or higher, which
  // works for every tier (a gym leader, an evil-team boss, a rival) without needing a story order. The buttons
  // under it come from the game's own vanilla gym leaders, evil bosses and so on (/api/trainer-progress), so
  // "from the 3rd gym leader" is one click.
  const megaCard = (() => {
    const box = h('div', { class: 'stack' });
    const m = y.megaFinale;
    const TIERS = [
      ['gymLeaders', 'Gym leaders'], ['league', 'Elite Four and Champion'], ['evilBosses', 'Evil-team bosses'],
      ['otherBosses', 'Other bosses (rivals, strong admins)'], ['everyone', 'Every trainer'],
    ];
    // Fixed (13/10/2026, web UI feedback): the quick-pick buttons below used to show and set each boss's level as
    // read straight off the unmodified ROM, with a disclaimer that it was "approximate" when trainer level scaling
    // was on. The backend's own fromLevel check (MegaFinaleApplier) compares against the trainer's FINAL level, after
    // scaling, so the buttons now apply the exact same formula (Randomizer.applyPercentageLevelModifier: round(level
    // * (1 + percent / 100)), clamped to 1-100) the trainer randomizer itself uses, so clicking "Gym 3" sets the real
    // post-scaling threshold instead of the vanilla one.
    const scaledLevel = (level) => S.values.trainersLevelModified
      ? Math.min(100, Math.max(1, Math.round(level * (1 + (S.values.trainersLevelModifier || 0) / 100))))
      : level;
    const draw = () => {
      const progress = S.progress || [];
      const on = megaAny(m);
      const levelLbl = h('span', { class: 'faint', style: { fontSize: '13px' }, text: m.fromLevel ? 'Only trainers whose strongest Pokemon is level ' + m.fromLevel + ' or higher.' : 'From the start of the game.' });
      const bossButtons = progress.length ? h('div', { class: 'row wrap' }, h('span', { class: 'faint', style: { fontSize: '13px' }, text: 'Start from:' }),
        h('button', { class: 'chip' + (m.fromLevel === 0 ? ' on' : ''), type: 'button', text: 'The start', onclick: () => { m.fromLevel = 0; save(); draw(); } }),
        ...progress.filter((it) => it.tier === 'gym' || it.tier === 'league').map((it, i) => {
          const lvl = scaledLevel(it.level);
          return h('button', { class: 'chip' + (m.fromLevel === lvl ? ' on' : ''), type: 'button', title: it.name, text: (it.tier === 'gym' ? 'Gym ' + (progress.filter((x) => x.tier === 'gym').indexOf(it) + 1) : it.name) + ' (Lv ' + lvl + ')', onclick: () => { m.fromLevel = lvl; save(); draw(); } });
        })) : null;
      box.replaceChildren(
        h('div', { class: 'row wrap' }, ...TIERS.map(([k, label]) => h('button', { class: 'chip' + (m[k] ? ' on' : ''), type: 'button', text: label, onclick: () => { m[k] = !m[k]; save(); draw(); } }))),
        h('div', { class: 'muted', style: { fontSize: '14px' }, text: on
          ? 'The last Pokemon of each chosen trainer holds a mega stone, so the fight ends with a Mega Evolution. A trainer that already has a Mega is left alone. You can combine any tiers.'
          : 'Pick one or more groups of trainers. Their last Pokemon will hold a mega stone, so the fight ends with a Mega Evolution.' }),
        on ? h('div', { class: 'stack' },
          h('button', { class: 'chip' + (m.replace ? ' on' : ''), type: 'button', text: 'Replace a last Pokemon that has no Mega by one that has', onclick: () => { m.replace = !m.replace; save(); draw(); } }),
          h('div', null, h('label', { class: 'field', text: 'Only from this level onward (0 = from the start)' }),
            slider({ min: 0, max: 100, step: 1, value: m.fromLevel, format: (v) => String(v), numberInput: true, onInput: (v) => { m.fromLevel = v; save(); levelLbl.textContent = v ? 'Only trainers whose strongest Pokemon is level ' + v + ' or higher.' : 'From the start of the game.'; }, onCommit: () => draw() }),
            levelLbl),
          bossButtons,
          h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'The level is the trainer\'s strongest Pokemon after all other changes (including trainer level scaling). The Gym buttons already account for your current trainer level scaling setting.' })) : null);
    };
    draw();
    if (f.megas && !S.progress) {
      api('/api/trainer-progress').then((r) => { S.progress = r.items || []; draw(); }).catch(() => { S.progress = []; });
    }
    return h('div', { class: 'card stack' + (f.megas ? '' : ' card-disabled') }, sectionHead('Mega finale', 'The last Pokemon of chosen trainers holds a mega stone, so the fight ends with a Mega Evolution.', f.megas ? h('span', { class: 'pill ok', text: 'Available for this game' }) : h('span', { class: 'pill bad', text: 'Not for this game' })), f.megas ? box : h('div', { class: 'notice', text: 'This game has no Mega Evolutions for trainers to use.' }));
  })();

  // --- battle style rules
  // Added (13/10/2026, web UI feedback item 49): the battle items a trainer's AI uses (Potion, Full Restore...), which
  // the original randomizer never touched, so an ordinary trainer on an early route could heal with a Full Restore.
  const itemsCard = (() => {
    const box = h('div', { class: 'stack' });
    const draw = () => {
      const tiered = y.trainerItems !== 'unchanged';
      box.replaceChildren(
        segControl([{ value: 'tiered', label: 'Limit by tier and stage' }, { value: 'unchanged', label: 'Keep the game\'s own' }], y.trainerItems, (v) => { y.trainerItems = v; save(); draw(); }, false),
        h('div', { class: 'muted', style: { fontSize: '14px' }, text: tiered ? 'Items trainers use during a battle are limited so early fights stay fair. Items are only ever lowered or removed, never added.' : 'Trainers keep whatever the game gave them.' }),
        tiered ? h('div', { class: 'stack', style: { gap: '6px' } },
          h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'Ordinary trainers: no items (unless you allow Potions below). Gym leaders: only the potion that fits the stage of the game, Potion before level 20, Super Potion before 35, Hyper Potion before 50, then Max Potion, and never Full Restore. Elite Four and Champion: Full Restore from level 35. Evil-team bosses, rivals and other bosses: Full Restore from level 45, potions before that. Full Heal and X items only for bosses from level 30 (gym leaders from 40). Revives are removed for everybody. The stage is the highest level in the trainer\'s team.' }),
          (() => { const i = h('input', { type: 'checkbox', 'aria-label': 'Ordinary trainers keep Potions' }); i.checked = !!y.regularTrainersKeepItems; i.addEventListener('change', () => { y.regularTrainersKeepItems = i.checked; save(); });
            return h('div', { class: 'opt' }, h('div', { class: 'name' }, h('span', { class: 't', text: 'Ordinary trainers may keep Potion and Super Potion' }), infoButton('Off by default: ordinary trainers use no items at all. On: they keep a Potion or Super Potion (never more, and cheap status cures), so long routes are a little less predictable.')), h('label', { class: 'switch' }, i, h('span', { class: 'track' }), h('span', { class: 'thumb' }))); })()) : null);
    };
    draw();
    const avail = f.trainerItems !== false;
    return h('div', { class: 'card stack' + (avail ? '' : ' card-disabled') },
      sectionHead('Trainer battle items', 'The Potions, Full Restores and other items trainers use mid-battle.', avail ? null : h('span', { class: 'pill warn', text: 'Not for this game' })),
      avail ? box : h('div', { class: 'notice', text: 'This game does not store battle items for trainers in a way the program can change (Gen 1 and 2), so nothing is changed.' }));
  })();

  // Added (12/10/2026, web UI feedback round 3): the native "Trainer battle style" only applies one style to every
  // trainer. These rules say, for a group of trainers, "this percent of them fight as a double battle (or
  // triple / rotation)", for example 10% of gym leaders and bosses. They run after the native setting.
  const battleRulesCard = (() => {
    const box = h('div', { class: 'stack' });
    const styles = f.battleStyles || [];
    const available = styles.length > 0;
    const STYLE_LABEL = { DOUBLE: 'Double battle', TRIPLE: 'Triple battle', ROTATION: 'Rotation battle' };
    const GROUPS = [['gymLeaders', 'Gym leaders'], ['league', 'Elite Four and Champion'], ['evilBosses', 'Evil-team bosses'], ['otherBosses', 'Other bosses (rivals, strong admins)'], ['everyone', 'Every trainer (all, including bosses)']];
    const draw = () => {
      const rules = y.battleStyleRules;
      const rows = rules.map((r, i) => {
        const gsel = h('select', { 'aria-label': 'Trainer group' }, GROUPS.map(([v, l]) => h('option', { value: v, text: l })));
        gsel.value = r.group; gsel.addEventListener('change', () => { r.group = gsel.value; save(); draw(); });
        const ssel = h('select', { 'aria-label': 'Battle style' }, styles.map((v) => h('option', { value: v, text: STYLE_LABEL[v] || v })));
        ssel.value = styles.includes(r.style) ? r.style : styles[0]; if (r.style !== ssel.value) { r.style = ssel.value; save(); }
        ssel.addEventListener('change', () => { r.style = ssel.value; save(); draw(); });
        const sentence = h('div', { class: 'faint', style: { fontSize: '13px' }, text: r.chance + '% of ' + (GROUPS.find((g) => g[0] === r.group) || ['', r.group])[1].toLowerCase() + ' fight as a ' + (STYLE_LABEL[r.style] || r.style).toLowerCase() + '.' });
        return h('div', { class: 'stack', style: { padding: '12px', border: '1px solid var(--line)', borderRadius: '10px' } },
          h('div', { class: 'row spread wrap' }, h('div', { class: 'row wrap' }, gsel, ssel), h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Remove', onclick: () => { rules.splice(i, 1); save(); draw(); } }, icon('x'))),
          h('label', { class: 'field', text: 'Chance for each trainer in the group (%)' }),
          slider({ min: 1, max: 100, step: 1, value: r.chance, format: (v) => v + '%', numberInput: true, onInput: (v) => { r.chance = v; save(); sentence.textContent = v + '% of ' + (GROUPS.find((g) => g[0] === r.group) || ['', r.group])[1].toLowerCase() + ' fight as a ' + (STYLE_LABEL[r.style] || r.style).toLowerCase() + '.'; } }),
          sentence);
      });
      box.replaceChildren(
        rows.length ? h('div', { class: 'stack' }, ...rows) : h('div', { class: 'muted', text: 'No rules. Add one, for example "10% of gym leaders fight as a double battle".' }),
        h('div', null, h('button', { class: 'btn small', type: 'button', disabled: rules.length >= 8, onclick: () => { rules.push({ group: 'gymLeaders', style: styles[0], chance: 10 }); save(); draw(); } }, icon('plus'), 'Add a rule')),
        h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'Rules run after "Trainer battle style" above. Each trainer is checked against the rules from top to bottom, and the first rule that matches and succeeds decides its style. The chance is per trainer: 10% means each individual trainer in the group has a 10% chance of fighting as a double, so a gym with 5 trainers has roughly a 41% chance of containing at least one double. "Every trainer" matches all trainers in the game, including bosses. A double battle with a one-Pokemon team repeats that Pokemon. Not available in Gen 1 and 2.' }));
    };
    draw();
    return h('div', { class: 'card stack' + (available ? '' : ' card-disabled') }, sectionHead('Battle style chances', 'Make some trainers fight as double (or triple, rotation) battles by chance.', available ? h('span', { class: 'pill ok', text: 'Available for this game' }) : h('span', { class: 'pill bad', text: 'Not for this game' })), available ? box : h('div', { class: 'notice', text: 'This game has no battle styles other than single battles.' }));
  })();

  // --- player sprites
  // Built but intentionally left out of the returned object below (11/10/2026, web UI feedback item 10):
  // hidden from the web UI for the v1.0.0 release. See the comment next to the return statement.
  const spriteCard = (() => {
    const box = h('div', { class: 'stack' });
    const sp = y.playerSprites;
    const field = (label, key) => {
      const input = h('input', { type: 'text', value: sp[key], placeholder: 'Path of a sprite sheet (PNG)', 'aria-label': label, oninput: (e) => { sp[key] = e.target.value; save(); } });
      return h('div', null, h('label', { class: 'field', text: label }), h('div', { class: 'row' }, input, h('button', { class: 'btn', type: 'button', onclick: () => nativeBrowse({ mode: 'open', filter: 'png', startDir: (sp[key] || S.rom.path).replace(/[\\/][^\\/]*$/, ''), title: 'Choose a sprite sheet (PNG)', onPick: (p) => { sp[key] = p; input.value = p; showPathEnd(input); save(); } }) }, icon('folder'), 'Browse')));
    };
    const draw = () => {
      box.replaceChildren(
        h('div', { class: 'muted', style: { fontSize: '14px' }, text: 'Replace the walking sprites of the player. Export the game\'s own sheets as templates, draw over them, and choose the PNG files here. 8 frames per row, 32 by 32 pixels each, at most 15 colours plus transparency.' }),
        field('Boy sheet', 'male'), field('Girl sheet', 'female'),
        h('div', { class: 'row wrap' }, h('button', { class: 'btn', type: 'button', onclick: () => nativeBrowse({ mode: 'save', startDir: S.rom.path.replace(/[\\/][^\\/]*$/, ''), title: 'Folder for the template sheets (type any file name)', onPick: async (p) => {
          const folder = p.replace(/[\\/][^\\/]*$/, '');
          try { const r = await api('/api/player-sprites/export', { folder }); toast('Templates written to ' + folder + ' (player-boy.png and player-girl.png).', 'ok', 8000); } catch (e) { toast(e.message, 'bad', 9000); }
        } }) }, icon('folder'), 'Export the templates'), h('span', { class: 'faint', style: { fontSize: '12.5px' }, text: 'The templates are the game\'s own artwork: keep them to yourself.' })));
    };
    draw();
    return h('div', { class: 'card stack' + (f.playerSprites.available ? '' : ' card-disabled') }, sectionHead('Player sprites', 'Your own character in the overworld.', availabilityPill(f.playerSprites)), f.playerSprites.available ? box : h('div', { class: 'notice', text: 'Not available for this game yet: ' + f.playerSprites.reason }));
  })();

  // --- booster
  // Rewritten for clarity (11/10/2026, style polish item 4.2): the floor used to be a bare number with no
  // visible effect, and each chosen Pokemon showed only a percentage. The floor's label and note now say
  // exactly what it does to a Pokemon's six stats, and every chosen-Pokemon row shows a real
  // "current base stat total -> new base stat total" preview, the same before/after treatment the per-species
  // stat editor (built earlier today) already uses, instead of a bare percentage with no visible outcome.
  // The floor itself is also now restricted to fully evolved Pokemon (BoosterApplier.java, same date): a
  // starter or baby Pokemon only gets stronger by evolving, not by this automatic rule, which the note below
  // and the "off" case both explain.
  // Merged (11/10/2026, web UI feedback item 25): this card used to also have a "Boost chosen Pokemon" picker
  // (species + percent, raise only). That picker was a strict subset of the per-species stat editor's quick mode
  // below (same idea, but quick mode can also lower stats), so the project owner had them merged into one. This
  // card now only holds the automatic floor; to raise or lower one chosen Pokemon's stats, use the stat editor.
  // Reworked (12/10/2026, web UI feedback round 3): the floor used to be a bare slider where "off" meant
  // "dragged all the way back to 0", with no quick way to switch it off and no way to type an exact total. It now
  // has its own on/off switch (the last chosen total is remembered while it is off, so switching it back on does
  // not start from scratch), an exact number field next to the slider, quick-pick totals, and a live preview
  // computed from this game's own Pokemon (how many fully evolved Pokemon are below the chosen total and how many
  // points they gain on average) so the effect is visible before running.
  const boostCard = (() => {
    const box = h('div', { class: 'stack' });
    const FLOOR_MAX = 780;
    const draw = () => {
      const on = y.boostFloor > 0;
      if (on) S.floorMemory = y.boostFloor;
      const sw = (() => {
        const i = h('input', { type: 'checkbox', 'aria-label': 'Automatic stat floor' }); i.checked = on;
        i.addEventListener('change', () => { y.boostFloor = i.checked ? (S.floorMemory || 450) : 0; save(); draw(); });
        return h('label', { class: 'switch' }, i, h('span', { class: 'track' }), h('span', { class: 'thumb' }));
      })();
      const parts = [h('div', { class: 'opt' }, h('div', { class: 'name' }, h('span', { class: 't', text: 'Boost weak, fully evolved Pokemon' }),
        infoButton('Every Pokemon with nothing left to evolve into and a base stat total below the number you choose counts as weak. You pick how they are boosted: up to that total, or by a percentage of their own total. The extra points are spread over its six stats in the proportions it already has (a fast Pokemon stays fast), no stat is ever lowered, and each stat stays within the game\'s own limit.')), sw)];
      if (on) {
        const impact = h('div', { class: 'faint', style: { fontSize: '13px' } });
        const refreshImpact = () => {
          const finals = S.species.filter((p) => p.bs && p.final !== false);
          const sum = (p) => p.bs.hp + p.bs.attack + p.bs.defense + p.bs.spatk + p.bs.spdef + p.bs.speed;
          const weak = finals.filter((p) => sum(p) < y.boostFloor);
          if (!finals.length) { impact.textContent = ''; return; }
          if (!weak.length) { impact.textContent = 'No fully evolved Pokemon in this game is below ' + y.boostFloor + ', so this would change nothing. Choose a higher total.'; return; }
          const before = weak.map(sum);
          const next = weak.map((b) => (y.boostPercent > 0 ? Math.min(1530, Math.round(sum(b) * (1 + y.boostPercent / 100))) : y.boostFloor));
          const avg = (a) => Math.round(a.reduce((x, c) => x + c, 0) / a.length);
          impact.textContent = 'In this game, ' + weak.length + ' of ' + finals.length + ' fully evolved Pokemon are below ' + y.boostFloor + ' and count as weak. Their average total goes from ' + avg(before) + ' to about ' + avg(next) + ' (weakest ' + Math.min(...before) + ' to about ' + Math.min(...next) + ', strongest of them ' + Math.max(...before) + ' to about ' + Math.max(...next) + '). Pokemon that still have an evolution ahead of them, and Pokemon at or above ' + y.boostFloor + ', are never touched. No stat goes above the game\'s limit.';
        };
        const pct = y.boostPercent > 0;
        const pctControls = pct ? h('div', { class: 'stack', style: { gap: '8px' } },
          h('label', { class: 'field', text: 'Boost each weak Pokemon by (drag, or type an exact number)' }),
          slider({ min: 1, max: 100, step: 1, value: y.boostPercent, format: (v) => v + '%', numberInput: true, onInput: (v) => { y.boostPercent = v; S.percentMemory = v; save(); refreshImpact(); } }),
          h('div', { class: 'row wrap' }, h('span', { class: 'faint', style: { fontSize: '13px' }, text: 'Quick picks:' }),
            ...[10, 20, 30, 50].map((n) => h('button', { class: 'chip' + (y.boostPercent === n ? ' on' : ''), type: 'button', text: n + '%', onclick: () => { y.boostPercent = n; S.percentMemory = n; save(); draw(); } })))) : null;
        refreshImpact();
        parts.push(
          h('label', { class: 'field', text: 'A fully evolved Pokemon is weak when its base stat total is below (drag, or type an exact number)' }),
          slider({ min: 100, max: FLOOR_MAX, step: 5, value: y.boostFloor, format: (v) => String(v), numberInput: true, onInput: (v) => { y.boostFloor = v; S.floorMemory = v; save(); refreshImpact(); } }),
          h('div', { class: 'row wrap' }, h('span', { class: 'faint', style: { fontSize: '13px' }, text: 'Quick picks:' }),
            ...[400, 450, 500, 550, 600].map((n) => h('button', { class: 'chip' + (y.boostFloor === n ? ' on' : ''), type: 'button', text: String(n), onclick: () => { y.boostFloor = n; S.floorMemory = n; save(); draw(); } }))),
          h('label', { class: 'field', text: 'How much are they boosted?' }),
          segControl([{ value: 'floor', label: 'Up to that total' }, { value: 'percent', label: 'By a percentage' }], pct ? 'percent' : 'floor', (v) => { y.boostPercent = v === 'percent' ? (S.percentMemory || 20) : 0; save(); draw(); }, false),
          pctControls,
          impact);
      } else {
        parts.push(h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'Off: no Pokemon is touched by this automatic rule. To raise or lower one chosen Pokemon\'s stats, use the per-Pokemon stat editor below.' }));
      }
      box.replaceChildren(...parts);
    };
    draw();
    return h('div', { class: 'card stack' }, sectionHead('Automatic stat boost', 'Boost the weak, fully evolved Pokemon in bulk: raise them to a minimum total, or by a percentage you choose. To boost one chosen Pokemon instead, use the per-Pokemon stat editor below.'), box);
  })();

  // --- per-species stat editor
  // Added (11/10/2026, upstream options review item 6 / web UI feedback item 9): quick mode is THE way to raise
  // or lower one chosen Pokemon's base stats by a percentage (it absorbed the old whole-dex "Boost chosen
  // Pokemon" picker, see web UI feedback item 25: that picker could only raise stats, quick mode can also lower
  // them, so the two were merged here). Fine-tune mode sets each of the six base stats independently, bounded to
  // the real minimum and maximum any Pokemon in this game has for that stat (f.statBounds, computed server side
  // from the loaded game's own species data, never a guessed range).
  const STAT_KEYS = [
    { key: 'hp', label: 'HP' }, { key: 'attack', label: 'Attack' }, { key: 'defense', label: 'Defense' },
    { key: 'spatk', label: 'Sp. Atk' }, { key: 'spdef', label: 'Sp. Def' }, { key: 'speed', label: 'Speed' },
  ];
  const statEditCard = (() => {
    const box = h('div', { class: 'stack' });
    const bounds = f.statBounds || Object.fromEntries(STAT_KEYS.map((k) => [k.key, { min: 0, max: 255 }]));
    const currentStats = (n) => (S.species.find((s) => s.n === n) || {}).bs || { hp: 50, attack: 50, defense: 50, spatk: 50, spdef: 50, speed: 50 };
    const draw = () => {
      const rows = y.statEdits.map((e, i) => {
        const modeRow = h('div', { class: 'row spread wrap' }, speciesButton(e.species, (n) => { e.species = n; save(); draw(); }),
          segControl([{ value: 'quick', label: 'Quick %' }, { value: 'fine', label: 'Fine-tune' }], e.mode, (v) => {
            if (v === 'fine' && e.hp === 0 && e.attack === 0 && e.defense === 0 && e.spatk === 0 && e.spdef === 0 && e.speed === 0) {
              Object.assign(e, currentStats(e.species));
            }
            e.mode = v; save(); draw();
          }, false),
          h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Remove', onclick: () => { y.statEdits.splice(i, 1); save(); draw(); } }, icon('x')));
        const body = e.mode === 'fine'
          ? h('div', { class: 'grid two' }, ...STAT_KEYS.map(({ key, label }) => h('div', null,
              h('div', { class: 'row spread' }, h('span', { class: 'muted', text: label }), h('span', { class: 'mono', style: { color: 'var(--cyan)' }, text: String(e[key]) })),
              slider({ min: bounds[key].min, max: bounds[key].max, step: 1, value: e[key], format: (v) => String(v), numberInput: true, onInput: (v) => { e[key] = v; save(); } }))))
          : h('div', null, h('div', { class: 'row spread' }, h('span', { class: 'muted', text: 'Change the whole stat spread by' }), h('span', { class: 'mono', style: { color: 'var(--cyan)' }, text: (e.percent >= 0 ? '+' : '') + e.percent + '%' })),
              slider({ min: -90, max: 300, step: 1, value: e.percent, format: (v) => (v >= 0 ? '+' : '') + v + '%', numberInput: true, onInput: (v) => { e.percent = v; save(); } }));
        return h('div', { class: 'stack', style: { padding: '12px', border: '1px solid var(--line)', borderRadius: '10px' } }, modeRow, body);
      });
      box.replaceChildren(
        rows.length ? h('div', { class: 'stack' }, ...rows) : h('div', { class: 'muted', text: 'No Pokemon chosen.' }),
        h('div', { style: { marginTop: '10px' } }, h('button', { class: 'btn small', type: 'button', onclick: () => openPicker({ title: 'Edit a Pokemon\'s stats', items: S.species, label: (s) => s.name, sub: (s) => '#' + s.n, onPick: (s) => { if (!y.statEdits.some((e) => e.species === s.n)) { y.statEdits.push({ species: s.n, mode: 'quick', percent: 10, hp: 0, attack: 0, defense: 0, spatk: 0, spdef: 0, speed: 0 }); save(); draw(); } } }) }, icon('plus'), 'Edit a Pokemon')),
        h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'Runs after the automatic stat floor above and after any whole-dex base stat randomization, so this edit is always the final word for that Pokemon.' }));
    };
    draw();
    return h('div', { class: 'card stack' }, sectionHead('Boost or edit a Pokemon\'s stats', 'Pick one Pokemon, see its real base stats, then raise or lower its whole spread by a percentage (Quick %) or set each stat precisely (Fine-tune).'), box);
  })();

  // --- evolutions
  const evoCard = (() => {
    const box = h('div', { class: 'stack' });
    const limited = S.rom.generation <= 2;
    const typeByValue = (v) => S.evoTypes.find((t) => t.value === v) || { level: false, item: false, move: false, species: false, label: v };
    // Live conflict checking added (11/10/2026, web UI feedback item 12). The old "dupes" check further down
    // only caught an exact from+to duplicate (two rows editing the very same evolution). The actual rule the
    // randomizer enforces is EvolutionEditor.overlaps: two evolutions of the SAME "from" Pokemon that use the
    // same trigger (the same kind of level up, the same item, the same move, and so on; day/night and
    // male/female variants are different triggers) can never both happen, whatever their targets are. The
    // frontend never asked about that rule, so a same-trigger conflict with different targets looked fine here
    // even though only one of the two edits would ever actually apply. Rather than re-implement that rule (with
    // its day/night and male/female exceptions) a second time in JS and risk it drifting from the real one, the
    // exact same static check is asked of the server via /api/evolution-conflicts every time the list changes.
    let conflictIndices = new Set();
    let conflictKey = '';
    async function refreshConflicts() {
      const key = JSON.stringify(y.evolutions);
      if (key === conflictKey) return;
      conflictKey = key;
      try {
        const r = await api('/api/evolution-conflicts', { evolutions: y.evolutions });
        if (JSON.stringify(y.evolutions) !== key) return; // the list changed again while this was in flight
        conflictIndices = new Set(r.conflicts || []);
      } catch (e) { conflictIndices = new Set(); }
      draw();
    }
    const draw = () => {
      refreshConflicts();
      // Restyled from a cramped multi-column table to a stacked list of full-width row cards (11/10/2026, web
      // UI feedback item 12), the same pattern the per-species stat editor list already uses just above, so
      // every picker button has room to show the full Pokemon/item/move name instead of being squeezed into a
      // narrow table column. The list scrolls instead of growing the card without bound once there are many rows.
      const rows = y.evolutions.map((e, i) => {
        const t = typeByValue(e.type);
        const sel = h('select', { 'aria-label': 'Method', style: { maxWidth: '220px' } }, S.evoTypes.filter((x) => !limited || x.value === 'LEVEL').map((x) => h('option', { value: x.value, text: x.label })));
        sel.value = e.type;
        sel.addEventListener('change', () => { e.type = sel.value; e.extra = 0; save(); draw(); });
        let extra;
        if (t.level) extra = h('input', { type: 'number', min: 2, max: 100, value: e.extra || 30, 'aria-label': 'Level', style: { maxWidth: '220px' }, onchange: (ev) => { e.extra = clamp(Number(ev.target.value) || 2, 2, 100); save(); draw(); } });
        else if (t.item) extra = itemButton(e.extra, (id) => { e.extra = id; save(); draw(); }, undefined, e.type.startsWith('STONE'));
        else if (t.move) extra = moveButton(e.extra, (id) => { e.extra = id; save(); draw(); });
        else if (t.species) extra = speciesButton(e.extra, (n) => { e.extra = n; save(); draw(); });
        else extra = h('span', { class: 'faint', text: 'no extra' });
        if (t.level && !e.extra) e.extra = 30;
        const conflicted = conflictIndices.has(i);
        return h('div', { class: 'stack', style: { padding: '12px', border: '1px solid ' + (conflicted ? 'var(--red)' : 'var(--line)'), borderRadius: '10px' } },
          h('div', { class: 'row spread wrap' },
            h('div', { class: 'row wrap', style: { flex: '1', minWidth: '0' } },
              speciesButton(e.from, (n) => { e.from = n; save(); draw(); }, 'Evolves from'),
              h('span', { class: 'faint', text: '→' }),
              speciesButton(e.to, (n) => { e.to = n; save(); draw(); }, 'Into')),
            h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Remove', onclick: () => { y.evolutions.splice(i, 1); save(); draw(); } }, icon('x'))),
          h('div', { class: 'grid two' }, h('div', null, h('label', { class: 'field', text: 'How' }), sel), h('div', null, h('label', { class: 'field', text: 'Detail' }), extra)),
          conflicted ? h('div', { class: 'notice bad', text: 'This uses the same trigger (' + t.label + ') as another evolution of this Pokemon, so one of them could never happen. Whichever is applied second is skipped.' }) : null);
      });
      const dupes = y.evolutions.filter((e, i) => y.evolutions.findIndex((o) => o.from === e.from && o.to === e.to) !== i);
      box.replaceChildren(
        limited ? h('div', { class: 'notice note', text: 'In this generation the game can only store the level of an existing level evolution, so only that kind of change works. Other edits are skipped with a note in the log.' }) : null,
        y.evolutions.length ? h('div', { class: 'stack', style: { maxHeight: '560px', overflowY: 'auto', paddingRight: '6px' } }, ...rows) : h('div', { class: 'muted', text: 'No evolution is changed. Add one, for example Eevee into Umbreon with a Moon Stone.' }),
        dupes.length ? h('div', { class: 'notice', text: 'Two rows change the same evolution (the same from and into): only the last one counts.' }) : null,
        h('div', { class: 'row wrap' },
          h('button', { class: 'btn small', type: 'button', onclick: () => { y.evolutions.push({ from: 0, to: 0, type: limited ? 'LEVEL' : 'STONE', extra: 0 }); save(); draw(); } }, icon('plus'), 'Change or add an evolution'),
          h('button', { class: 'btn small', type: 'button', disabled: limited, onclick: () => { y.removals.push({ from: 0, to: 0 }); save(); draw(); } }, icon('plus'), 'Remove an evolution')),
        y.removals.length ? h('table', { class: 'table' }, h('tbody', null, y.removals.map((r, i) => h('tr', null, h('td', null, speciesButton(r.from, (n) => { r.from = n; save(); draw(); }, 'From')), h('td', { style: { width: '90px', textAlign: 'center' }, class: 'faint', text: 'no longer →' }), h('td', null, speciesButton(r.to, (n) => { r.to = n; save(); draw(); }, 'Into')), h('td', { style: { width: '40px' } }, h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Remove', onclick: () => { y.removals.splice(i, 1); save(); draw(); } }, icon('x'))))))) : null);
    };
    draw();
    return h('div', { class: 'card stack' }, sectionHead('Evolutions', 'Choose how Pokemon evolve. The program checks that two evolutions of one Pokemon never use the same trigger.'), box);
  })();

  // --- fully evolved by level (independent of "Make Evolutions Easier")
  const fullEvoCard = (() => {
    const box = h('div', { class: 'stack' });
    const draw = () => {
      const on = y.fullyEvolvedByLevel > 0;
      if (on) S.fullEvoMemory = y.fullyEvolvedByLevel;
      const lbl = h('span', { class: 'mono', style: { color: 'var(--cyan)' }, text: 'level ' + y.fullyEvolvedByLevel });
      const sw = (() => { const i = h('input', { type: 'checkbox', 'aria-label': 'Every Pokemon is fully evolved by a level' }); i.checked = on; i.addEventListener('change', () => { y.fullyEvolvedByLevel = i.checked ? (S.fullEvoMemory || 40) : 0; save(); draw(); }); return h('label', { class: 'switch' }, i, h('span', { class: 'track' }), h('span', { class: 'thumb' })); })();
      box.replaceChildren(
        h('div', { class: 'opt' }, h('div', { class: 'name' }, h('span', { class: 't', text: 'Every Pokemon is fully evolved by level' }),
          infoButton('Every level-up evolution that happens later than this is moved earlier, so every Pokemon reaches its final stage by this level (the middle stage of a three-stage line by about 75% of it). It changes the game\'s evolution levels for everyone: your Pokemon, rivals and every other trainer. It works on its own: you do not need "Make Evolutions Easier".', WIKI.makeEvolutionsEasier)), sw),
        on ? h('div', { class: 'stack' },
          h('div', { class: 'row spread' }, h('label', { class: 'field', text: 'Final stage reached by' }), lbl),
          slider({ min: 15, max: 60, step: 1, value: y.fullyEvolvedByLevel, format: (v) => 'level ' + v, onInput: (v) => { y.fullyEvolvedByLevel = v; S.fullEvoMemory = v; save(); lbl.textContent = 'level ' + v; } }),
          h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'Only level-up evolutions move earlier. Stone, trade and friendship evolutions keep their method (a stone Pokemon can already evolve at any level). Applies to all Pokemon in the game, rivals included. Trainers whose Pokemon are set to "evolve as far as possible" follow these new levels. If you also use the evolution level scale below, it is applied afterwards, on top of this.' })) : h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'Off: evolution levels are not changed by this rule.' }));
    };
    draw();
    return h('div', { class: 'card stack' }, sectionHead('Fully evolved by a level', 'Pull late evolutions earlier so everything reaches its final stage by a level you choose.'), box);
  })();

  // --- stone evolutions also by level (Phase 19, 13/10/2026)
  const stoneLevelCard = (() => {
    const i = h('input', { type: 'checkbox', 'aria-label': 'Stone evolutions can also be reached by leveling' });
    i.checked = !!y.stoneEvolutionsAlsoLevel;
    const tip = 'A Pokemon that has exactly one evolution, and gets it with a stone (Vulpix, Growlithe, Pikachu, Shellder, Staryu...), will also evolve by leveling, at a level worked out from how similar Pokemon evolve. The stone keeps working. Pokemon with several evolutions (Eevee, Gloom, Poliwhirl...) and anything you set yourself in the evolution editor are left exactly as they are. Not available in Gen 1 and 2, whose games cannot store a second evolution.';
    i.addEventListener('change', () => { y.stoneEvolutionsAlsoLevel = i.checked; save(); });
    const gen12 = S.rom.generation <= 2;
    return h('div', { class: 'card stack' + (gen12 ? ' card-disabled' : '') },
      sectionHead('Stone evolutions by leveling', 'Pokemon with a single stone evolution can also evolve by leveling.'),
      h('div', { class: 'opt' }, h('div', { class: 'name' }, h('span', { class: 't', text: 'Stone evolutions can also be reached by leveling' }), infoButton(tip)),
        h('label', { class: 'switch' }, i, h('span', { class: 'track' }), h('span', { class: 'thumb' }))),
      gen12 ? h('div', { class: 'notice', text: 'This game cannot store a second evolution for a Pokemon, so the setting is ignored here.' })
        : h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'Only Pokemon with exactly one evolution are changed. The level follows "Fully evolved by" and "Make Evolutions Easier" when those are on, and the evolution level scale below.' }));
  })();

  // --- evolution level scale
  // Moved out of evoCard and given its own explicit toggle (11/10/2026, web UI feedback item 28). It used to be
  // a bare slider with no visible on/off state (0% silently meant "off"), and it lived at the bottom of the
  // custom-evolutions card with no visual link to "Make Evolutions Easier" even though the two interact: see
  // GameRandomizer.maybeApplyEvolutionImprovements() (Make Evolutions Easier) running BEFORE
  // maybeEditEvolutionLevels() (this scale), so when both are on, this percentage is taken off the level
  // Make Evolutions Easier already produced, not the original vanilla level. renderPokemonSection() below
  // groups this card with the native "Evolution fixes" group (which holds Make Evolutions Easier) under one
  // "Evolution timing" family heading, the same pattern used for the Base Stats family.
  const evoScaleCard = (() => {
    const box = h('div', { class: 'stack' });
    const exampleLevel = f.exampleEvolutionLevel;
    const draw = () => {
      const s = y.evolutionScale;
      // The preview always uses an example level at or above the current threshold (the real rule only touches
      // evolutions at "From level" or higher), and matches EvolutionEditor.java's own math exactly: round down,
      // floored at 2 (the game's own minimum level), never at the threshold itself.
      const previewFrom = exampleLevel ? Math.max(exampleLevel, s.minLevel) : null;
      const preview = previewFrom && s.enabled
        ? 'Level ' + previewFrom + ' → approximately Level ' + Math.max(2, Math.round(previewFrom * (1 - s.percent / 100)))
        : null;
      const minLbl = h('span', { class: 'mono', style: { color: 'var(--cyan)' }, text: String(s.minLevel) });
      const pctLbl = h('span', { class: 'mono', style: { color: 'var(--cyan)' }, text: '-' + s.percent + '%' });
      box.replaceChildren(
        h('div', { class: 'opt' },
          h('div', { class: 'name' }, h('span', { class: 't', text: 'Lower high-level evolutions' }),
            infoButton('Lowers every level-up evolution at or above the chosen level by a percentage. Evolutions you set yourself above are never touched by it.')),
          (() => { const i = h('input', { type: 'checkbox' }); i.checked = s.enabled; i.addEventListener('change', () => { s.enabled = i.checked; save(); draw(); }); return h('label', { class: 'switch' }, i, h('span', { class: 'track' }), h('span', { class: 'thumb' })); })()),
        h('div', { class: 'stack' + (s.enabled ? '' : ' dep-off') },
          h('div', { class: 'row spread' }, h('label', { class: 'field', text: 'From level' }), minLbl),
          slider({ min: 2, max: 60, step: 1, value: s.minLevel, format: (v) => 'from level ' + v, disabled: !s.enabled, onInput: (v) => { s.minLevel = v; save(); minLbl.textContent = String(v); }, onCommit: () => draw() }),
          h('div', { class: 'row spread' }, h('label', { class: 'field', text: 'Lower by' }), pctLbl),
          slider({ min: 5, max: 90, step: 5, value: s.percent, format: (v) => '-' + v + '%', disabled: !s.enabled, onInput: (v) => { s.percent = v; save(); pctLbl.textContent = '-' + v + '%'; }, onCommit: () => draw() }),
          preview ? h('div', { class: 'faint', style: { fontSize: '13px' }, text: preview + ' (rounded, for a representative evolution level in this game).' }) : null,
          h('div', { class: 'faint', style: { fontSize: '13px' }, text: 'The evolutions you set yourself above are never lowered. If "Make Evolutions Easier" is also on, this percentage is taken off the level Make Evolutions Easier already produced, not the original vanilla level.' })));
    };
    draw();
    return h('div', { class: 'card stack' }, sectionHead('Evolution level scale', 'Make high-level evolutions happen earlier, by a percentage instead of a fixed level.'), box);
  })();

  // spriteCard is built but deliberately left out of the object below (11/10/2026, web UI feedback item 10):
  // the user decided player sprite replacement should not be exposed as a web control for the v1.0.0 release.
  // The underlying feature (DsPlayerSprites, the CLI's --player-sprite and --player-sprite-female flags,
  // /api/player-sprites/export) is untouched and still usable from the CLI. Do not expose spriteCard anywhere
  // in this interface without checking with the user first.
  return { shinyCard, lockeCard, eggCard, shopCard, boostCard, statEditCard, evoCard, fullEvoCard, stoneLevelCard, evoScaleCard, megaCard, battleRulesCard, itemsCard };
}

// ------------------------------------------------------------------ view 4: run
function needsConsent() {
  const y = S.yao; const f = S.rom.features;
  return (y.shinyPercent > 0 && f.shiny.available) || (y.locke !== 'none' && f.lockeCenters.available);
}

function defaultOutputName() {
  const base = S.rom.path.split(/[\\/]/).pop().replace(/\.[^.]*$/, '');
  return base + ' randomized' + (S.rom.threeDs ? (S.run.threeDsFormat === 'cxi' ? '.cxi' : '') : '.' + S.rom.extension);
}

function renderRun() {
  const root = $('#view-run');
  root.replaceChildren();
  const g = S.rom; const y = S.yao;
  if (!S.run.output) {
    const dir = g.path.replace(/[\\/][^\\/]*$/, '');
    const sep = g.path.includes('\\') ? '\\' : '/';
    S.run.output = dir + sep + defaultOutputName();
  }
  S.run.nameHint = defaultOutputName();
  const outInput = h('input', { type: 'text', value: S.run.output, 'aria-label': 'Where to save', oninput: (e) => { S.run.output = e.target.value; } });
  // Changed (13/10/2026, web UI feedback): the seed is never empty. A fresh random one is shown every time this
  // screen loads (so what is shown is exactly what will be used), editable, with a dice button to roll another.
  const newSeed = () => String(Math.floor(Math.random() * 9e14) + 1e14);
  S.run.seed = newSeed();
  setTimeout(() => showPathEnd(outInput), 0);
  const seedInput = h('input', { type: 'text', value: S.run.seed, inputmode: 'numeric', 'aria-label': 'Seed', oninput: (e) => { S.run.seed = e.target.value; persist(); } });
    const rerollBtn = h('button', { class: 'btn', type: 'button', title: 'Roll a new random seed', 'aria-label': 'Roll a new random seed', style: { flex: 'none', padding: '0 12px' }, onclick: () => { S.run.seed = newSeed(); seedInput.value = S.run.seed; persist(); } }, icon('dice'));
  const toggle = (label, tipText, key) => {
    const i = h('input', { type: 'checkbox' }); i.checked = S.run[key];
    i.addEventListener('change', () => { S.run[key] = i.checked; persist(); });
    return h('div', { class: 'opt' }, h('div', { class: 'name' }, h('span', { class: 't', text: label }), infoButton(tipText)), h('label', { class: 'switch' }, i, h('span', { class: 'track' }), h('span', { class: 'thumb' })));
  };
  const skipped = [];
  if (y.shinyPercent > 0 && !g.features.shiny.available) skipped.push('Shiny chance');
  if (y.eggBox.count > 0 && !g.features.eggBox.available) skipped.push('Egg box');
  if (y.locke !== 'none' && !g.features.lockeCenters.available) skipped.push('Locke (Pokemon Center part)');

  const stats = h('div', { class: 'summary-grid' },
    h('div', { class: 'stat' }, h('div', { class: 'v', text: changedCount() }), h('div', { class: 'l', text: 'Randomizer options changed' })),
    h('div', { class: 'stat' }, h('div', { class: 'v', text: yaoCount() }), h('div', { class: 'l', text: 'Extras in use' })),
    h('div', { class: 'stat' }, h('div', { class: 'v', text: g.generation }), h('div', { class: 'l', text: 'Generation' })));
  const term = h('div', { class: 'terminal hidden', id: 'terminal', 'aria-live': 'polite' });
  const bar = h('div', { class: 'progress hidden', id: 'bar' }, h('i'));
  const result = h('div', { id: 'result' });
  const go_ = h('button', { class: 'btn primary big', id: 'goBtn', onclick: () => startRun(false) }, icon('play'), 'Randomize');
  root.append(
    h('h2', { text: 'Ready when you are' }),
    h('div', { class: 'muted', text: 'Your original game is never changed. A new file is written where you say.' }),
    h('div', { class: 'grid two', style: { marginTop: '16px' } },
      h('div', { class: 'card stack' },
        h('h3', { text: g.threeDs ? 'Save the randomized game in this folder' : 'Save the randomized game as' }),
        h('div', { class: 'row' }, outInput, h('button', { class: 'btn', style: { flex: 'none', whiteSpace: 'nowrap' }, onclick: () => nativeBrowse({ mode: 'folder', startDir: S.run.output.replace(/[\\/][^\\/]*$/, ''), title: 'Choose the folder to save the randomized game in', onPick: (p) => { const sep = p.includes('\\') ? '\\' : '/'; S.run.output = p.replace(/[\\/]+$/, '') + sep + defaultOutputName(); outInput.value = S.run.output; showPathEnd(outInput); } }) }, icon('folder'), 'Choose folder')),
        g.threeDs ? threeDsFormatPicker(outInput) : null,
        h('div', { class: 'faint', style: { fontSize: '13px' }, text: g.threeDs ? 'You can type just a name or a folder. The right extension or folder layout is added for you, and your original game is never replaced.' : 'You can type just a name. The right extension (.' + g.extension + ') is added for you, and your original game is never replaced.' }),
        h('div', null, h('label', { class: 'field', text: 'Seed' }), h('div', { class: 'row', style: { alignItems: 'stretch' } }, seedInput, rerollBtn)),
        toggle('Save a detailed log next to the game', 'The log lists everything that was randomized and the notes about the extras.', 'log'),
        toggle('Also write a guide to the randomized game (HTML)', 'A page with every change, spoiler-safe until you reveal it. It reveals the whole game, so it can be cheating if you compete against someone.', 'guide'),
      ),
      h('div', { class: 'card stack' }, h('h3', { text: 'Summary' }), stats,
        skipped.length ? h('div', { class: 'notice', text: 'This game cannot take: ' + skipped.join(', ') + '. Those are skipped.' }) : null,
        needsConsent() ? h('div', { class: 'notice note', text: 'You will be asked to accept the notice about code patches before the run.' }) : null,
        h('div', { class: 'row wrap', style: { marginTop: 'auto' } }, go_, h('button', { class: 'btn', onclick: () => go('rand'), text: '← Randomizer' }))),
    ),
    h('div', { class: 'stack', style: { marginTop: '18px' } }, bar, term, result),
  );
  if (S.job) pollJob(S.job, true);
}

function threeDsFormatPicker(outInput) {
  const mk = (value, title, text) => {
    const r = h('input', { type: 'radio', name: 'threeDsFormat', value });
    r.checked = (S.run.threeDsFormat || 'folder') === value;
    r.addEventListener('change', () => {
      S.run.threeDsFormat = value; persist();
      S.run.output = ''; renderRun();
    });
    return h('label', { class: 'opt', style: { cursor: 'pointer' } }, r, h('div', null, h('div', { class: 'name' }, h('span', { class: 't', text: title })), h('div', { class: 'faint', style: { fontSize: '13px' }, text })));
  };
  return h('div', { class: 'stack' },
    mk('folder', 'Luma3DS patch folder', 'Small. Needs the same game version installed, plus Luma3DS game patching on the console or the mods folder in Citra. Saved under luma/titles/<title id>.'),
    mk('cxi', 'Single .cxi game file', 'Big (about the size of the game). A standalone file you can install or load directly in an emulator.'));
}

async function startRun(overwrite) {
  const g = S.rom;
  if (needsConsent() && !S.run.consent) {
    const handle = openModal({
      title: 'Before changing the game\'s code',
      body: h('div', { class: 'stack' }, h('p', { text: S.info.notices.codePatchEn }), h('details', null, h('summary', { class: 'muted', text: 'Español' }), h('p', { class: 'muted', text: S.info.notices.codePatchEs }))),
      footer: (close) => [h('button', { class: 'btn ghost', text: 'Reject (run without these options)', onclick: () => { close(); S.run.consent = false; runNow(overwrite, false); } }), h('button', { class: 'btn primary', text: 'Accept and apply', onclick: () => { S.run.consent = true; close(); runNow(overwrite, true); } })],
    });
    return handle;
  }
  runNow(overwrite, S.run.consent);
}

async function runNow(overwrite, consent) {
  if (S.run.guide && !S.run.guideAck) {
    const handle = openModal({
      title: 'The guide reveals the whole game',
      body: h('div', { class: 'stack' }, h('p', { text: S.info.notices.guideEn }), h('details', null, h('summary', { class: 'muted', text: 'Español' }), h('p', { class: 'muted', text: S.info.notices.guideEs }))),
      footer: (close) => [h('button', { class: 'btn ghost', text: 'No guide', onclick: () => { close(); S.run.guide = false; persist(); runNow(overwrite, consent); } }), h('button', { class: 'btn primary', text: 'Yes, write the guide', onclick: () => { S.run.guideAck = true; close(); runNow(overwrite, consent); } })],
    });
    return;
  }
  const btn = $('#goBtn'); if (btn) btn.disabled = true;
  try {
    const r = await api('/api/run', { values: S.values, yao: cleanYao(), seed: S.run.seed, output: S.run.output, threeDsFormat: S.run.threeDsFormat || 'folder', guide: S.run.guide, saveLog: S.run.log, acceptCodePatches: consent, overwrite });
    S.job = { id: r.job, output: r.resolvedOutput || r.output, from: 0, howToUse: r.howToUse };
    if (r.outputWarnings && r.outputWarnings.length) r.outputWarnings.forEach((w) => addLine('WARNING: ' + w));
    $('#terminal').classList.remove('hidden'); $('#terminal').replaceChildren();
    $('#bar').classList.remove('hidden', 'done', 'fail');
    $('#result').replaceChildren();
    if (r.warnings && r.warnings.length) r.warnings.forEach((w) => addLine('WARNING: ' + w));
    pollJob(S.job, false);
  } catch (e) {
    if (btn) btn.disabled = false;
    if (e.data && e.data.exists) {
      openModal({ title: 'That file already exists', body: h('p', { text: e.data.exists }), footer: (close) => [h('button', { class: 'btn ghost', text: 'Cancel', onclick: close }), h('button', { class: 'btn danger', text: 'Replace it', onclick: () => { close(); startRun(true); } })] });
    } else toast(e.message, 'bad', 9000);
  }
}

function addLine(text) {
  const term = $('#terminal'); if (!term) return;
  const cls = /^ERROR|exception|Exception/.test(text) ? 'e' : /^WARNING|Warning/.test(text) ? 'w' : /Randomized successfully|written to/.test(text) ? 'g' : '';
  term.append(cls ? h('span', { class: cls, text: text + '\n' }) : document.createTextNode(text + '\n'));
  term.scrollTop = term.scrollHeight;
}

async function pollJob(job, resume) {
  if (resume) { const t = $('#terminal'); if (t) { t.classList.remove('hidden'); t.replaceChildren(); } job.from = 0; }
  const goBtn = $('#goBtn');
  for (;;) {
    let r;
    try { r = await api('/api/job?id=' + encodeURIComponent(job.id) + '&from=' + job.from); } catch (e) { toast(e.message, 'bad'); return; }
    r.lines.forEach((l) => { if (!/^\d+$/.test(l.trim())) addLine(l); });
    job.from = r.next;
    const bar = $('#bar');
    if (r.status !== 'running') {
      if (bar) { bar.classList.remove('hidden'); bar.classList.add(r.status === 'done' ? 'done' : 'fail'); }
      if (goBtn) goBtn.disabled = false;
      showResult(r, job);
      S.job = null;
      return;
    }
    await new Promise((res) => setTimeout(res, 800));
    if (S.view !== 'run' && !$('#terminal')) return;
  }
}

function showResult(r, job) {
  const box = $('#result'); if (!box) return;
  if (r.status === 'done') {
    box.replaceChildren(h('div', { class: 'card stack' }, h('h2', { text: 'Done in ' + r.seconds + (r.seconds === 1 ? ' second' : ' seconds') }),
      h('div', { class: 'mono', style: { overflowWrap: 'anywhere' }, text: job.output }),
      h('div', { class: 'row wrap' },
        h('button', { class: 'btn primary', onclick: () => openFolder(job.id), text: 'Go to the folder' }),
        h('button', { class: 'btn', onclick: () => { navigator.clipboard && navigator.clipboard.writeText(job.output).then(() => toast('Path copied.', 'ok')); }, text: 'Copy the path' }),
        h('button', { class: 'btn', onclick: () => { S.run.output = ''; S.run.seed = ''; renderRun(); }, text: 'Randomize again' }),
        h('button', { class: 'btn ghost', onclick: () => { S.rom = null; enableSteps(); go('game'); }, text: 'Choose another game' })),
      job.howToUse ? h('div', { class: 'faint', style: { fontSize: '13px' }, text: job.howToUse }) : null));
    toast('Randomized successfully!', 'ok', 7000);
  } else if (r.status === 'cancelled') {
    box.replaceChildren(h('div', { class: 'notice', text: 'The run was cancelled.' }));
  } else {
    box.replaceChildren(h('div', { class: 'card stack' }, h('h2', { text: 'Something went wrong' }), h('div', { class: 'muted', text: 'The details are in the black box above. Nothing was written to your original game.' })));
  }
}

// Added (13/10/2026, web UI feedback): replaces the "Open the guide" button (a blob tab the browser kept blocking).
// The server opens the folder holding the game, the log and the guide in the OS file manager.
// Updated (14/10/2026): shows a brief toast with the resolved path so the user can confirm where it opened.
async function openFolder(id) {
  try {
    const r = await api('/api/job/open-folder', { id });
    if (r && r.path) toast('Opened: ' + r.path, 'ok', 4000);
  } catch (e) { toast(e.message, 'bad', 8000); }
}

async function openGuide(id) {
  // Fixed (13/10/2026, web UI feedback): window.open() called after an await is no longer "a direct result of a
  // click" as far as most browsers are concerned, so it was silently popup-blocked (or opened a dead blank tab)
  // instead of the guide. Opening the tab synchronously, inside the click handler, and only navigating it once
  // the fetch resolves keeps it within that same trusted click, so it is never blocked.
  const tab = window.open('', '_blank');
  try {
    const res = await fetch('/api/job/guide?id=' + encodeURIComponent(id), { headers: { 'X-YAO-Token': TOKEN } });
    if (!res.ok) throw new Error('The guide is not available.');
    const blob = new Blob([await res.text()], { type: 'text/html' });
    const url = URL.createObjectURL(blob);
    if (tab) { tab.location.replace(url); } else { window.open(url, '_blank'); }
  } catch (e) {
    if (tab) tab.close();
    toast(e.message, 'bad');
  }
}

// ------------------------------------------------------------------ start
async function boot() {
  initEffects();
  $('#themeBtn').addEventListener('click', () => {
    const light = document.documentElement.dataset.theme !== 'light';
    document.documentElement.dataset.theme = light ? 'light' : 'dark';
    safeStorage('set', 'yao.theme', light ? 'light' : 'dark');
  });
  const savedTheme = safeStorage('get', 'yao.theme');
  if (savedTheme === 'light') document.documentElement.dataset.theme = 'light';
  $$('#steps button').forEach((b) => b.addEventListener('click', () => { if (!b.disabled) go(b.dataset.view); }));
  try {
    S.info = await api('/api/info');
    $('#version').textContent = 'based on FVX ' + S.info.version;
    if (S.info.rom) {
      // the page was reloaded while a game was loaded: take it up again
      const path = S.info.rom.path; S.rom = null;
      await loadRom(path);
      return;
    }
  } catch (e) {
    toast(e.message, 'bad', 12000);
  }
  enableSteps();
  renderGame();
}
boot();
