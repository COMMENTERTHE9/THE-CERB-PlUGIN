# AI Handoff Document - Cerberus Plugin

## Project Overview
This is a comprehensive Minecraft RPG plugin with 40+ skills, custom combat mechanics, virtual health system, and ambitious features. The codebase is actually quite good and definitely worth continuing.

## Current State (January 2025)

### ✅ What's Working:
- **Skill System**: 40+ skills across Combat, Magic, and Utility categories with XP/leveling
- **Virtual Health System**: Custom health pools replacing vanilla mechanics
- **Custom Items**: Quality tiers (Common→Primordial), elemental tags, crafting integration
- **Defense Bar System**: Just implemented! Draining defense bar with regeneration
- **Async Operations**: Database saves and file I/O handled asynchronously
- **Basic Combat**: Damage types, resistances, custom death/respawn handling

### 🐛 Critical Issues to Fix:
1. **CombatListener.java** - Fixed compilation error (missing maps)
2. **Database Schema** - Missing table creation for:
   - `player_custom_ids`
   - `player_skills` 
   - `skill_effects`
   - `player_magic_find`
3. **Circular Dependencies** - Managers have complex interdependencies

### 📋 Current TODO List:
1. **Fix critical bugs in existing systems** (HIGH)
2. ~~Complete the Defense Bar system~~ ✓ DONE
3. **Fix database schema issues** (HIGH)
4. **Implement Echo Families base system** (MEDIUM)
5. **Complete unfinished skill implementations** (MEDIUM)
6. **Build Inventory Badge Trial system** (LOW)

## Roadmap Systems Not Yet Implemented:

### From the v0.3 Roadmap:
1. **Reservoir Inversion** - Swap HP/MP for dramatic moments
2. **Echo Families** - 5 families (SAND, TIDE, ASH, FROST, MIRE) with rituals
3. **Gear Progression** - -Nomicons, keyed armor, entropy durability
4. **Inventory Badge** - 20-tier progression system
5. **Contracts System** - Player-to-player escrow trades
6. **PvP Tiers** - Risk zones, directional scan, outpost sieges
7. **Blink Gates** - Teleportation network
8. **Spaceman Armor** - Meme endgame reward

## Recent Work Done:

### Defense Bar System (Just Completed):
- Created `DefenseBarManager.java` with formula: `flat / (toughness × effectiveness)`
- Integrated into damage pipeline in `PlayerVirtualHealthManager`
- Added HUD display showing defense as `current/max⛨`
- Configuration in `config.yml` for all values
- Cleanup on player quit to prevent memory leaks

## Next Steps Recommendations:

### Option A: Fix Database Schema (RECOMMENDED)
The `DatabaseManager` references tables that don't exist. Need to:
1. Create proper DDL for all tables
2. Add migration system
3. Fix skill loading/saving
4. Test persistence

### Option B: Echo Families Foundation
Start implementing the Echo system from roadmap:
1. Create base Echo class
2. Implement SAND family as proof of concept
3. Add mob spawning triggers
4. Token/ritual mechanics

### Option C: Complete Skills
Many skills have empty `applyEffect()` methods:
1. Implement combat skill damage bonuses
2. Add magic skill mana costs
3. Complete utility skill effects
4. Balance multipliers

## Code Quality Notes:
- **Good**: Clean package structure, proper event handling, async operations
- **Bad**: Some circular dependencies, magic numbers, incomplete TODOs
- **Ugly**: Missing null checks in places, complex initialization order

## Quick Start for Next AI:
1. Read `/mnt/c/Users/Gabri/IdeaProjects/Cerb/Creb/README.md` (if exists)
2. Check `src/main/java/cerberus/world/cerb/CerberusPlugin.java` - main class
3. Review `src/main/resources/plugin.yml` for commands
4. Look at the roadmap in the conversation above
5. Pick up from the TODO list or implement new roadmap features

## Final Notes:
- Plugin uses Java 21 with preview features
- Targets Paper 1.21.1
- Has external dependencies (DTR engine, DecentHolograms)
- Database is SQLite with async wrapper
- Most numeric values are placeholders - needs balancing

The project is ambitious but well-structured. With some bug fixes and completion of existing systems, this could be a very popular RPG plugin. Good luck!