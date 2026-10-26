// Player Menu 2.0 in-game checks. LOCAL SANDBOX servers only.  Usage: node menu.js <phase> [botName]
//   nav    - hotbar head and /menu open the hub; every hub tile honest; Skills -> category -> skill pages
//            (training, attributes, effects with status, next levels), browsing arrows, Back/Home/Close,
//            the four renamed skills. Works for ops and non-ops (non-ops skip the level-setting checks).
//   names  - (op) renamed skills in chat: level-up message, kill/command output, /cerb skill accepts both names
//   safety - (op) with the menu open: shift/number-key/drop/double clicks move nothing; outside it the head
//            can't be dropped, swapped, placed, framed or put on an armor stand; one head after rejoin/respawn
//   perf   - (op) browsing every screen three times opens no database connection and rebuilds no snapshot
//   shelves - (op) decision B: three chained powered shelves swap the whole hotbar except the head and the admin chest;
//            the shelf items meant for those two slots stay on the shelves; nothing is lost or duplicated
//   motion - (op) Player Menu 2.1: opening reveal (centre out, buttons work at once), header sweep between screens,
//            page-turn sounds, the progress strip filling, new progress since the last close (glow, lines, one chime,
//            one level-up flash), category tabs, last opened category, animations cancelled on fast clicks and
//            none left running on an idle open menu
//   smithing - (op) Smithing's growth on its pages: the Utility tile and the skill page name +0.25 Fortitude and +0.3%
//            wear resistance a level, what they give now and at the next level, both live; Next levels shows wear
//            resistance at the milestones and keeps the forged-quality odds; the Stats page shows the wear resistance
const mineflayer = require('mineflayer');
const phase = process.argv[2] || 'nav';
const NAME = process.argv[3] || 'CerbSmoke';
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`); };

let bot;
function connect() {
  bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: NAME, version: '1.21.11', auth: 'offline' });
  bot.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
  bot.on('kicked', r => { console.log('KICKED', r); process.exit(2); });
  return new Promise(r => bot.once('spawn', r));
}

const plain = v => {
  if (v == null) return '';
  if (typeof v === 'string') { try { return plain(JSON.parse(v)); } catch { return v; } }
  const s = JSON.stringify(v);
  const nbt = [...s.matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(m => m[1]);
  if (nbt.length) return nbt.join('');
  return [...s.matchAll(/"text":"([^"]*)"/g)].map(m => m[1]).join('');
};
const title = () => bot.currentWindow ? plain(bot.currentWindow.title) : '';
const slot = i => bot.currentWindow && bot.currentWindow.slots[i];
const slotName = i => { const it = slot(i); return it ? plain(it.customName) : ''; };
const slotLore = i => { const it = slot(i); return it && it.customLore ? it.customLore.map(plain).join(' | ') : ''; };
/** Lore as one line: wrapped lines joined, indentation collapsed (so checks don't depend on wrapping). */
const flat = i => { const it = slot(i); return it && it.customLore ? it.customLore.map(plain).join(' ').replace(/\s+/g, ' ').trim() : ''; };
const findSlot = re => { if (!bot.currentWindow) return -1; for (let i = 0; i < 54; i++) if (re.test(slotName(i))) return i; return -1; };
async function waitTitle(re, ms = 4000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (bot.currentWindow && re.test(title())) return true; await sleep(100); } return false; }
async function waitClosed(ms = 3000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (!bot.currentWindow) return true; await sleep(100); } return false; }
async function click(i, wait = 700) { await bot.clickWindow(i, 0, 0); await sleep(wait); }
async function cmd(c, wait = 1000) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const isOp = () => process.env.OP !== '0';
const SCREEN = name => new RegExp('^✦ ' + name.replace(/[&]/g, '\\$&') + '$');
const heads = () => bot.inventory.slots.filter(it => it && it.name === 'player_head').length;

async function closeAny() { if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); } }

async function openViaHead() {
  await closeAny();
  bot.setQuickBarSlot(8); await sleep(300);
  bot.activateItem(); await sleep(300); bot.deactivateItem();
  const ok = await waitTitle(SCREEN('Player Menu'));
  await sleep(300); // Player Menu 2.1: the content appears over 4 ticks after the window opens
  return ok;
}

// ── nav ───────────────────────────────────────────────────────────

async function nav() {
  if (isOp()) {
    for (const [s, l] of [['Blade_Mastery', 3], ['Navigation', 2], ['Survival', 4], ['First_Aid', 0]]) await cmd(`/cerb skill ${NAME} ${s} level ${l}`, 400);
  }
  const head = bot.inventory.slots[44];
  check('Player Menu head sits in hotbar slot 9', !!head && head.name === 'player_head' && /Player Menu/.test(plain(head.customName)));
  check('Exactly one Player Menu head', heads() === 1, heads() + ' heads');

  check('Right-clicking the head opens the hub', await openViaHead(), title());
  check('Hub header: realm and total skill level', /R\d+ \w+/.test(slotLore(4)) && /Total skill level: \d+ \/ 3,?500/.test(slotLore(4)), slotLore(4));
  check('Hub header: all six attributes', /Power .*Agility .*Vitality .*Fortitude .*Essence .*Fortune /.test(slotLore(4)), slotLore(4));
  check('Health tile shows numbers, bar and Vitality skills', /Health/.test(slotName(11)) && /\d \/ \d/.test(slotLore(11)) && /\d+%/.test(slotLore(11)) && /Survival/.test(slotLore(11)) && /Heavy Armor Mastery/.test(slotLore(11)), slotLore(11));
  check('Realm tile: honest about breakthroughs', /Realm: /.test(slotName(13)) && /In development: breakthroughs/.test(slotLore(13)), slotLore(13));
  check('Essence tile shows pool and regeneration', /Essence/.test(slotName(15)) && /Regeneration: [\d.,]+ \/ s/.test(slotLore(15)), slotLore(15));
  const expect = { 19: ['Skills', 'Available'], 21: ['Stats & Attributes', 'Available'], 23: ['Ascension', 'Available'],
    25: ['Equipment', 'Available'], 28: ['Bestiary', 'Available'], 30: ['World & Exploration', 'Available'],
    32: ['Collections', 'In development'], 34: ['Recipes', 'In development'], 38: ['Quests', 'In development'],
    40: ['Classes & Abilities', 'Available'], 42: ['Guide', 'Available'] };
  const wrong = Object.entries(expect).filter(([s, [n, st]]) => slotName(+s) !== n || !slotLore(+s).includes(st)).map(([s]) => s + ':' + slotName(+s));
  check('Every hub destination is there with an honest state', wrong.length === 0, wrong.join(', '));
  check('Hub has Close but no Back/Home', slotName(53) === 'Close' && slotName(45) === '' && slotName(49) === '', [slotName(45), slotName(49), slotName(53)].join('/'));

  check('Available tiles still say what inside them is not built', /Breakthroughs are not built yet/.test(flat(23)) && /Kill tracking is not built yet/.test(flat(28)), flat(23) + ' || ' + flat(28));
  let at = chat.length;
  await click(32);
  check('An in-development tile explains itself and stays on the hub', !!saw(/Collections is in development/, at) && SCREEN('Player Menu').test(title()), chat.slice(at).join(' / '));

  await click(19);
  check('Skills opens', await waitTitle(SCREEN('Skills')), title());
  check('Skills header: totals', /Total skill level/.test(slotLore(4)) && /Skills you can train today: \d+ \/ 35/.test(slotLore(4)), slotLore(4));
  check('Three categories', slotName(20) === 'Combat Skills' && slotName(22) === 'Magic Skills' && slotName(24) === 'Utility Skills');
  check('"How skills work" explains the status labels', /Live.*Against players only.*Not active yet/.test(slotLore(31)), slotLore(31));

  await click(20);
  check('Combat category opens', await waitTitle(SCREEN('Combat Skills')), title());
  check('Blade Mastery tile: level, attribute, speed, effects, training', /^Blade Mastery\s+Lv \d+$/.test(slotName(10))
      && /\+1 Power \(swords\) per level/.test(flat(10)) && /\+0\.3% sword attack speed per level/.test(flat(10))
      && /1 vs players/.test(flat(10)) && /Trains by kills: any sword/.test(flat(10)), slotName(10) + ' | ' + flat(10));
  check('Heavy Armor Mastery tile says its former name', /^Heavy Armor Mastery/.test(slotName(14)) && /Formerly Heavy Armor Training/.test(slotLore(14)), slotName(14) + ' | ' + slotLore(14));

  await click(10);
  check('Skill page opens', await waitTitle(SCREEN('Blade Mastery')), title());
  check('Skill header: category and level', /Combat Skills/.test(slotLore(4)) && /Level \d+ \/ 100/.test(slotLore(4)), slotLore(4));
  check('Progress strip is labelled', /Level \d+ → \d+: \d+%|Max level/.test(slotName(10)) && slotName(10) === slotName(16), slotName(10));
  check('How to train: kills with swords, XP per mob level', /Kill mobs while holding: any sword\./.test(flat(20)) && /Lv1 \d+ · Lv25 \d+ · Lv50 \d+ · Lv100 \d+/.test(flat(20)), flat(20));
  check('Attributes & speed: per level, now, and what it does', /Each level gives: \+1 Power \(swords\) \+0\.3% sword attack speed/.test(flat(22))
      && /Sword damage \(mobs\): x\d\.\d\d → x\d\.\d\d/.test(flat(22)) && /Sword attack speed: [+-][\d.]+% → [+-][\d.]+%/.test(flat(22))
      && /Attack speed: ● Active/.test(flat(22)) && /pause during PvP/.test(flat(22)), flat(22));
  check('Indented lines keep their indent', (slot(22).customLore || []).map(plain).some(l => /^  \+1 Power/.test(l)), slotLore(22));
  check('Effects carry their status', /Extra sword damage ◐ Against players only Now: [\d.]+%/.test(flat(24)), flat(24));
  check('Next levels: milestones with XP', /At level \d+ +\([\d,]+ XP away\)/.test(slotLore(31)), slotLore(31));
  check('Browse arrows name the neighbours', /Martial Expertise ▶/.test(slotName(43)) && /◀ Critical Strike/.test(slotName(37)), slotName(37) + ' / ' + slotName(43));
  await click(43);
  check('Next arrow browses in place', await waitTitle(SCREEN('Martial Expertise')), title());
  check('Bare-hand training reads naturally', /Kill mobs while holding: an empty hand\./.test(flat(20)), flat(20));
  await click(45);
  check('Back after browsing returns to the category', await waitTitle(SCREEN('Combat Skills')), title());

  await click(45);
  await waitTitle(SCREEN('Skills'));
  await click(24);
  check('Utility category opens', await waitTitle(SCREEN('Utility Skills')), title());
  const pf = findSlot(/^Pathfinding/), fm = findSlot(/^Field Medicine/), sv = findSlot(/^Survival/);
  check('Pathfinding (formerly Navigation) trains by exploring', pf >= 0 && /Formerly Navigation/.test(slotLore(pf)) && /Trains: Enter a chunk/.test(slotLore(pf)), pf >= 0 ? slotLore(pf) : 'missing');
  check('Field Medicine (formerly First Aid) is honest: no training yet', fm >= 0 && /Formerly First Aid/.test(slotLore(fm)) && /No way to train it yet/.test(slotLore(fm)), fm >= 0 ? slotLore(fm) : 'missing');
  await click(pf);
  await waitTitle(SCREEN('Pathfinding'));
  check('Pathfinding page: chunk XP, and movement speed from the attribute engine', /Enter a chunk you have never visited: 5 XP/.test(flat(20))
      && /\+0\.1% movement speed/.test(flat(22)) && /Movement speed: \+[\d.]+% → \+[\d.]+%/.test(flat(22)) && /Formerly Navigation/.test(flat(4)), flat(20) + ' || ' + flat(22));
  await click(45); await waitTitle(SCREEN('Utility Skills'));
  await click(fm); await waitTitle(SCREEN('Field Medicine'));
  check('Untrainable skill page says so', /Nothing in the game trains this skill yet/.test(flat(20)) && /In development/.test(flat(20)), flat(20));
  await click(45); await waitTitle(SCREEN('Utility Skills'));
  await click(sv); await waitTitle(SCREEN('Survival'));
  check('Survival page: fire resistance says which fire counts', /Fire resistance ● Live Now: [\d.]+%.*Flames, burning afterwards, campfires, magma blocks and fire spells; lava only partly/.test(flat(24)), flat(24));
  check('Survival page: live resistances with values; projectiles train it', /Fire resistance ● Live Now: [\d.]+%/.test(flat(24)) && /Take hits from mobs/.test(flat(20)) && /arrows and other projectiles included/.test(flat(20)) && /idle for a minute/.test(flat(20)), flat(20));
  if (isOp()) check('Survival 4 shows 4.0% fire resistance, 5.0% next', /Fire resistance ● Live Now: 4\.0% next level 5\.0%/.test(flat(24)), flat(24));

  await click(49);
  check('Home returns to the hub', await waitTitle(SCREEN('Player Menu')), title());
  await click(21);
  check('Stats opens from the hub', await waitTitle(SCREEN('Stats & Attributes')), title());
  await click(45);
  check('Back from Stats returns to the hub', await waitTitle(SCREEN('Player Menu')), title());
  await systems();
  await click(53);
  check('Close closes', await waitClosed(), title());

  await cmd('/menu', 1200);
  check('/menu opens the hub', await waitTitle(SCREEN('Player Menu')), title());
  await closeAny();
  check('Still exactly one head after browsing', heads() === 1 && bot.inventory.slots[44] && bot.inventory.slots[44].name === 'player_head', heads() + ' heads');
}

/** The destinations built on existing systems: Ascension, Equipment, Bestiary, World, Guide. Starts and ends on the hub. */
async function systems() {
  await click(13);
  check('Realm tile opens Ascension', await waitTitle(SCREEN('Ascension')), title());
  check('Ascension: your realm, honest about breakthroughs', /Your realm: R\d+ \w+/.test(flat(4)) && /moving up a realm is not built yet/.test(flat(4)), flat(4));
  const here = [11, 12, 13, 14, 15, 20, 21, 22, 23, 24].find(i => /✦/.test(slotName(i)));
  check('Ascension: ten realms, you are marked', [11, 12, 13, 14, 15, 20, 21, 22, 23, 24].every(i => /^R\d+ /.test(slotName(i))) && here !== undefined && /You are here/.test(flat(here)),
      [11, 24].map(slotName).join(' .. ') + ', here=' + here);
  check('Ascension: each realm is x8 the one before', /x8 the realm before it/.test(flat(15)) && /Above your realm/.test(flat(24)), flat(15));
  await click(45); await waitTitle(SCREEN('Player Menu'));

  let wore = false;
  if (isOp()) { // wear an iron chestplate so the page has something to explain
    await closeAny();
    await cmd('/clear @s iron_chestplate', 400);
    await cmd('/give @s iron_chestplate', 900);
    const cp = bot.inventory.items().find(it => it.name === 'iron_chestplate');
    if (cp) { try { await bot.equip(cp, 'torso'); wore = true; } catch (e) { console.log('  equip failed: ' + e.message); } }
    await sleep(800);
    await openViaHead();
  }
  await click(25);
  check('Equipment opens', await waitTitle(SCREEN('Equipment')), title());
  if (wore) check('Equipment: a worn iron chestplate shows its reduction and that it is heavy',
      /^Chestplate: Iron Chestplate$/.test(slotName(20)) && /Damage reduction: \+[1-9][\d.]*%/.test(flat(20)) && /Heavy armor: trains Heavy Armor Mastery/.test(flat(20)) && /Heavy armor: yes/.test(flat(4)),
      slotName(20) + ' | ' + flat(20) + ' || ' + flat(4));
  check('Equipment: damage reduction with its parts', /Damage reduction: [\d.]+%/.test(flat(4)) && /From armor pieces: \+[\d.]+% From Heavy Armor Mastery: \+[\d.]+% From Fortitude: \+[\d.]+%/.test(flat(22)), flat(22));
  check('Equipment: four armor slots and both hands', [11, 20, 29, 38].every(i => /^(Helmet|Chestplate|Leggings|Boots): /.test(slotName(i))) && /^Main hand: /.test(slotName(15)) && /^Off hand: /.test(slotName(24)),
      [11, 20, 29, 38, 15, 24].map(slotName).join(' / '));
  check('Equipment: custom gear marked in development', /In development/.test(flat(42)), flat(42));
  await click(45); await waitTitle(SCREEN('Player Menu'));
  if (wore) {
    await closeAny();
    try { await bot.unequip('torso'); } catch (e) {}
    await cmd('/clear @s iron_chestplate', 500);
    await openViaHead();
  }

  await click(28);
  check('Bestiary opens', await waitTitle(SCREEN('Bestiary')), title());
  const elite = findSlot(/^Elite Zombie$/);
  check('Bestiary: entries with tier, realm, XP and health', /Named creatures: \d+/.test(flat(4)) && /Tier: \w+ \(\w+ realm\)/.test(flat(10)) && /Health at level 50: /.test(flat(10)), flat(10));
  check('Bestiary: how to recognise a creature, in words', elite >= 0 && /A Zombie named 'Elite \.\.\.'/.test(flat(elite)), elite >= 0 ? flat(elite) : 'no Elite Zombie tile');
  check('Bestiary: kill tracking marked in development', /kill counts and discoveries are not tracked yet/.test(flat(4)), flat(4));
  await click(45); await waitTitle(SCREEN('Player Menu'));

  await click(30);
  check('World & Exploration opens', await waitTitle(SCREEN('World & Exploration')), title());
  check('World: distance and creature level here', /Distance from spawn: [\d,]+ blocks?/.test(flat(4)) && /Creature level here: \d+ \/ \d+/.test(flat(4)), flat(4));
  check('World: the distance rule and chunk discovery', /Every [\d,]+ blocks from world spawn adds a level/.test(flat(20)) && /Chunks discovered: [\d,]+/.test(flat(22)), flat(20) + ' || ' + flat(22));
  await click(45); await waitTitle(SCREEN('Player Menu'));

  await click(42);
  check('Guide opens', await waitTitle(SCREEN('Guide')), title());
  check('Guide: live numbers and honest notes', /every [\d,]+ blocks adds a level/.test(flat(19)) && /Fortune: a chance of one extra ore when mining/.test(flat(23)) && /not built yet/.test(flat(29)), flat(19) + ' || ' + flat(29));
  await click(45); await waitTitle(SCREEN('Player Menu'));
}

// ── names ─────────────────────────────────────────────────────────

async function names() {
  await cmd(`/cerb skill ${NAME} Navigation level 1`, 600);
  let at = await cmd(`/cerb skill ${NAME} Pathfinding level 3`, 900);
  check('/cerb skill accepts the new name', !!saw(/Pathfinding \(Navigation\) is now level 3/, at), chat.slice(at).join(' / '));
  // A real level-up shows the new name (an admin's XP grant celebrates nothing, docs/design/LEVEL_UP.md): Heavy Armor
  // Mastery from a zombie's hit while wearing a netherite chestplate, 1 XP short of level 4.
  const xpFor = l => Math.round(75 * Math.pow(l, 1.85) + 40 * l);
  await cmd(`/cerb skill ${NAME} Heavy_Armor_Training level 3`, 500);
  await cmd(`/cerb skill ${NAME} Heavy_Armor_Training xp ${xpFor(3) - 1}`, 500);
  await cmd('/gamemode survival', 400);
  await cmd('/effect clear @s resistance', 300); // another harness's leftover Resistance V would block the hit
  await cmd('/clear @s netherite_chestplate', 300);
  await cmd('/give @s netherite_chestplate', 800);
  const cp = bot.inventory.slots.findIndex((it, i) => it && it.name === 'netherite_chestplate' && i >= 36 && i <= 44);
  if (cp >= 0) { bot.setQuickBarSlot(cp - 36); await sleep(300); await bot.look(bot.entity.yaw, Math.PI / 2, true); bot.activateItem(); await sleep(250); bot.deactivateItem(); await sleep(600); }
  await cmd('/summon zombie ~2 ~ ~ {NoAI:1b}', 900);
  // The defense bar takes most of a hit: one is worth less than 1 XP, and the fraction waits for the next (XpIntegrity).
  at = chat.length;
  for (let i = 0; i < 4 && !saw(/▲ Heavy Armor Mastery reached level 4/, at); i++) {
    await cmd(`/damage ${NAME} 6 minecraft:mob_attack by @e[type=zombie,limit=1,sort=nearest]`, 1200);
  }
  check('Level-up message uses the new name', !!saw(/▲ Heavy Armor Mastery reached level 4/, at), chat.slice(at).join(' / '));
  await cmd('/kill @e[type=zombie,distance=..8]', 400);
  await cmd('/clear @s netherite_chestplate', 300);
  await cmd(`/cerb heal ${NAME}`, 400);
  await cmd('/gamemode creative', 400);
  at = await cmd(`/cerb skill ${NAME} heavy_armor_mastery level 2`, 900);
  check('Display name with underscores works', !!saw(/Heavy Armor Mastery \(Heavy Armor Training\) is now level 2/, at), chat.slice(at).join(' / '));
  at = await cmd(`/cerb skill ${NAME} Blade_Mastery level 4`, 900);
  check('Unrenamed skills read as before', !!saw(/: Blade Mastery is now level 4/, at), chat.slice(at).join(' / '));
  for (const s of ['Navigation', 'Heavy_Armor_Training', 'Blade_Mastery']) await cmd(`/cerb skill ${NAME} ${s} level 1`, 400);
}

// ── safety ────────────────────────────────────────────────────────

/** Item counts by name as the server sees them: a rejoin resends the whole inventory. */
async function rejoinCounts() {
  bot.quit(); await sleep(2500);
  await connect(); await sleep(3000);
  const counts = {};
  for (const it of bot.inventory.slots) if (it) counts[it.name] = (counts[it.name] || 0) + it.count;
  return counts;
}
const sameCounts = (a, b) => {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  return [...keys].filter(k => (a[k] || 0) !== (b[k] || 0)).map(k => `${k} ${a[k] || 0}->${b[k] || 0}`);
};
const itemsOnGround = () => Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(bot.entity.position) < 10).length;
const headInSlot9 = () => !!bot.inventory.slots[44] && bot.inventory.slots[44].name === 'player_head';

async function safety() {
  // A small cleared pad next to world spawn (sandbox world only): respawns land at random points nearby,
  // so fixed offsets would otherwise hit terrain (a chest under a block won't open, frames pop off).
  const sp = bot.spawnPoint || bot.entity.position;
  const X = Math.floor(sp.x) + 8, Y = Math.floor(sp.y) + 1, Z = Math.floor(sp.z);
  await cmd(`/fill ${X - 2} ${Y} ${Z - 2} ${X + 3} ${Y + 4} ${Z + 5} air`, 600);
  await cmd(`/fill ${X - 2} ${Y - 1} ${Z - 2} ${X + 3} ${Y - 1} ${Z + 5} stone`, 600);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 0`, 1500); // yaw 0 = facing +z
  await cmd('/gamemode survival', 600);
  // Leftovers from earlier kill tests fill the hotbar; the control items below need free hotbar slots.
  for (const junk of ['rotten_flesh', 'leather', 'potato', 'wheat_seeds', 'dirt', 'carved_pumpkin', 'diamond_sword']) await cmd('/clear @s ' + junk, 300);
  await cmd('/give @s diamond_sword', 800);
  await cmd('/kill @e[type=item,distance=..16]', 600);
  const base = await rejoinCounts();
  check('Setup: one head, one sword, nothing on the ground', base.player_head === 1 && base.diamond_sword === 1 && itemsOnGround() === 0,
      JSON.stringify(base) + ', ground ' + itemsOnGround());

  // Inside the menu: every click type is cancelled server-side.
  await openViaHead();
  const HEAD = 89;   // hotbar slot 9 inside a 54-slot window
  const SWORD = bot.currentWindow.slots.findIndex((it, i) => i >= 54 && it && it.name === 'diamond_sword');
  const attempts = [[HEAD, 0, 1], [19, 8, 2], [HEAD, 0, 2], [HEAD, 0, 4], [HEAD, 1, 4], [HEAD, 0, 6],
    [HEAD, 0, 0], [22, 0, 0], [SWORD, 0, 0], [-999, 0, 0], [19, 0, 1], [19, 0, 0], [SWORD, 0, 0], [SWORD, 0, 1]];
  for (const [sl, b, m] of attempts) {
    try { await bot.clickWindow(sl, b, m); } catch (e) { /* the client may refuse; the server is what matters */ }
    await sleep(350);
  }
  await closeAny(); await sleep(1000);
  check('In the menu: the head never moved, nothing hit the ground', heads() === 1 && headInSlot9() && itemsOnGround() === 0, heads() + ' heads, ground ' + itemsOnGround());
  let now = await rejoinCounts();
  let diff = sameCounts(base, now);
  check('In the menu: inventory identical after a rejoin (no item moved, lost or duplicated)', diff.length === 0, diff.join(', ') || 'identical');

  // Outside any menu: Q, Ctrl+Q, F.
  bot.setQuickBarSlot(8); await sleep(300);
  const dig = status => bot._client.write('block_dig', { status, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 });
  dig(4); await sleep(500); dig(3); await sleep(500); dig(6); await sleep(900);
  check('Q / Ctrl+Q / F: the head stays in slot 9, nothing dropped', headInSlot9() && heads() === 1 && itemsOnGround() === 0, 'ground ' + itemsOnGround());
  await closeAny();

  // Placing it as a block opens the menu; no head block appears.
  const pos = bot.entity.position.floored();
  await cmd(`/setblock ${pos.x} ${pos.y - 1} ${pos.z + 2} stone`, 500);
  const ground = bot.blockAt(pos.offset(0, -1, 2));
  bot.setQuickBarSlot(8); await sleep(200);
  try { await bot.lookAt(ground.position.offset(0.5, 1, 0.5), true); await bot.activateBlock(ground, { x: 0, y: 1, z: 0 }); } catch (e) {}
  await sleep(900);
  const placed = bot.findBlock({ matching: b => b && /player_(wall_)?head/.test(b.name), maxDistance: 6 });
  check('Right-clicking a block opens the menu and places nothing', !placed && SCREEN('Player Menu').test(title()) && headInSlot9(),
      (placed ? 'head block at ' + placed.position : 'no block') + ', ' + title());
  await closeAny();

  // Armor stand: never wears the head.
  await cmd('/kill @e[type=armor_stand,distance=..8]', 400);
  await cmd(`/summon armor_stand ${pos.x + 0.5} ${pos.y} ${pos.z + 1.5}`, 900);
  let stand = Object.values(bot.entities).find(e => e.name === 'armor_stand');
  if (stand) {
    await bot.lookAt(stand.position.offset(0, 1.6, 0), true); bot.setQuickBarSlot(8); await sleep(200);
    try { await bot.activateEntityAt(stand, stand.position.offset(0, 1.7, 0)); } catch (e) {}
    await sleep(900);
    stand = bot.entities[stand.id];
  }
  const worn = stand && (stand.equipment || []).some(Boolean);
  check('Armor stand: the head is not put on it', !!stand && !worn && headInSlot9(), stand ? 'equipment ' + JSON.stringify((stand.equipment || []).map(x => x && x.name)) : 'no stand');
  await closeAny();
  // Control: the same interaction with an ordinary helmet item does equip the stand.
  if (stand) {
    await cmd('/give @s carved_pumpkin', 700);
    const pk = bot.inventory.slots.findIndex((it, i) => it && it.name === 'carved_pumpkin' && i >= 36 && i <= 43);
    if (pk >= 0) {
      bot.setQuickBarSlot(pk - 36); await sleep(300);
      await bot.lookAt(stand.position.offset(0, 1.6, 0), true);
      try { await bot.activateEntityAt(stand, stand.position.offset(0, 1.7, 0)); } catch (e) {}
      await sleep(900);
    }
    const s2 = bot.entities[stand.id] || Object.values(bot.entities).find(e => e.name === 'armor_stand');
    check('Control: a carved pumpkin does go on the stand (the test reaches the server)', !!s2 && (s2.equipment || []).some(x => x && x.name === 'carved_pumpkin'),
        s2 ? JSON.stringify((s2.equipment || []).map(x => x && x.name)) : 'stand gone');
    await cmd('/clear @s carved_pumpkin', 400);
  }
  await cmd('/kill @e[type=armor_stand,distance=..8]', 400);

  // Item frame: never holds the head; the menu opens instead.
  await cmd(`/setblock ${pos.x} ${pos.y + 1} ${pos.z + 3} stone`, 400);
  await cmd(`/summon item_frame ${pos.x} ${pos.y + 1} ${pos.z + 2} {Facing:2b}`, 900);
  let frame = Object.values(bot.entities).find(e => e.name === 'item_frame');
  if (frame) {
    await bot.lookAt(frame.position, true); bot.setQuickBarSlot(8); await sleep(200);
    try { await bot.activateEntity(frame); } catch (e) {}
    await sleep(900);
    frame = bot.entities[frame.id];
  }
  const framed = frame && frame.metadata && frame.metadata[9] && frame.metadata[9].itemCount > 0;
  check('Item frame: the head is not put in it, the menu opens', !!frame && !framed && headInSlot9() && SCREEN('Player Menu').test(title()),
      (frame ? 'frame item ' + JSON.stringify(frame.metadata && frame.metadata[9]) : 'no frame') + ', ' + title());
  await closeAny();
  if (frame) { // control: an ordinary item does go in
    await cmd('/give @s dirt 1', 700);
    const dj = bot.inventory.slots.findIndex((it, i) => it && it.name === 'dirt' && i >= 36 && i <= 43);
    if (dj >= 0) { bot.setQuickBarSlot(dj - 36); await sleep(300); await bot.lookAt(frame.position, true); try { await bot.activateEntity(frame); } catch (e) {} await sleep(900); }
    const f2 = bot.entities[frame.id] || Object.values(bot.entities).find(e => e.name === 'item_frame');
    check('Control: dirt does go in the frame (the test reaches the server)', !!f2 && f2.metadata && f2.metadata[9] && f2.metadata[9].itemCount > 0,
        f2 ? JSON.stringify(f2.metadata && f2.metadata[9]) : 'frame gone');
  }
  await cmd('/kill @e[type=item_frame,distance=..8]', 400);
  await cmd('/kill @e[type=item,distance=..16]', 400);
  await cmd(`/setblock ${pos.x} ${pos.y + 1} ${pos.z + 3} air`, 300);

  // Own inventory, no menu open (window 0: hotbar slot 9 is window slot 44)
  for (const [sl, b, m] of [[44, 0, 0], [44, 0, 1], [9, 8, 2], [44, 0, 4], [44, 1, 4], [44, 0, 6]]) {
    try { await bot.clickWindow(sl, b, m); } catch (e) {}
    await sleep(300);
  }
  try { if (bot.inventory.selectedItem) await bot.clickWindow(-999, 0, 0); } catch (e) {}
  await sleep(600);
  check('Own inventory: pick up / shift / number key / drop leave the head in slot 9', headInSlot9() && heads() === 1 && itemsOnGround() === 0, heads() + ' heads');

  // A chest: shift-click the head in, or number-key it into a chest slot
  await cmd(`/setblock ${pos.x + 1} ${pos.y} ${pos.z + 2} chest`, 600);
  const chestBlock = bot.blockAt(pos.offset(1, 0, 2));
  let chestOk = false;
  try {
    const win = await bot.openContainer(chestBlock);
    const headSlot = win.inventoryStart + 27 + 8;   // hotbar slot 9 below the chest's 27 slots
    for (const [sl, b, m] of [[headSlot, 0, 1], [0, 8, 2], [headSlot, 0, 0], [5, 0, 0]]) {
      try { await bot.clickWindow(sl, b, m); } catch (e) {}
      await sleep(300);
    }
    await sleep(500);
    chestOk = win.containerItems().length === 0;
    bot.closeWindow(win);
  } catch (e) { console.log('  chest: ' + e.message); }
  await sleep(600);
  check('Chest: the head cannot be shift-clicked or number-keyed into it', chestOk && headInSlot9() && heads() === 1, chestOk ? 'chest empty' : 'chest not empty or did not open');
  await cmd(`/setblock ${pos.x + 1} ${pos.y} ${pos.z + 2} air`, 400);
  await cmd('/kill @e[type=item,distance=..16]', 400);

  // Pick-block with a full hotbar and slot 9 selected: the server would swap the head out
  const fillers = ['oak_planks', 'birch_planks', 'spruce_planks', 'jungle_planks', 'acacia_planks', 'dark_oak_planks', 'mangrove_planks', 'cherry_planks', 'bamboo_planks'];
  let fi = 0;
  for (let i = 36; i < 44; i++) if (!bot.inventory.slots[i]) { await cmd('/give @s ' + fillers[fi++] + ' 1', 300); }
  await cmd('/give @s stone 1', 600); // goes to the main inventory: the hotbar is full
  await cmd(`/setblock ${pos.x} ${pos.y} ${pos.z + 2} stone`, 500);
  bot.setQuickBarSlot(8); await sleep(300);
  bot._client.write('pick_item_from_block', { position: { x: pos.x, y: pos.y, z: pos.z + 2 }, includeData: false });
  await sleep(900);
  check('Pick-block with slot 9 selected leaves the head in slot 9', headInSlot9() && heads() === 1, 'slot 9 = ' + (bot.inventory.slots[44] && bot.inventory.slots[44].name));
  // Control: with an ordinary item selected (a filler plank: not the head, not the admin chest) the same packet swaps
  const plain = bot.inventory.slots.findIndex((it, i) => i >= 36 && i <= 43 && it && /_planks$/.test(it.name));
  if (plain >= 0) bot.setQuickBarSlot(plain - 36);
  await sleep(300);
  bot._client.write('pick_item_from_block', { position: { x: pos.x, y: pos.y, z: pos.z + 2 }, includeData: false });
  await sleep(900);
  check('Control: pick-block swaps stone into an ordinary selected slot (the test reaches the server)',
      plain >= 0 && !!bot.inventory.slots[plain] && bot.inventory.slots[plain].name === 'stone',
      'slot ' + (plain - 35) + ' = ' + (plain >= 0 && bot.inventory.slots[plain] && bot.inventory.slots[plain].name));
  await cmd(`/setblock ${pos.x} ${pos.y} ${pos.z + 2} air`, 300);
  for (const f of fillers.concat(['stone'])) await cmd('/clear @s ' + f, 150);

  // A powered shelf swaps its three items with hotbar slots 7-9 (vanilla). It must still work, but the
  // head must stay in slot 9 and the shelf item meant for slot 9 must stay on the shelf (decision B).
  for (const f of ['diamond', 'emerald', 'gold_ingot']) await cmd('/clear @s ' + f, 150);
  await cmd(`/setblock ${pos.x} ${pos.y} ${pos.z + 3} redstone_block`, 400);
  await cmd(`/setblock ${pos.x} ${pos.y} ${pos.z + 2} oak_shelf[facing=north,powered=true]{Items:[{Slot:0b,id:"minecraft:diamond",count:1},{Slot:1b,id:"minecraft:emerald",count:1},{Slot:2b,id:"minecraft:gold_ingot",count:1}]}`, 600);
  const shelf = bot.blockAt(pos.offset(0, 0, 2));
  // Keep the test hermetic: whatever sits in hotbar slots 7-8 really goes onto the shelf.
  for (const s7 of [42, 43]) {
    if (!bot.inventory.slots[s7]) continue;
    const free = bot.inventory.slots.findIndex((x, i) => !x && i >= 9 && i <= 35);
    if (free >= 0) { await bot.moveSlotItem(s7, free); await sleep(300); }
  }
  const hand = bot.inventory.slots.findIndex((it, i) => i >= 37 && i <= 41 && (!it || it.name === 'diamond_sword'));
  if (hand >= 0) bot.setQuickBarSlot(hand - 36);
  await sleep(300);
  const has = n => bot.inventory.slots.some(it => it && it.name === n);
  try { await bot.lookAt(shelf.position.offset(0.5, 0.5, 0), true); await bot.activateBlock(shelf, { x: 0, y: 0, z: -1 }); } catch (e) { console.log('  shelf: ' + e.message); }
  await sleep(1200);
  const hb = i => { const it = bot.inventory.slots[36 + i]; return it ? it.name : '-'; };
  check('Powered shelf works: its items reach hotbar slots 7-8, the head stays in slot 9, the third item stays on the shelf',
      /^oak_shelf/.test(shelf ? shelf.name : '') && hb(6) === 'diamond' && hb(7) === 'emerald' && headInSlot9() && heads() === 1 && !has('gold_ingot'),
      `${shelf ? shelf.name : 'no shelf'}: slots 7-9 = ${hb(6)}, ${hb(7)}, ${hb(8)}; gold in inventory: ${has('gold_ingot')}`);
  await cmd(`/setblock ${pos.x} ${pos.y} ${pos.z + 2} air`, 300);
  await cmd(`/setblock ${pos.x} ${pos.y} ${pos.z + 3} air`, 300);
  for (const f of ['diamond', 'emerald', 'gold_ingot']) await cmd('/clear @s ' + f, 150);
  await cmd('/kill @e[type=item,distance=..16]', 400);

  now = await rejoinCounts();
  diff = sameCounts(base, now);
  check('After all of that, the inventory is unchanged (server view after rejoin)', diff.length === 0, diff.join(', ') || 'identical');
  check('After rejoin: exactly one head, in slot 9', heads() === 1 && headInSlot9(), heads() + ' heads');
  await cmd('/kill @s', 2500);
  if (bot.health <= 0) { try { bot.respawn(); } catch (e) {} }
  await sleep(2500);
  check('After death and respawn: exactly one head, in slot 9', heads() === 1 && headInSlot9(), heads() + ' heads');
  check('Death dropped nothing', itemsOnGround() === 0, 'ground ' + itemsOnGround());
  for (const it of ['diamond_sword', 'dirt']) await cmd('/clear @s ' + it, 300);
  await cmd('/gamemode creative', 400);
}

