/*
 * MurderMystery - Find the murderer, kill him and survive!
 * Copyright (C) 2019  Plajer's Lair - maintained by Plajer and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 * Murder Mystery is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Murder Mystery is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Murder Mystery.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.plajer.murdermystery.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import pl.plajer.murdermystery.ConfigPreferences;
import pl.plajer.murdermystery.Main;
import pl.plajer.murdermystery.api.StatsStorage;
import pl.plajer.murdermystery.api.events.game.MMGameJoinAttemptEvent;
import pl.plajer.murdermystery.api.events.game.MMGameLeaveAttemptEvent;
import pl.plajer.murdermystery.api.events.game.MMGameStopEvent;
import pl.plajer.murdermystery.arena.role.Role;
import pl.plajer.murdermystery.handlers.ChatManager;
import pl.plajer.murdermystery.handlers.PermissionsManager;
import pl.plajer.murdermystery.handlers.items.SpecialItemManager;
import pl.plajer.murdermystery.handlers.language.LanguageManager;
import pl.plajer.murdermystery.user.User;
import pl.plajer.murdermystery.utils.ItemPosition;
import pl.plajer.murdermystery.utils.MessageUtils;
import pl.plajerlair.core.debug.Debugger;
import pl.plajerlair.core.debug.LogLevel;
import pl.plajerlair.core.utils.InventoryUtils;
import pl.plajerlair.core.utils.ItemBuilder;
import pl.plajerlair.core.utils.MinigameUtils;
import pl.plajerlair.core.utils.XMaterial;

/**
 * @author Plajer
 * <p>
 * Created at 13.05.2018
 */
public class ArenaManager {

  private static Main plugin = JavaPlugin.getPlugin(Main.class);

  /**
   * Attempts player to join arena.
   * Calls MMGameJoinAttemptEvent.
   * Can be cancelled only via above-mentioned event
   *
   * @param p player to join
   * @see MMGameJoinAttemptEvent
   */
  public static void joinAttempt(Player p, Arena arena) {
    Debugger.debug(LogLevel.INFO, "Initial join attempt, " + p.getName());
    MMGameJoinAttemptEvent gameJoinAttemptEvent = new MMGameJoinAttemptEvent(p, arena);
    Bukkit.getPluginManager().callEvent(gameJoinAttemptEvent);
    if (!arena.isReady()) {
      p.sendMessage(ChatManager.PLUGIN_PREFIX + ChatManager.colorMessage("In-Game.Arena-Not-Configured"));
      return;
    }
    if (gameJoinAttemptEvent.isCancelled()) {
      p.sendMessage(ChatManager.PLUGIN_PREFIX + ChatManager.colorMessage("In-Game.Join-Cancelled-Via-API"));
      return;
    }
    if (!plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
      if (!p.hasPermission(PermissionsManager.getJoinPerm().replace("<arena>", "*"))
          || !p.hasPermission(PermissionsManager.getJoinPerm().replace("<arena>", arena.getID()))) {
        p.sendMessage(ChatManager.PLUGIN_PREFIX + ChatManager.colorMessage("In-Game.Join-No-Permission"));
        return;
      }
    }
    Debugger.debug(LogLevel.INFO, "Final join attempt, " + p.getName());
    if ((arena.getArenaState() == ArenaState.IN_GAME || (arena.getArenaState() == ArenaState.STARTING && arena.getTimer() <= 3) || arena.getArenaState() == ArenaState.ENDING)) {
      if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.INVENTORY_MANAGER_ENABLED)) {
        p.setLevel(0);
        InventoryUtils.saveInventoryToFile(plugin, p);
      }
      arena.teleportToStartLocation(p);
      p.sendMessage(ChatManager.colorMessage("In-Game.You-Are-Spectator"));
      p.getInventory().clear();

      p.getInventory().setItem(0, new ItemBuilder(XMaterial.COMPASS.parseItem()).name(ChatManager.colorMessage("In-Game.Spectator.Spectator-Item-Name")).build());
      p.getInventory().setItem(4, new ItemBuilder(XMaterial.COMPARATOR.parseItem()).name(ChatManager.colorMessage("In-Game.Spectator.Settings-Menu.Item-Name")).build());
      p.getInventory().setItem(8, SpecialItemManager.getSpecialItem("Leave").getItemStack());

      for (PotionEffect potionEffect : p.getActivePotionEffects()) {
        p.removePotionEffect(potionEffect.getType());
      }

