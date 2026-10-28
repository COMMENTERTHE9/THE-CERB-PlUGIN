// Fishing Equipment, Guided Discovery and Collections in game (docs/design/FISHING_EQUIPMENT.md). LOCAL SANDBOX servers
// only; the bot must be op (it sets levels, time and biomes). Usage: node fishgear.js [phase[,phase...]] [botName]
//   pages    - the skill pages' layout and sections: Fishing (Journal, Encounters; Equipment from increment B) and
//              Woodcutting (Woods) open their screens and Back returns to the page; the hub's Collections index lists
//              fish species, fishing treasure, sea creatures defeated and woods with the same counts the skill pages show,
//              opens the same screens, and Back returns to it; the journal's tabs and filters, kept across a redraw
//   track    - tracking a species: each requirement checked where you fish (a labelled preview of the water you look at,
//              your bobber while a cast is out, "not known" otherwise, never where you stand), "Available here" lists
//              exactly what /cerb fish info (the bite's own eligibility) lists, the tracked species is saved (/fishing
//              track), and the cast hint says whether it can bite there, once per change; open water is told only at
//              the bobber, so treasure is "Available here" only for a bobber in open water
//   summary  - "since your last visit": real catches and the XP really awarded (measured), a discovery and its weight
//              among the notable catches, redraws never clear it, marking it read (even a quick double click) takes off
//              what was shown; creative fishing adds nothing, and the journal says only vanilla fish bite there
//   terms    - Encounters: a banked sea creature says "encounter", /fishing encounters (and the old /fishing lures) list them
//   equip    - Fishing → Equipment for a new character: presets, the four categories and their levels, fitting a part (asks
//              first, uses one item, owned once, any rod), the effect previewed from the engine (raw change, when it
//              applies), switching presets, renaming by chat (bad names refused, "cancel"), one specialist per preset, a
//              part the server removed (needs CERB_SERVER_DIR: it edits the sandbox resources.yml and puts it back), menus
//              never moving items on shift-clicks or number keys
//   bait     - crafting bait (and a recipe that waits for its milestone), the Bait Bag, one bait held per cast and used
//              only by a completed catch it applied to: put back on a reel-in, a refused catch (needs CerbTestProtect's
//              /testnocatch), a refused reel-in (/testnoreel), a logout; a preset switch mid-cast; situational bait
//              waiting in the bag; "Bait depleted" once; opt-in restocking; a held bait with nowhere to go comes back
//              as an item; one that took its slot's last bait goes back to that slot
//   reel     - "/fishing reel on" before it is fitted says it does nothing yet; the Auto-hook Reel (crafted, fitted at
//              Fishing 50): reels real bites through the normal path (XP, the rod's
//              wear), never recasts, one catch per bite when you reel too, off when switched off, a refused catch (the
//              cast goes on), a full inventory, with a menu open, switching items, a breaking rod, death, logout, and
//              never a Sneak-cast call
//   rewards  - the collection milestones: shown in Collections and on the Fishing page; species in 3 waters open the
//              Wayfarer's Bait recipe, 8 species Surveyor's Float (into Discoveries), personal bests the Specimen Hook
//              (the sandbox counts 2 for the check); each once, never again on a rejoin; one already met is granted at
//              the next join with one message; Master Angler's emblem on the page, the journal and Collections (the
//              last three need CERB_SERVER_DIR: they lower thresholds in the sandbox resources.yml and put them back)
//   specialists - Surveyor's Float in /cerb fish info (the shares the bite rolls with): the same species and share of
//              species bites, the caught one less often, the others at most x1.5; "normal odds" on the tile and once at
//              the first bite where nothing new can bite; the Specimen Hook replacing it, weights in range; a specialist
//              granted where one is in a preset stays out; "Next levels" count a part from the level it opens at
//   trophy   - Keep as trophy: asks first, one fish becomes the catch's trophy (name and weight, who, date, water), once
//              per notable catch, trophies never stack, a stale confirmation changes nothing
//   oldstate - (run on the build BEFORE Fishing equipment, with the bot name you will use for legacy) a character with an
//              old journal, an encounter and rods upgraded the old way
//   legacy   - after updating: that character's journal and old rods (they keep working), the setup and an old rod
//              counting an upgrade once, converting (preview, a full inventory and a changed rod refused, name,
//              enchantments and damage kept, an owned part's item back once); without oldstate's rods it makes them
//              with /cerb fish legacyrod
//   restart-set / restart-check - a setup (parts, a renamed preset, the one in use, switches, two bait slots) with a
//              bait on the line when the server stops cleanly: all of it after the restart, the bait back once (the
//              caller restarts the server on "READY"; STATE names the file passed between the two)
//   crash-set / crash-check - the same mid-cast, but the player's data is written with the bait held (needs
//              CerbTestProtect's /testsave) and the server is killed: the bait is back once at the next join, not twice
//   cue      - the bite cue: a "!" over the bobber, green then yellow then red, gone when the fish gets away or is caught,
//              a bell in the owner's ear, nothing for another player at the pond, nothing with /fishing cue off, and in
//              creative too
//   all      - pages, track, summary, terms, equip, bait, reel, rewards, specialists, trophy, cue
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const fs = require('fs');
const path = require('path');
const phaseArg = process.argv[2] || 'all';
// A new name is a fresh character (op it first: the sandbox console's "op <name>"); oldstate and legacy share one name.
// In a run of several phases, bait, rewards, specialists and trophy each get a fresh character of their own (<name>bai...).
let NAME = process.argv[3] || 'CerbGear';
/**
 * Phases that need a character with nothing reached yet (bait checks the Wayfarer's Bait recipe before its milestone,
 * which a character can earn by the catches of earlier phases): in a run of several phases each gets its own (op'd by
 * the bot).
 */
const FRESH = ['bait', 'rewards', 'specialists', 'trophy'];
const sleep = ms => new Promise(r => setTimeout(r, ms));
const chat = [];
const bars = [];
const results = [];
const failed = [];
const check = (name, ok, detail = '') => {
  results.push(ok);
  if (!ok) failed.push(name);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  (' + String(detail).slice(0, 400) + ')' : ''}`);
};
const note = s => console.log('      ' + s);
let bot, pchat;
/** True once a phase waits for the server to stop: the kick that follows is expected. */
let serverGoing = false;
function connect(name = NAME) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.11', auth: 'offline' });
  b.on('messagestr', (m, pos) => { if (pos !== 'game_info') chat.push(m); });
  b._client.on('action_bar', p => {
    try { pchat = pchat || require('prismarine-chat')(b.registry); bars.push({ t: Date.now(), m: pchat.fromNotch(p.text).toString() }); } catch (e) { /* ignore */ }
  });
  b.on('kicked', r => { if (serverGoing) return; console.log('KICKED', name, r); process.exit(2); });
  bot = b;
  return new Promise(r => b.once('spawn', () => r(b)));
}
async function cmd(c, wait = 600) { const at = chat.length; bot.chat(c); await sleep(wait); return at; }
const saw = (re, since) => chat.slice(since).find(m => re.test(m));
const sawAll = (re, since) => chat.slice(since).filter(m => re.test(m));
const hudSince = t => [...new Set(bars.filter(b => b.t >= t).map(b => b.m))];

// ── menus ────────────────────────────────────────────────────────
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
const flat = i => { const it = slot(i); return it && it.customLore ? it.customLore.map(plain).join(' ').replace(/\s+/g, ' ').trim() : ''; };
const findSlot = (re, from = 0, to = 53) => { if (!bot.currentWindow) return -1; for (let i = from; i <= to; i++) if (re.test(slotName(i))) return i; return -1; };
const SCREEN = name => new RegExp('^✦ ' + name + '$');
async function waitTitle(re, ms = 4000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (bot.currentWindow && re.test(title())) return true; await sleep(100); } return false; }
/** Wait until a tile matching {@code re} is drawn (a page with "since you last looked" reveals its tiles over a moment). */
async function waitSlot(re, from = 0, to = 53, ms = 3000) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) { const i = findSlot(re, from, to); if (i >= 0) return i; await sleep(100); }
  return -1;
}
async function click(i, wait = 750, button = 0, mode = 0) { await bot.clickWindow(i, button, mode); await sleep(wait); }
async function closeAny() { if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); } }
async function openMenu() { await closeAny(); await cmd('/menu', 1200); await sleep(300); return waitTitle(SCREEN('Player Menu')); }
async function openSkill(name) {
  if (!(await openMenu())) return false;
  await click(19); if (!(await waitTitle(SCREEN('Skills')))) return false;
  await click(24); if (!(await waitTitle(SCREEN('Utility Skills')))) return false;
  const s = await waitSlot(new RegExp('^(▲ )?' + name + '\\b')); // "▲ Fishing" when it has news since you last looked
  if (s < 0) return false;
  await click(s);
  return waitTitle(SCREEN(name));
}

// ── skills, the world ────────────────────────────────────────────
const xpForLevel = l => Math.round(75 * Math.pow(Math.max(1, l), 1.85) + 40 * Math.max(1, l));
const totalXp = s => { let t = s.xp; for (let l = 1; l < s.level; l++) t += xpForLevel(l); return t; };
async function skill(name, who = NAME) {
  const at = await cmd(`/cerb skill ${who} ${name} xp 0`, 600);
  const m = (saw(/is now level \d+ \(\d+ XP\)/, at) || '').match(/level (\d+) \((\d+) XP\)/);
  return m ? { level: Number(m[1]), xp: Number(m[2]) } : { level: NaN, xp: NaN };
}
async function setLevel(name, level, who = NAME) { await cmd(`/cerb skill ${who} ${name} level ${level}`, 500); }
async function hold(item) {
  await cmd('/clear @s ' + item.replace(/\[.*$/, ''), 400); await cmd('/give @s ' + item, 900);
  const base = item.replace(/\[.*$/, '');
  const i = bot.inventory.slots.findIndex((it, s) => it && it.name === base && s >= 36 && s <= 44);
  if (i >= 0) { bot.setQuickBarSlot(i - 36); await sleep(300); }
  return i;
}
const ROD = 'fishing_rod[enchantments={lure:3}]';
let X = 0, Y = 120, Z = 0, SP = null;
/** A pond in the sky (7x7, 3 deep, open water), its own biome, far from spawn. */
async function arena(dx = 0, biome = 'river') {
  SP = SP || bot.spawnPoint || bot.entity.position;
  X = Math.floor(SP.x) - 900 + dx; Z = Math.floor(SP.z) + 900;
  // Folia moves a player to a far region asynchronously, and a command sent meanwhile can be lost: wait until the bot is
  // there, then run each change until the server confirms it.
  for (let i = 0; i < 3; i++) {
    await cmd(`/tp @s ${X + 0.5} ${Y + 2} ${Z + 0.5}`, 2500);
    if (bot.entity.position.distanceTo(new Vec3(X + 0.5, Y + 2, Z + 0.5)) < 3) break;
  }
  await sleep(1500);
  await sure(`/fill ${X - 6} ${Y - 5} ${Z - 3} ${X + 6} ${Y - 1} ${Z + 12} stone`, /filled|No blocks were filled/);
  await sure(`/fill ${X - 6} ${Y} ${Z - 3} ${X + 6} ${Y + 6} ${Z + 12} air`, /filled|No blocks were filled/);
  await sure(`/fill ${X - 3} ${Y - 3} ${Z + 2} ${X + 3} ${Y - 1} ${Z + 8} water`, /filled|No blocks were filled/);
  await sure(`/fillbiome ${X - 6} ${Y - 5} ${Z - 3} ${X + 6} ${Y + 6} ${Z + 12} minecraft:${biome}`, /biome|filled/i);
  await cmd('/time set day', 300);
  await cmd('/weather clear', 300);
  await stand();
}
/** Run a command until the server answers with {@code ok} (up to three tries). */
async function sure(c, ok) {
  for (let i = 0; i < 3; i++) {
    const at = await cmd(c, 900);
    if (saw(ok, at)) return true;
  }
  note('no confirmation for ' + c);
  return false;
}
async function stand() {
  await cmd(`/tp @s ${X + 0.5} ${Y} ${Z + 0.5} 0 20`, 800);
  try { await bot.lookAt(new Vec3(X + 0.5, Y - 1, Z + 5.5), true); } catch (e) { /* best effort */ }
}
async function survival(level, clear = true) {
  await cmd('/gamemode survival', 400);
  await cmd('/effect give @s resistance infinite 4 true', 300);
  await cmd('/effect give @s saturation infinite 1 true', 300);
  if (clear) await cmd('/clear @s', 400);
  if (level !== undefined) await setLevel('Fishing', level);
  if (clear) await hold(ROD);
}
async function catchOne(timeoutMs = 45000) {
  try {
    await Promise.race([bot.fish(), new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), timeoutMs))]);
    await sleep(900);
    return true;
  } catch (e) {
    note('no catch: ' + (e.message || e));
    try { bot.activateItem(); await sleep(300); } catch (x) { /* reel in */ }
    return false;
  }
}
/** Cast and leave the bobber in the water (no reel). */
async function castOnly() { bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(2500); }
async function reelIn() { bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(500); }
/** Species ids the admin journal shows as not caught (✘). */
async function uncaught() {
  const at = await cmd(`/cerb fish journal ${NAME}`, 1400);
  return sawAll(/✘/, at).map(l => l.replace(/^.*✘\s*/, '').split(' · ')[0].trim());
}
const idOf = name => name.toLowerCase().replace(/ /g, '_');
/** The summary counters from the admin dump. */
async function summaryDump() {
  const at = await cmd(`/cerb fish journal ${NAME}`, 1400);
  const l = saw(/unread summary: /, at) || '';
  const m = l.match(/unread summary: (\d+) catches, (\d+) XP, (\d+) species, (\d+) bests, (\d+) encounters · notable: (\d+) \((\d+) unread\)/);
  return m ? { catches: +m[1], xp: +m[2], species: +m[3], bests: +m[4], encounters: +m[5], notable: +m[6], unread: +m[7] } : null;
}