// ── shelves ───────────────────────────────────────────────────────

async function shelves() {
  const sp = bot.spawnPoint || bot.entity.position;
  const X = Math.floor(sp.x) + 8, Y = Math.floor(sp.y) + 1, Z = Math.floor(sp.z);
  await cmd(`/fill ${X - 3} ${Y} ${Z - 2} ${X + 3} ${Y + 4} ${Z + 6} air`, 600);
  await cmd(`/fill ${X - 3} ${Y - 1} ${Z - 2} ${X + 3} ${Y - 1} ${Z + 6} stone`, 600);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 0`, 1500);
  await cmd('/gamemode survival', 600);
  // A known hotbar: clear everything; on rejoin the admin chest lands in slot 1 and the head in slot 9.
  await cmd('/clear @s', 800);
  await cmd('/kill @e[type=item,distance=..16]', 400);
  bot.quit(); await sleep(2500); await connect(); await sleep(3000);
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 0`, 1500);
  const PLANKS = ['oak_planks', 'birch_planks', 'spruce_planks', 'jungle_planks', 'acacia_planks', 'dark_oak_planks', 'cherry_planks'];
  for (const pl of PLANKS) await cmd('/give @s ' + pl + ' 1', 300);
  await sleep(600);
  const hb = () => bot.inventory.slots.slice(36, 45).map(it => it ? it.name : '-');
  const count = n => bot.inventory.slots.reduce((a, it) => a + (it && it.name === n ? it.count : 0), 0);
  const before = hb();
  const panel = before.indexOf('ender_chest');
  check('Setup: admin chest and head in the hotbar, the other 7 slots filled', panel >= 0 && before[8] === 'player_head'
      && before.filter(n => /_planks$/.test(n)).length === 7, before.join(' '));

  // Three chained shelves facing the bot, powered by redstone blocks behind them, nine different items.
  const SHELF = [['diamond', 'emerald', 'gold_ingot'], ['iron_ingot', 'coal', 'redstone'], ['lapis_lazuli', 'quartz', 'amethyst_shard']];
  for (let dx = -1; dx <= 1; dx++) await cmd(`/setblock ${X + dx} ${Y} ${Z + 3} redstone_block`, 200);
  for (let dx = -1; dx <= 1; dx++) {
    const items = SHELF[dx + 1].map((id, sl) => `{Slot:${sl}b,id:"minecraft:${id}",count:1}`).join(',');
    await cmd(`/setblock ${X + dx} ${Y} ${Z + 2} oak_shelf[facing=north,powered=true]{Items:[${items}]}`, 300);
  }
  await sleep(600);
  const pos = bot.entity.position.floored();
  const middle = bot.blockAt(pos.offset(0, 0, 2));
  bot.setQuickBarSlot(panel === 1 ? 2 : 1); await sleep(300); // hold a plank
  const at = chat.length;
  try { await bot.lookAt(middle.position.offset(0.5, 0.5, 0), true); await bot.activateBlock(middle, { x: 0, y: 0, z: -1 }); } catch (e) { console.log('  shelf: ' + e.message); }
  await sleep(1500);
  const after = hb();
  const shelfItems = SHELF.flat();
  const swapped = after.filter((n, i) => i !== panel && i !== 8 && shelfItems.includes(n));
  const stayed = shelfItems.filter(n => !after.includes(n));
  console.log(`  hotbar before: ${before.join(' ')}`);
  console.log(`  hotbar after:  ${after.join(' ')}`);
  check('Positive: the 7 ordinary slots swapped with the shelves', /^oak_shelf/.test(middle ? middle.name : '') && swapped.length === 7 && !after.some(n => /_planks$/.test(n)),
      `${swapped.length} shelf items in the hotbar`);
  check('Negative: the admin chest and the head stayed in their slots, one of each', after[panel] === 'ender_chest' && after[8] === 'player_head'
      && count('ender_chest') === 1 && heads() === 1, `slot ${panel + 1} ${after[panel]}, slot 9 ${after[8]}, ${count('ender_chest')} chest(s), ${heads()} head(s)`);
  check('Negative: the 2 shelf items meant for those slots stayed on the shelves (not in the inventory, not on the ground)',
      stayed.length === 2 && stayed.every(n => count(n) === 0) && itemsOnGround() === 0, `on the shelves: ${stayed.join(', ')}; ground ${itemsOnGround()}`);
  check('No refusal message any more', !saw(/powered shelf would swap/i, at), chat.slice(at).join(' / '));

  const server = await rejoinCounts();
  const want = { player_head: 1, ender_chest: 1 };
  for (const n of swapped) want[n] = 1;
  const diff = sameCounts(want, server);
  check('Server view after rejoin: exactly the head, the admin chest and the 7 swapped items', diff.length === 0, diff.join(', ') || 'identical');

  // Break the shelves: their contents drop. Everything must be there exactly once: 7 planks + the 2 kept items.
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z - 2 + 0.5} 0 0`, 1200); // out of pickup range of the drops
  for (let dx = -1; dx <= 1; dx++) await cmd(`/setblock ${X + dx} ${Y} ${Z + 2} air destroy`, 300);
  await sleep(1500);
  const dropped = {};
  for (const e of Object.values(bot.entities)) {
    if (e.name !== 'item' || e.position.distanceTo(bot.entity.position) > 12) continue;
    const it = e.getDroppedItem(); if (it) dropped[it.name] = (dropped[it.name] || 0) + it.count;
  }
  const expectDrops = { oak_shelf: 3 };
  for (const n of PLANKS.concat(stayed)) expectDrops[n] = 1;
  const dd = sameCounts(expectDrops, dropped);
  check('Nothing lost or duplicated: the shelves held the 7 planks and the 2 kept items', dd.length === 0, dd.join(', ') || JSON.stringify(dropped));

  for (let dx = -1; dx <= 1; dx++) await cmd(`/setblock ${X + dx} ${Y} ${Z + 3} air`, 150);
  await cmd('/kill @e[type=item,distance=..16]', 400);
  for (const n of shelfItems) await cmd('/clear @s ' + n, 120);
  await cmd('/gamemode creative', 400);
}

// ── motion (Player Menu 2.1) ──────────────────────────────────────

const typeAt = i => { const it = slot(i); return it ? it.name : '-'; };
const glows = i => { const it = slot(i); return !!it && /glint/.test(JSON.stringify(it.components || [])); };
/** Skill level and XP from /cerb skill ... xp 0 (adds nothing). */
async function skillNow(skill) {
  const at = await cmd(`/cerb skill ${NAME} ${skill} xp 0`, 900);
  const m = (chat.slice(at).find(x => /is now level \d+ \(\d+ XP\)/.test(x)) || '').match(/level (\d+) \((\d+) XP\)/);
  return m ? { level: Number(m[1]), xp: Number(m[2]) } : null;
}
/** Record every slot update of the next window the server opens (in-place switches send a new open). */
function watchNext() {
  const log = [];
  bot.once('windowOpen', w => { const t0 = Date.now(); w.on('updateSlot', (sl, o, n) => log.push({ s: sl, at: Date.now() - t0, name: n ? n.name : '-' })); });
  return log;
}
async function headOpen() {
  await closeAny();
  bot.setQuickBarSlot(8); await sleep(250);
  const opened = new Promise(r => bot.once('windowOpen', r));
  bot.activateItem(); await sleep(60); bot.deactivateItem();
  await opened;
}

async function motion() {
  // The plugin sends sounds by registry id, and this test client's 1.21.11 id table is out of date (it names them
  // wrongly). Each menu sound has its own volume and pitch, so they are told apart by those; the page turns must
  // also share one sound id, different from the click, the chime and the level-up.
  const SIG = { 'item.book.page_turn': [[0.8, 1.0], [0.6, 1.2], [0.6, 0.85]], 'ui.button.click': [[0.4, 1.2]],
    'block.amethyst_block.chime': [[0.7, 1.3]], 'entity.player.levelup': [[0.4, 1.4]] };
  const sounds = [];
  bot._client.on('sound_effect', pk => {
    const vol = pk.volume, pitch = pk.pitch;
    const key = Object.keys(SIG).find(k => SIG[k].some(([v, p]) => Math.abs(vol - v) < 0.02 && Math.abs(pitch - p) < 0.02));
    sounds.push({ key: key || '?', id: pk.sound ? (pk.sound.soundId ?? JSON.stringify(pk.sound.data)) : pk.soundId, pitch, at: Date.now() });
  });
  const heard = (key, since) => sounds.filter(x => x.at >= since && x.key === key);
  const names = since => sounds.filter(x => x.at >= since).map(x => x.key + '#' + x.id).join(', ') || 'none';
  await cmd('/gamemode creative', 400);
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 3`, 600);

  // 1. Opening: header and navigation at once; content from the centre column outwards; done within ~0.3 s.
  let t = Date.now();
  const log = watchNext();
  await headOpen();
  const headAtOnce = typeAt(4), skillsAtOnce = typeAt(19);
  await sleep(450);
  // When each column's real tiles arrived (the window's first contents hold the background in their place).
  const first = col => Math.min(...log.filter(c => c.s >= 9 && c.s < 45 && c.s % 9 === col && c.name !== 'black_stained_glass_pane').map(c => c.at));
  check('Opening: header at once, content appears after it (Skills tile hidden, then there)', headAtOnce === 'player_head'
      && skillsAtOnce === 'black_stained_glass_pane' && typeAt(19) === 'experience_bottle', `${headAtOnce}; Skills tile ${skillsAtOnce} -> ${typeAt(19)}`);
  check('Opening: centre column first, edges last, all within 0.4 s', first(4) < first(1) && first(2) <= first(1) && first(1) < 400,
      `column 4 at ${first(4)} ms, 2 at ${first(2)} ms, 1 at ${first(1)} ms`);
  check('Opening sound: a page turn', heard('item.book.page_turn', t).length >= 1, names(t));

  // 2. A button works before it has appeared.
  await headOpen();
  await bot.clickWindow(19, 0, 0);
  check('A button works before it has appeared (no waiting for animations)', await waitTitle(SCREEN('Skills')), title());
  await sleep(500);
  // ...and the opening animation it interrupted wrote nothing into the new screen.
  const hubOnly = { 11: 'glistering_melon_slice', 13: 'nether_star', 15: 'heart_of_the_sea', 21: 'blaze_powder', 23: 'end_crystal', 25: 'netherite_chestplate', 28: 'zombie_head', 30: 'filled_map' };
  const leaked = Object.entries(hubOnly).filter(([sl, n]) => typeAt(Number(sl)) === n).map(([sl, n]) => sl + ':' + n);
  check('An interrupted animation writes nothing into the next screen', leaked.length === 0 && SCREEN('Skills').test(title()), leaked.join(', ') || 'clean');

  // 3. Moving between screens: a light sweeps the header row from the centre out and settles; page-turn pitch by direction.
  t = Date.now();
  const sweep = watchNext();
  await click(45, 700); // Back -> hub, in place
  const lit = [...new Set(sweep.filter(x => x.s < 9 && x.name === 'white_stained_glass_pane').map(x => x.s))].sort();
  const firstLit = s => (sweep.find(x => x.s === s && x.name === 'white_stained_glass_pane') || { at: 1e9 }).at;
  check('Moving between screens: a light sweeps the header from the centre out, then settles', SCREEN('Player Menu').test(title())
      && [3, 5, 0, 8].every(s => lit.includes(s)) && firstLit(3) <= firstLit(0) && [0, 1, 2, 3, 5, 6, 7, 8].every(s => typeAt(s) !== 'white_stained_glass_pane'),
      `lit ${lit.join(',')}; slot 3 at ${firstLit(3)} ms, slot 0 at ${firstLit(0)} ms`);
  const back = heard('item.book.page_turn', t);
  t = Date.now();
  await click(19, 700); // forward -> Skills
  const fwd = heard('item.book.page_turn', t);
  check('Page turns: lower pitch going back, higher going forward', back.length >= 1 && fwd.length >= 1 && back[0].pitch < fwd[0].pitch,
      `back ${back.map(x => x.pitch.toFixed(2))}, forward ${fwd.map(x => x.pitch.toFixed(2))}`);
  t = Date.now();
  await click(52, 600); // Refresh: not a move, a plain click
  check('A button that stays on the screen clicks instead', heard('ui.button.click', t).length >= 1 && heard('item.book.page_turn', t).length === 0, names(t));

  // 4. Category tabs and the selected category.
  await click(20, 800); // Combat
  const cat = title();
  const tabL = slotName(2), tabR = slotName(6);
  check('Category screen: the shown category glows in the header, the other two are tabs beside it', glows(4) && /◀ /.test(tabL) && / ▶/.test(tabR), `${cat}: [${tabL}] [${tabR}] glow ${glows(4)}`);
  await click(6, 800);
  check('A tab switches category in place', title() !== cat && /✦ /.test(title()), `${cat} -> ${title()}`);
  await click(45, 800); // Back -> Skills (the tab replaced the screen; history unchanged)
  const lastTile = [20, 22, 24].find(sl => /Last opened/.test(slotLore(sl)));
  check('Back on Skills: the category last opened is marked', SCREEN('Skills').test(title()) && lastTile !== undefined,
      `${title()}; marked slot ${lastTile}`);

  // 5. The progress strip fills as the page appears; XP since the last close starts from where it was.
  await click(20, 800); // Combat
  const bmSlot = findSlot(/^(▲ )?Blade Mastery\b/);
  await click(bmSlot, 600);
  const req = Number(((slotLore(10).match(/XP: [\d,]+ \/ ([\d,]+)/) || [])[1] || '0').replace(/,/g, ''));
  await closeAny(); await sleep(500); // closing remembers what was seen: Blade Mastery 3, 0 XP
  await cmd(`/cerb skill ${NAME} Blade_Mastery xp ${Math.round(req * 0.6)}`, 900);
  await openViaHead(); await click(19, 700); await click(20, 700);
  const strip = watchNext();
  await click(findSlot(/^(▲ )?Blade Mastery\b/), 50);
  const limeAtOnce = [10, 11, 12, 13, 14, 15, 16].filter(i => typeAt(i) === 'lime_stained_glass_pane').length;
  await sleep(500);
  const limeLater = [10, 11, 12, 13, 14, 15, 16].filter(i => typeAt(i) === 'lime_stained_glass_pane').length;
  check('Skill page: the strip fills as the page appears (60% of a level -> 4 of 7 panes)', req > 0 && limeAtOnce < limeLater && limeLater === 4,
      `${limeAtOnce} -> ${limeLater} lit; ${strip.filter(x => x.s >= 10 && x.s <= 16).length} strip updates`);
  check('Skill page: XP gained since the last close is called out', /▲ \+[\d,]+ XP since you last looked/.test(slotLore(4)), slotLore(4).split(' | ')[0]);

  // 6. Levels gained since the last close: glow and line on the hub, category and skill; one chime; one level-up flash.
  await closeAny(); await sleep(500);
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 5`, 900);
  t = Date.now();
  await openViaHead(); await sleep(300);
  const count = i => { const it = slot(i); return it ? it.count : 0; };
  check('Hub: the Skills tile counts the new levels and says what is new', count(19) === 2 && /▲ \+2 levels since you last looked/.test(slotLore(19)),
      `count ${count(19)}; ${slotLore(19).split(' | ').find(x => /▲/.test(x)) || 'no ▲ line'}`);
  check('Hub: one soft chime for the news', heard('block.amethyst_block.chime', t).length === 1, names(t));
  await click(19, 700);
  const combat = [20, 22, 24].find(sl => /Combat/.test(slotName(sl)));
  check('Skills: the tile with new levels has a ▲ before its name (a count of 1 is not drawn)', combat !== undefined && /^▲ /.test(slotName(combat)), combat !== undefined ? slotName(combat) : 'no Combat tile');
  check('Skills: the category with new levels counts them and says so', combat !== undefined && /▲ \+2 levels/.test(slotLore(combat)) && count(combat) === 2,
      combat !== undefined ? `count ${count(combat)}; ${slotLore(combat).split(' | ')[0]}` : 'no Combat tile');
  await click(combat, 700);
  const bm = findSlot(/^(▲ )?Blade Mastery\b/);
  check('Category: the skill with new levels glows, counts them and says so', bm >= 0 && glows(bm) && count(bm) === 2 && /▲ \+2 levels/.test(slotLore(bm)),
      bm >= 0 ? `count ${count(bm)}; ${slotLore(bm).split(' | ')[0]}` : 'none');
  t = Date.now();
  const flash = watchNext();
  await click(bm, 900);
  const yellow = flash.filter(x => x.s >= 10 && x.s <= 16 && x.name === 'yellow_stained_glass_pane').length;
  check('Skill page: the level-up since the last close flashes the strip gold, with a level-up sound', /▲ Level 3 → 5 since you last looked/.test(slotLore(4))
      && yellow === 7 && heard('entity.player.levelup', t).length === 1, `${slotLore(4).split(' | ')[0]}; ${yellow} gold panes; sounds ${names(t)}`);
  await click(45, 700);
  t = Date.now();
  const again = watchNext();
  await click(findSlot(/^(▲ )?Blade Mastery\b/), 900);
  check('...only the first time: opening it again just fills', again.filter(x => x.s >= 10 && x.s <= 16 && x.name === 'yellow_stained_glass_pane').length === 0
      && heard('entity.player.levelup', t).length === 0, `${again.filter(x => x.name === 'yellow_stained_glass_pane').length} gold`);

  const ids = k => [...new Set(sounds.filter(x => x.key === k).map(x => String(x.id)))];
  const turn = ids('item.book.page_turn'), others = ['ui.button.click', 'block.amethyst_block.chime', 'entity.player.levelup'].flatMap(ids);
  check('Sounds: every page turn is one sound, distinct from the click, the chime and the level-up', turn.length === 1 && others.length === 3 && !others.includes(turn[0]),
      `page turn ${turn.join('/')}; others ${others.join('/')}`);

  // 7. Nothing keeps running on an open menu.
  await sleep(1500);
  const q = await cmd('/cerb showqueues', 900);
  const run = (chat.slice(q).find(x => /Menu animations running/.test(x)) || '');
  check('An open menu nobody touches runs no animation', /Menu animations running: 0\b/.test(run) && !!bot.currentWindow, run);

  await closeAny();
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`, 500);
}

