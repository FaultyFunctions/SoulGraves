package dev.faultyfunctions.soulgraves.tasks

import org.bukkit.scheduler.BukkitRunnable
import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.managers.ConfigManager
import dev.faultyfunctions.soulgraves.managers.MessageManager
import dev.faultyfunctions.soulgraves.utils.SoulState
import dev.faultyfunctions.soulgraves.api.event.SoulPickupEvent
import dev.faultyfunctions.soulgraves.database.MessageAction
import dev.faultyfunctions.soulgraves.database.RedisDatabase
import dev.faultyfunctions.soulgraves.database.RedisPacket
import dev.faultyfunctions.soulgraves.managers.DatabaseManager
import dev.faultyfunctions.soulgraves.managers.STORE_MODE
import dev.faultyfunctions.soulgraves.utils.Soul
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.entity.Marker
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class SoulPickupTask(val soul: Soul) : BukkitRunnable() {
	override fun run() {
		if (!soul.location.world?.isChunkLoaded(soul.location.chunk)!! || soul.state == SoulState.EXPLODING) { return }

		val marker: Marker = Bukkit.getEntity(soul.markerUUID!!) as Marker

		for (entity in marker.getNearbyEntities(0.5, 0.5, 0.5)) {
			if (entity !is Player) { continue }

			val player: Player = entity
			if (!player.isDead && player.gameMode != GameMode.SPECTATOR) {
				// CHECK IF PLAYER NEEDS TO BE OWNER
				if (ConfigManager.ownerLocked && (player.uniqueId != soul.ownerUUID)) { continue }

				// CALL EVENT
				val soulPickupEvent = SoulPickupEvent(player, soul)
				Bukkit.getPluginManager().callEvent(soulPickupEvent)
				if (soulPickupEvent.isCancelled) { continue }

				// HANDLE INVENTORY
				val missedItems: MutableList<ItemStack> = mutableListOf()
				soul.inventory.forEachIndexed { index, item ->
					if (item != null) {
						if (player.inventory.getItem(index) == null) {
							player.inventory.setItem(index, item)
						} else {
							missedItems.add(item)
						}
					}
				}
				val missHashMap = player.inventory.addItem(*missedItems.toTypedArray())
				missHashMap.forEach { (_, item) ->
					soul.location.world!!.dropItem(soul.location, item)
				}

				// HANDLE XP
				val owner: Player? = Bukkit.getPlayer(soul.ownerUUID)
				val xpMultiplier = if (player.uniqueId == owner?.uniqueId) ConfigManager.xpPercentageOwner else ConfigManager.xpPercentageOthers
				player.giveExp((soul.xp * xpMultiplier).toInt())

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
					when (DatabaseManager.storeMode) {
						STORE_MODE.PDC  -> {
							owner?.let {
								if (MessageManager.soulBurstComponent != null)
									SoulGraves.plugin.adventure().player(owner).sendMessage(MessageManager.soulBurstComponent!!)
								if (ConfigManager.soulsDropItems && MessageManager.soulBurstDropItemsComponent != null)
									SoulGraves.plugin.adventure().player(owner).sendMessage(MessageManager.soulBurstDropItemsComponent!!)
								else if (MessageManager.soulBurstLoseItemsComponent != null)
									SoulGraves.plugin.adventure().player(owner).sendMessage(MessageManager.soulBurstLoseItemsComponent!!)
							}
						}
						STORE_MODE.DATABASE -> {
							RedisDatabase.instance.publish(RedisPacket(DatabaseManager.serverName, MessageAction.NOTIFY_SOUL_OTHER_PICKUP, soul.ownerUUID.toString()))
						}
					}
				}

				// REMOVE SOUL
				soul.delete()
			}
		}
	}
}