package GUIs.player;

import Classes.AbilityDef;
import Classes.ClassCatalog;
import Classes.ClassDef;
import Classes.ClassProfile;
import Classes.ClassRanks;
import Classes.ClassService;
import Classes.Loadout;
import Classes.WeaponFamily;
import GUIs.menu.Fmt;
import GUIs.menu.Ui;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Tiles shared by the Classes & Abilities screens. Read-only: they draw the profile, never change it. */
final class ClassView {

    private ClassView() {}

    /** The five cast keys, by active slot. */
    static final String[] KEYS = {"Sneak + right-click", "Sneak + left-click", "Sneak + F", "Sneak + Q", "Sneak + scroll"};

    static Material icon(ClassCatalog cat, String id, Material fallback) {
        String name = cat.icon(id);
        Material m = name == null ? null : Material.matchMaterial(name);
        return m != null && m.isItem() && !m.isAir() ? m : fallback;
    }

    static Material abilityIcon(ClassCatalog cat, AbilityDef a) {
        Material fallback = switch (a.kind()) {
            case ACTIVE -> Material.BLAZE_POWDER;
            case PASSIVE -> Material.BOOK;
            case KEYSTONE -> Material.NETHER_STAR;
        };
        return icon(cat, a.id(), fallback);
    }

    /** "a blade or an axe" / "any hand". */
    static String hand(AbilityDef a) {
        if (a.weapons().isEmpty() || a.weapons().contains(WeaponFamily.ANY)) return "any hand";
        List<String> w = new ArrayList<>();
        for (WeaponFamily f : a.weapons()) w.add(f.withArticle());
        return w.size() == 1 ? w.get(0) : String.join(", ", w.subList(0, w.size() - 1)) + " or " + w.get(w.size() - 1);
    }

    static String kind(AbilityDef a) {
        return switch (a.kind()) {
            case ACTIVE -> "Active";
            case PASSIVE -> "Passive";
            case KEYSTONE -> "Keystone";
        };
    }

    /** Where this ability sits in the player's loadouts ("Loadout 1: A2, Loadout 3: A1"), or null. */
    static String equippedIn(ClassProfile prof, String abilityId) {
        List<String> where = new ArrayList<>();
        for (int i = 0; i < prof.loadouts().size(); i++) {
            Loadout l = prof.loadouts().get(i);
            for (int s = 0; s < Loadout.ACTIVE_SLOTS; s++) if (abilityId.equals(l.actives().get(s))) where.add("Loadout " + (i + 1) + ": A" + (s + 1));
            for (int s = 0; s < Loadout.PASSIVE_SLOTS; s++) if (abilityId.equals(l.passives().get(s))) where.add("Loadout " + (i + 1) + ": P" + (s + 1));
            if (abilityId.equals(l.keystone())) where.add("Loadout " + (i + 1) + ": K");
        }
        return where.isEmpty() ? null : String.join(", ", where);
    }

    /** An ability: what it is, what it needs, its numbers, and whether this player has it. */
    static ItemStack abilityTile(ClassService svc, ClassProfile prof, AbilityDef a, String hint) {
        ClassCatalog cat = svc.catalog();
        ClassDef c = cat.classDef(a.classId());
        String className = c == null ? a.classId() : c.displayName();
        boolean open = prof.unlocked(a);
        Ui.Lore l = Ui.lore();
        l.raw(ChatColor.DARK_GRAY + className + " · " + kind(a) + " · rank " + a.unlockRank());
        if (a.isActive()) {
            l.stat("Needs", hand(a))
                    .stat("Cooldown", String.format(Locale.ROOT, "%.1f s", a.cooldownMs() / 1000.0));
            if (a.essenceCost() > 0) l.stat("Essence", String.valueOf(Math.round(a.essenceCost())));
            if (!a.groups().isEmpty()) l.stat("Shares a lockout with", String.join(", ", a.groups()) + " abilities");
        } else {
            for (AbilityDef.Passive ps : a.passives()) l.raw(ChatColor.WHITE + "  " + ClassService.bonusText(ps));
        }
        if (!a.exclusive().isEmpty()) l.stat("Only one at a time", String.join(", ", a.exclusive()));
        l.gap();
        for (String line : PlayerTheme.wrap(a.description(), 34)) l.text(line);
        l.gap();
        if (open) l.raw(ChatColor.GREEN + "✔ Unlocked");
        else if (!prof.knows(a.classId())) l.raw(ChatColor.GOLD + "✖ Learn " + className + " first");
        else l.raw(ChatColor.GOLD + "✖ Unlocks at " + className + " rank " + a.unlockRank() + ChatColor.GRAY + " (you: " + prof.rank(a.classId()) + ")");
        String where = equippedIn(prof, a.id());
        if (where != null) l.stat("Equipped", where);
        if (hint != null) l.hint(hint);
        String name = (open ? ChatColor.YELLOW : ChatColor.GRAY) + a.displayName();
        return Ui.glow(Ui.item(open ? abilityIcon(cat, a) : Material.GRAY_DYE, name, l.build()), where != null);
    }