      arena.addPlayer(p);
      p.setLevel(0);
      p.setExp(1);
      p.setFoodLevel(20);
      p.setGameMode(GameMode.SURVIVAL);
      p.setAllowFlight(true);
      p.setFlying(true);
      User user = plugin.getUserManager().getUser(p);
      user.setSpectator(true);
      for (StatsStorage.StatisticType stat : StatsStorage.StatisticType.values()) {
        if (!stat.isPersistent()) {
          user.setStat(stat, 0);
        }
      }
      p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0));
      ArenaUtils.hidePlayer(p, arena);

      for (Player spectator : arena.getPlayers()) {
        if (plugin.getUserManager().getUser(spectator).isSpectator()) {
          p.hidePlayer(spectator);
        } else {
          p.showPlayer(spectator);
        }
      }
      ArenaUtils.hidePlayersOutsideTheGame(p, arena);
      return;
    }
    if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.INVENTORY_MANAGER_ENABLED)) {
      p.setLevel(0);
      InventoryUtils.saveInventoryToFile(plugin, p);
    }
    arena.teleportToLobby(p);
    arena.addPlayer(p);
    p.setFoodLevel(20);
    p.getInventory().setArmorContents(new ItemStack[] {new ItemStack(Material.AIR), new ItemStack(Material.AIR), new ItemStack(Material.AIR), new ItemStack(Material.AIR)});
    p.setFlying(false);
    p.setAllowFlight(false);
    p.getInventory().clear();
    arena.showPlayers();
    arena.doBarAction(Arena.BarAction.ADD, p);
    if (!plugin.getUserManager().getUser(p).isSpectator()) {
      ChatManager.broadcastAction(arena, p, ChatManager.ActionType.JOIN);
    }
    if (arena.getArenaState() == ArenaState.STARTING || arena.getArenaState() == ArenaState.WAITING_FOR_PLAYERS) {
      p.getInventory().setItem(SpecialItemManager.getSpecialItem("Leave").getSlot(), SpecialItemManager.getSpecialItem("Leave").getItemStack());
    }
    p.updateInventory();
    for (Player player : arena.getPlayers()) {
      ArenaUtils.showPlayer(player, arena);
    }
    arena.showPlayers();
  }

  /**
   * Attempts player to leave arena.
   * Calls MMGameLeaveAttemptEvent event.
   *
   * @param player player to join
   * @see MMGameLeaveAttemptEvent
   */
  public static void leaveAttempt(Player player, Arena arena) {
    Debugger.debug(LogLevel.INFO, "Initial leave attempt, " + player.getName());
    MMGameLeaveAttemptEvent gameLeaveAttemptEvent = new MMGameLeaveAttemptEvent(player, arena);
    Bukkit.getPluginManager().callEvent(gameLeaveAttemptEvent);
    User user = plugin.getUserManager().getUser(player);
    if (user.getStat(StatsStorage.StatisticType.LOCAL_SCORE) > user.getStat(StatsStorage.StatisticType.HIGHEST_SCORE)) {
      user.setStat(StatsStorage.StatisticType.HIGHEST_SCORE, user.getStat(StatsStorage.StatisticType.LOCAL_SCORE));
    }
    //-1 cause we didn't remove player yet
    if (arena.getArenaState() == ArenaState.IN_GAME && arena.getPlayersLeft().size() - 1 > 1) {
      if (Role.isRole(Role.MURDERER, player)) {
        List<Player> players = new ArrayList<>();
        for (Player gamePlayer : arena.getPlayersLeft()) {
          if (Role.isRole(Role.ANY_DETECTIVE, gamePlayer)) {
            continue;
          }
          players.add(gamePlayer);
        }
        Player newMurderer = players.get(new Random().nextInt(players.size() - 1));
        arena.setMurderer(newMurderer);
        for (Player gamePlayer : arena.getPlayers()) {
          MessageUtils.sendTitle(gamePlayer, ChatManager.colorMessage("In-Game.Messages.Previous-Role-Left-Title").replace("%role%",
              ChatManager.colorMessage("Scoreboard.Roles.Murderer")));
          MessageUtils.sendSubTitle(gamePlayer, ChatManager.colorMessage("In-Game.Messages.Previous-Role-Left-Subtitle").replace("%role%",
              ChatManager.colorMessage("Scoreboard.Roles.Murderer")));
        }
        MessageUtils.sendTitle(newMurderer, ChatManager.colorMessage("In-Game.Messages.Role-Set.Murderer-Title"));
        MessageUtils.sendSubTitle(newMurderer, ChatManager.colorMessage("In-Game.Messages.Role-Set.Murderer-Subtitle"));
        ItemPosition.setItem(newMurderer, ItemPosition.MURDERER_SWORD, new ItemStack(Material.IRON_SWORD, 1));
        user.setStat(StatsStorage.StatisticType.CONTRIBUTION_MURDERER, 1);
      } else if (Role.isRole(Role.ANY_DETECTIVE, player)) {
        arena.setDetectiveDead(true);
        if (Role.isRole(Role.FAKE_DETECTIVE, player)) {
          arena.setFakeDetective(null);
        } else {
          user.setStat(StatsStorage.StatisticType.CONTRIBUTION_DETECTIVE, 1);
        }
        ArenaUtils.dropBowAndAnnounce(arena, player);
      }
      plugin.getCorpseHandler().spawnCorpse(player, arena);
    }
    player.getInventory().clear();
    player.getInventory().setArmorContents(null);
    arena.removePlayer(player);
    if (!user.isSpectator()) {
      ChatManager.broadcastAction(arena, player, ChatManager.ActionType.LEAVE);
    }
    player.setGlowing(false);
    user.setSpectator(false);
    user.removeScoreboard();
    arena.doBarAction(Arena.BarAction.REMOVE, player);
    player.setFoodLevel(20);
    player.setLevel(0);
    player.setExp(0);
    player.setFlying(false);
    player.setAllowFlight(false);
    for (PotionEffect effect : player.getActivePotionEffects()) {
      player.removePotionEffect(effect.getType());
    }
    player.setFireTicks(0);
    if (arena.getPlayers().size() == 0) {
      arena.setArenaState(ArenaState.ENDING);
      arena.setTimer(0);
    }

    player.setGameMode(GameMode.SURVIVAL);
    for (Player players : plugin.getServer().getOnlinePlayers()) {
      if (ArenaRegistry.getArena(players) == null) {
        players.showPlayer(player);
      }
      player.showPlayer(players);
    }
    arena.teleportToEndLocation(player);
    if (!plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)
        && plugin.getConfigPreferences().getOption(ConfigPreferences.Option.INVENTORY_MANAGER_ENABLED)) {
      InventoryUtils.loadInventory(plugin, player);
    }
    for (StatsStorage.StatisticType stat : StatsStorage.StatisticType.values()) {
      if (!stat.isPersistent()) {
        user.setStat(stat, 0);
      }
    }
  }

  /**
   * Stops current arena. Calls MMGameStopEvent event
   *
   * @param quickStop should arena be stopped immediately? (use only in important cases)
   * @see MMGameStopEvent
   */
  public static void stopGame(boolean quickStop, Arena arena) {
    Debugger.debug(LogLevel.INFO, "Game stop event initiate, arena " + arena.getID());
    if (arena.getArenaState() != ArenaState.IN_GAME) {
      Debugger.debug(LogLevel.INFO, "Game stop event finish, arena " + arena.getID());
      return;
    }
    MMGameStopEvent gameStopEvent = new MMGameStopEvent(arena);
    Bukkit.getPluginManager().callEvent(gameStopEvent);
    List<String> summaryMessages = LanguageManager.getLanguageList("In-Game.Messages.Game-End-Messages.Summary-Message");
    Random rand = new Random();
    for (final Player player : arena.getPlayers()) {
      User user = plugin.getUserManager().getUser(player);
      if (Role.isRole(Role.FAKE_DETECTIVE, player) || Role.isRole(Role.INNOCENT, player)) {
        user.setStat(StatsStorage.StatisticType.CONTRIBUTION_MURDERER, rand.nextInt(4) + 1);
        user.setStat(StatsStorage.StatisticType.CONTRIBUTION_DETECTIVE, rand.nextInt(4) + 1);
      }
      player.getInventory().clear();
      player.getInventory().setItem(SpecialItemManager.getSpecialItem("Leave").getSlot(), SpecialItemManager.getSpecialItem("Leave").getItemStack());
      for (String msg : summaryMessages) {
        MinigameUtils.sendCenteredMessage(player, formatSummaryPlaceholders(msg, arena));
      }
      user.removeScoreboard();
      if (!quickStop && plugin.getConfig().getBoolean("Firework-When-Game-Ends", true)) {
        new BukkitRunnable() {
          int i = 0;

          public void run() {
            if (i == 4 || !arena.getPlayers().contains(player)) {
              this.cancel();
            }
            MinigameUtils.spawnRandomFirework(player.getLocation());
            i++;
          }
        }.runTaskTimer(plugin, 30, 30);
      }
    }
    Debugger.debug(LogLevel.INFO, "Game stop event finish, arena " + arena.getID());
  }

  private static String formatSummaryPlaceholders(String msg, Arena arena) {
    String formatted = msg;
    if (arena.getPlayersLeft().size() == 1 && arena.getPlayersLeft().get(0).equals(arena.getMurderer())) {
      formatted = StringUtils.replace(formatted, "%winner%", ChatManager.colorMessage("In-Game.Messages.Game-End-Messages.Winners.Murderer"));
    } else {
      formatted = StringUtils.replace(formatted, "%winner%", ChatManager.colorMessage("In-Game.Messages.Game-End-Messages.Winners.Players"));
    }
    if (arena.isDetectiveDead()) {
      formatted = StringUtils.replace(formatted, "%detective%", ChatColor.STRIKETHROUGH + arena.getDetective().getName());
    } else {
      formatted = StringUtils.replace(formatted, "%detective%", arena.getDetective().getName());
    }
    if (arena.isMurdererDead()) {
      formatted = StringUtils.replace(formatted, "%murderer%", ChatColor.STRIKETHROUGH + arena.getMurderer().getName());
    } else {
      formatted = StringUtils.replace(formatted, "%murderer%", arena.getMurderer().getName());
    }
    formatted = StringUtils.replace(formatted, "%murderer_kills%", String.valueOf(plugin.getUserManager().getUser(arena.getMurderer()).getStat(StatsStorage.StatisticType.LOCAL_KILLS)));
    formatted = StringUtils.replace(formatted, "%hero%", arena.getHero() == null ? ChatManager.colorMessage("In-Game.Messages.Game-End-Messages.Winners.Nobody") : arena.getHero().getName());
    return formatted;
  }

}