// ════════════════════════════════════════════════════════════════════

async function pages() {
  await cmd('/gamemode creative', 300);
  check('Fishing page opens', await openSkill('Fishing'), title());
  const row2 = [19, 21, 23, 25].map(slotName);
  check('Row 2 keeps the page readable: How to train, Attributes & speed, What it does, Next levels',
    /How to train/.test(row2[0]) && /Attributes & speed/.test(row2[1]) && /What it does/.test(row2[2]) && /Next levels/.test(row2[3]), row2.join(' | '));
  const journalTile = findSlot(/^Journal: \d+ of \d+ species$/, 27, 35), encTile = findSlot(/^Encounters: \d+$/, 27, 35);
  check('Section tiles in row 3: Journal: N of M species, Encounters: N', journalTile >= 0 && encTile >= 0,
    [27, 28, 29, 30, 31, 32, 33, 34, 35].map(slotName).filter(Boolean).join(' | '));
  const jn = (slotName(journalTile).match(/Journal: (\d+) of (\d+)/) || []).slice(1).map(Number);
  check('No giant tooltip: the Journal tile is a few lines', flat(journalTile).length < 220, flat(journalTile).length + ' chars');
  check('The summary tile sits between the skill arrows', /Since your last visit/.test(slotName(40)) && /◀/.test(slotName(37)) && /▶/.test(slotName(43)), slotName(40));
  await click(journalTile);
  check('Journal opens from the Fishing page', await waitTitle(SCREEN('Fishing Journal')), title());
  check('Journal header: species and treasure counts', new RegExp(`Species: ${jn[0]} of ${jn[1]}`).test(flat(4)) && /Treasure: \d+ of \d+/.test(flat(4)), flat(4));
  check('Tabs: All waters (shown), each water, Treasure', /▶ All waters/.test(slotName(9)) && /Rivers/.test([10, 11, 12, 13, 14, 15, 16].map(slotName).join(' ')) && /Treasure/.test(slotName(17)),
    [9, 10, 11, 12, 13, 14, 15, 16, 17].map(slotName).join(' | '));
  const entries = () => [19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43].filter(i => slotName(i) && !/Nothing to show/.test(slotName(i)));
  check('A page of species entries with rarity, water and requirements', entries().length === 21 && /Fishing \d+/.test(flat(entries()[0])) && /(Common|Uncommon|Rare|Epic|Legendary) · /.test(flat(entries()[0])),
    entries().length + ' entries; ' + flat(entries()[0]));
  await click(47);
  check('Filter: Undiscovered', /Filter: Undiscovered/.test(slotName(47)) && entries().every(i => /Not caught yet/.test(flat(i))), slotName(47) + ' ' + entries().length);
  const riverTab = findSlot(/Rivers/, 9, 17);
  await click(riverTab);
  check('A water tab keeps the filter', /▶ Rivers/.test(slotName(riverTab)) && /Filter: Undiscovered/.test(slotName(47)) && entries().every(i => /Rivers/.test(flat(i))),
    slotName(riverTab) + ' / ' + slotName(47));
  await click(52);
  check('A redraw keeps the tab and the filter', /▶ Rivers/.test(slotName(riverTab)) && /Filter: Undiscovered/.test(slotName(47)), slotName(riverTab) + ' / ' + slotName(47));
  await click(45);
  check('Back from the journal returns to the Fishing page', await waitTitle(SCREEN('Fishing')), title());
  await click(encTile);
  check('Encounters opens from the Fishing page', await waitTitle(SCREEN('Encounters')) && /^Encounters: \d+$/.test(slotName(4)), title() + ' / ' + slotName(4));
  const creatures = [19, 20, 21, 22, 23, 24, 25, 28].map(slotName).filter(Boolean);
  check('Every sea creature is listed with its waters and drops', creatures.length === 8 && /Waters: /.test(flat(19)) && /Drops:/.test(flat(19)), creatures.join(' | '));
  await click(45);
  check('Back from Encounters returns to the Fishing page', await waitTitle(SCREEN('Fishing')), title());

  // Woodcutting: one section.
  check('Woodcutting page opens', await openSkill('Woodcutting'), title());
  const woods = findSlot(/^Woods: \d+ of \d+ discovered$/, 27, 35);
  check('Woodcutting has one section: Woods: N of M discovered', woods === 31, slotName(woods) + ' at ' + woods);
  const wn = (slotName(woods).match(/Woods: (\d+) of (\d+)/) || []).slice(1).map(Number);
  await click(woods);
  check('Woods opens with every wood', await waitTitle(SCREEN('Woods')) && [19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31].every(i => slotName(i)), title());
  await click(45);
  check('Back from Woods returns to the Woodcutting page', await waitTitle(SCREEN('Woodcutting')), title());
  // A skill without functionality has no sections.
  await click(45); await waitTitle(SCREEN('Utility Skills'));
  const mining = findSlot(/^Mining\b/);
  await click(mining); await waitTitle(SCREEN('Mining'));
  check('A skill without sections shows none', [28, 29, 30, 31, 32, 33, 34].every(i => /^ ?$/.test(slotName(i))), [28, 29, 30, 31, 32, 33, 34].map(slotName).join('|'));

  // Collections: the same data and the same screens.
  await openMenu();
  check('The hub\'s Collections tile is available', /Collections/.test(slotName(32)) && /Available/.test(flat(32)), flat(32));
  await click(32);
  check('Collections opens', await waitTitle(SCREEN('Collections')), title());
  const names = [20, 22, 24, 28].map(slotName);
  check('Four collections: fish species, fishing treasure, sea creatures defeated, woods', names.join('|') === 'Fish species|Fishing treasure|Sea creatures defeated|Woods', names.join(' | '));
  const fishC = (flat(20).match(/Collected: (\d+) of (\d+)/) || []).slice(1).map(Number);
  const woodC = (flat(28).match(/Collected: (\d+) of (\d+)/) || []).slice(1).map(Number);
  check('The same counts as the skill pages (from the definitions)', fishC.join('/') === jn.join('/') && woodC.join('/') === wn.join('/'),
    `fish ${fishC} vs ${jn}, woods ${woodC} vs ${wn}`);
  await click(20);
  check('Fish species opens the Fishing Journal', await waitTitle(SCREEN('Fishing Journal')) && /▶ All waters/.test(slotName(9)), title());
  await click(45);
  check('Back returns to Collections', await waitTitle(SCREEN('Collections')), title());
  await click(22);
  check('Fishing treasure opens the journal on its Treasure tab', await waitTitle(SCREEN('Fishing Journal')) && /▶ Treasure/.test(slotName(17)) && /treasure/.test(flat(19)), slotName(17) + ' / ' + flat(19));
  await click(45); await waitTitle(SCREEN('Collections'));
  await click(24);
  check('Sea creatures defeated opens Encounters', await waitTitle(SCREEN('Encounters')), title());
  await click(45); await waitTitle(SCREEN('Collections'));
  await click(28);
  check('Woods opens the Woods screen', await waitTitle(SCREEN('Woods')), title());
  await closeAny();
}

async function track() {
  await arena(0, 'river');
  await survival(30);
  await cmd('/fishing track off', 500);
  // Looking at the river pond, then opening the journal: a labelled preview of that water.
  await stand();
  await openSkill('Fishing');
  await click(findSlot(/^Journal: /, 27, 35));
  await waitTitle(SCREEN('Fishing Journal'));
  check('Before a cast: the water you look at, labelled a preview', /Where you fish: Rivers/.test(slotName(2)) && /preview/.test(flat(2)) && !/Open water/.test(flat(2)), slotName(2) + ' / ' + flat(2));
  const trout = findSlot(/Goldfin Trout/);
  await click(trout);
  check('Clicking a species tracks it', /★ Tracked: Goldfin Trout/.test(slotName(6)), slotName(6));
  check('Each requirement on its own line: level met, water met (preview)', /✓ Fishing 25 \(you: 30\)/.test(flat(6)) && /✓ Rivers \(preview: Rivers\)/.test(flat(6)) && /It can bite here now/.test(flat(6)), flat(6));
  const brook = findSlot(/Brook Char/);
  await click(brook);
  check('Tracking another species replaces it; by day it says what is missing', /Tracked: Brook Char/.test(slotName(6)) && /✕ Night only \(it is day\)/.test(flat(6)) && /can't bite here now/.test(flat(6)), flat(6));
  await closeAny();
  await cmd('/time set midnight', 500);
  await openSkill('Fishing');
  await click(findSlot(/^Journal: /, 27, 35));
  await waitTitle(SCREEN('Fishing Journal'));
  check('At night the time requirement is met', /✓ Night only \(it is night\)/.test(flat(6)) && /It can bite here now/.test(flat(6)), flat(6));
  // Available here = the bite's own eligibility (/cerb fish info lists the eligible species at this level).
  await click(47); await click(47); // All -> Undiscovered -> Available here
  const here = [19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34].map(slotName).filter(n => n && !/Nothing to show/.test(n)).map(n => n.trim()).sort();
  await closeAny();
  const at = await cmd('/cerb fish info', 1200);
  const info = sawAll(/^\s+\S.* [\d.]+%$/, at).filter(l => !/Lure|Treasure|Encounter/.test(l)).map(l => l.trim().replace(/ [\d.]+%$/, '')).map(s => s.split(' ').map(w => w[0].toUpperCase() + w.slice(1)).join(' ')).sort();
  check('"Available here" lists exactly what the bite allows (/cerb fish info)', here.length > 0 && JSON.stringify(here) === JSON.stringify(info), `journal ${here.join(', ')} | info ${info.join(', ')}`);
  // The tracked species is saved: a rejoin keeps it.
  bot.quit(); await sleep(3500); await connect(); await sleep(3000);
  const at2 = await cmd('/fishing track', 800);
  check('The tracked species is saved across a rejoin', !!saw(/Tracking .*Brook Char/, at2), sawAll(/Track/, at2).join(' / '));
  // With a cast out, "here" is the bobber; the cast also brings the tracked species' hint (night: it can bite here).
  await survival(30); await stand();
  const tCast = Date.now();
  await castOnly();
  const hint1 = hudSince(tCast).filter(m => /★ Brook Char/.test(m));
  await openSkill('Fishing');
  await click(findSlot(/^Journal: /, 27, 35));
  await waitTitle(SCREEN('Fishing Journal'));
  check('With a cast out: where your bobber floats', /Where you fish: Rivers/.test(slotName(2)) && /where your bobber floats/.test(flat(2)) && /your bobber: Rivers/.test(flat(6)), flat(2) + ' / ' + flat(6));
  check('...and whether it floats in open water (vanilla\'s treasure rule)', /Open water: yes/.test(flat(2)), flat(2));
  await click(17); await click(47); await click(47); // Treasure, then All -> Undiscovered -> Available here
  const tre = [19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34].map(slotName).filter(n => n && !/Nothing to show/.test(n));
  check('"Available here" on Treasure with the bobber in open water: what can come (Fishing 30: the level-25 treasure)', tre.length === 2, tre.join(', ') || flat(31));
  await closeAny();
  await reelIn();
  // Without a bobber, treasure can't be told: vanilla decides open water at the bobber.
  await stand();
  await openSkill('Fishing');
  await click(findSlot(/^Journal: /, 27, 35));
  await waitTitle(SCREEN('Fishing Journal'));
  await click(17); await click(47); await click(47);
  check('"Available here" on Treasure with only a preview: cast to check, nothing claimed', /Nothing to show/.test(slotName(31)) && /open water around/.test(flat(31)), slotName(31) + ' / ' + flat(31));
  await closeAny();
  // Cast hint: once per verdict.
  check('The cast hint said the tracked species can bite here', hint1.some(m => /can bite here/.test(m)), hint1.join(' / ') || 'no hint');
  let t = Date.now();
  await castOnly(); await sleep(2500);
  check('The same verdict is not repeated on the next cast', hudSince(t).filter(m => /★ Brook Char/.test(m)).length === 0, hudSince(t).join(' / '));
  await reelIn();
  await cmd('/time set day', 400);
  t = Date.now();
  await castOnly(); await sleep(2500);
  let dayHint = hudSince(t).some(m => /★ Brook Char bites only at night/.test(m));
  if (!dayHint) { // a busy bar (a catch's message as the last reel-in caught something) drops a hint: the next cast tries again
    note('no hint on the first cast (' + hudSince(t).join(' / ') + '): casting again');
    await reelIn(); await sleep(3500);
    t = Date.now(); await castOnly(); await sleep(2500);
    dayHint = hudSince(t).some(m => /★ Brook Char bites only at night/.test(m));
  }
  check('A change (day) shows what is missing', dayHint, hudSince(t).join(' / '));
  await reelIn();
  // Unknown place: no bobber, looking at the sky.
  try { await bot.look(0, Math.PI / 2, true); } catch (e) { /* best effort */ }
  await openSkill('Fishing');
  await click(findSlot(/^Journal: /, 27, 35));
  await waitTitle(SCREEN('Fishing Journal'));
  check('No cast and no water in sight: the place is not known (not where you stand)', /not known yet/.test(slotName(2)) && /\? Rivers \(cast to check\)/.test(flat(6)), slotName(2) + ' / ' + flat(6));
  await click(47); await click(47);
  check('"Available here" with no known place explains itself', /Nothing to show/.test(slotName(31)) && /cast your line/.test(flat(31)), flat(31));
  await closeAny();
  await cmd('/fishing track off', 400);
}

async function summary() {
  await arena(60, 'plains');
  await survival(30);
  // Start from a read summary.
  await openSkill('Fishing');
  if (/Right-click: mark as read/.test(flat(40))) { await click(40, 900, 1); }
  await closeAny();
  const s0 = await summaryDump();
  check('A read summary has no unread counters', s0 && s0.catches === 0 && s0.xp === 0 && s0.unread === 0, JSON.stringify(s0));
  const fresh = (await uncaught()).map(idOf).find(id => !/abyssal|crystal|riverking|sturgeon/.test(id)) || 'emerald_tench';
  await cmd(`/cerb fish force ${NAME} species ${fresh}`, 400);
  const xp0 = totalXp(await skill('Fishing'));
  let caught = 0;
  for (let i = 0; i < 4; i++) if (await catchOne()) caught++;
  const xp1 = totalXp(await skill('Fishing'));
  const s1 = await summaryDump();
  check('Catches and the XP really awarded are counted', s1 && s1.catches === caught && s1.xp === xp1 - xp0, `caught ${caught}, xp +${xp1 - xp0}; ${JSON.stringify(s1)}`);
  check('The forced first catch counts as a new species among the notable catches', s1 && s1.species >= 1 && s1.unread >= 1, JSON.stringify(s1));
  await openSkill('Fishing');
  const tile = flat(40);
  check('The Fishing page shows it: catches, XP, new species', new RegExp(`${caught} catch(es)? · \\+[\\d,]+ Fishing XP`).test(tile) && /new species/.test(tile), tile);
  await closeAny();
  await openSkill('Fishing');
  check('Reopening the page does not clear it', flat(40) === tile, flat(40));
  await click(40);
  check('Click: view new discoveries', await waitTitle(SCREEN('New discoveries')) && /NEW/.test(slotName(19)) && /\d+\.\d+ kg/.test(flat(19)), slotName(19) + ' / ' + flat(19));
  // A catch while the screen is open is not lost when it is marked read.
  let extra = false;
  try {
    await Promise.race([bot.fish(), new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 45000))]);
    await sleep(900);
    const d = await summaryDump();
    extra = !!d && d.catches > s1.catches;
  } catch (e) { note('the bot could not catch with the window open (' + e.message + '): FishingJournalTest covers that case'); }
  check('The screen stayed open during that catch', SCREEN('New discoveries').test(title()), title());
  const before = await summaryDump();
  // Two quick clicks: the second lands on the redrawn screen, which shows the catch made meanwhile: it is not taken.
  Promise.resolve(bot.clickWindow(51, 0, 0)).catch(() => {}); await sleep(60);
  Promise.resolve(bot.clickWindow(51, 0, 0)).catch(() => {}); await sleep(900);
  const after = await summaryDump();
  if (extra) check('Mark as read takes off what the screen showed: the catch made while it was open stays unread', after && after.catches === before.catches - s1.catches && after.catches >= 1,
    `shown ${s1.catches}, before ${before && before.catches}, after ${after && after.catches}`);
  else check('Mark as read clears what the screen showed', after && after.catches === 0 && after.species === 0, JSON.stringify(after));
  await closeAny();
  // Creative: nothing is counted.
  await openSkill('Fishing'); if (/Right-click: mark as read/.test(flat(40))) await click(40, 900, 1); await closeAny();
  const c0 = await summaryDump();
  await cmd('/gamemode creative', 400);
  await hold(ROD);
  for (let i = 0; i < 2; i++) await catchOne();
  const c1 = await summaryDump();
  check('Creative fishing adds nothing to the summary', c1 && c0 && c1.catches === c0.catches && c1.xp === c0.xp, `${JSON.stringify(c0)} -> ${JSON.stringify(c1)}`);
  await stand();
  await openSkill('Fishing');
  await click(findSlot(/^Journal: /, 27, 35));
  await waitTitle(SCREEN('Fishing Journal'));
  check('The journal in creative says only vanilla fish bite there', /Creative or spectator: vanilla fishing/.test(flat(2)), flat(2));
  await click(47); await click(47);
  check('...and "Available here" lists nothing, saying why', /Nothing to show/.test(slotName(31)) && /Creative or spectator/.test(flat(31)), slotName(31) + ' / ' + flat(31));
  await closeAny();
  await cmd('/gamemode survival', 300);
}

