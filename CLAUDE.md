# Claude Code Memory - Cerberus Plugin

## Project Context
Minecraft RPG plugin with extensive skill system, custom combat mechanics, and ambitious roadmap for gameplay features.

## Key Technical Details
- **Main Class**: `cerberus.world.cerb.CerberusPlugin`
- **Java Version**: 21 with preview features
- **Target**: Paper 1.21.1
- **Database**: SQLite with async operations
- **Package Structure**: Organized by feature (Skills/, Manager/, Listener/, etc.)

## Recent Changes
- Fixed compilation error in CombatListener (added missing maps)
- Implemented Defense Bar system with draining/regeneration mechanics
- Added defense bar to HUD display
- Created config.yml with tunable values

## Common Commands
- `/cerb reload` - Reload plugin configuration
- `/cerb status` - Show server status
- `/spawncustomzombie <health>` - Test mob spawning
- `/givetestitem` - Get test items
- `/region` - Region management

## Important Files
- `CerberusPlugin.java` - Main plugin class with initialization
- `DatabaseManager.java` - Handles all database operations (needs schema fixes)
- `SkillManager.java` - Central skill system orchestrator
- `PlayerVirtualHealthManager.java` - Custom health system implementation
- `DefenseBarManager.java` - NEW: Defense bar with formula from roadmap

## Known Issues
1. Database tables not created on startup
2. Many skill effects not implemented
3. Some managers have circular dependencies
4. Various TODO comments throughout code

## Testing Notes
- Virtual health system replaces vanilla health
- Defense bar absorbs damage before health
- Skills affect combat through multipliers
- Custom damage types have unique resistances

## Next Session Should:
1. Fix database schema creation
2. Complete skill implementations
3. Start Echo Families system
4. Or implement another roadmap feature

## Useful Patterns
- Use `AsyncSaveManager` for all file/DB saves
- Events use priority flags for proper ordering
- Virtual health keeps vanilla health at max
- Defense effectiveness affects total defense

Remember: The roadmap values are placeholders - implement first, balance later!