package dev.faultyfunctions.soulgraves.listeners

import dev.faultyfunctions.soulgraves.managers.ConfigManager
import dev.faultyfunctions.soulgraves.utils.Soul
import dev.faultyfunctions.soulgraves.*
import dev.faultyfunctions.soulgraves.api.RedisPublishAPI
import dev.faultyfunctions.soulgraves.api.SoulGravesAPI
import dev.faultyfunctions.soulgraves.api.event.SoulPreSpawnEvent
import dev.faultyfunctions.soulgraves.api.event.SoulSpawnEvent
import dev.faultyfunctions.soulgraves.managers.MessageManager
import dev.faultyfunctions.soulgraves.managers.STORAGE_MODE
import dev.faultyfunctions.soulgraves.managers.StorageType
import dev.faultyfunctions.soulgraves.utils.SpigotCompatUtils
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import kotlin.collections.forEach

class PlayerDeathListener : Listener {
	val soulPreSpawnEventMap = HashMap<UUID, SoulPreSpawnEvent>()

	@EventHandler(priority = EventPriority.LOWEST)
	fun onLowestPlayerDeathEvent(e: PlayerDeathEvent) {
		// CALL PRE-SPAWN EVENT
		val soulPreSpawnEvent = SoulPreSpawnEvent(e)
		Bukkit.getPluginManager().callEvent(soulPreSpawnEvent)
		if (soulPreSpawnEvent.isNotCancelled) {
			soulPreSpawnEventMap[e.entity.uniqueId] = soulPreSpawnEvent
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	fun onHighestPlayerDeathEvent(e: PlayerDeathEvent) {
		// CHECK IF PRE-SPAWN EVENT EXISTS
		if (soulPreSpawnEventMap[e.entity.uniqueId] == null) { return }

		// Remove only drops that match the inventory contents so we don't delete items from other plugins
		if (ConfigManager.soulsStoreItems) {
			SoulGraves.plugin.logger.info("CALL e.drops.remove(item)")
			for (item in e.entity.inventory.contents) {
				if (item != null) {
					e.drops.remove(item)
				}
			}
		}
		if (ConfigManager.soulsStoreXP) {
			e.droppedExp = 0
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	fun onMonitorPlayerDeathEvent(e: PlayerDeathEvent) {
		val player: Player = e.entity
		val soulPreSpawnEvent = soulPreSpawnEventMap.remove(player.uniqueId) ?: return

		// CHECK PLAYER HAS PERMISSION
		if (ConfigManager.permissionRequired && !player.hasPermission("soulgraves.spawn")) return

		// CHECK TO MAKE SURE WE ARE IN AN ENABLED WORLD
		if (ConfigManager.disabledWorlds.contains(player.world.name)) return

		// CHECK KEEP INVENTORY
		var storeInventory = true
		val isInventoryEmpty = player.inventory.contents.all { it == null || it.type == Material.AIR }
		val keepInventoryGameRule = player.world.getGameRuleValue(GameRule.KEEP_INVENTORY) ?: false
		if (!ConfigManager.soulsStoreItems || soulPreSpawnEvent.keepInventory || e.keepInventory || keepInventoryGameRule || isInventoryEmpty) {
			storeInventory = false
		}

		// CHECK KEEP XP
		var storeXP = true
		if (!ConfigManager.soulsStoreXP || soulPreSpawnEvent.keepLevel || e.keepLevel || player.level == 0) {
			storeXP = false
		}

		// CHECK TO MAKE SURE WE NEED TO SPAWN A SOUL
		if (!storeInventory && !storeXP) return

		// PROCESS DATA
		val inventoryCopy = player.inventory.contents.map { deepCopyItemStack(it) }
		val deathInfo = DeathInfo(player, inventoryCopy, player.level, player.location)

		// SPAWN SOUL NEXT TICK (TO AVOID ISSUES WITH OTHER PLUGINS)
		spawnSoulNextTick(deathInfo, storeInventory, storeXP)
	}

	private fun spawnSoulNextTick(deathInfo: DeathInfo, storeInventory: Boolean, storeXP: Boolean) {
		Bukkit.getScheduler().runTask(SoulGraves.plugin, Runnable {
			val safeLocation: Location = findSafeLocation(deathInfo.location.clone())
			val isDuplicateSoul = SoulGraves.soulList.any { soul ->
				soul.ownerUUID == deathInfo.player.uniqueId &&
				soul.location.world == safeLocation.world &&
				soul.location.distanceSquared(safeLocation) < 0.01 &&
				(System.currentTimeMillis() - soul.deathTime) < 10000
			}

			if (isDuplicateSoul) {
				return@Runnable
			}

			val soulInventory: MutableList<ItemStack?> = mutableListOf()

			if (storeInventory) {
				deathInfo.inventoryContents.forEach { item ->
					if (item != null) {
						if (item.enchantments.filter { it.key.key.toString() == "minecraft:vanishing_curse" }.isNotEmpty()) return@forEach
						soulInventory.add(item)
					} else {
						soulInventory.add(null) // This is to make sure we keep the same item slot index when we restore items later
					}
				}
			}

			var xp = 0
			if (storeXP) {
				xp = SpigotCompatUtils.calculateTotalExperiencePoints(deathInfo.level)
			}

			// TIME
			val deathTime = System.currentTimeMillis()
			val expireTime = deathTime + ((ConfigManager.timeStable + ConfigManager.timeUnstable) * 1000)

			// SPAWN & DEFINE ENTITY
			val marker: Entity = deathInfo.location.world?.spawnEntity(safeLocation, EntityType.MARKER) ?: return@Runnable
			marker.isPersistent = true
			marker.isSilent = true
			marker.isInvulnerable = true

			// CREATE SOUL & STORE DATA IN MARKER
			val soul = Soul.createNewForPlayerDeath(
				deathInfo.player.uniqueId,
				marker,
				safeLocation,
				soulInventory,
				xp,
				deathTime,
				expireTime
			)

			// CALL EVENT
			val soulSpawnEvent = SoulSpawnEvent(deathInfo.player, soul)
			Bukkit.getPluginManager().callEvent(soulSpawnEvent)

			// EXPLODE OLD SOUL
			if (ConfigManager.maxSoulsPerPlayer > 0) explodeOldestSoul(deathInfo.player)
		})
	}

	private fun deepCopyItemStack(item: ItemStack?): ItemStack? {
		if (item == null || item.type == Material.AIR) return null
		return ItemStack.deserialize(item.serialize())
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
					SoulGravesAPI.getPlayerSouls(player.uniqueId)
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
					val future = SoulGravesAPI.getPlayerSoulsCrossServer(player.uniqueId)
					// Database has been sorted.
					future.thenAccept { soulList ->
						if (soulList.size > ConfigManager.maxSoulsPerPlayer) {
							val toRemove = soulList.size - ConfigManager.maxSoulsPerPlayer
							if (toRemove > 0) {
								soulList.take(toRemove).forEach { soul ->
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

private data class DeathInfo (
	val player: Player,
	val inventoryContents: List<ItemStack?>,
	val level: Int,
	val location: Location
)