async function terms() {
  await arena(120, 'river');
  await survival(30);
  await cmd(`/cerb fish force ${NAME} lure river_lurker`, 400);
  const at = chat.length;
  await catchOne();
  await sleep(800);
  check('A banked sea creature says "encounter banked"', !!saw(/River Lurker .*took your bait.*Encounter banked/, at), sawAll(/took your bait/, at).join(' / '));
  const at2 = await cmd('/fishing encounters', 900);
  check('/fishing encounters lists them', !!saw(/^Encounters: .*River Lurker/, at2), chat.slice(at2).join(' / '));
  const at3 = await cmd('/fishing lures', 900);
  check('The old /fishing lures still works', !!saw(/^Encounters: .*River Lurker/, at3), chat.slice(at3).join(' / '));
  await openSkill('Fishing');
  const enc = findSlot(/^Encounters: \d+$/, 27, 35);
  check('The Fishing page\'s tile is "Encounters: N"', enc >= 0 && Number(slotName(enc).split(': ')[1]) >= 1, slotName(enc));
  await closeAny();
}

// ── equipment (increment B) ──────────────────────────────────────
const nameOf = it => it ? plain(it.customName) : '';
const loreText = it => it && it.customLore ? it.customLore.map(plain).join(' | ') : '';
const carry = name => bot.inventory.items().filter(it => nameOf(it).includes(name)).reduce((n, it) => n + it.count, 0);
const rodsHeld = () => bot.inventory.items().filter(it => it.name === 'fishing_rod');
const enchOf = it => {
  const c = it && (it.components || []).find(x => x.type === 'enchantments');
  const list = (c && c.data && c.data.enchantments) || [];
  return list.map(e => ((bot.registry.enchantments[e.id] || {}).name || e.id) + ' ' + e.level).sort().join(', ');
};
const damageOf = it => (it && typeof it.durabilityUsed === 'number') ? it.durabilityUsed : NaN;
async function give(id, n = 1) { await cmd(`/cerb gather item ${id} ${n}`, 700); }
/** The admin dump of the bot's equipment (/cerb fish gear). */
async function gear() {
  const at = await cmd(`/cerb fish gear ${NAME}`, 1000);
  const lines = chat.slice(at);
  const owned = ((lines.find(l => /^owned: /.test(l)) || '').match(/owned: \[([^\]]*)\]/) || [])[1];
  const presets = lines.map(l => l.match(/^(▶ )?\s*(\d) (.*?): \[([^\]]*)\] bait slots (\d+)/)).filter(Boolean)
    .map(m => ({ active: !!m[1], i: +m[2], name: m[3], parts: m[4].split(',').map(x => x.trim()).filter(Boolean), slots: m[5] }));
  const bagLine = lines.find(l => /^bag: /.test(l)) || '';
  const bag = [1, 2, 3].map(i => { const m = bagLine.match(new RegExp('\\b' + i + '=([a-z_]+) ×(\\d+)')); return m ? { bait: m[1], n: +m[2] } : null; });
  const held = bagLine.match(/held: ([a-z_]+) \(slot (\d)\)/) || [];
  return {
    owned: owned ? owned.split(',').map(x => x.trim()).filter(Boolean) : [], presets, bag, held: held[1] || null,
    reel: /reel on/.test(bagLine), restock: /restock on/.test(bagLine), raw: lines.join(' / '),
  };
}
const baitIn = (g, slot = 1) => (g.bag[slot - 1] ? g.bag[slot - 1].n : 0);
/** /cerb fish info at the pond: the lure speed ("bites N% sooner"), encounter chance and treasure chance as the engine gives them. */
async function fishInfo() {
  await stand();
  const at = await cmd('/cerb fish info', 1100);
  const t = chat.slice(at).join(' | ');
  const num = re => { const m = t.match(re); return m ? Number(m[1]) : NaN; };
  return { lure: num(/bites ([\d.]+)% sooner/), creature: num(/Encounter chance per catch ([\d.]+)%/), treasure: num(/Treasure per catch in open water ([\d.]+)%/), text: t };
}
async function openEquipment() {
  if (!(await openSkill('Fishing'))) return false;
  const eq = await waitSlot(/^Equipment: /, 27, 35);
  if (eq < 0) return false;
  await click(eq);
  return waitTitle(SCREEN('Fishing Equipment'));
}
const CAT = { Rod: 19, Line: 21, Lure: 23, Reel: 25 };
async function openCategory(name) { if (!(await openEquipment())) return false; await click(CAT[name]); return waitTitle(SCREEN('Equipment: ' + name)); }
async function openBag() { if (!(await openEquipment())) return false; await click(29); return waitTitle(SCREEN('Bait Bag')); }
/** Fit a part from its category screen (it asks first). */
async function fit(category, partRe) {
  if (!(await openCategory(category))) return 'no ' + category + ' screen: ' + title();
  const s = findSlot(partRe, 19, 34);
  if (s < 0) return 'no tile for ' + partRe;
  await click(s);
  if (!(await waitTitle(/^Confirm: Fit /))) return 'no confirmation: ' + title();
  await click(11, 1300);
  return 'ok';
}
/** Select a rod in the hotbar by a test on it (moves it there first when needed). */
async function selectRod(test) {
  const it = bot.inventory.items().find(test);
  if (!it) return false;
  if (it.slot >= 36 && it.slot <= 44) { bot.setQuickBarSlot(it.slot - 36); await sleep(400); return true; }
  try { await bot.clickWindow(it.slot, 0, 2); } catch (e) { /* the server decides */ } // number key 1: into hotbar slot 1
  await sleep(500);
  bot.setQuickBarSlot(0); await sleep(400);
  return true;
}
/**
 * Craft at a table beside the pond: one of each ingredient into the grid, then take the result (a shift-click, or a plain
 * click when {@code shift} is false: the old build's upgrade of an already upgraded rod only takes a plain click). Returns
 * what the result slot showed.
 */
async function craftAt(preds, shift = true) {
  const ct = new Vec3(X - 2, Y, Z - 2);
  await cmd(`/setblock ${ct.x} ${ct.y} ${ct.z} crafting_table`, 600);
  let result = '';
  try {
    const tbl = bot.blockAt(ct);
    await bot.lookAt(tbl.position.offset(0.5, 0.5, 0.5), true);
    const w = await bot.openBlock(tbl);
    await sleep(500);
    const findIn = pred => { for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && pred(w.slots[i])) return i; return -1; };
    const cw = async (slot, button = 0, mode = 0) => { try { await bot.clickWindow(slot, button, mode); } catch (e) { /* the server decides */ } await sleep(250); };
    let to = 1;
    for (const pred of preds) {
      const from = findIn(pred);
      if (from < 0) { note('craft: an ingredient is missing'); continue; }
      await cw(from); await cw(to, 1); await cw(from);
      to++;
    }
    await sleep(700);
    result = w.slots[0] ? `${w.slots[0].name} x${w.slots[0].count} ${nameOf(w.slots[0])} ${loreText(w.slots[0])}` : '';
    if (w.slots[0]) await cw(0, 0, shift ? 1 : 0); // a plain click leaves it on the cursor: closing the table puts it away
    for (let i = 1; i <= 9; i++) if (w.slots[i]) await cw(i, 0, 1);
    bot.closeWindow(w);
  } catch (e) { note('crafting: ' + e.message); }
  await sleep(700);
  return result;
}

/** Run on the build BEFORE Fishing equipment: a character with an old journal, an encounter and rods upgraded the old way. */
async function oldstate() {
  await arena(420, 'river');
  await survival(40);
  for (const id of ['silver_dace', 'river_perch', 'goldfin_trout']) { await cmd(`/cerb fish force ${NAME} species ${id}`, 300); await catchOne(); }
  await cmd(`/cerb fish force ${NAME} lure river_lurker`, 300); await catchOne();
  await cmd('/clear @s', 400);
  await cmd('/give @s fishing_rod[custom_name={text:"Old Faithful",italic:false},enchantments={unbreaking:3,lure:2},damage=17]', 900);
  await give('lurker_scale', 2); await give('bog_resin', 1);
  const r1 = await craftAt([it => it.name === 'fishing_rod' && /Old Faithful/.test(nameOf(it)), it => /Lurker Scale/.test(nameOf(it))], false);
  const r2 = await craftAt([it => it.name === 'fishing_rod' && /Scaled grip/.test(loreText(it)), it => /Bog Resin/.test(nameOf(it))], false);
  await cmd('/give @s fishing_rod', 700);
  const r3 = await craftAt([it => it.name === 'fishing_rod' && !loreText(it), it => /Lurker Scale/.test(nameOf(it))], false);
  const rods = rodsHeld().map(it => `${nameOf(it) || 'rod'} [${loreText(it)}] {${enchOf(it)}} dmg ${damageOf(it)}`);
  check('Old build: Old Faithful has a Scaled grip and a Resin line', rods.some(r => /Old Faithful/.test(r) && /Scaled grip/.test(r) && /Resin line/.test(r)), rods.join(' || ') + ' | ' + r1 + ' | ' + r2);
  check('Old build: a second rod with a Scaled grip', rods.some(r => !/Old Faithful/.test(r) && /Scaled grip/.test(r)), r3);
  const at = await cmd(`/cerb fish journal ${NAME}`, 1300);
  check('Old build: species and an encounter in the journal', sawAll(/✔/, at).length >= 3 && !!saw(/river_lurker=\d/, at), chat.slice(at).join(' / ').slice(0, 300));
}