    /** A class: rank and XP (or what it needs), its role and signature, and its unlock progress. */
    static ItemStack classTile(ClassService svc, ClassProfile prof, ClassDef c, UUID player) {
        ClassCatalog cat = svc.catalog();
        ClassProfile.Progress pr = prof.classes().get(c.id());
        int total = 0, unlocked = 0;
        AbilityDef next = null;
        for (String id : c.abilities()) {
            AbilityDef a = cat.ability(id);
            if (a == null) continue;
            total++;
            if (prof.unlocked(a)) unlocked++;
            else if (next == null || a.unlockRank() < next.unlockRank()) next = a;
        }
        Ui.Lore l = Ui.lore();
        l.raw(ChatColor.GRAY + c.role());
        l.stat("Signature", c.signature());
        l.gap();
        if (pr != null) {
            long need = ClassRanks.xpForRank(pr.rank());
            l.stat("Rank", pr.rank() + "" + ChatColor.DARK_GRAY + " / " + ClassRanks.MAX_RANK);
            if (pr.rank() < ClassRanks.MAX_RANK) {
                l.raw(PlayerTheme.bar(pr.xp(), need, 20, ChatColor.GOLD));
                l.stat("XP", Fmt.grouped(pr.xp()) + ChatColor.DARK_GRAY + " / " + Fmt.grouped(need));
            }
            l.stat("Abilities unlocked", unlocked + " / " + total);
            if (next != null) l.stat("Next", next.displayName() + " at rank " + next.unlockRank());
            l.hint("Click: its abilities");
        } else {
            l.raw(ChatColor.GOLD + "✖ Not learned yet");
            for (String line : PlayerTheme.wrap("Needs " + svc.requirements(c, player), 34)) l.text(line);
            l.text("It opens by itself when your skills get there.");
            l.hint("Click: see its abilities");
        }
        String name = (pr != null ? ChatColor.GOLD + "" + ChatColor.BOLD : ChatColor.GRAY + "") + c.displayName()
                + (pr != null ? ChatColor.YELLOW + " · rank " + pr.rank() : "");
        return Ui.glow(Ui.item(icon(cat, c.id(), Material.BOOK), name, l.build()), false);
    }

    /** One loadout slot: the ability in it (or empty) and the key that casts it. */
    static ItemStack slotTile(ClassService svc, ClassProfile prof, String label, String key, String abilityId, String hint) {
        AbilityDef a = svc.catalog().ability(abilityId);
        if (a == null) {
            Ui.Lore l = Ui.lore();
            if (key != null) l.stat("Key", key);
            l.text("Empty");
            if (hint != null) l.hint(hint);
            return Ui.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + label + ": empty", l.build());
        }
        ItemStack tile = abilityTile(svc, prof, a, hint);
        org.bukkit.inventory.meta.ItemMeta meta = tile.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GRAY + label + ": " + ChatColor.YELLOW + a.displayName());
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            if (key != null) lore.add(0, ChatColor.GRAY + "Key: " + ChatColor.WHITE + key);
            meta.setLore(lore);
            tile.setItemMeta(meta);
        }
        return tile;
    }
}
