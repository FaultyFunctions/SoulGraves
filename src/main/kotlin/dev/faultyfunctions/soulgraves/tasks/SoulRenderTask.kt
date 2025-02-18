package dev.faultyfunctions.soulgraves.tasks

import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.utils.Soul
import dev.faultyfunctions.soulgraves.utils.SoulState
import org.bukkit.Particle
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.sin

class SoulRenderTask(val soul: Soul) : BukkitRunnable() {
	override fun run() {
		if (!soul.location.world?.isChunkLoaded(soul.location.chunk)!!) { return }

		val endRodOffsetY = sin(System.currentTimeMillis().toDouble() * 0.001) * 0.5

		when (soul.state) {
			SoulState.NORMAL -> {
				soul.location.world?.spawnParticle(Particle.END_ROD, soul.location.clone().add(0.0, 1.0 + endRodOffsetY, 0.0), 5, 0.01, 0.01, 0.01, 0.01, null, true)
				soul.location.world?.spawnParticle(Particle.SCULK_SOUL, soul.location.clone().add(0.0, 1.0, 0.0), 1, 0.3, 0.5, 0.3, 0.01, null, true)
				soul.location.world?.spawnParticle(Particle.ELECTRIC_SPARK, soul.location.clone().add(0.0, 1.0, 0.0), 1, 0.8, 0.5, 0.8, 0.0, null, true)
			}
			SoulState.PANIC -> {
				if (soul.timeLeft > 1) {
					soul.location.world?.spawnParticle(Particle.SCULK_SOUL, soul.location.clone().add(0.0, 1.0, 0.0), soul.timeLeft / 4, 0.01, 0.01, 0.01, 0.1, null, true)
					soul.location.world?.spawnParticle(Particle.END_ROD, soul.location.clone().add(0.0, 1.0, 0.0), 1, 0.01, 0.01, 0.01, 0.01, null, true)
				}
			}
			SoulState.EXPLODING -> {
				if (!soul.implosion) {
					soul.implosion = true
					object : BukkitRunnable() {
						override fun run() {
							soul.location.world?.spawnParticle(Particle.SONIC_BOOM, soul.location.clone().add(0.0, 1.0, 0.0), 1, 0.0, 0.0, 0.0, 0.0, null, true)
							this.cancel()
						}
					}.runTaskLater(SoulGraves.plugin, 8)
				}
			}
		}
	}
}