/** After updating: the old character's journal and rods, and converting a rod (docs/design/FISHING_EQUIPMENT.md §6). */
async function legacy() {
  await arena(420, 'river');
  await survival(40, false);
  const at = await cmd(`/cerb fish journal ${NAME}`, 1300);
  check('The journal from before the update reads as it was (species, encounters)', sawAll(/✔/, at).length >= 3 && !!saw(/^Encounters: .*river_lurker=\d/, at), chat.slice(at).join(' / ').slice(0, 300));
  if (!rodsHeld().some(it => /Scaled grip/.test(loreText(it)) && /Resin line/.test(loreText(it)))) {
    note('no rod from the old build here: upgrading one the old way with /cerb fish legacyrod');
    await cmd('/clear @s fishing_rod', 400);
    await cmd('/give @s fishing_rod[custom_name={text:"Old Faithful",italic:false},enchantments={unbreaking:3,lure:2},damage=17]', 900);
    await selectRod(it => /Old Faithful/.test(nameOf(it)));
    await cmd(`/cerb fish legacyrod ${NAME} scaled_grip,resin_line`, 900);
    await cmd('/give @s fishing_rod', 600);
    await selectRod(it => it.name === 'fishing_rod' && !nameOf(it));
    await cmd(`/cerb fish legacyrod ${NAME} scaled_grip`, 900);
  }
  const isOld = it => it.name === 'fishing_rod' && /Old Faithful/.test(nameOf(it));
  const old0 = rodsHeld().find(isOld);
  const before = { ench: enchOf(old0), dmg: damageOf(old0), lore: loreText(old0) };
  note(`Old Faithful: ${before.lore} {${before.ench}} dmg ${before.dmg}`);
  await cmd('/give @s fishing_rod', 600);
  await selectRod(it => it.name === 'fishing_rod' && !loreText(it) && !nameOf(it));
  const plainNow = await fishInfo();
  await selectRod(isOld);
  const withOld = await fishInfo();
  check('An old upgraded rod keeps working: its upgrades count while it is in hand', withOld.lure > plainNow.lure && withOld.creature > plainNow.creature,
    `plain rod: ${plainNow.lure}% sooner, ${plainNow.creature}% encounters; Old Faithful: ${withOld.lure}%, ${withOld.creature}%`);
  // Owning the Scaled grip part too: the rod's grip and the setup's count once.
  await give('lurker_scale', 1);
  check('Fit a Scaled grip in Equipment', (await fit('Rod', /^Scaled grip/)) === 'ok' && (await gear()).owned.includes('scaled_grip'), (await gear()).raw);
  await closeAny();
  await selectRod(it => it.name === 'fishing_rod' && !loreText(it) && !nameOf(it));
  const partOnly = await fishInfo();
  await selectRod(isOld);
  const both = await fishInfo();
  check('The setup\'s Scaled grip and the rod\'s count once (no double counting)', both.lure === partOnly.lure && partOnly.lure > plainNow.lure,
    `plain ${plainNow.lure}%, part ${partOnly.lure}%, part + old rod ${both.lure}%`);
  // The preview.
  await openEquipment();
  check('Holding it, Equipment offers to convert it', /Upgraded rod in your hand/.test(slotName(33)), slotName(33));
  await click(33);
  check('The conversion preview', await waitTitle(SCREEN('Convert a rod')), title());
  const preview = flat(22);
  check('...says what happens to each upgrade', /Scaled grip: you already own it: 1 Lurker Scale comes back/.test(preview) && /Resin line: becomes your part/.test(preview), preview);
  // A full inventory refuses, changing nothing.
  await closeAny();
  for (let i = 0; i < 40; i++) bot.chat('/give @s dirt 64');
  await sleep(2500);
  await selectRod(isOld);
  await openEquipment(); await click(33); await waitTitle(SCREEN('Convert a rod'));
  let at2 = chat.length;
  await click(31); await waitTitle(/^Confirm: Convert/); await click(11, 1300);
  check('A full inventory: refused, nothing converted', !!saw(/Make room for 1 item first: nothing was converted/, at2) && /Scaled grip/.test(loreText(rodsHeld().find(isOld))),
    sawAll(/convert|room/i, at2).join(' / '));
  await closeAny();
  await cmd('/clear @s dirt', 600);
  await cmd('/kill @e[type=item,distance=..12]', 600);
  // A stale preview: the rod changes while the preview is open.
  await selectRod(isOld);
  await openEquipment(); await click(33); await waitTitle(SCREEN('Convert a rod'));
  await cmd('/enchant @s minecraft:luck_of_the_sea 1', 800);
  at2 = chat.length;
  await click(31); await waitTitle(/^Confirm: Convert/); await click(11, 1300);
  check('The rod changed since the preview: refused, nothing converted', !!saw(/changed since the preview: nothing was converted/, at2) && /Scaled grip/.test(loreText(rodsHeld().find(isOld))),
    sawAll(/convert|changed/i, at2).join(' / '));
  await closeAny();
  // Converting (a double click on Yes converts once).
  const scales0 = carry('Lurker Scale');
  await selectRod(isOld);
  const beforeConvert = await fishInfo();
  const ench1 = [...before.ench.split(', ').filter(Boolean), 'luck_of_the_sea 1'].sort().join(', '); // the stale test added one
  await openEquipment(); await click(33); await waitTitle(SCREEN('Convert a rod'));
  at2 = chat.length;
  await click(31); await waitTitle(/^Confirm: Convert/);
  try { bot.clickWindow(11, 0, 0); bot.clickWindow(11, 0, 0); } catch (e) { /* the second may find another window */ }
  await sleep(1500);
  await closeAny();
  const old1 = rodsHeld().find(isOld);
  const g = await gear();
  check('Converted: one message', sawAll(/Converted: 2 upgrades moved to your setup, 1 item back/, at2).length === 1, sawAll(/Convert/, at2).join(' / '));
  check('...its upgrades left the rod', old1 && !/Scaled grip|Resin line/.test(loreText(old1)), old1 ? loreText(old1) : 'rod gone');
  check('...name, enchantments and damage stay', old1 && /Old Faithful/.test(nameOf(old1)) && enchOf(old1) === ench1 && damageOf(old1) === before.dmg,
    old1 ? `${nameOf(old1)} {${enchOf(old1)}} dmg ${damageOf(old1)} (was {${ench1}} dmg ${before.dmg})` : 'rod gone');
  check('...the Resin line is yours, in every preset', g.owned.includes('resin_line') && g.presets.every(p => p.parts.includes('resin_line')), g.raw);
  check('...the Lurker Scale for the grip you already had came back once', carry('Lurker Scale') === scales0 + 1, `${scales0} -> ${carry('Lurker Scale')}`);
  await selectRod(isOld);
  const afterConvert = await fishInfo();
  check('...and the numbers are the same as before converting (nothing lost, nothing doubled)', afterConvert.lure === beforeConvert.lure && afterConvert.creature === beforeConvert.creature,
    `before ${beforeConvert.lure}% / ${beforeConvert.creature}%, after ${afterConvert.lure}% / ${afterConvert.creature}%`);
  await openEquipment();
  check('A converted rod is a plain rod: nothing more to convert', /Old upgraded rods/.test(slotName(33)), slotName(33));
  await closeAny();
  // The second old rod: its grip is owned already, so its item comes back.
  const isSecond = it => it.name === 'fishing_rod' && !nameOf(it) && /Scaled grip/.test(loreText(it));
  if (await selectRod(isSecond)) {
    await openEquipment(); await click(33); await waitTitle(SCREEN('Convert a rod'));
    check('A second rod with the same upgrade: its item comes back', /Scaled grip: you already own it: 1 Lurker Scale comes back/.test(flat(22)), flat(22));
    await click(31); await waitTitle(/^Confirm: Convert/); await click(11, 1300); await closeAny();
    check('...converted, one more Lurker Scale, the setup unchanged', carry('Lurker Scale') === scales0 + 2 && !rodsHeld().some(isSecond), `${carry('Lurker Scale')}`);
  } else note('no second old rod in this character');
  // A converted rod fishes as before.
  await selectRod(isOld);
  check('The converted rod catches', await catchOne(), '');
}

async function equip() {
  await arena(180, 'river');
  await survival(1);
  check('Fishing page opens', await openSkill('Fishing'), title());
  const eq = await waitSlot(/^Equipment: /, 27, 35), jr = findSlot(/^Journal: /, 27, 35), en = findSlot(/^Encounters: /, 27, 35);
  check('Fishing sections in order: Equipment, Journal, Encounters', eq >= 0 && eq < jr && jr < en, `${eq} ${jr} ${en}`);
  check('A new character\'s Equipment tile', /^Equipment: Discoveries$/.test(slotName(eq)) && /Parts counting: 0 of 0 owned/.test(flat(eq)) && /Bait in the bag: 0/.test(flat(eq)), slotName(eq) + ' / ' + flat(eq));
  await click(eq);
  check('Equipment opens', await waitTitle(SCREEN('Fishing Equipment')), title());
  check('Three presets: Discoveries in use, Treasure, Sea Creatures', /Discoveries/.test(slotName(11)) && /In use/.test(flat(11)) && /Treasure/.test(slotName(13)) && /Sea Creatures/.test(slotName(15)),
    [11, 13, 15].map(slotName).join(' | '));
  check('Categories: Rod open; Line 10, Lure 25, Reel 50', /✓ Open/.test(flat(19)) && /Opens at Fishing 10/.test(flat(21)) && /Opens at Fishing 25/.test(flat(23)) && /Opens at Fishing 50/.test(flat(25)),
    [19, 21, 23, 25].map(i => slotName(i) + ': ' + flat(i).slice(0, 40)).join(' | '));
  check('Bait Bag: one slot now, the second at Fishing 20, the third at 40', /Slot 2: opens at Fishing 20/.test(flat(29)) && /Slot 3: opens at Fishing 40/.test(flat(29)), flat(29));
  check('The header says any rod uses it, with the stats from the engine', /Any normal fishing rod/.test(flat(4)) && /Lure speed: /.test(flat(4)), flat(4));
  await closeAny();
  // Fit a part.
  const i0 = await fishInfo();
  await give('lurker_scale', 1);
  await openCategory('Rod');
  const grip = findSlot(/^Scaled grip/, 19, 34);
  check('Rod: the Scaled grip, not owned, made with 1 Lurker Scale (you carry 1), from River Lurkers', grip >= 0 && /Not owned yet/.test(flat(grip)) && /you carry 1/.test(flat(grip)) && /Dropped by .*River Lurker/.test(flat(grip)),
    flat(grip));
  check('...its effect, raw before caps', /Lure speed \+6(\.0)?% \(raw, before caps\)/.test(flat(grip)), flat(grip));
  await closeAny();
  let at = chat.length;
  check('Fitting it asks first, then fits', (await fit('Rod', /^Scaled grip/)) === 'ok' && !!saw(/Fitted the Scaled grip \(1 Lurker Scale used\)/, at), sawAll(/Fitted|need/, at).join(' / '));
  let g = await gear();
  check('...owned once, in all three presets, the scale used', g.owned.join() === 'scaled_grip' && g.presets.length === 3 && g.presets.every(p => p.parts.includes('scaled_grip')) && carry('Lurker Scale') === 0, g.raw);
  const i1 = await fishInfo();
  check('Bites come sooner', i1.lure > i0.lure, `${i0.lure}% -> ${i1.lure}%`);
  await hold('fishing_rod');
  const i2 = await fishInfo();
  check('Any normal rod uses it (a plain rod: the same)', i2.lure === i1.lure, `${i1.lure}% vs ${i2.lure}%`);
  await hold(ROD);
  // Owned once: a second scale doesn't fit it again; the tile now takes it in and out, with the effect previewed.
  await give('lurker_scale', 1);
  await openCategory('Rod');
  const g2 = findSlot(/^Scaled grip/, 19, 34);
  check('An owned part: in the preset, and what taking it out changes (engine values, raw change)', /✔ In Discoveries/.test(flat(g2)) && /Taking it out:/.test(flat(g2)) && /Lure speed: [\d.]+% → [\d.]+% \(-6(\.0)?% raw\)/.test(flat(g2)),
    flat(g2));
  check('...and when it applies', /always, with any rod/.test(flat(g2)), flat(g2));
  await click(g2);
  const i3 = await fishInfo();
  check('Taking it out of the preset: back to the plain numbers, the scale kept', (await gear()).presets[0].parts.length === 0 && i3.lure === i0.lure && carry('Lurker Scale') === 1, `${i3.lure}%`);
  await openCategory('Rod'); await click(findSlot(/^Scaled grip/, 19, 34)); await closeAny();
  // Locked categories: a lure part waits for Fishing 25 (owned, in the preset, not counting).
  await give('sea_glass', 1);
  at = chat.length;
  check('A Lure part can be fitted before Fishing 25', (await fit('Lure', /^Sea-glass float/)) === 'ok' && !!saw(/Fitted the Sea-glass float/, at), sawAll(/Fitted|need/, at).join(' / '));
  g = await gear();
  check('...a treasure part goes into Treasure and Sea Creatures, not Discoveries', !g.presets[0].parts.includes('sea_glass_float') && g.presets[1].parts.includes('sea_glass_float') && g.presets[2].parts.includes('sea_glass_float'), g.raw);
  await openEquipment();
  check('...and waits: Lure says it opens at 25', /Opens at Fishing 25/.test(flat(23)), flat(23));
  await closeAny();
  // Presets: switching is free, previewed, and counts from the next cast.
  await setLevel('Fishing', 30);
  const t0 = await fishInfo();
  await openEquipment();
  check('Treasure\'s tile previews switching to it', /Switching to it:/.test(flat(13)) && /Treasure chance: [\d.]+% → [\d.]+%/.test(flat(13)), flat(13));
  at = chat.length;
  await click(13);
  check('Switching: free, from the next cast', !!saw(/Using Treasure from your next cast/, at) && /In use/.test(flat(13)), sawAll(/Using/, at).join(' / '));
  await closeAny();
  const t1 = await fishInfo();
  check('Treasure counts its Sea-glass float', t1.treasure > t0.treasure, `${t0.treasure}% -> ${t1.treasure}%`);
  // Rename by chat; bad names refused; "cancel" cancels.
  await openEquipment();
  await click(15, 900, 0, 1);
  at = chat.length;
  bot.chat('Deep Calls'); await sleep(1200);
  check('Shift-click a preset, type a name: renamed', !!saw(/Preset 3 is now Deep Calls/, at) && (await gear()).presets[2].name === 'Deep Calls', sawAll(/Preset|name/, at).join(' / '));
  await openEquipment(); await click(15, 900, 0, 1);
  at = chat.length;
  bot.chat('x'.repeat(24)); await sleep(1200);
  check('A name over 20 characters is refused', !!saw(/A name is 1-20 letters/, at) && (await gear()).presets[2].name === 'Deep Calls', sawAll(/name/, at).join(' / '));
  await openEquipment(); await click(15, 900, 0, 1);
  at = chat.length;
  bot.chat('cancel'); await sleep(1000);
  check('"cancel" cancels', !!saw(/Rename cancelled/, at) && (await gear()).presets[2].name === 'Deep Calls', sawAll(/ancel/, at).join(' / '));
  // A part a server removes from resources.yml: owned, shown unavailable, counting nothing; back when it returns.
  const dir = process.env.CERB_SERVER_DIR;
  if (dir) {
    const fs = require('fs'), file = require('path').join(dir, 'plugins/CerberusPlugin/resources.yml');
    const orig = fs.readFileSync(file, 'utf8');
    fs.writeFileSync(file, orig.replace(/^\s+sea_glass_float:\s+\{ category: lure, upgrade: sea_glass_float \}\n/m, ''));
    await cmd('/cerb reload', 2500);
    const t2 = await fishInfo();
    g = await gear();
    await openEquipment();
    check('A part the server no longer has: still owned, shown unavailable', g.owned.includes('sea_glass_float') && /Unavailable parts: 1/.test(slotName(40)) && /sea_glass_float \(unavailable\)/.test(flat(13)),
      slotName(40) + ' / ' + flat(13));
    check('...and it counts nothing', t2.treasure < t1.treasure, `${t1.treasure}% -> ${t2.treasure}%`);
    await closeAny();
    fs.writeFileSync(file, orig);
    await cmd('/cerb reload', 2500);
    const t3 = await fishInfo();
    check('...back in the file: it counts again', t3.treasure === t1.treasure && (await gear()).presets[1].parts.includes('sea_glass_float'), `${t3.treasure}%`);
  } else note('CERB_SERVER_DIR not set: skipping the removed-part check (it edits the sandbox resources.yml)');
  // One specialist per preset (Surveyor's Float and the Specimen Hook come from collection milestones; the admin gives them here).
  await cmd(`/cerb fish part ${NAME} surveyors_float`, 600);
  await cmd(`/cerb fish part ${NAME} specimen_hook`, 600);
  await openEquipment(); await click(11); await closeAny(); // Discoveries
  g = await gear();
  check('Surveyor\'s Float goes into Discoveries; the Specimen Hook into none', g.presets[0].parts.includes('surveyors_float') && !g.presets.some(p => p.parts.includes('specimen_hook')), g.raw);
  await openCategory('Lure');
  const hook = findSlot(/^Specimen Hook/, 19, 34);
  check('Putting the Specimen Hook in says what it removes', /Removes: Surveyor's Float \(one specialist at a time\)/.test(flat(hook)), flat(hook));
  await click(hook); await closeAny();
  g = await gear();
  check('...and it replaces the Float in Discoveries', g.presets[0].parts.includes('specimen_hook') && !g.presets[0].parts.includes('surveyors_float') && g.owned.includes('surveyors_float'), g.raw);
  // Menus never hand out or take items: shift-clicks and number keys on equipment screens.
  await give('lurker_scale', 1);
  const scales = carry('Lurker Scale');
  await openEquipment();
  const inv0 = bot.inventory.items().map(it => it.name + it.count).sort().join();
  const scaleSlot = (() => { const w = bot.currentWindow; for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && /Lurker Scale/.test(nameOf(w.slots[i]))) return i; return -1; })();
  if (scaleSlot >= 0) await click(scaleSlot, 700, 0, 1);
  await click(19, 700, 0, 2); // number key 1 on a category tile
  await closeAny();
  check('Shift-clicks and number keys move no item in or out', bot.inventory.items().map(it => it.name + it.count).sort().join() === inv0 && carry('Lurker Scale') === scales,
    `${inv0} -> ${bot.inventory.items().map(it => it.name + it.count).sort().join()}`);
  await openEquipment(); await click(11); await closeAny();
}