// ── perf ──────────────────────────────────────────────────────────

async function counters() {
  const at = await cmd('/cerb showqueues', 900);
  const get = re => { const m = chat.slice(at).map(x => x.match(re)).find(Boolean); return m ? Number(m[1]) : NaN; };
  return { db: get(/Database connections since start: (\d+)/), rebuilds: get(/Attribute rebuilds since start: (\d+)/), previews: get(/Attribute previews since start: (\d+)/) };
}

async function perf() {
  const before = await counters();
  for (let round = 0; round < 3; round++) {
    await openViaHead();
    await click(19); await waitTitle(SCREEN('Skills'));
    for (const cat of [20, 22, 24]) {
      await click(cat, 600);
      for (const s of [10, 11, 12]) { await click(s, 500); await click(43, 400); await click(43, 400); await click(45, 500); }
      await click(45, 500);
    }
    await click(49, 500);
    await click(21, 600); await click(52, 500); await click(45, 500);
    for (const sec of [23, 25, 28, 30, 42]) { await click(sec, 600); await click(45, 500); }
    await closeAny();
  }
  const after = await counters();
  check('Browsing every screen 3x opened no database connection', after.db === before.db, `${before.db} -> ${after.db}`);
  check('Browsing rebuilt no attribute snapshot', after.rebuilds === before.rebuilds, `${before.rebuilds} -> ${after.rebuilds}`);
  console.log(`  (menu previews computed: ${after.previews - before.previews})`);
}

