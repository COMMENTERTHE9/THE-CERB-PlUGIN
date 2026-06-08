package Skills;

import Manager.CraftingManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * DEFERRED IMPLEMENTATION.
 *
 * Trading skill is intentionally a placeholder until the NPC, marketplace,
 * and bazaar systems are implemented. Methods in this class do nothing
 * by design.
 *
 * DO NOT delete this class.
 * DO NOT attempt to add real logic until the prerequisite systems exist.
 *
 * When trading is built, this class will be the entry point for skill XP
 * gained from trading actions.
 */
public class TradingSkill extends UtilitySkill {

    private final CraftingManager craftingManager;

    public TradingSkill(String name, CraftingManager craftingManager) {
        super(name);
        this.craftingManager = craftingManager;
    }

    @Override
    public void applyEffect(Player player) {
        if (player == null) return;
        int level = this.getLevel();

        // Calculate the trade value bonus: 3% increase per skill level
        double tradeBonus = level * 0.03;

        // Assuming we have an item that the player is trading
        ItemStack itemToTrade = getItemToTradeFromPlayer(player);

        // Apply trade bonuses using CraftingManager's enhanceTradeValue method
        if (itemToTrade != null) {
            craftingManager.enhanceTradeValue(player, itemToTrade, level);
        }

        // Apply future logic for trading with Main Market, Bazaar, and Stocks
        applyMarketTradingBonuses(player, level);
        applyBazaarTradingBonuses(player, level);
        applyStockTradingBonuses(player, level);
    }

    // Placeholder method to retrieve the item the player is trading
    private ItemStack getItemToTradeFromPlayer(Player player) {
        // Logic to retrieve the item the player is trading
        return player.getInventory().getItemInMainHand(); // Example: Getting the item in the main hand
    }

    // Placeholder for market-related trading bonuses
    private void applyMarketTradingBonuses(Player player, int level) {
        // Example logic for Main Market trading
        player.sendMessage("Applied a " + (level * 3) + "% trade bonus for market trading.");
        // Placeholder: Logic to connect with future Main Market system and adjust prices
    }

    // Placeholder for bazaar-related trading bonuses
    private void applyBazaarTradingBonuses(Player player, int level) {
        // Example logic for Bazaar trading
        player.sendMessage("Applied a " + (level * 3) + "% trade bonus for bazaar trading.");
        // Placeholder: Logic to connect with future Bazaar system for bulk trading
    }

    // Placeholder for stock-related trading bonuses
    private void applyStockTradingBonuses(Player player, int level) {
        // Example logic for stock market
        player.sendMessage("Applied a " + (level * 3) + "% bonus for stock market investments.");
        // Placeholder: Logic to connect with future stock trading system
    }

}
