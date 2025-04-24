package dev.faultyfunctions.soulgraves.listeners

import dev.faultyfunctions.soulgraves.managers.ConfigManager
import dev.faultyfunctions.soulgraves.utils.Soul
import dev.faultyfunctions.soulgraves.*
import dev.faultyfunctions.soulgraves.api.RedisPublishAPI
import dev.faultyfunctions.soulgraves.api.SoulGraveAPI
import dev.faultyfunctions.soulgraves.api.event.SoulPreSpawnEvent
import dev.faultyfunctions.soulgraves.api.event.SoulSpawnEvent
import dev.faultyfunctions.soulgraves.managers.MessageManager
import dev.faultyfunctions.soulgraves.managers.STORAGE_MODE
import dev.faultyfunctions.soulgraves.managers.StorageType
import dev.faultyfunctions.soulgraves.utils.SpigotCompatUtils
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack

class PlayerDeathListener() : Listener {
	@EventHandler(priority = EventPriority.NORMAL)
	fun onPlayerDeathEvent(e: PlayerDeathEvent) {
		// READABILITY VARIABLES
		val player: Player = e.entity

		// CHECK PLAYER HAS PERMISSION
		if (ConfigManager.permissionRequired && !player.hasPermission("soulgraves.spawn")) return

		// CHECK TO MAKE SURE WE ARE IN AN ENABLED WORLD
		if (ConfigManager.disabledWorlds.contains(player.world.name)) return

		// CHECK PLAYER IS NOT KEEP INVENTORY
		if (e.keepInventory || player.world.getGameRuleValue(GameRule.KEEP_INVENTORY) == true) return

		// CHECK PLAYER HAVE ITEMS OR XP
		if (player.level == 0 && e.drops.isEmpty()) return

		// CALL EVENT
		val soulPreSpawnEvent = SoulPreSpawnEvent(player, e)
		Bukkit.getPluginManager().callEvent(soulPreSpawnEvent)
		if (soulPreSpawnEvent.isCancelled) return

		// DATA
		val inventory: MutableList<ItemStack?> = mutableListOf()
		player.inventory.forEach { item ->
			if (item != null) {
				if (item.enchantments.filter { it.key.key.toString() == "minecraft:vanishing_curse" }.isNotEmpty()) return@forEach
				inventory.add(item)
			} else {
				inventory.add(null) // This is to make sure we keep the same item slot index when we restore items later
			}
		}
		val xp: Int = SpigotCompatUtils.calculateTotalExperiencePoints(player.level)

		// TIME
		val deathTime = System.currentTimeMillis()
		val expireTime = deathTime + ((ConfigManager.timeStable + ConfigManager.timeUnstable) * 1000)

		// SPAWN & DEFINE ENTITY
		val marker: Entity = player.world.spawnEntity(findSafeLocation(player.location), EntityType.MARKER)
		marker.isPersistent = true
		marker.isSilent = true
		marker.isInvulnerable = true

		// CREATE SOUL & STORE DATA IN MARKER
		val soul = Soul.createNewForPlayerDeath(
			player.uniqueId,
			marker,
			findSafeLocation(player.location),
			inventory,
			xp,
			deathTime,
			expireTime)

		// CANCEL DROPS
		e.drops.clear()
		e.droppedExp = 0

		// CALL EVENT
		val soulSpawnEvent = SoulSpawnEvent(player, soul)
		Bukkit.getPluginManager().callEvent(soulSpawnEvent)

		// EXPLODE OLD SOUL
		if (ConfigManager.maxSoulsPerPlayer > 0) explodeOldestSoul(player)
	}

	private fun findSafeLocation(locationToCheck: Location): Location {
		val safeLocation: Location = locationToCheck
		var block: Block = safeLocation.block

		// CHECK IF ABOVE THE MAX HEIGHT!!!
		val environment = locationToCheck.world!!.environment
		while (block.type.isSolid || block.isLiquid || block.type == Material.VOID_AIR) {
			// NETHER
			if (environment == World.Environment.NETHER) {
				if (locationToCheck.y < 128 && safeLocation.y >= 127) {
					return safeLocation
				} else if (locationToCheck.y >= 128 && safeLocation.y >= 254) {
					return safeLocation
				}
			}

			// NORMAL
			if (safeLocation.y >= 319) return safeLocation
			safeLocation.add(0.0, 1.0, 0.0)
			block = safeLocation.block
		}

		return safeLocation
	}

	// EXPLODE OLD SOUL
	private fun explodeOldestSoul(player: Player) {
		Bukkit.getScheduler().runTaskAsynchronously(SoulGraves.plugin, Runnable {
			when(STORAGE_MODE) {
				StorageType.PDC -> {
					SoulGraveAPI.getPlayerSouls(player.uniqueId)
						.takeIf { it.size > ConfigManager.maxSoulsPerPlayer }
						?.let { souls ->
							val sorted = souls.sortedBy { it.expireTime }
							val toRemove = souls.size - ConfigManager.maxSoulsPerPlayer
							if (toRemove > 0) {
								sorted.take(toRemove).forEach { soul ->
									Bukkit.getScheduler().runTask(SoulGraves.plugin, Runnable {
										soul.explode()
									})
								}
								// Send Message
								MessageManager.soulLimitExplodeComponent?.let {
									SoulGraves.plugin.adventure().player(player).sendMessage(it)
								}
							}
						}
				}

				StorageType.CROSS_SERVER -> {
					val future = SoulGraveAPI.getPlayerSoulsCrossServer(player.uniqueId)
					// Database has been sorted.
					future.thenAccept {
						if (it.size > ConfigManager.maxSoulsPerPlayer) {
							val toRemove = it.size - ConfigManager.maxSoulsPerPlayer
							if (toRemove > 0) {
								it.take(toRemove).forEach { soul ->
									RedisPublishAPI.explodeSoul(soul.markerUUID)
								}
								// Send Message
								MessageManager.soulLimitExplodeComponent?.let {
									SoulGraves.plugin.adventure().player(player).sendMessage(it)
								}
							}
						}
					}
				}
			}
		})
	}
}