async function bait() {
  await arena(240, 'river');
  await survival(1);
  // Crafting: three recipes open to everyone, one waits for its milestone.
  await cmd('/give @s rotten_flesh 2', 400); await cmd('/give @s wheat_seeds 2', 400);
  const made = await craftAt([it => it.name === 'rotten_flesh', it => it.name === 'wheat_seeds']);
  check('Rotten flesh + wheat seeds: 4 Grub Bait', /x4 Grub Bait/.test(made) && carry('Grub Bait') === 4, made + ' -> ' + carry('Grub Bait'));
  await cmd('/give @s dried_kelp 1', 400); await cmd('/give @s sweet_berries 1', 400);
  const locked = await craftAt([it => it.name === 'dried_kelp', it => it.name === 'sweet_berries']);
  check('Wayfarer\'s Bait waits for its milestone (no result)', locked === '' && carry("Wayfarer's Bait") === 0, locked || 'no result');
  // The bag.
  await openBag();
  check('The Bait Bag: slot 1 empty, slots 2-3 locked until 20 and 40', /Slot 1: empty/.test(slotName(20)) && /Opens at Fishing 20/.test(flat(22)) && /Opens at Fishing 40/.test(flat(24)),
    [20, 22, 24].map(slotName).join(' | '));
  check('Its recipes are shown, Wayfarer\'s locked', /Recipe: Grub Bait/.test([38, 39, 40, 41, 42].map(slotName).join(' ')) && /Locked/.test([38, 39, 40, 41, 42].map(flat).join(' ')),
    [38, 39, 40, 41, 42].map(slotName).join(' | '));
  const put = findSlot(/^Put in: Grub Bait$/, 29, 33);
  let at = chat.length;
  await click(put);
  check('Put in: 4 Grub Bait go into slot 1', !!saw(/Put 4 Grub Bait into slot 1/, at) && carry('Grub Bait') === 0 && /Slot 1: Grub Bait ×4/.test(slotName(20)), slotName(20));
  await closeAny();
  await cmd(`/cerb fish bait ${NAME} grub_bait 8`, 600);
  let g = await gear();
  check('Bag: 12 Grub Bait', baitIn(g) === 12, g.raw);
  // A cast holds one; a reel-in without a catch puts it back.
  await stand();
  await castOnly();
  g = await gear();
  check('A cast holds one bait on the line', g.held === 'grub_bait' && baitIn(g) === 11, g.raw);
  await reelIn(); await sleep(600);
  g = await gear();
  check('Reeling in without a catch puts it back', !g.held && baitIn(g) === 12, g.raw);
  // A catch uses exactly one.
  await cmd(`/cerb fish force ${NAME} species silver_dace`, 300);
  check('A catch...', await catchOne(), '');
  g = await gear();
  check('...uses exactly one bait', !g.held && baitIn(g) === 11, g.raw);
  // A refused catch (another plugin cancels it) uses none.
  await cmd(`/testnocatch ${NAME}`, 400);
  const s0 = await summaryDump();
  await stand();
  try { await Promise.race([bot.fish(), new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 40000))]); } catch (e) { note('refused catch: ' + e.message); }
  await sleep(3000); // the bite passes
  g = await gear();
  const s1 = await summaryDump();
  check('A refused catch: no catch counted, the bait still on the line', s1 && s0 && s1.catches === s0.catches && g.held === 'grub_bait' && baitIn(g) === 10, g.raw + ' ' + JSON.stringify(s1));
  await cmd('/testnocatch off', 400);
  const s2 = await summaryDump();
  await reelIn(); await sleep(600);
  g = await gear();
  const s3 = await summaryDump();
  const bitFirst = !!(s2 && s3 && s3.catches > s2.catches); // a new bite before the reel-in: that catch used it, rightly
  check('...and when the cast ends, it goes back', !g.held && baitIn(g) === (bitFirst ? 10 : 11), g.raw + (bitFirst ? ' (a fish bit first: its catch used it)' : ''));
  if (bitFirst) await cmd(`/cerb fish bait ${NAME} grub_bait 1`, 600);
  // A reel-in another plugin refuses (CerbTestProtect's /testnoreel) keeps the bobber out: the cast and its bait go on.
  const sA = await summaryDump();
  await stand(); await castOnly();
  await cmd(`/testnoreel ${NAME}`, 400);
  await reelIn(); await sleep(700);
  g = await gear();
  const stillHeld = g.held === 'grub_bait';
  await cmd('/testnoreel off', 400);
  await reelIn(); await sleep(800);
  g = await gear();
  const sB = await summaryDump();
  const bitMeanwhile = !!(sA && sB && sB.catches > sA.catches); // a bite before the last reel-in: a catch used it
  check('A refused reel-in: the cast goes on with its bait; the next reel-in ends it', stillHeld && !g.held && baitIn(g) === (bitMeanwhile ? 10 : 11),
    `held after the refused reel-in: ${stillHeld}; ${g.raw}${bitMeanwhile ? ' (a catch meanwhile)' : ''}`);
  if (bitMeanwhile) await cmd(`/cerb fish bait ${NAME} grub_bait 1`, 600);
  // Logging out with bait on the line: it goes back.
  await castOnly();
  g = await gear();
  check('(a cast holding bait)', g.held === 'grub_bait', g.raw);
  bot.quit(); await sleep(3500); await connect(); await sleep(3000);
  g = await gear();
  check('Logging out mid-cast: the bait is back in the bag', !g.held && baitIn(g) === 11, g.raw);
  await survival(1, false); await hold(ROD); await stand();
  // Switching presets mid-cast: the cast keeps what it was cast with.
  await openEquipment(); await click(13); await closeAny();           // Treasure
  await openBag(); await click(20, 800, 1); await closeAny();          // Treasure skips slot 1
  await openEquipment(); await click(11); await closeAny();           // back to Discoveries
  await hold('fishing_rod');                                          // a plain rod: no bite before 5 s
  await stand();
  await cmd(`/cerb fish force ${NAME} species river_perch`, 300);
  const fishing = Promise.race([bot.fish(), new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 45000))]).then(() => true, e => { note(e.message); return false; });
  await sleep(1500);
  g = await gear();
  const heldAtCast = g.held;
  await openEquipment(); await click(13); await closeAny();           // switch to Treasure with the line out
  await fishing;
  await sleep(1200);
  g = await gear();
  check('Switching presets mid-cast: the cast keeps its bait (used by its catch)', heldAtCast === 'grub_bait' && !g.held && baitIn(g) === 10, `held at cast ${heldAtCast}; ${g.raw}`);
  await castOnly();
  g = await gear();
  check('...and the next cast uses Treasure (which skips slot 1): no bait', !g.held && baitIn(g) === 10, g.raw);
  await reelIn();
  await openEquipment(); await click(11); await closeAny();
  // Situational bait waits in the bag: night bait by day, encounter bait before encounters open.
  await openBag(); await click(20); await closeAny();                  // take the grub out
  await cmd(`/cerb fish bait ${NAME} glowworm_bait 3`, 600);
  await stand(); await castOnly();
  g = await gear();
  check('Night bait by day: not taken (it waits in the bag)', !g.held && g.bag[0] && g.bag[0].bait === 'glowworm_bait' && baitIn(g) === 3, g.raw);
  await reelIn();
  await openBag();
  check('...and the bag says so', /Waits for the night/.test(flat(20)), flat(20));
  await click(20); await closeAny();
  await cmd(`/cerb fish bait ${NAME} chum 3`, 600);
  await stand(); await castOnly();
  g = await gear();
  check('Encounter bait before encounters open: not taken', !g.held && baitIn(g) === 3, g.raw);
  await reelIn();
  await openBag(); await click(20); await closeAny();
  // Depleted: said once; restocking (opt-in) refills from the inventory.
  await cmd(`/cerb fish bait ${NAME} grub_bait 1`, 600);
  at = chat.length;
  await cmd(`/cerb fish force ${NAME} species silver_dace`, 300); await catchOne();
  await cmd(`/cerb fish force ${NAME} species silver_dace`, 300); await catchOne();
  check('"Bait depleted. Normal fishing continues." once', sawAll(/Bait depleted\. Normal fishing continues\./, at).length === 1, sawAll(/Bait/, at).join(' / '));
  g = await gear();
  check('...and fishing goes on without bait', !g.held && baitIn(g) === 0, g.raw);
  await cmd('/clear @s slime_ball', 500);                               // Grub Bait is a slime ball
  await openEquipment(); await click(41); await closeAny();             // restock on
  await give('grub_bait', 5);
  await cmd(`/cerb fish bait ${NAME} grub_bait 1`, 600);
  await cmd(`/cerb fish force ${NAME} species silver_dace`, 300); await catchOne();
  g = await gear();
  check('Restocking: the empty slot refills with the same bait from the inventory', g.restock && baitIn(g) === 5 && carry('Grub Bait') === 0, `${g.raw}; carried ${carry('Grub Bait')}`);
  await openEquipment(); await click(41); await closeAny();             // restock off
  // A held bait with no room to go back: handed over as an item (never hidden in a locked slot). A fish that bites
  // before the reel-in makes it a catch, which rightly uses the bait: then the scenario runs again.
  let heldNow = null, grub0 = 0, grubAfter = 0;
  for (let t = 0; t < 3; t++) {
    const before = await summaryDump();
    await stand(); await castOnly();
    g = await gear();
    heldNow = g.held;
    if (baitIn(g, 1) > 0) { await openBag(); await click(20); await closeAny(); } // take out what's left in slot 1
    await cmd(`/cerb fish bait ${NAME} chum 2`, 600);                   // slot 1 now holds chum
    grub0 = carry('Grub Bait');
    await reelIn(); await sleep(800);
    g = await gear();
    grubAfter = carry('Grub Bait');
    const after = await summaryDump();
    if (!(before && after && after.catches > before.catches)) break;
    note('a fish bit before the reel-in (its catch used the bait): again');
    await openBag(); await click(20); await closeAny();                // the chum out
    await cmd(`/cerb fish bait ${NAME} grub_bait 1`, 600);
  }
  check('No room in the open slots: the held bait comes back as an item', heldNow === 'grub_bait' && grubAfter === grub0 + 1 && !g.bag[1] && !g.bag[2],
    `held ${heldNow}; ${g.raw}; grub ${grub0} -> ${grubAfter}`);
  // Bag screen: number keys and shift-clicks never move items into or out of the bag by themselves.
  await openBag();
  const bag0 = (await gear()).raw;
  await click(20, 700, 0, 2);
  const grubSlot = (() => { const w = bot.currentWindow; for (let i = w.inventoryStart; i < w.inventoryEnd; i++) if (w.slots[i] && /Grub Bait/.test(nameOf(w.slots[i]))) return i; return -1; })();
  if (grubSlot >= 0) await click(grubSlot, 700, 0, 1);
  await closeAny();
  check('Number keys and shift-clicks on the bag screen move nothing', (await gear()).raw.replace(/held: [^·]*/, '') === bag0.replace(/held: [^·]*/, '') && carry('Grub Bait') === grub0 + 1,
    (await gear()).raw);
  await openBag(); await click(20); await closeAny();
  // A cast that took a slot's last bait: an empty end puts it back in that slot, never in one the preset skips.
  await setLevel('Fishing', 20);
  await cmd(`/cerb fish bait ${NAME} grub_bait 1`, 600);                 // slot 1
  await cmd(`/cerb fish bait ${NAME} wayfarers_bait 1`, 600);            // slot 2
  await openBag(); await click(20); await closeAny();                  // slot 1 emptied
  await openEquipment(); await click(13); await closeAny();           // Treasure skips slot 1
  let tookLast = false;
  for (let t = 0; t < 3; t++) {
    const before = await summaryDump();
    await stand(); await castOnly();
    g = await gear();
    tookLast = g.held === 'wayfarers_bait' && !g.bag[1];
    await reelIn(); await sleep(800);
    g = await gear();
    const after = await summaryDump();
    if (!(before && after && after.catches > before.catches)) break;
    note('a fish bit before the reel-in (its catch used the bait): again');
    await cmd(`/cerb fish bait ${NAME} grub_bait 1`, 600); await cmd(`/cerb fish bait ${NAME} wayfarers_bait 1`, 600);
    await openBag(); await click(20); await closeAny();
  }
  check('The last bait of slot 2, held and not used: back in slot 2, slot 1 left empty', tookLast && g.bag[1] && g.bag[1].bait === 'wayfarers_bait' && g.bag[1].n === 1 && !g.bag[0], g.raw);
  await openEquipment(); await click(11); await closeAny();
  await openBag(); await click(22); await closeAny();
  await setLevel('Fishing', 1);
}

