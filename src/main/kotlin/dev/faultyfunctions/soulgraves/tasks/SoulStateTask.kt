package dev.faultyfunctions.soulgraves.tasks

import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.managers.ConfigManager
import dev.faultyfunctions.soulgraves.soulTimeLeftKey
import dev.faultyfunctions.soulgraves.utils.SoulState
import com.jeff_media.morepersistentdatatypes.DataType
import dev.faultyfunctions.soulgraves.utils.Soul
import org.bukkit.Bukkit
import org.bukkit.entity.Marker
import org.bukkit.scheduler.BukkitRunnable

class SoulStateTask(val soul: Soul) : BukkitRunnable() {
	override fun run() {
		if (ConfigManager.offlineOwnerTimerFreeze && Bukkit.getPlayer(soul.ownerUUID) == null) { return }

		// SET STATE , IF STATE IS EXPLODING MEAN MAYBE SOMEONE MAKE SOUL INSTANTLY EXPLOSION BY API
		if (soul.state != SoulState.EXPLODING) {
			if (soul.timeLeft > ConfigManager.timeUnstable)
				soul.state = SoulState.NORMAL
			else if (soul.timeLeft <= ConfigManager.timeUnstable && soul.timeLeft > 0)
				soul.state = SoulState.PANIC
			else
				soul.state = SoulState.EXPLODING
		}

		soul.timeLeft -= 1

		// LOAD CHUNK & GRAB ENTITY
		soul.location.world?.loadChunk(soul.location.chunk)
		val soulEntity: Marker = Bukkit.getEntity(soul.markerUUID!!) as Marker

		// STORE TIME LEFT IN ENTITY'S PDC
		soulEntity.persistentDataContainer.set(soulTimeLeftKey, DataType.INTEGER, soul.timeLeft)

		// UNLOAD CHUNK
		soul.location.world?.unloadChunk(soul.location.chunk)
	}
}