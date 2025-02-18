package dev.faultyfunctions.soulgraves.managers

import dev.dejvokep.boostedyaml.YamlDocument
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings
import dev.faultyfunctions.soulgraves.SoulGraves
import org.bukkit.Bukkit
import org.bukkit.Particle
import java.io.File
import java.io.IOException
import kotlin.properties.Delegates

class SoundConfig() {
	var enabled by Delegates.notNull<Boolean>()
	val sounds = mutableListOf<String>()
	val volumes = mutableListOf<Float>()
	val pitches = mutableListOf<Float>()
}

object ConfigManager {
	private lateinit var config: YamlDocument

	// CONFIG VALUES
	var permissionRequired by Delegates.notNull<Boolean>()
	var timeStable by Delegates.notNull<Int>()
	var timeUnstable by Delegates.notNull<Int>()
	var offlineOwnerTimerFreeze by Delegates.notNull<Boolean>()
	var notifyNearbyPlayers by Delegates.notNull<Boolean>()
	var notifyRadius by Delegates.notNull<Int>()
	var notifyOwnerPickup by Delegates.notNull<Boolean>()
	var xpPercentageOwner by Delegates.notNull<Double>()
	var xpPercentageOthers by Delegates.notNull<Double>()
	var xpPercentageBurst by Delegates.notNull<Double>()
	var ownerLocked by Delegates.notNull<Boolean>()
	var soulsDropItems by Delegates.notNull<Boolean>()
	var soulsDropXP by Delegates.notNull<Boolean>()
	val pickupSound = SoundConfig()
	val burstSound = SoundConfig()
	val notifyNearbySound = SoundConfig()
	val notifyOwnerBurstSound = SoundConfig()
	val notifyOwnerPickupSound = SoundConfig()
	lateinit var disabledWorlds: List<String>
	// PARTICLES CONFIG
	var enableParticles by Delegates.notNull<Boolean>()
	var particlesFollowRadius by Delegates.notNull<Double>()
	var particleType by Delegates.notNull<Particle>()
	var particlesInitDistance by Delegates.notNull<Double>()
	var particleSpeed by Delegates.notNull<Double>()
	var particleSpeedBound by Delegates.notNull<Double>()
	var particleMaxAmount by Delegates.notNull<Int>()
	var particleOffsetBound by Delegates.notNull<Double>()


	fun loadConfig() {
		try {
			config = YamlDocument.create(File(SoulGraves.plugin.dataFolder, "config.yml"),
				SoulGraves.plugin.getResource("config.yml")!!,
				GeneralSettings.DEFAULT,
				LoaderSettings.builder().setAutoUpdate(true).build(),
				DumperSettings.DEFAULT,
				UpdaterSettings.builder().setVersioning(BasicVersioning("file-version")).setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS).build()
			)

			config.update()
			config.save()
		} catch (e: IOException) {
			SoulGraves.plugin.logger.severe("Failed to load config.yml! The plugin will now shut down.")
			Bukkit.getServer().pluginManager.disablePlugin(SoulGraves.plugin)
		}

		// LOAD VALUES
		permissionRequired = config.getBoolean("permission-required")
		timeStable = config.getInt("time-stable")
		timeUnstable = config.getInt("time-unstable")
		offlineOwnerTimerFreeze = config.getBoolean("offline-owner-timer-freeze")
		notifyNearbyPlayers = config.getBoolean("notify-nearby-players")
		notifyRadius = config.getInt("notify-radius")
		notifyOwnerPickup = config.getBoolean("notify-owner-pickup")
		xpPercentageOwner = config.getDouble("xp-percentage-owner")
		xpPercentageOthers = config.getDouble("xp-percentage-others")
		xpPercentageBurst = config.getDouble("xp-percentage-burst")
		ownerLocked = config.getBoolean("owner-locked")
		soulsDropItems = config.getBoolean("souls-drop-items")
		soulsDropXP = config.getBoolean("souls-drop-xp")
		pickupSound.enabled = config.getBoolean("pickup-sound.enabled")
		val pickupSounds = config.getStringList("pickup-sound.sounds")
		for (sound in pickupSounds) {
			val split = sound.split(",")
			pickupSound.sounds.add(split[0])
			pickupSound.volumes.add(split[1].toFloat())
			pickupSound.pitches.add(split[2].toFloat())
		}
		burstSound.enabled = config.getBoolean("burst-sound.enabled")
		val burstSounds = config.getStringList("burst-sound.sounds")
		for (sound in burstSounds) {
			val split = sound.split(",")
			burstSound.sounds.add(split[0])
			burstSound.volumes.add(split[1].toFloat())
			burstSound.pitches.add(split[2].toFloat())
		}
		notifyNearbySound.enabled = config.getBoolean("notify-nearby-sound.enabled")
		val notifyNearbySounds = config.getStringList("notify-nearby-sound.sounds")
		for (sound in notifyNearbySounds) {
			val split = sound.split(",")
			notifyNearbySound.sounds.add(split[0])
			notifyNearbySound.volumes.add(split[1].toFloat())
			notifyNearbySound.pitches.add(split[2].toFloat())
		}
		notifyOwnerBurstSound.enabled = config.getBoolean("notify-owner-burst-sound.enabled")
		val notifyOwnerBurstSounds = config.getStringList("notify-owner-burst-sound.sounds")
		for (sound in notifyOwnerBurstSounds) {
			val split = sound.split(",")
			notifyOwnerBurstSound.sounds.add(split[0])
			notifyOwnerBurstSound.volumes.add(split[1].toFloat())
			notifyOwnerBurstSound.pitches.add(split[2].toFloat())
		}
		notifyOwnerPickupSound.enabled = config.getBoolean("notify-owner-pickup-sound.enabled")
		val notifyOwnerPickupSounds = config.getStringList("notify-owner-pickup-sound.sounds")
		for (sound in notifyOwnerPickupSounds) {
			val split = sound.split(",")
			notifyOwnerPickupSound.sounds.add(split[0])
			notifyOwnerPickupSound.volumes.add(split[1].toFloat())
			notifyOwnerPickupSound.pitches.add(split[2].toFloat())
		}
		disabledWorlds = config.getStringList("disabled-worlds")
		// PARTICLES CONFIG
		enableParticles = config.getBoolean("particles.enabled", true)
		particlesFollowRadius = config.getDouble("particles.follow-radius", 50.0)
		particleType = Particle.valueOf(config.getString("particles.particle.type", "soul_fire_flame")!!)
		particlesInitDistance = config.getDouble("particles.particle.init-distance", 2.5)
		particleSpeed = config.getDouble("particles.particle.speed", 0.005)
		particleSpeedBound = config.getDouble("particles.particle.speed-bound", 0.005)
		particleMaxAmount  = config.getInt("particles.particle.max-amount", 5)
		particleOffsetBound = config.getDouble("particles.particle.offset-bound", 1.5)
	}
}