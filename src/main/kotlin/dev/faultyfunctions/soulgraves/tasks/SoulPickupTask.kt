package dev.faultyfunctions.soulgraves.tasks

import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.api.event.SoulPickupEvent
import dev.faultyfunctions.soulgraves.database.MessageAction
import dev.faultyfunctions.soulgraves.database.RedisDatabase
import dev.faultyfunctions.soulgraves.database.RedisPacket
import dev.faultyfunctions.soulgraves.managers.*
import dev.faultyfunctions.soulgraves.utils.Soul
import dev.faultyfunctions.soulgraves.utils.SoulState
import dev.faultyfunctions.soulgraves.utils.SpigotCompatUtils
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable

class SoulPickupTask(val soul: Soul) : BukkitRunnable() {
	override fun run() {
		if (soul.state == SoulState.EXPLODING) return
		if (soul.location.world?.isChunkLoaded(soul.location.chunk) != true) return

		soul.location.world?.getNearbyEntities(soul.location, 0.5,0.5,0.5)
			?.takeIf { Bukkit.getEntity(soul.markerUUID) != null }
			?.filterIsInstance<Player>()
			?.filterNot { it.isDead }
			?.filterNot { it.gameMode == GameMode.SPECTATOR }
			// CHECK IF PLAYER NEEDS TO BE OWNER
			?.filter { !ConfigManager.ownerLocked || (it.uniqueId == soul.ownerUUID) }
			?.forEach { player ->
				// CALL EVENT
				val soulPickupEvent = SoulPickupEvent(player, soul)
				Bukkit.getPluginManager().callEvent(soulPickupEvent)
				if (soulPickupEvent.isCancelled) { return@forEach }

				// HANDLE INVENTORY
				val missedItems: MutableList<ItemStack> = mutableListOf()
				soul.inventory.forEachIndexed { index, item ->
					if (item != null) {
						if (player.inventory.getItem(index) == null) {
							player.inventory.setItem(index, item)
						} else { // if there is already an item in the slot, add to missed items list
							missedItems.add(item)
						}
					}
				}
				val missHashMap = player.inventory.addItem(*missedItems.toTypedArray()) // try to place items into player's inventory if they can fit
				missHashMap.forEach { (_, item) ->
					soul.location.world!!.dropItem(soul.location, item) // drop items on ground that couldn't fit
				}

				// HANDLE XP
				val owner: Player? = Bukkit.getPlayer(soul.ownerUUID)
				val xpMultiplier = if (player.uniqueId == owner?.uniqueId) ConfigManager.xpPercentageOwner else ConfigManager.xpPercentageOthers
				if (SpigotCompatUtils.calculateTotalExperiencePoints(player.level) != soul.xp) {
					player.giveExp((soul.xp * xpMultiplier).toInt())
				}

				// PLAY SOUNDS
				if (ConfigManager.pickupSound.enabled) {
					ConfigManager.pickupSound.sounds.forEachIndexed { index, soundKey ->
						player.playSound(player.location, soundKey, ConfigManager.pickupSound.volumes[index], ConfigManager.pickupSound.pitches[index])
					}
				}

				// SPAWN PARTICLES
				player.world.spawnParticle(Particle.END_ROD, soul.location, 200, 1.0, 1.0, 1.0, 0.5)
				player.world.spawnParticle(Particle.FIREWORK, soul.location, 50, 1.0, 1.0, 1.0, 0.1)

				// SEND MESSAGE TO PLAYER
				if (MessageManager.soulCollectComponent != null) SoulGraves.plugin.adventure().player(player).sendMessage(MessageManager.soulCollectComponent!!)

				// SEND MESSAGE TO OWNER IF NEEDED
				if (player.uniqueId != soul.ownerUUID && ConfigManager.notifyOwnerPickup) {
					when (DatabaseManager.storageMode) {
						StorageType.PDC  -> {
							owner?.let {
								if (MessageManager.soulBurstComponent != null)
									SoulGraves.plugin.adventure().player(owner).sendMessage(MessageManager.soulBurstComponent!!)
								if (ConfigManager.soulsDropItems && MessageManager.soulBurstDropItemsComponent != null)
									SoulGraves.plugin.adventure().player(owner).sendMessage(MessageManager.soulBurstDropItemsComponent!!)
								else if (MessageManager.soulBurstLoseItemsComponent != null)
									SoulGraves.plugin.adventure().player(owner).sendMessage(MessageManager.soulBurstLoseItemsComponent!!)
							}
						}
						StorageType.CROSS_SERVER -> {
							RedisDatabase.instance.publish(RedisPacket(SERVER_NAME, MessageAction.NOTIFY_SOUL_OTHER_PICKUP, soul.ownerUUID.toString()))
						}
					}
				}

				// REMOVE SOUL
				soul.delete()
			}
	}
}