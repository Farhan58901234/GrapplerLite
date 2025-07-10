package me.farhan;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.NamespacedKey;

import java.util.*;

public class GrapplerLite extends JavaPlugin implements Listener, TabExecutor {

    private final Set<UUID> cooldown = new HashSet<>();
    private final Set<UUID> noFall = new HashSet<>();
    private final Map<UUID, Vector> lastCastDirection = new HashMap<>();
    private double pullPower;
    private double sprintBoost;
    private int cooldownTicks;
    private int defaultUses;
    private String hookName;
    private List<String> hookLore;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadGrappleConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("gp")).setExecutor(this);
        getCommand("gp").setTabCompleter(this);
        getLogger().info("Grappling Hook enabled.");

        if (getConfig().getBoolean("enable-crafting", true)) {
            registerCraftingRecipe();
        }
    }

    public void reloadGrappleConfig() {
        reloadConfig();
        cooldownTicks = getConfig().getInt("cooldown", 2) * 20;
        pullPower = getConfig().getDouble("pull-power", 2.5);
        sprintBoost = getConfig().getDouble("sprint-boost", 0.5);
        defaultUses = getConfig().getInt("default-uses", 20);

        hookName = ChatColor.translateAlternateColorCodes('&', getConfig().getString("hook-name", "&bGrappling Hook"));
        hookLore = getConfig().getStringList("hook-lore");
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("give")) {
                if (!(s.hasPermission("gp.give") || s instanceof ConsoleCommandSender)) {
                    s.sendMessage(ChatColor.RED + "No permission to give Grapple Hooks.");
                    return true;
                }
                if (args.length < 3) return false;
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    s.sendMessage(ChatColor.RED + "Player not online.");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException ex) {
                    s.sendMessage(ChatColor.RED + "Invalid number.");
                    return true;
                }

                int uses = -1; // default unlimited
if (args.length >= 4) {
    if (args[3].equalsIgnoreCase("unlimited")) {
        uses = -1;
    } else {
        try {
            uses = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            s.sendMessage(ChatColor.RED + "Invalid uses number.");
            return true;
        }
    }
}

ItemStack rod = createGrapplingHook(amount, uses);

                target.getInventory().addItem(rod);
                s.sendMessage(ChatColor.GREEN + "Gave " + amount + " Grappling Hook(s) to " + target.getName());
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!s.hasPermission("gp.reload")) {
                    s.sendMessage(ChatColor.RED + "No permission to reload.");
                    return true;
                }
                reloadGrappleConfig();
                s.sendMessage(ChatColor.GREEN + "Grappling Hook config reloaded.");
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("give", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> matches = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    matches.add(p.getName());
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }

@EventHandler
public void onFish(PlayerFishEvent e) {
    Player p = e.getPlayer();
    ItemStack rod = p.getInventory().getItemInMainHand();

    if (!isGrapplingRod(rod)) return;
    if (!p.hasPermission("gp.use")) return;

    // Only pull when the player reels in OR the hook hits the ground
    if (e.getState() != PlayerFishEvent.State.REEL_IN &&
        e.getState() != PlayerFishEvent.State.IN_GROUND) return;

    if (cooldown.contains(p.getUniqueId())) {
        p.sendMessage(ChatColor.RED + "Grappling Hook is on cooldown!");
        return;
    }

    if (e.getHook() == null) return;

    Vector dir = e.getHook().getLocation().toVector().subtract(p.getLocation().toVector());
    triggerGrapple(p, dir);
    decreaseUses(p, rod);

    // Cancel fishing action
    e.setCancelled(true);

    // Simulate the hook being reeled back instantly (so it doesn't stay stuck)
    e.getHook().remove(); // This removes the fishing hook entity
}



@EventHandler
public void onFallDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player)) return;
    Player p = (Player) e.getEntity();

    if (e.getCause() == EntityDamageEvent.DamageCause.FALL && noFall.remove(p.getUniqueId())) {
        e.setCancelled(true); // Cancel the fall damage
    }
}

private void triggerGrapple(Player p, Vector dir) {
    Vector velocity = dir.clone().normalize();
    velocity.multiply(Math.min(dir.length() / 2, pullPower));
    if (p.isSprinting()) velocity.add(p.getLocation().getDirection().multiply(sprintBoost));
    velocity.setY(Math.max(velocity.getY(), 0.1));

    p.setVelocity(velocity);
    noFall.add(p.getUniqueId());
    cooldown.add(p.getUniqueId());

    Bukkit.getScheduler().runTaskLater(this, () -> cooldown.remove(p.getUniqueId()), cooldownTicks);
}

    private boolean isGrapplingRod(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return false;
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() &&
            ChatColor.stripColor(meta.getDisplayName()).equalsIgnoreCase(ChatColor.stripColor(hookName));
    }

private void registerCraftingRecipe() {
    ItemStack rod = createGrapplingHook(1, defaultUses); // already sets meta fully

    NamespacedKey key = new NamespacedKey(this, "grappling_hook");

    ShapedRecipe recipe = new ShapedRecipe(key, rod);
    recipe.shape(" S ", "SRS", " S ");
    recipe.setIngredient('S', Material.STRING);
    recipe.setIngredient('R', Material.FISHING_ROD);

    Bukkit.addRecipe(recipe);
}


private ItemStack createGrapplingHook(int amount, int uses) {
    ItemStack rod = new ItemStack(Material.FISHING_ROD, amount);
    ItemMeta meta = rod.getItemMeta();

    meta.setDisplayName(hookName);

    List<String> coloredLore = new ArrayList<>();
    for (String line : hookLore) {
        coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
    }

    if (uses >= 0) {
        coloredLore.add(ChatColor.GRAY + "Uses: " + uses);
    } else {
        coloredLore.add(ChatColor.GRAY + "Uses: Unlimited");
    }

    meta.setLore(coloredLore);
    meta.addEnchant(Enchantment.LURE, 3, true);
    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    rod.setItemMeta(meta);
    return rod;
}

private int getUses(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return -1;
    ItemMeta meta = item.getItemMeta();
    if (!meta.hasLore()) return -1;
    for (String line : meta.getLore()) {
        if (ChatColor.stripColor(line).startsWith("Uses:")) {
            String value = ChatColor.stripColor(line).replace("Uses:", "").trim();
            if (value.equalsIgnoreCase("Unlimited")) return -1;
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {}
        }
    }
    return -1;
}

private void decreaseUses(Player p, ItemStack rod) {
    int uses = getUses(rod);
    if (uses == -1) return; // Unlimited

    uses--;

    if (uses <= 0) {
        // Play item break sound
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);

        // Break the rod properly (handle stacks too)
        if (rod.getAmount() > 1) {
            rod.setAmount(rod.getAmount() - 1);
        } else {
            p.getInventory().removeItem(rod);
        }

        p.sendMessage(ChatColor.RED + "Your Grappling Hook broke!");
        return;
    }

    if (!rod.hasItemMeta()) return;
    ItemMeta meta = rod.getItemMeta();
    if (meta == null) return;

    List<String> lore = meta.getLore();
    List<String> newLore = new ArrayList<>();

    if (lore != null) {
        for (String line : lore) {
            if (!ChatColor.stripColor(line).startsWith("Uses:")) {
                newLore.add(line);
            }
        }
    }

    newLore.add(ChatColor.GRAY + "Uses: " + uses);
    meta.setLore(newLore);
    rod.setItemMeta(meta);
}
}