async function reel() {
  await arena(300, 'river');
  await survival(50);
  const owns = (await gear()).owned.includes('auto_reel');
  const reelOn = await cmd('/fishing reel on', 800);
  if (!owns) check('"/fishing reel on" before the reel is fitted says it does nothing yet', !!saw(/does nothing until the Auto-hook Reel is fitted/, reelOn), sawAll(/reel/i, reelOn).join(' / '));
  await cmd('/give @s iron_ingot 3', 400); await cmd('/give @s tripwire_hook 1', 400); await cmd('/give @s redstone 1', 400);
  const made = await craftAt([it => it.name === 'iron_ingot', it => it.name === 'iron_ingot', it => it.name === 'iron_ingot', it => it.name === 'tripwire_hook', it => it.name === 'redstone']);
  check('3 iron ingots, a tripwire hook and redstone: an Auto-hook Reel', /Auto-hook Reel/.test(made) && carry('Auto-hook Reel') === 1, made);
  let at = chat.length;
  check('Fit it (Reel, Fishing 50)', (await fit('Reel', /^Auto-hook Reel/)) === 'ok' && !!saw(/Fitted the Auto-hook Reel/, at), sawAll(/Fitted|need/, at).join(' / '));
  let g = await gear();
  check('...in all three presets, switched on', g.presets.every(p => p.parts.includes('auto_reel')) && g.reel, g.raw);
  await openEquipment();
  check('Equipment shows the switch and that it is in use', /Auto-hook reel: on/.test(slotName(39)) && /In use in Discoveries/.test(flat(39)), slotName(39) + ' / ' + flat(39));
  await closeAny();
  // A plain rod: its wear shows the reel used the normal path.
  await cmd('/clear @s', 400);
  await hold('fishing_rod');
  const statsNum = async re => Number(((chat.slice(await cmd('/cerb fish stats', 900)).join(' ').match(re)) || [])[1]);
  const autoN = () => statsNum(/auto-reeled (\d+)/);
  const a0 = await autoN();
  const s0 = await summaryDump();
  const xp0 = totalXp(await skill('Fishing'));
  const dmg0 = damageOf(bot.heldItem);
  await stand(); await castOnly();
  let a1 = a0;
  for (let i = 0; i < 45 && a1 === a0; i++) { await sleep(1000); a1 = await autoN(); }
  await sleep(1000);
  const s1 = await summaryDump();
  const xp1 = totalXp(await skill('Fishing'));
  check('Left alone, a bite is reeled in for you', a1 === a0 + 1 && s1.catches === s0.catches + 1, `auto-reeled ${a0} -> ${a1}; catches ${s0.catches} -> ${s1.catches}`);
  check('...through the normal path: Fishing XP, the rod\'s normal wear', xp1 > xp0 && damageOf(bot.heldItem) === dmg0 + 1, `xp +${xp1 - xp0}; damage ${dmg0} -> ${damageOf(bot.heldItem)}`);
  check('...and it never recasts', /casts tracked 0/.test(chat.slice(await cmd('/cerb fish stats', 900)).join(' ')), '');
  // Manual and automatic at once: one catch per bite.
  const s2 = await summaryDump();
  let caught = 0;
  for (let i = 0; i < 4; i++) { await stand(); if (await catchOne()) caught++; }
  await sleep(1500);
  const s3 = await summaryDump();
  check('Reeling yourself with the reel on: exactly one catch per bite', s3.catches - s2.catches === caught && caught >= 3, `${caught} reels, ${s3.catches - s2.catches} catches`);
  // Switched off: nothing is reeled.
  await openEquipment(); await click(39); await closeAny();
  const a2 = await autoN();
  await stand(); await castOnly(); await sleep(30000);
  check('Switched off: the bite is left to you', (await autoN()) === a2, '');
  await reelIn();
  await openEquipment(); await click(39); await closeAny();
  // A catch another plugin refuses: the reel's attempt catches nothing, the cast goes on, and the next bite is reeled.
  await cmd(`/testnocatch ${NAME}`, 400);
  const r0 = await autoN(), c0 = (await summaryDump()).catches;
  await stand(); await castOnly();
  let r1 = r0;
  for (let i = 0; i < 45 && r1 === r0; i++) { await sleep(1000); r1 = await autoN(); }
  await sleep(1500);
  check('A refused catch: the reel tried, nothing was caught', r1 === r0 + 1 && (await summaryDump()).catches === c0, `reels ${r0} -> ${r1}`);
  await cmd('/testnocatch off', 400);
  let r2 = r1;
  for (let i = 0; i < 60 && r2 === r1; i++) { await sleep(1000); r2 = await autoN(); }
  await sleep(1500);
  check('...the cast went on: its next bite is reeled and caught', r2 === r1 + 1 && (await summaryDump()).catches === c0 + 1, `reels ${r1} -> ${r2}`);
  // A full inventory: the catch falls at the player's feet as usual, and still counts and pays.
  for (let i = 0; i < 36; i++) bot.chat('/give @s dirt 64');
  await sleep(2500);
  const f0 = await summaryDump(), x0 = totalXp(await skill('Fishing')), r3 = await autoN();
  await stand(); await castOnly();
  let r4 = r3;
  for (let i = 0; i < 45 && r4 === r3; i++) { await sleep(1000); r4 = await autoN(); }
  await sleep(1500);
  const f1 = await summaryDump(), x1 = totalXp(await skill('Fishing'));
  check('A full inventory: reeled, counted and paid', r4 === r3 + 1 && f1.catches === f0.catches + 1 && x1 > x0, `catches ${f0.catches} -> ${f1.catches}, xp +${x1 - x0}`);
  await cmd('/clear @s dirt', 500); await cmd('/kill @e[type=item,distance=..16]', 500);
  // A menu open meanwhile: the reel still works and the menu stays.
  const a3 = await autoN();
  await stand(); await castOnly();
  await openMenu();
  let a4 = a3;
  for (let i = 0; i < 45 && a4 === a3; i++) { await sleep(1000); a4 = await autoN(); }
  check('With a menu open: reeled, the menu still open', a4 === a3 + 1 && SCREEN('Player Menu').test(title()), `${a3} -> ${a4}; ${title()}`);
  await closeAny();
  // Switching to another item: the bobber goes, nothing is reeled later.
  await cmd('/give @s stick 1', 400);
  await stand(); await castOnly();
  const sticky = bot.inventory.items().find(it => it.name === 'stick');
  const a5 = await autoN();
  if (sticky) { bot.setQuickBarSlot(sticky.slot - 36); }
  await sleep(30000);
  check('Switching items mid-cast: no catch later', (await autoN()) === a5, '');
  await hold('fishing_rod');
  // A rod about to break: the reel's catch wears it like any catch, and it breaks cleanly.
  await hold('fishing_rod[damage=63]');
  const a6 = await autoN();
  await stand(); await castOnly();
  let a7 = a6;
  for (let i = 0; i < 45 && a7 === a6; i++) { await sleep(1000); a7 = await autoN(); }
  await sleep(1000);
  check('A rod with one use left: the reel\'s catch breaks it, nothing else happens', a7 === a6 + 1 && !bot.inventory.items().some(it => it.name === 'fishing_rod'), bot.inventory.items().map(i => i.name).join());
  // Death and logout mid-cast: nothing reeled, no errors.
  await hold('fishing_rod');
  await stand(); await castOnly();
  const a8 = await autoN();
  await cmd('/effect clear @s', 300);
  await cmd('/kill @s', 1500);
  try { bot.respawn && bot.respawn(); } catch (e) { /* auto */ }
  await sleep(12000);
  check('Dying mid-cast: nothing is reeled', (await autoN()) === a8, '');
  await survival(50); await hold('fishing_rod'); await stand();
  await castOnly();
  const a9 = await autoN();
  bot.quit(); await sleep(15000); await connect(); await sleep(3000);
  check('Logging out mid-cast: nothing is reeled', (await autoN()) === a9, '');
  // Encounters are never spent by the reel: a Sneak-cast call is left alone.
  await survival(50); await hold('fishing_rod'); await stand();
  await cmd(`/cerb fish lure ${NAME} river_lurker 1`, 600);
  const lurkers = async () => Number((chat.slice(await cmd(`/cerb fish journal ${NAME}`, 1300)).join(' ').match(/river_lurker=(\d+)/) || [])[1] || 0);
  const e0 = await lurkers();
  const a10 = await autoN();
  bot.setControlState('sneak', true); await sleep(300);
  await castOnly(); await sleep(6000);
  bot.setControlState('sneak', false);
  const st = chat.slice(await cmd('/cerb fish stats', 900)).join(' ');
  check('A Sneak-cast call is not reeled automatically', (await autoN()) === a10 && /creatures out now 1/.test(st), st.slice(0, 200));
  bot.quit(); await sleep(4000); await connect(); await sleep(3000); // the fight ends with a logout (the encounter comes back)
  const e1 = await lurkers();
  check('...its encounter came back when the fight ended with the logout', e1 === e0, `river_lurker ${e0} before the call, ${e1} after`);
}

// ── increment C: milestones, specialists, trophies ───────────────
/** The admin journal dump's milestone line: the granted ids. */
async function milestones() {
  const at = await cmd(`/cerb fish journal ${NAME}`, 1300);
  const l = saw(/^Milestones granted: /, at) || '';
  const m = l.match(/Milestones granted: \[([^\]]*)\]/);
  return { granted: m ? m[1].split(',').map(x => x.trim()).filter(Boolean) : [], trophies: Number((l.match(/trophies kept: (\d+)/) || [])[1] || 0), line: l };
}
/** A forced species, cast from the pond's edge looking at the water (a crafting table or a menu may have turned the bot). */
async function forceCatch(id) { await stand(); await cmd(`/cerb fish force ${NAME} species ${id}`, 300); return catchOne(); }
/** Edit the sandbox's resources.yml (CERB_SERVER_DIR), reload, and give back a function that puts it back. */
async function editConfig(edit) {
  const dir = process.env.CERB_SERVER_DIR;
  if (!dir) return null;
  const fs = require('fs'), file = require('path').join(dir, 'plugins/CerberusPlugin/resources.yml');
  const orig = fs.readFileSync(file, 'utf8');
  const next = edit(orig);
  if (next === orig) { note('config edit matched nothing'); return null; }
  fs.writeFileSync(file, next);
  await cmd('/cerb reload', 2500);
  return async () => { fs.writeFileSync(file, orig); await cmd('/cerb reload', 2500); };
}

