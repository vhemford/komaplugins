package kr.koma.dragonegg;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.java.JavaPlugin;

public class DragonEggSurvival extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DragonEggSurvival 활성화!");
    }

    /* ===================== 1) 날카 10 / 보호 7 ===================== */

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent e) {
        AnvilInventory inv = e.getInventory();
        ItemStack left = inv.getFirstItem();
        ItemStack right = inv.getSecondItem();
        if (left == null || right == null || left.getType() == Material.AIR) return;

        ItemStack result = e.getResult();
        ItemStack base = (result != null) ? result.clone() : left.clone();
        boolean changed = false;

        int sharpMax = getConfig().getInt("enchant.sharpness-max", 10);
        int protMax = getConfig().getInt("enchant.protection-max", 7);

        changed |= upgrade(left, right, base, Enchantment.SHARPNESS, sharpMax);
        changed |= upgrade(left, right, base, Enchantment.PROTECTION, protMax);

        if (!changed) return;

        e.setResult(base);

        AnvilView view = e.getView();
        view.setMaximumRepairCost(getConfig().getInt("enchant.anvil-max-repair-cost", 1000));
        int lv = Math.max(level(base, Enchantment.SHARPNESS), level(base, Enchantment.PROTECTION));
        int cost = Math.max(view.getRepairCost(), lv * 3);

        // 날카로움 N 이상 책끼리 합칠 때는 고정 레벨 요구
        int minLv = getConfig().getInt("enchant.sharpness-book-combine-min-level", 5);
        int fixedCost = getConfig().getInt("enchant.sharpness-book-combine-cost", 39);
        boolean bookCombine = left.getType() == Material.ENCHANTED_BOOK
                && right.getType() == Material.ENCHANTED_BOOK
                && level(left, Enchantment.SHARPNESS) >= minLv
                && level(right, Enchantment.SHARPNESS) >= minLv;
        if (bookCombine) cost = fixedCost;

        view.setRepairCost(cost);
    }

    /** 같은 레벨 두 개를 합치면 +1, 다르면 높은 쪽. 상한(max)까지만. */
    private boolean upgrade(ItemStack left, ItemStack right, ItemStack base, Enchantment ench, int max) {
        int l = level(left, ench);
        int r = level(right, ench);
        if (l <= 0 || r <= 0) return false;

        int next = (l == r) ? l + 1 : Math.max(l, r);
        next = Math.min(next, max);

        if (next <= level(base, ench)) return false;
        setLevel(base, ench, next);
        return true;
    }

    private int level(ItemStack item, Enchantment ench) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta esm) return esm.getStoredEnchantLevel(ench);
        return item.getEnchantmentLevel(ench);
    }

    private void setLevel(ItemStack item, Enchantment ench, int lvl) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta esm) {
            esm.addStoredEnchant(ench, lvl, true);
            item.setItemMeta(esm);
        } else {
            item.addUnsafeEnchantment(ench, lvl);
        }
    }

    /* ===================== 2) 플레이어 사망 시 경험치 그대로 드랍 ===================== */

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!getConfig().getBoolean("player-death.enabled", true)) return;
        if (e.getKeepLevel()) return; // keepInventory 등으로 레벨 유지 시 중복 방지

        Player victim = e.getEntity();
        if (getConfig().getBoolean("player-death.only-when-killed-by-player", true)
                && victim.getKiller() == null) return;

        int total = totalXp(victim);
        e.setDroppedExp(total);
        e.setNewExp(0);
        e.setNewLevel(0);
        e.setNewTotalExp(0);
    }

    /** 현재 레벨 + 진행도를 합산한 실제 총 경험치 (getTotalExperience 오차 방지) */
    private int totalXp(Player p) {
        int lv = p.getLevel();
        double base;
        if (lv <= 16) base = lv * lv + 6.0 * lv;
        else if (lv <= 31) base = 2.5 * lv * lv - 40.5 * lv + 360;
        else base = 4.5 * lv * lv - 162.5 * lv + 2220;

        int need;
        if (lv <= 15) need = 2 * lv + 7;
        else if (lv <= 30) need = 5 * lv - 38;
        else need = 9 * lv - 158;

        return (int) Math.round(base + p.getExp() * need);
    }

    /* ===================== 3) 드래곤 처치 경험치 ===================== */

    @EventHandler(priority = EventPriority.HIGH)
    public void onDragonDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof EnderDragon dragon)) return;
        if (!getConfig().getBoolean("dragon.enabled", true)) return;

        // 첫 처치 판정: 바닐라는 첫 처치일 때 12000 이상(=기본 드랍량)이 설정되어 있음
        boolean first = e.getDroppedExp() >= 12000;
        int xp = first
                ? getConfig().getInt("dragon.first-kill-xp", 12000)
                : getConfig().getInt("dragon.respawn-kill-xp", 12000);
        e.setDroppedExp(xp);
    }
}
