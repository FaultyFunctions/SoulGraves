package dev.faultyfunctions.soulgraves.tasks

import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.utils.Soul
import dev.faultyfunctions.soulgraves.utils.SoulState
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.scheduler.BukkitRunnable

class SoulSoundTask(val soul: Soul) : BukkitRunnable() {
	override fun run() {
		if (!soul.location.world?.isChunkLoaded(soul.location.chunk)!!) { return }

		if (soul.state == SoulState.NORMAL) {
			soul.location.world?.playSound(soul.location, Sound.ENTITY_WARDEN_HEARTBEAT, 3.0f, 1.0f)
		}

		if (soul.state == SoulState.PANIC) {
			soul.location.world?.playSound(soul.location, Sound.ENTITY_WARDEN_HEARTBEAT, 3.0f, 0.5f)
			soul.location.world?.playSound(soul.location, Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, 8.0f, 0.5f)
			soul.location.world?.spawnParticle(Particle.END_ROD, soul.location.clone().add(0.0, 1.0, 0.0), 75, 0.0, 0.0, 0.0, 0.1, null, true)
		}
	}
}