async function rewards() {
  await arena(480, 'river');
  await survival(30);
  // Where they are shown before any is reached.
  await openMenu(); await click(32); await waitTitle(SCREEN('Collections'));
  check('Collections: the Fishing milestones tile, nothing reached', /^Fishing milestones: 0 of 4$/.test(slotName(40)) && /Wayfarer: 0 of 3 waters/.test(flat(40))
    && /Wayfarer's Bait recipe/.test(flat(40)) && /Surveyor: \d+ of 8 species/.test(flat(40)) && /Record Keeper: \d+ of 10 personal bests/.test(flat(40)), slotName(40) + ' / ' + flat(40));
  await closeAny();
  await openSkill('Fishing');
  const jt = await waitSlot(/^Journal: /, 27, 35);
  check('The Fishing page names the next milestone', /Next milestone: Wayfarer · 0 of 3 waters/.test(flat(jt)), flat(jt));
  await closeAny();
  // Species in three waters: the Wayfarer's Bait recipe, once.
  let at = chat.length;
  for (const id of ['silver_dace', 'pond_bream']) await forceCatch(id);
  check('Two waters: nothing yet', !saw(/Fishing milestone/, at), sawAll(/milestone/i, at).join(' / '));
  await forceCatch('sea_bass');
  await sleep(800);
  check('A third water: "Wayfarer" with what it opens', sawAll(/Fishing milestone: Wayfarer \(species found in 3 waters\)\. Unlocked: the Wayfarer's Bait recipe/, at).length === 1,
    sawAll(/milestone/i, at).join(' / '));
  let ms = await milestones();
  check('...granted and remembered', ms.granted.includes('waters_3'), ms.line);
  await cmd('/give @s dried_kelp 1', 400); await cmd('/give @s sweet_berries 1', 400);
  const made = await craftAt([it => it.name === 'dried_kelp', it => it.name === 'sweet_berries']);
  check('...and Wayfarer\'s Bait now crafts', /Wayfarer's Bait/.test(made) && carry("Wayfarer's Bait") === 4, made || 'no result');
  at = chat.length;
  await forceCatch('mud_loach');
  check('A fourth water grants nothing again', !saw(/Fishing milestone: Wayfarer/, at), sawAll(/milestone/i, at).join(' / '));
  // Eight species: Surveyor's Float, into Discoveries.
  at = chat.length;
  for (const id of ['river_perch', 'reef_wrasse', 'ice_cod', 'blind_cavefish']) await forceCatch(id); // species 5-8
  await sleep(800);
  check('Eight species: "Surveyor" and Surveyor\'s Float', sawAll(/Fishing milestone: Surveyor \(8 species\)\. Unlocked: Surveyor's Float \(Equipment → Lure\)/, at).length === 1,
    sawAll(/milestone/i, at).join(' / '));
  let g = await gear();
  check('...owned once and put in Discoveries', g.owned.includes('surveyors_float') && g.presets[0].parts.includes('surveyors_float'), g.raw);
  // Rejoining grants nothing again.
  bot.quit(); await sleep(3500);
  const rejoin = chat.length;
  await connect(); await sleep(5000);
  check('Rejoining shows no milestone again', !chat.slice(rejoin).some(m => /Fishing milestone/.test(m)), chat.slice(rejoin).filter(m => /milestone/i.test(m)).join(' / '));
  await survival(30, false); await hold(ROD); await stand();
  ms = await milestones();
  check('...and the journal keeps them', ms.granted.includes('waters_3') && ms.granted.includes('species_8'), ms.line);
  // Personal bests: the sandbox counts 2 for this check (CERB_SERVER_DIR; put back after).
  const restoreBests = await editConfig(t => t.replace(/(bests_10:\s+\{ name: "Record Keeper", bests: )10/, '$12'));
  if (restoreBests) {
    at = chat.length;
    let tries = 0;
    while (!saw(/Fishing milestone: Record Keeper/, at) && tries++ < 24) await forceCatch(tries % 2 ? 'silver_dace' : 'pond_bream');
    await sleep(800);
    check('Personal bests: "Record Keeper" and the Specimen Hook', sawAll(/Fishing milestone: Record Keeper \(2 personal bests\)\. Unlocked: Specimen Hook \(Equipment → Lure\)/, at).length === 1,
      `${tries} catches; ` + sawAll(/milestone/i, at).join(' / '));
    g = await gear();
    check('...owned, in no preset (you choose)', g.owned.includes('specimen_hook') && !g.presets.some(p => p.parts.includes('specimen_hook')), g.raw);
    await restoreBests();
  } else note('CERB_SERVER_DIR not set: skipping the personal-bests milestone (it lowers the sandbox threshold for the check)');
  // A milestone met before: granted at the next join, once. The sandbox asks 5 species for Master Angler meanwhile.
  const restoreMaster = await editConfig(t => t.replace(/(master_angler:\s+\{ name: "Master Angler", species: )all/, '$15'));
  if (restoreMaster) {
    ms = await milestones();
    check('Already met after a reload: nothing granted until the next join', !ms.granted.includes('master_angler'), ms.line);
    bot.quit(); await sleep(3500);
    const before = chat.length;
    await connect(); await sleep(5000);
    const shown = chat.slice(before).filter(m => /Fishing milestone: Master Angler/.test(m));
    check('At the next join: "Master Angler", one message', shown.length === 1, chat.slice(before).filter(m => /milestone/i.test(m)).join(' / '));
    await restoreMaster();
    ms = await milestones();
    check('...kept when the file asks every species again (granted once)', ms.granted.includes('master_angler'), ms.line);
    await survival(30, false);
    await openSkill('Fishing');
    const j2 = await waitSlot(/^Journal: /, 27, 35);
    check('The emblem on the Fishing page', /✦ Master Angler/.test(flat(j2)), flat(j2));
    await click(j2); await waitTitle(SCREEN('Fishing Journal'));
    check('...on the journal', /✦ Master Angler/.test(flat(4)), flat(4));
    await closeAny();
    await openMenu(); await click(32); await waitTitle(SCREEN('Collections'));
    check('...and on Collections', /✦ Master Angler/.test(flat(20)) && /^Fishing milestones: \d of 4$/.test(slotName(40)), flat(20));
    await closeAny();
  } else note('CERB_SERVER_DIR not set: skipping the join grant and the emblem (they lower the sandbox threshold for the check)');
}

async function specialists() {
  await arena(540, 'river');
  await survival(100);
  await cmd(`/cerb fish part ${NAME} surveyors_float`, 500);
  await cmd(`/cerb fish part ${NAME} specimen_hook`, 500);
  await openEquipment(); await click(11); await closeAny(); // Discoveries, with the Float
  await forceCatch('silver_dace');
  // /cerb fish info reads the same shares the bite rolls with.
  const parse = text => {
    const out = {};
    for (const m of text.matchAll(/\| {2}([a-z ]+) ([\d.]+)%( ×[\d.]+)?/g)) out[m[1].trim()] = { pct: Number(m[2]), fav: !!m[3] };
    return out;
  };
  const withF = await fishInfo();
  await openEquipment(); await click(13); await closeAny(); // Treasure: no specialist
  const without = await fishInfo();
  await openEquipment(); await click(11); await closeAny();
  const a = parse(withF.text), b = parse(without.text);
  const names = Object.keys(b);
  const sum = o => Object.values(o).reduce((s, x) => s + x.pct, 0);
  check('The Float is named in /cerb fish info', /with your Surveyor's Float: species you haven't caught ×1\.50/.test(withF.text), withF.text.slice(0, 200));
  check('The same species can bite, the same share of bites are species', names.length > 2 && JSON.stringify(Object.keys(a).sort()) === JSON.stringify(names.sort()) && Math.abs(sum(a) - sum(b)) < 0.3,
    `${names.length} species; ${sum(a).toFixed(1)}% vs ${sum(b).toFixed(1)}%`);
  check('Silver Dace (caught) comes less often, the others more, at most x1.5', a['silver dace'] && a['silver dace'].pct < b['silver dace'].pct
    && names.filter(n => n !== 'silver dace').every(n => a[n].fav && a[n].pct > b[n].pct && a[n].pct <= b[n].pct * 1.5 + 0.1),
    names.map(n => `${n} ${b[n].pct}→${a[n] && a[n].pct}`).join(', '));
  // Where nothing new can bite: the tile says so, and the first bite there says it once. (Lure opens at 25: by day a
  // river then has Silver Dace, River Perch and Goldfin Trout.)
  await setLevel('Fishing', 25);
  await forceCatch('river_perch'); await forceCatch('goldfin_trout');
  await stand();
  await openSkill('Fishing');
  const eq = await waitSlot(/^Equipment: /, 27, 35);
  check('Everything here caught: the Equipment tile says the odds are normal (a preview of the water you look at)', /Nothing new can bite in Rivers now: normal odds \(preview: the water you look at\)/.test(flat(eq)), flat(eq));
  await closeAny();
  // A catch's own HUD line can hold the bar at that moment: the hint then waits for a later bite there.
  let t = Date.now(), said = false, bites = 0;
  for (; bites < 4 && !said; bites++) {
    t = Date.now();
    await forceCatch('silver_dace'); await sleep(1500);
    said = hudSince(t).some(m => /Surveyor's Float: everything that bites in Rivers now is in your journal · normal odds/.test(m));
  }
  check('...and a bite there says so', said, `after ${bites} bite(s); ` + hudSince(t).join(' / '));
  t = Date.now();
  await forceCatch('silver_dace'); await sleep(1500);
  check('...once', !hudSince(t).some(m => /Surveyor's Float/.test(m)), hudSince(t).join(' / '));
  // The Specimen Hook in place of the Float: catches as usual, every weight in its range.
  await setLevel('Fishing', 100);
  await openCategory('Lure'); await click(findSlot(/^Specimen Hook/, 19, 34)); await closeAny();
  const g = await gear();
  check('The Specimen Hook replaces the Float in Discoveries', g.presets[0].parts.includes('specimen_hook') && !g.presets[0].parts.includes('surveyors_float'), g.raw);
  let ok = 0;
  for (let i = 0; i < 4; i++) if (await forceCatch('goldfin_trout')) ok++;
  const jl = chat.slice(await cmd(`/cerb fish journal ${NAME}`, 1300)).join(' ');
  const best = Number((jl.match(/Goldfin Trout · \w+ · best ([\d.]+) kg/) || [])[1]);
  check('With the hook: catches as usual, weights in the species\' range (0.5-4.5 kg)', ok >= 3 && best >= 0.5 && best <= 4.5, `${ok} catches, best ${best} kg`);
  // A Float granted again while the Hook is in Discoveries: it stays out (one specialist; the player chose the Hook).
  await cmd(`/cerb fish part ${NAME} surveyors_float remove`, 500);
  await cmd(`/cerb fish part ${NAME} surveyors_float`, 500);
  const g2 = await gear();
  check('A specialist granted where one is already in a preset stays out of it', g2.owned.includes('surveyors_float') && g2.presets[0].parts.includes('specimen_hook') && !g2.presets[0].parts.includes('surveyors_float'), g2.raw);
  // Next levels at Fishing 24 count the Rime hook (a Lure part, which opens at 25) only at 25.
  await setLevel('Fishing', 24);
  await cmd(`/cerb fish part ${NAME} rime_hook`, 500);
  const lureAt25 = async () => {
    await openSkill('Fishing');
    const m = flat(25).match(/At level 25 .*?Lure speed: \+([\d.]+)%/);
    await closeAny();
    return m ? Number(m[1]) : NaN;
  };
  const withHook = await lureAt25();
  await cmd(`/cerb fish part ${NAME} rime_hook remove`, 500);
  const withoutHook = await lureAt25();
  // +4% raw; the engine's diminishing returns make it about +3% here. Before the fix both read the same.
  check('"Next levels" at Fishing 24: level 25 counts the Lure part that opens there', withHook - withoutHook > 1, `with ${withHook}%, without ${withoutHook}%`);
}

async function trophy() {
  await arena(600, 'river');
  await survival(30);
  const fresh = (await uncaught()).map(idOf).find(id => /goldfin|brook|perch/.test(id)) || 'river_perch';
  const name = fresh.split('_').map(w => w[0].toUpperCase() + w.slice(1)).join(' ');
  await forceCatch(fresh);
  await sleep(600);
  const fish0 = bot.inventory.items().filter(it => nameOf(it) === name).reduce((n, it) => n + it.count, 0);
  const openDiscoveries = async () => { await openSkill('Fishing'); await waitSlot(/Since your last visit/, 40, 40); await click(40); return waitTitle(SCREEN('New discoveries')); };
  await openDiscoveries();
  const tileAt = findSlot(new RegExp('^' + name), 19, 32);
  check('The new catch offers to keep it as a trophy', tileAt >= 0 && /Click: keep it as a trophy \(uses 1 of the \d+ you carry\)/.test(flat(tileAt)), flat(tileAt));
  const kg = (flat(tileAt).match(/Weight: ([\d.]+ kg)/) || [])[1];
  await click(tileAt);
  check('It asks first', await waitTitle(/^Confirm: Keep /), title());
  let at = chat.length;
  await click(11, 1300);
  check('Kept: "Kept as a trophy" with the catch\'s weight', !!saw(new RegExp('Kept as a trophy: ' + name + ' · ' + kg.replace('.', '\\.')), at), sawAll(/trophy/i, at).join(' / '));
  await closeAny();
  const trophyItem = bot.inventory.items().find(it => /Trophy/.test(loreText(it)));
  check('The trophy: the catch\'s name and weight, who caught it, the date and the water', trophyItem && nameOf(trophyItem) === `${name} · ${kg}` && new RegExp(`Caught by ${NAME}`).test(loreText(trophyItem))
    && /\d{4}-\d{2}-\d{2} · Rivers/.test(loreText(trophyItem)), trophyItem ? nameOf(trophyItem) + ' | ' + loreText(trophyItem) : 'none');
  check('...made from the fish: one fewer', bot.inventory.items().filter(it => nameOf(it) === name).reduce((n, it) => n + it.count, 0) === fish0 - 1, `${fish0}`);
  await openDiscoveries();
  const again = findSlot(new RegExp('^' + name), 19, 32);
  check('That catch is a trophy now: no second one', /✦ Kept as a trophy/.test(flat(again)) && !/Click: keep/.test(flat(again)), flat(again));
  await click(again, 900);
  check('...clicking it does nothing', SCREEN('New discoveries').test(title()), title());
  await closeAny();
  // A second trophy (another notable catch: a personal best) never stacks with the first.
  const NOTABLE = [19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32];
  for (let i = 0; i < 12; i++) {
    await forceCatch(fresh);
    await openDiscoveries();
    const offer = NOTABLE.find(k => /Click: keep it as a trophy/.test(flat(k)));
    if (offer !== undefined) { await click(offer); await waitTitle(/^Confirm: Keep /); await click(11, 1300); await closeAny(); break; }
    await closeAny();
  }
  const trophies = bot.inventory.items().filter(it => /Trophy/.test(loreText(it)));
  check('Two trophies stay two items', trophies.length === 2 && trophies.every(t => t.count === 1), trophies.map(t => nameOf(t) + ' x' + t.count).join(', '));
  // A stale confirmation (the fish left while it asked) changes nothing: a second species' discovery to keep.
  const other = (await uncaught()).map(idOf).find(id => /perch|goldfin|brook|dace/.test(id) && id !== fresh) || 'silver_dace';
  const otherName = other.split('_').map(w => w[0].toUpperCase() + w.slice(1)).join(' ');
  await forceCatch(other); await sleep(600);
  await openDiscoveries();
  const offer = NOTABLE.find(k => new RegExp('^' + otherName).test(slotName(k)) && /Click: keep it as a trophy/.test(flat(k)));
  if (offer !== undefined) {
    await click(offer); await waitTitle(/^Confirm: Keep /);
    const fishIt = bot.inventory.items().find(it => nameOf(it) === otherName);
    await cmd(`/clear @s ${fishIt ? fishIt.name : 'cod'} 64`, 600); // the fish leave while it asks
    const ms0 = await milestones();
    at = chat.length;
    await click(11, 1300);
    const ms1 = await milestones();
    check('A stale confirmation: refused, nothing changed', !!saw(new RegExp('Carry one ' + otherName), at) && ms1.trophies === ms0.trophies,
      sawAll(/trophy|Carry/i, at).join(' / '));
  } else check('A second species\' discovery offers a trophy', false, NOTABLE.map(slotName).filter(Boolean).join(' | '));
  await closeAny();
}

// ── the bite cue: a "!" over the bobber for its owner, green → yellow → red, and a bell ──
async function cue() {
  await arena(780, 'river');
  await survival(10);
  await cmd('/fishing cue on', 400);
  const tdType = bot.registry.entitiesByName.text_display.id;
  const cues = [], bells = [];
  const onSpawn = p => { if (p.type === tdType) cues.push({ id: p.entityId, t: Date.now(), colors: [], gone: 0 }); };
  const onMeta = p => {
    const c = cues.find(x => x.id === p.entityId);
    if (!c) return;
    const str = JSON.stringify(p.metadata);
    for (const col of ['green', 'yellow', 'red']) if (str.includes('"' + col + '"') && !c.colors.includes(col)) c.colors.push(col);
  };
  const onGone = p => { for (const c of cues) if (!c.gone && p.entityIds.includes(c.id)) c.gone = Date.now(); };
  const onSound = p => { if (Math.abs((p.volume || 0) - 0.8) < 0.01 && Math.abs((p.pitch || 0) - 1.6) < 0.01) bells.push(Date.now()); };
  bot._client.on('spawn_entity', onSpawn); bot._client.on('entity_metadata', onMeta);
  bot._client.on('entity_destroy', onGone); bot._client.on('sound_effect', onSound);
  // Another player at the pond: the "!" is only its owner's.
  const watcher = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'CerbWatch', version: '1.21.11', auth: 'offline' });
  let watcherSaw = 0;
  watcher._client.on('spawn_entity', p => { if (p.type === tdType) watcherSaw++; });
  await new Promise(r => watcher.once('spawn', r));
  await sleep(1500);
  await cmd(`/tp CerbWatch ${X + 1.5} ${Y} ${Z + 0.5}`, 1500);
  const castWait = async (n, ms = 45000) => { const t0 = Date.now(); while (cues.length === n && Date.now() - t0 < ms) await sleep(50); return cues[n]; };
  try {
    // A bite left alone: green, yellow, red, gone when the fish gets away.
    await stand();
    bot.activateItem(); await sleep(150); bot.deactivateItem();
    const first = await castWait(0);
    await sleep(3500);
    check('A bite: a "!" over the bobber, green, then yellow, then red', !!first && first.colors.join(',') === 'green,yellow,red', first ? first.colors.join(',') : 'no "!" in 45 s');
    check('...gone when the fish gets away', !!first && first.gone > 0 && first.gone - first.t < 3000, first ? (first.gone ? `${first.gone - first.t} ms` : 'still there') : '');
    check('...with a bell in the owner\'s ear', !!first && bells.some(t => Math.abs(t - first.t) < 800), `${bells.length} bell(s)`);
    check('...and the other player at the pond sees no "!"', watcherSaw === 0, `${watcherSaw} seen`);
    bot.activateItem(); await sleep(150); bot.deactivateItem(); await sleep(600); // reel the line in
    // Reeling at the "!": a catch, and it goes at once.
    const s0 = await summaryDump();
    bot.activateItem(); await sleep(150); bot.deactivateItem();
    const second = await castWait(1);
    const reelAt = Date.now();
    bot.activateItem(); await sleep(150); bot.deactivateItem();
    await sleep(1200);
    const s1 = await summaryDump();
    check('Reeling at the "!": a catch, and the "!" goes at once', !!second && s1 && s0 && s1.catches === s0.catches + 1 && second.gone > 0 && second.gone - reelAt < 600,
      second ? `catches ${s0 && s0.catches} -> ${s1 && s1.catches}; gone ${second.gone ? second.gone - reelAt + ' ms after the reel' : 'no'}` : 'no "!" in 45 s');
    // Off: a bite and a catch, no "!" and no bell.
    await cmd('/fishing cue off', 500);
    const n = cues.length, b = bells.length;
    const caught = await catchOne();
    check('/fishing cue off: the bite and the catch as usual, no "!", no bell', caught && cues.length === n && bells.length === b, `caught ${caught}; ${cues.length - n} "!", ${bells.length - b} bell(s)`);
    await cmd('/fishing cue on', 500);
    // Creative: vanilla fishing, but a bite is still shown.
    await cmd('/gamemode creative', 500);
    await stand();
    const m = cues.length;
    bot.activateItem(); await sleep(150); bot.deactivateItem();
    const inCreative = await castWait(m);
    check('In creative too: a bite shows the "!"', !!inCreative, inCreative ? inCreative.colors.join(',') : 'no "!" in 45 s');
    bot.activateItem(); await sleep(150); bot.deactivateItem();
    await sleep(800);
    check('...and it goes when you reel in', !!inCreative && inCreative.gone > 0, inCreative && inCreative.gone ? 'gone' : 'still there');
    await cmd('/gamemode survival', 500);
  } finally {
    bot._client.removeListener('spawn_entity', onSpawn); bot._client.removeListener('entity_metadata', onMeta);
    bot._client.removeListener('entity_destroy', onGone); bot._client.removeListener('sound_effect', onSound);
    watcher.quit();
  }
}

// ── restarts and crashes (the caller stops or kills the server on "READY", then runs the -check phase) ──
const STATE = process.env.STATE || path.join(__dirname, '.fishgear-state.json');
async function holdUntilServerGoes() {
  serverGoing = true;
  console.log('READY');
  await new Promise(res => { bot.once('end', res); setTimeout(res, 240000); });
}
const setup = g => JSON.stringify({ owned: g.owned, presets: g.presets.map(p => [p.active, p.name, p.parts, p.slots]), reel: g.reel, restock: g.restock });
/** A setup worth keeping, with bait held on the line when the server stops cleanly. */
async function restartSet() {
  await arena(660, 'river');
  await survival(25);
  await cmd(`/cerb fish part ${NAME} scaled_grip`, 500);
  await cmd(`/cerb fish part ${NAME} rime_hook`, 500);
  await cmd('/fishing setup rename 3 Deep Water', 600);
  await cmd('/fishing setup 2', 600);
  await cmd('/fishing reel off', 500); await cmd('/fishing restock on', 500);
  await cmd(`/cerb fish bait ${NAME} grub_bait 5`, 600); await cmd(`/cerb fish bait ${NAME} chum 3`, 600);
  await stand(); await castOnly();
  const g = await gear();
  fs.writeFileSync(STATE, JSON.stringify({ setup: setup(g) }));
  check('Before the stop: parts, a renamed preset, Treasure in use, switches, and one Grub Bait on the line',
    g.held === 'grub_bait' && baitIn(g, 1) === 4 && baitIn(g, 2) === 3 && g.presets[2].name === 'Deep Water' && g.presets[1].active && !g.reel && g.restock
    && g.owned.includes('scaled_grip') && g.owned.includes('rime_hook'), g.raw);
  await holdUntilServerGoes();
}
async function restartCheck() {
  const s = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  await sleep(2500); // the join's own release runs first
  const g = await gear();
  check('After a clean stop: the same setup (parts, presets, names, the one in use, switches)', setup(g) === s.setup, `${s.setup} vs ${setup(g)}`);
  check('...and the bait on the line is back in its slot, once', !g.held && baitIn(g, 1) === 5 && baitIn(g, 2) === 3, g.raw);
}
/** A crash mid-cast: the player's data was written with the bait held (CerbTestProtect's /testsave, as an autosave). */
async function crashSet() {
  await arena(660, 'river');
  await survival(25, false); await hold(ROD);
  await stand(); await castOnly();
  const g = await gear();
  fs.writeFileSync(STATE, JSON.stringify({ setup: setup(g) }));
  check('A cast holds one bait', g.held === 'grub_bait' && baitIn(g, 1) === 4, g.raw);
  const at = await cmd(`/testsave ${NAME}`, 1500);
  check('...and the player\'s data was written with it (sandbox test plugin)', !!saw(/testsave: saved/, at), sawAll(/testsave|Unknown/, at).join(' / ') || 'no answer');
  await holdUntilServerGoes();
}
async function crashCheck() {
  const s = JSON.parse(fs.readFileSync(STATE, 'utf8'));
  await sleep(2500);
  let g = await gear();
  check('After a crash mid-cast: the held bait is back in the bag, once; the setup unchanged', !g.held && baitIn(g, 1) === 5 && baitIn(g, 2) === 3 && setup(g) === s.setup, g.raw);
  bot.quit(); await sleep(3500); await connect(); await sleep(3000);
  g = await gear();
  check('...and a second join gives nothing more', !g.held && baitIn(g, 1) === 5 && baitIn(g, 2) === 3, g.raw);
}

const PHASES = { pages, track, summary, terms, equip, bait, reel, rewards, specialists, trophy, cue, oldstate, legacy,
  'restart-set': restartSet, 'restart-check': restartCheck, 'crash-set': crashSet, 'crash-check': crashCheck };
const ALL = ['pages', 'track', 'summary', 'terms', 'equip', 'bait', 'reel', 'rewards', 'specialists', 'trophy', 'cue'];

connect().then(async () => {
  await sleep(3500);
  const list = phaseArg === 'all' ? ALL : phaseArg.split(',');
  const base = NAME;
  for (const ph of list) {
    if (!PHASES[ph]) { console.log('Unknown phase ' + ph); results.push(false); continue; }
    if (list.length > 1 && FRESH.includes(ph)) { // a new character for it: op it, then rejoin under its name
      const fresh = (base.slice(0, 13) + ph.slice(0, 3)).slice(0, 16);
      try { await closeAny(); } catch (e) { /* none open */ }
      await cmd('/op ' + fresh, 800);
      bot.quit(); await sleep(3500);
      NAME = fresh; await connect(fresh); await sleep(3000);
    }
    console.log(`── ${ph} ──${NAME !== base ? ' (' + NAME + ')' : ''}`);
    try { await PHASES[ph](); } catch (e) { console.log('SCRIPT ERROR in ' + ph, e.stack); results.push(false); failed.push(ph + ' (script error)'); }
  }
  try { await closeAny(); await cmd('/gamemode creative', 300); } catch (e) { /* gone */ }
  const bad = results.filter(r => !r).length;
  console.log(`\n${results.length - bad}/${results.length} passed`);
  if (failed.length) console.log('FAILED: ' + failed.join(' | '));
  try { bot.quit(); } catch (e) { /* the server is gone */ }
  await sleep(500); process.exit(bad ? 1 : 0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(3); }, 3600000);
