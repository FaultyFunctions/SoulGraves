package dev.faultyfunctions.soulgraves.tasks

import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.managers.ConfigManager
import dev.faultyfunctions.soulgraves.utils.Soul
import org.bukkit.GameMode
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

class SoulParticleTask(private val soul: Soul) : BukkitRunnable() {

    // Pre-compiled constants for better readability
    private companion object {
        const val VECTOR_MODE = 0
        const val Y_AXIS_IGNORE = 0.0
    }

    override fun run() {
        val world = soul.location.world ?: return
        val ownerUUID = soul.ownerUUID

        // Skip if chunk isn't loaded
        if (!world.isChunkLoaded(soul.location.chunk)) return

        // Find target player within follow radius
        val targetPlayer = world.getNearbyEntities(
            soul.location,
            ConfigManager.particlesFollowRadius,
            ConfigManager.particlesFollowRadius,
            ConfigManager.particlesFollowRadius
        )
            .filterIsInstance<Player>()
            .firstOrNull {
                it.uniqueId == ownerUUID &&
                it.gameMode != GameMode.SPECTATOR
            } ?: return

        // Validate particle spawning conditions
        if (!targetPlayer.isOnline ||
            targetPlayer.isDead ||
            targetPlayer.location.distance(soul.location) > ConfigManager.particlesFollowRadius) {
            return
        }

        // Calculate particle trajectory
        val eyeDirection = targetPlayer.eyeLocation.direction.apply { y = Y_AXIS_IGNORE }
        val spawnOrigin = targetPlayer.eyeLocation.add(eyeDirection.multiply(ConfigManager.particlesInitDistance))
        val directionVector = soul.location.toVector().subtract(spawnOrigin.toVector())

        // Generate randomized particles
        val random = Random()
        val particleCount = random.nextInt(1, ConfigManager.particleMaxAmount + 1)
        val speedVariation = ConfigManager.particleSpeedBound
        val baseSpeed = ConfigManager.particleSpeed

        repeat(particleCount) {
            val randomizedOffset = Triple(
                random.nextDouble(-ConfigManager.particleOffsetBound, ConfigManager.particleOffsetBound),
                random.nextDouble(-ConfigManager.particleOffsetBound, ConfigManager.particleOffsetBound),
                random.nextDouble(-ConfigManager.particleOffsetBound, ConfigManager.particleOffsetBound)
            )

            world.spawnParticle(
                ConfigManager.particleType,
                spawnOrigin.clone().add(randomizedOffset.first, randomizedOffset.second, randomizedOffset.third),
                VECTOR_MODE,  // Use vector direction mode
                directionVector.x,
                directionVector.y,
                directionVector.z,
                random.nextDouble(baseSpeed - speedVariation, baseSpeed + speedVariation),
                null,  // No extra data
                true  // Force show to players
            )
        }
    }
}

