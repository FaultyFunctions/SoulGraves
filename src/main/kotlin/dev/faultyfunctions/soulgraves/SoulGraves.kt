package dev.faultyfunctions.soulgraves

import dev.faultyfunctions.soulgraves.commands.ReloadCommand
import dev.faultyfunctions.soulgraves.managers.ConfigManager
import dev.faultyfunctions.soulgraves.managers.MessageManager
import dev.faultyfunctions.soulgraves.database.MySQLDatabase
import dev.faultyfunctions.soulgraves.database.PDCDatabase
import dev.faultyfunctions.soulgraves.database.RedisDatabase
import dev.faultyfunctions.soulgraves.listeners.PlayerDeathListener
import dev.faultyfunctions.soulgraves.managers.DatabaseManager
import dev.faultyfunctions.soulgraves.managers.STORE_MODE
import dev.faultyfunctions.soulgraves.utils.Soul
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bstats.bukkit.Metrics
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

val soulChunksKey = NamespacedKey(SoulGraves.plugin, "soul-chunks")
val soulKey = NamespacedKey(SoulGraves.plugin, "soul")
val soulOwnerKey = NamespacedKey(SoulGraves.plugin, "soul-owner")
val soulInvKey = NamespacedKey(SoulGraves.plugin, "soul-inv")
val soulXpKey = NamespacedKey(SoulGraves.plugin, "soul-xp")
val soulTimeLeftKey = NamespacedKey(SoulGraves.plugin, "soul-time-left")

class SoulGraves : JavaPlugin() {
	companion object {
		lateinit var plugin: SoulGraves
		var soulList: MutableList<Soul> = mutableListOf()
	}

	private lateinit var adventure: BukkitAudiences
	fun adventure(): BukkitAudiences {
		return this.adventure
	}

	override fun onEnable() {
		plugin = this
		plugin.adventure = BukkitAudiences.create(plugin)

		// LOAD CONFIG
		ConfigManager.loadConfig()
		MessageManager.loadMessages()
		DatabaseManager.loadConfig()

		// INIT SOULS
		when (DatabaseManager.storeMode) {
			// PDC
			STORE_MODE.PDC  -> {
				PDCDatabase.instance
			}
			// MYSQL + REDIS
			STORE_MODE.DATABASE -> {
				MySQLDatabase.instance
				RedisDatabase.instance
				soulList = MySQLDatabase.instance.getCurrentServerSouls()
			}
			// OTHER NOT VALID MODE
			else -> {
				logger.severe("ERROR STORE MODE! PLUGIN WILL DISABLED!")
				server.pluginManager.disablePlugin(this)
			}
		}
		for (soul in soulList) soul.startTasks()

		// LISTENERS
		server.pluginManager.registerEvents(PlayerDeathListener(), this)

		// COMMANDS
		getCommand("soulgraves")?.setExecutor(ReloadCommand())
		getCommand("soulgraves")?.tabCompleter = ReloadCommand()

		// SET UP BSTATS
		val pluginId = 23436
		val metrics = Metrics(this, pluginId)

		logger.info("Enabled!")
	}

	override fun onDisable() {
		this.adventure.close()
		RedisDatabase.instance.shutdown()
		logger.info("Disabled!")
	}
}