// ── smithing ──────────────────────────────────────────────────────

async function smithingPages() {
  if (!isOp()) { check('The smithing phase needs op (it sets Smithing to 50)', false); return; }
  await cmd(`/cerb skill ${NAME} Smithing level 50`, 600);
  check('The hub opens', await openViaHead(), title());
  await click(19); await waitTitle(SCREEN('Skills'));
  await click(24);
  check('Utility category opens', await waitTitle(SCREEN('Utility Skills')), title());
  const sm = findSlot(/^Smithing/);
  check('Smithing tile: +0.25 Fortitude and +0.3% wear resistance per level', sm >= 0 && /\+0\.25 Fortitude per level/.test(flat(sm))
      && /\+0\.3% wear resistance per level/.test(flat(sm)), sm >= 0 ? flat(sm) : 'missing');
  await click(sm);
  check('Smithing page opens', await waitTitle(SCREEN('Smithing')), title());
  const t = flat(22);
  check('Attributes & speed: each level gives +0.25 Fortitude and +0.3% wear resistance (no "gives no attribute")',
    /Each level gives: \+0\.25 Fortitude \+0\.3% wear resistance/.test(t) && !/gives no attribute/.test(t), t);
  check('...from this skill now (level 50): +12.5 Fortitude, +15% wear resistance (before diminishing returns)',
    /From this skill now \(level 50\): \+12\.5 Fortitude \+15% wear resistance/.test(t), t);
  check('...what they do, now and at the next level: armor, defense bar, resistances, wear resistance 12.2%',
    /Armor: \+[\d.]+%/.test(t) && /Defense bar: x[\d.]+/.test(t) && /Resistances: \+[\d.]+%/.test(t)
      && /Wear resistance: \+12\.2% → \+12\.4%/.test(t), t);
  check('...both live, and the PvP pause said', /Fortitude: ● Active/.test(t) && /Wear resistance: ● Active/.test(t) && /pause during PvP/.test(t), t);
  check('Next levels: wear resistance at each milestone, and the forged-quality odds stay',
    /At level 51 .*Wear resistance: \+12\.4%/.test(flat(31)) && /At level 100 .*\+25 Fortitude .*Wear resistance: \+20\.1%/.test(flat(31))
      && /Superior or better forged gear/.test(flat(31)), flat(31));
  await click(49, 600); // Home
  await waitTitle(SCREEN('Player Menu'));
  await click(21);
  check('Stats opens', await waitTitle(SCREEN('Stats & Attributes')), title());
  check('Stats: wear resistance 12.2%, raised by Smithing', /Wear resistance: 12\.2% of your gear's wear avoided/.test(flat(22)) && /Smithing/.test(flat(22)), flat(22));
  await closeAny();
  await cmd(`/cerb skill ${NAME} Smithing level 1`, 600);
}

(async () => {
  await connect();
  await sleep(3000);
  try {
    if (phase === 'nav') await nav();
    else if (phase === 'names') await names();
    else if (phase === 'safety') await safety();
    else if (phase === 'perf') await perf();
    else if (phase === 'shelves') await shelves();
    else if (phase === 'motion') await motion();
    else if (phase === 'smithing') await smithingPages();
    else console.log('unknown phase ' + phase);
  } catch (e) {
    check('phase ran without errors', false, e.stack);
  }
  await closeAny();
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
  bot.quit();
  setTimeout(() => process.exit(failed ? 1 : 0), 500);
})();
