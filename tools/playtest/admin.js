// In-game check of the Cerberus Control Center (admin panel). LOCAL SANDBOX servers only.
// Usage: node admin.js <phase>
//   main    - bot must be op: clicks through every section and edits/spawns/removes things
//   nonop   - bot must NOT be op: /cerb panel and a forged panel item must both be refused
//   deopmid - bot is op; opens the panel, prints READY, waits 6 s (deop it then), then clicks
//   owners  - bot and CerbTarget must be op: cleanup removes only the clicking admin's test mobs
//   attrs   - bot must be op: Attributes section, inspector, simulator (changes nothing), dev buffs
//   buffexpire - bot must be op: a dev buff ends by itself after 5 minutes (max health returns)
//   staleclick - bot must be op: a laggy double-click (the second click aimed at the skill list, arriving after the
//                skill editor opened) must not press the editor's button in that slot (+10 levels)
const mineflayer = require('mineflayer');
const phase = process.argv[2] || 'main';
const NAME = process.argv[3] || 'CerbSmoke';
const sleep = ms => new Promise(r => setTimeout(r, ms));
// Text of a chat component as mineflayer exposes it (NBT compound, JSON string, or JSON object).
const plain = v => {
  if (v == null) return '';
  if (typeof v === 'string') { try { return plain(JSON.parse(v)); } catch { return v; } }
  const s = JSON.stringify(v);
  const nbt = [...s.matchAll(/"text":\{"type":"string","value":"([^"]*)"/g)].map(m => m[1]);
  if (nbt.length) return nbt.join('');
  return [...s.matchAll(/"text":"([^"]*)"/g)].map(m => m[1]).join('');
};
const chat = [];
const results = [];
const check = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + detail + ')' : ''}`); };
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: NAME, version: '1.21.11', auth: 'offline' });
bot.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
bot.on('kicked', r => { console.log('KICKED', r); process.exit(2); });

let lastWindow = null;
bot.on('windowOpen', w => { lastWindow = w; });
const title = () => bot.currentWindow ? plain(bot.currentWindow.title) : '';
const slotName = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it ? plain(it.customName) : ''; };
const slotLore = i => { const it = bot.currentWindow && bot.currentWindow.slots[i]; return it && it.customLore ? it.customLore.map(plain).join(' | ') : ''; };
const findSlotWith = (nameRe, loreRe) => { const w = bot.currentWindow; if (!w) return -1; for (let i = 0; i < w.inventoryStart; i++) if (w.slots[i] && nameRe.test(plain(w.slots[i].customName)) && loreRe.test(slotLore(i))) return i; return -1; };
const findSlot = re => { const w = bot.currentWindow; if (!w) return -1; for (let i = 0; i < w.inventoryStart; i++) if (w.slots[i] && re.test(plain(w.slots[i].customName))) return i; return -1; };
async function waitTitle(re, ms = 4000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (bot.currentWindow && re.test(title())) return true; await sleep(100); } return false; }
async function click(i, wait = 700) { await bot.clickWindow(i, 0, 0); await sleep(wait); }
/** "Swords: P -> xM damage" from a Power tile; M must equal 1 + 0.05 * P (both as displayed). */
const swordPower = lore => { const m = (lore || '').match(/Swords: ([\d.]+) +-> +x([\d.]+) damage/); return m ? { p: Number(m[1]), m: Number(m[2]) } : null; };
const consistent = sp => !!sp && Math.abs(sp.m - (1 + 0.05 * sp.p)) < 0.011;

async function cmd(c, wait = 1200) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).some(m => re.test(m));

async function main() {
  await cmd('/time set night', 400); // test zombies must not burn to death mid-check
  await cmd('/kill @e[type=!player,type=!text_display,distance=..48]', 1000); // start from a clean area
  // Home
  let at = await cmd('/cerb panel', 1500);
  check('/cerb panel opens the Control Center', await waitTitle(/Control Center/), title());
  const tiles = ['Players', 'Combat Lab', 'Progression Lab', 'Attributes', 'My Test Character', 'Server & Plugin', 'Maintenance'];
  const missing = tiles.filter(t => findSlot(new RegExp('^' + t.replace(/[&]/g, '.') + '$')) < 0);
  check('Home shows all 7 sections', missing.length === 0, missing.length ? 'missing ' + missing.join(', ') : '');
  check('Home header shows plugin build', /build \d{4}-\d\d-\d\d/.test(slotLore(4)), slotLore(4).slice(0, 80));
  check('Attributes tile is marked live', /Live/.test(slotLore(findSlot(/^Attributes$/))), slotLore(findSlot(/^Attributes$/)));

  // Players -> me -> skills -> Blade Mastery
  await click(findSlot(/^Players$/));
  check('Players list opens', await waitTitle(/Players/), title());
  const me = findSlot(new RegExp('^' + NAME + '$'));
  check('Players list contains me', me >= 0);
  await click(me);
  check('Player inspector opens', await waitTitle(new RegExp('Player: ' + NAME)), title());
  check('Inspector shows custom HP', /Custom HP: [\d.]+ \/ [\d.]+/.test(slotLore(19)), slotLore(19).slice(0, 60));
  await click(findSlot(/^Skills$/));
  check('Skill list opens', await waitTitle(/Skills:/), title());
  await click(findSlot(/^Blade Mastery$/));
  check('Skill editor opens', await waitTitle(/Blade Mastery/), title());
  const lvl = () => { const m = slotName(22).match(/Level (\d+)/); return m ? Number(m[1]) : NaN; };
  // normalise to level 1 first (may need confirm)
  await click(28, 900);
  if (/Confirm/.test(title())) { await click(11, 900); }
  await waitTitle(/Blade Mastery/);
  const base = lvl();
  check('Set level 1 preset works', base === 1, 'level ' + base);
  at = chat.length;
  await click(25, 900); // +10
  check('+10 levels raises the level', lvl() === base + 10, base + ' -> ' + lvl());
  check('Action reports success in chat', saw(/Blade Mastery 1 -> 11/, at), chat.slice(at).join(' / ').slice(0, 120));
  await click(20, 900); // -1 -> confirm
  check('Lowering a level asks for confirmation', await waitTitle(/Confirm/), title());
  await click(15, 900); // cancel
  check('Cancel keeps the level', (await waitTitle(/Blade Mastery/)) && lvl() === base + 10, 'level ' + lvl());
  await click(20, 900);
  await waitTitle(/Confirm/);
  await click(11, 1000); // yes
  check('Confirm applies the lower level', (await waitTitle(/Blade Mastery/)) && lvl() === base + 9, 'level ' + lvl());
  at = chat.length;
  await click(41, 1000); // +100,000 XP
  check('XP grant goes through the award path (level-ups)', lvl() > base + 9 && saw(/reached level/, at), 'level ' + lvl());
  await click(28, 900); if (/Confirm/.test(title())) await click(11, 900); // back to level 1

  // Combat Lab: spawn a level-50 zombie punching bag
  await click(49); // Home
  await waitTitle(/Control Center/);
  await click(findSlot(/^Combat Lab$/));
  check('Combat Lab opens', await waitTitle(/Combat Lab/), title());
  await click(20);
  check('Mob picker opens', await waitTitle(/choose a mob/), title());
  check('Wither is listed but refused (terrain)', /destroys terrain/.test(slotLore(findSlot(/^Wither$/))));
  await click(findSlot(/^Zombie$/));
  check('Spawn setup opens', await waitTitle(/Spawn Zombie/), title());
  await click(32); // level 50 preset
  check('Level preset updates the preview', /Level: 50 \//.test(slotLore(4)), slotLore(4).slice(0, 90));
  at = chat.length;
  await click(42, 2500); // spawn (stay)
  check('Spawn reports success', saw(/Spawned 1 x Zombie \(Common\) at level 50/, at), chat.slice(at).join(' / '));
  const labels = Object.values(bot.entities).filter(e => e.name === 'text_display' && e.position.distanceTo(bot.entity.position) < 8)
      .map(e => plain(e.metadata));
  check('Spawned zombie is labelled level 50', labels.some(l => /Zombie.*Lv50/.test(l)), labels.join(' | '));
  await click(45); // back to picker
  await click(45); // back to Combat Lab
  await waitTitle(/Combat Lab/);
  await click(22);
  check('Nearby mobs opens', await waitTitle(/Nearby mobs/), title());
  let zs = findSlotWith(/^Zombie$/, /Level 50/);
  check('Nearby list shows the zombie at level 50', zs >= 0 && /Level 50/.test(slotLore(zs)), slotLore(zs).slice(0, 80));
  await click(zs);
  check('Mob inspector opens', await waitTitle(/Mob/), title());
  await click(21, 1200); // set 10%
  check('Set HP to 10% updates the pool', /\(10\.0%\)/.test(slotLore(4)), slotLore(4).slice(0, 100));
  await click(24, 900); // highlight
  check('Highlight toggles on', /Highlight: on/.test(slotName(24)), slotName(24));
  at = chat.length;
  await click(25, 1500); // remove (test spawn -> no confirm)
  check('Remove test mob works without confirm', saw(/Removed\./, at) && /Nearby mobs/.test(title()), title());

  // Spawn 3 more, then clean up via confirm
  await click(45); await waitTitle(/Combat Lab/);
  await click(20); await waitTitle(/choose a mob/);
  await click(findSlot(/^Skeleton$/)); await waitTitle(/Spawn Skeleton/);
  await click(38); // count 3
  await click(42, 2500);
  await click(45); await click(45); await waitTitle(/Combat Lab/);
  await click(24); // cleanup -> confirm
  check('Cleanup asks for confirmation', await waitTitle(/Confirm/), title());
  at = chat.length;
  await click(11, 1500);
  check('Cleanup removes the 3 test mobs', saw(/Removed 3 test mob/, at), chat.slice(at).join(' / '));

  // Server info + maintenance
  await click(49); await waitTitle(/Control Center/);
  await click(findSlot(/^Server & Plugin$/));
  check('Server & Plugin opens', await waitTitle(/Server & Plugin/), title());
  check('Server info shows Folia + Java', /Folia/.test(slotLore(20)) && /Java: \d+/.test(slotLore(20)), slotLore(20).slice(0, 80));
  await click(49); await waitTitle(/Control Center/);
  await click(findSlot(/^Maintenance$/));
  await click(20); // reload -> confirm
  check('Reload asks for confirmation', await waitTitle(/Confirm/), title());
  at = chat.length;
  await click(11, 1500);
  check('Reload runs and reports', saw(/Configuration reloaded/, at), chat.slice(at).join(' / '));

  // Progression lab + My Test Character render
  await click(49); await waitTitle(/Control Center/);
  await click(findSlot(/^Progression Lab$/));
  check('Progression Lab shows the XP curve total', /Level 1 -> 100 in total: [\d,]+ XP/.test(slotLore(19)), '');
  await click(49); await waitTitle(/Control Center/);
  await click(findSlot(/^My Test Character$/));
  check('My Test Character opens', await waitTitle(/My test character/), title());
  await click(53, 800); // close
  check('Close closes the menu', !bot.currentWindow, title());

  // Panel item opens it too
  const itemSlot = bot.inventory.slots.findIndex(it => it && /Admin Control Panel/.test(plain(it.customName)));
  if (itemSlot >= 36 && itemSlot <= 44) {
    bot.setQuickBarSlot(itemSlot - 36); await sleep(300);
    bot.activateItem(); await sleep(1500);
    check('Right-clicking the panel item opens it (op)', /Control Center/.test(title()), title());
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  } else {
    check('Admin has the panel item in the hotbar', false, 'slot ' + itemSlot);
  }
}

async function nonop() {
  let at = await cmd('/cerb panel', 1500);
  // Non-admins don't even see /cerb (plugin.yml permission), so the server answers "Unknown command".
  check('Non-op: /cerb panel refused', !bot.currentWindow && saw(/lack permission|admins only|Unknown or incomplete command/, at), chat.slice(at).join(' / '));
  await sleep(3000); // the harness gives a forged item from the console now
  const forged = bot.inventory.slots.findIndex(it => it && it.name === 'ender_chest' && plain(it.customName) === 'Admin Control Panel');
  if (forged >= 36 && forged <= 44) {
    at = chat.length;
    bot.setQuickBarSlot(forged - 36); await sleep(300);
    bot.activateItem(); await sleep(1500);
    check('Non-op: forged panel item opens nothing', !/Control Center/.test(title()) && saw(/admins only/, at), chat.slice(at).join(' / '));
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  } else {
    check('Non-op: forged item in hotbar for the test', false, 'slot ' + forged + ' (give it from the console first)');
  }
}

async function dupe() {
  const count = () => bot.inventory.slots.slice(9, 45).filter(Boolean).reduce((n, it) => n + it.count, 0);
  const menuItemsInInv = () => bot.inventory.slots.slice(9, 45).filter(it => it && /^(Players|Combat Lab|Close|Refresh)$/.test(plain(it.customName))).length;
  const before = count();
  await cmd('/cerb panel', 1500);
  await waitTitle(/Control Center/);
  const tile = findSlot(/^Players$/);
  for (const [mode, button] of [[1, 0], [2, 0], [2, 3], [4, 0], [4, 1]]) { // shift, number keys, drop
    try { await bot.clickWindow(tile, button, mode); } catch (e) { /* client may refuse; server is what matters */ }
    await sleep(500);
  }
  try { await bot.clickWindow(53, 0, 1); } catch (e) {}
  await sleep(800);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(1000);
  check('Shift/number-key/drop clicks never move menu items into the inventory', menuItemsInInv() === 0 && count() === before,
      'items ' + before + ' -> ' + count() + ', menu items in inventory ' + menuItemsInInv());
  const dropped = Object.values(bot.entities).filter(e => e.name === 'item' && e.position.distanceTo(bot.entity.position) < 6).length;
  check('No item entities were dropped from the menu', dropped === 0, dropped + ' item entities nearby');
}

async function cross() {
  // A second client joins; the admin edits that player's skill and restores them from the panel.
  const other = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'CerbTarget', version: '1.21.11', auth: 'offline' });
  const otherChat = [];
  other.on('messagestr', (m, pos) => { if (pos !== 'game_info') otherChat.push(m); });
  await new Promise(r => other.once('spawn', r));
  await sleep(3000);
  await cmd('/cerb panel', 1500);
  await click(findSlot(/^Players$/));
  const t = findSlot(/^CerbTarget$/);
  check('Admin sees the other player in the list', t >= 0);
  await click(t);
  check('Inspector opens for the other player', await waitTitle(/Player: CerbTarget/), title());
  await click(19, 1500); // full restore
  check('Other player is told about the restore', otherChat.some(m => /CerbSmoke fully restored/.test(m)), otherChat.join(' / '));
  await click(findSlot(/^Skills$/));
  await click(findSlot(/^Blade Mastery$/));
  await waitTitle(/Blade Mastery/);
  await click(28, 900); if (/Confirm/.test(title())) await click(11, 900);
  const at = chat.length;
  await click(25, 1500); // +10
  check('Admin edit of another player applies', saw(/CerbTarget: Blade Mastery 1 -> 11/, at), chat.slice(at).join(' / '));
  check('The other player is notified of the change', otherChat.some(m => /set your Blade Mastery to level 11/.test(m)), otherChat.slice(-3).join(' / '));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  other.quit();
  await sleep(1500);
}

async function owners() {
  // Two admins: "Clean up my test mobs" removes only the clicker's own test spawns.
  // The harness must op CerbTarget first.
  const other = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'CerbTarget', version: '1.21.11', auth: 'offline' });
  other.on('messagestr', () => {});
  await new Promise(r => other.once('spawn', r));
  await sleep(3000);
  await cmd('/time set day', 400);
  await cmd('/tp CerbTarget ' + NAME, 800); // other tests move us around: stand together
  await cmd('/kill @e[type=zombie,distance=..64]', 800);
  await cmd('/cerb spawn zombie 3 noai', 1200);
  other.chat('/cerb spawn zombie 4 noai');
  await sleep(1500);
  const zombies = () => Object.values(bot.entities).filter(e => e.name === 'zombie' && e.position.distanceTo(bot.entity.position) < 12).length;
  check('Both admins spawned a test zombie', zombies() === 2, zombies() + ' zombies');
  await cmd('/cerb panel', 1500);
  await click(findSlot(/^Combat Lab$/));
  await waitTitle(/Combat Lab/);
  const lore = slotLore(24);
  check('Cleanup counts only my own test mobs', /Your test mobs nearby: 1\b/.test(lore), lore);
  const at = chat.length;
  await click(24, 900);
  check('Cleanup asks to confirm', /Confirm|Remove your test mobs/.test(title()), title());
  await click(11, 1500);
  check('Cleanup removed exactly one mob', saw(/Removed 1 test mob/, at), chat.slice(at).join(' / '));
  check("The other admin's test mob is still there", zombies() === 1, zombies() + ' zombies');
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  other.chat('/kill @e[type=zombie,distance=..64]');
  await sleep(800);
  other.quit();
  await sleep(1500);
}

async function attrs() {
  await cmd('/cerb skill ' + NAME + ' Blade_Mastery level 1', 600);
  await cmd('/cerb skill ' + NAME + ' Survival level 1', 600);
  // Max health before any buff (102 for a fresh character; a class loadout can add to it).
  await cmd('/stats', 1500);
  const baseHp = Number(((slotLore(31).match(/Max health: ([\d,]+)/) || [])[1] || 'NaN').replace(/,/g, ''));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await sleep(300);
  await cmd('/cerb panel', 1500);
  const tile = findSlot(/^Attributes$/);
  check('Home shows Attributes as live', tile >= 0 && /Live/.test(slotLore(tile)), slotLore(tile));
  await click(tile, 900);
  check('Attributes section lists players, you first', await waitTitle(/Attributes$/) && new RegExp(NAME).test(slotName(10)), slotName(10));
  await click(10, 900);
  check('Inspector opens for yourself', await waitTitle(new RegExp('Attributes: ' + NAME)), title());
  const before = slotLore(10);
  check('Inspector shows the same Power tile as the Stats page', consistent(swordPower(before)) && /Gained from \(per skill level\)/.test(before), before);
  check('Rules tile shows integrations and limits', /PvE offense.*: on/.test(slotLore(40)) && /Offense budget: x6\.00/.test(slotLore(40)), slotLore(40));

  // Simulator: Blade Mastery at 100, then confirm nothing really changed
  await click(29, 900);
  check('Simulator opens', await waitTitle(/Attribute simulator/), title());
  for (let i = 0; i < 25 && !/^Blade Mastery$/.test(slotName(22)); i++) await click(25, 500);
  await click(43, 900); // preset level 100
  const sim = slotLore(10).match(/Swords: x([\d.]+) -> x([\d.]+)/);
  check('Simulator: Blade Mastery 100 adds 99 sword Power (+4.95)', !!sim && Math.abs(Number(sim[2]) - Number(sim[1]) - 4.95) < 0.011 && /changes/.test(slotName(10)), slotName(10) + ' | ' + slotLore(10));
  await click(45, 900); // back
  check('Simulating changed nothing', slotLore(10) === before, slotLore(10));

  // Dev buffs: +100 Vitality, check max health, clear
  await click(31, 900);
  check('Dev buffs open', await waitTitle(/Dev buffs/), title());
  let at = chat.length;
  const vit = findSlot(/^\+100 Vitality$/);
  await click(vit, 1500);
  check('Buff applied and reported', saw(/\+100 Vitality for 5 minutes/, at), chat.slice(at).join(' / '));
  check('Active list shows the buff', /Dev buff \+100 Vitality/.test(slotLore(31)), slotLore(31));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await cmd('/stats', 1500);
  const buffed = (slotLore(31).match(/Max health: ([\d,]+)/) || [])[1];
  check(`Max health rose by 100 with the buff (${baseHp} -> ${baseHp + 100})`, Number(String(buffed).replace(/,/g, '')) === baseHp + 100, slotLore(31));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await cmd('/cerb panel', 1500);
  await click(findSlot(/^Attributes$/), 900); await click(10, 900); await click(31, 900);
  at = chat.length;
  await click(40, 1500);
  check('Clear all buffs', saw(/Cleared 1 buff/, at), chat.slice(at).join(' / '));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await cmd('/stats', 1500);
  const after = (slotLore(31).match(/Max health: ([\d,]+)/) || [])[1];
  check(`Max health back to normal after clearing (${baseHp})`, Number(String(after).replace(/,/g, '')) === baseHp, slotLore(31));
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}

async function buffexpire() {
  await cmd('/cerb panel', 1500);
  await click(findSlot(/^Attributes$/), 900); await click(10, 900); await click(31, 900);
  await click(findSlot(/^\+100 Vitality$/), 1500);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  await cmd('/stats', 1500);
  const during = (slotLore(31).match(/Max health: ([\d,]+)/) || [])[1];
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  console.log('buff applied, max health ' + during + '; waiting 5m10s for it to expire');
  await sleep(310000);
  await cmd('/stats', 1500);
  const after = (slotLore(31).match(/Max health: ([\d,]+)/) || [])[1];
  check('Dev buff expires on its own: max health ' + during + ' -> back to normal', during === '202' && after === '102', `${during} -> ${after}`);
}

async function deopmid() {
  await cmd('/cerb panel', 1500);
  check('Opened as op', await waitTitle(/Control Center/), title());
  console.log('READY');
  await sleep(6000); // the harness deops us now
  const at = chat.length;
  await click(findSlot(/^Players$/), 1200);
  check('After deop: click is refused and the menu closes', !bot.currentWindow && saw(/don't have permission/, at), title() + ' / ' + chat.slice(at).join(' / '));
}

// Every phase starts at world spawn: other phases teleport the bot, and test mobs spawned
// inside a hillside suffocate mid-check.
async function staleclick() {
  const level = async () => { const at = await cmd(`/cerb skill ${NAME} Blade_Mastery xp 0`, 900); const m = (chat.slice(at).find(x => /is now level \d+/.test(x)) || '').match(/level (\d+)/); return m ? Number(m[1]) : NaN; };
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`, 900);
  await cmd('/cerb panel', 1500);
  await waitTitle(/Control Center/);
  await click(findSlot(/^Players$/)); await waitTitle(/Players/);
  await click(findSlot(new RegExp('^' + NAME + '$'))); await waitTitle(new RegExp('Player: ' + NAME));
  await click(findSlot(/^Skills$/));
  check('Skill list opens', await waitTitle(/Skills:/), title());
  for (const delay of [130, 180, 240, 320]) {
    const list = bot.currentWindow;
    const tile = findSlot(/^Blade Mastery$/);
    bot.clickWindow(tile, 0, 0);
    await sleep(delay);
    // What a client with lag sends: a click on the skill list's window, after the server already moved on.
    const cur = bot.currentWindow;
    bot.currentWindow = list;
    try { bot.clickWindow(25, 0, 0); } catch (e) {}
    bot.currentWindow = cur;
    await sleep(900);
    const lv = await level();
    check(`A click aimed at the skill list ${delay} ms after the editor opened does not press +10 levels`, lv === 1, 'level ' + lv);
    await waitTitle(/Blade Mastery/);
    await click(45, 900); // Back
    await waitTitle(/Skills:/);
  }
  // Control: a click made on the editor itself does press +10 (the slot and the clicks are right).
  const before = await level();
  await click(findSlot(/^Blade Mastery$/), 900);
  await waitTitle(/Blade Mastery/);
  await click(25, 900);
  const lv = await level();
  check('Control: +10 levels clicked on the editor itself works', lv === before + 10, before + ' -> ' + lv);
  await cmd(`/cerb skill ${NAME} Blade_Mastery level 1`, 600);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
}

async function goHome() {
  const s = bot.spawnPoint;
  if (s) { bot.chat(`/tp @s ${s.x + 0.5} ${s.y} ${s.z + 0.5}`); await new Promise(r => setTimeout(r, 1200)); }
}

bot.once('spawn', async () => {
  await sleep(2500);
  try {
    await goHome();
    if (phase === 'main') await main();
    else if (phase === 'nonop') await nonop();
    else if (phase === 'deopmid') await deopmid();
    else if (phase === 'dupe') await dupe();
    else if (phase === 'cross') await cross();
    else if (phase === 'owners') await owners();
    else if (phase === 'attrs') await attrs();
    else if (phase === 'buffexpire') await buffexpire();
    else if (phase === 'staleclick') await staleclick();
  } catch (e) { console.log('SCRIPT ERROR', e.stack); results.push(false); }
  const failed = results.filter(r => !r).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  bot.quit(); await sleep(400); process.exit(failed ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, phase === 'buffexpire' ? 420000 : 240000);
