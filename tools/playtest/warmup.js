// Warms a freshly started SANDBOX server before a regression: a bot (op) visits a few far, never-generated places, the kind
// the suites build their arenas at, and waits for their terrain. Right after a restart the server generates its first
// far chunks slowly (its code not yet compiled), and with three sandboxes on four cores a suite's first phase could time
// out before its arena existed (2026-11 audit: a brewing stand that never opened, a field never planted). Asserts nothing.
// Usage: MC_PORT=25566 node warmup.js [name=CerbSmoke] [places=4]
const mineflayer = require('mineflayer');
const name = process.argv[2] || 'CerbSmoke', places = Number(process.argv[3] || 4);
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(process.env.MC_PORT || 25565), username: name, version: '1.21.11', auth: 'offline' });
bot.once('kicked', r => { console.log('KICKED', JSON.stringify(r)); process.exit(1); });
bot.once('spawn', async () => {
  await sleep(2000);
  bot.chat('/gamemode creative'); await sleep(500);
  for (let i = 0; i < places; i++) {
    const x = -200000 - Math.floor(Math.random() * 800000), z = 300 + Math.floor(Math.random() * 600);
    const t = Date.now();
    bot.chat(`/tp @s ${x} 200 ${z}`);
    await sleep(1500);
    try { await Promise.race([bot.waitForChunksToLoad(), sleep(30000)]); } catch (e) { /* timed out: the next place */ }
    console.log(`place ${i + 1}: ${x} ${z}, terrain in ${((Date.now() - t) / 1000).toFixed(1)} s`);
  }
  bot.quit();
  await sleep(1000);
  process.exit(0);
});
setTimeout(() => { console.log('TIMEOUT'); process.exit(2); }, 240000);
