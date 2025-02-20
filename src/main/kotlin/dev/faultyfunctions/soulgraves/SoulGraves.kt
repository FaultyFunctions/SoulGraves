package dev.faultyfunctions.soulgraves

import dev.faultyfunctions.soulgraves.commands.ReloadCommand
import dev.faultyfunctions.soulgraves.managers.ConfigManager
import dev.faultyfunctions.soulgraves.managers.MessageManager
import dev.faultyfunctions.soulgraves.database.MySQLDatabase
import dev.faultyfunctions.soulgraves.database.PDCDatabase
import dev.faultyfunctions.soulgraves.database.RedisDatabase
import dev.faultyfunctions.soulgraves.listeners.PlayerDeathListener
import dev.faultyfunctions.soulgraves.managers.DatabaseManager
import dev.faultyfunctions.soulgraves.managers.STORAGE_TYPE
import dev.faultyfunctions.soulgraves.utils.Soul
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bstats.bukkit.Metrics
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.CopyOnWriteArrayList

class SoulGraves : JavaPlugin() {
	companion object {
		lateinit var plugin: SoulGraves
		var soulList = CopyOnWriteArrayList<Soul>()
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
		when (DatabaseManager.storageMode) {
			// PDC
			STORAGE_TYPE.PDC  -> {
				PDCDatabase.instance
			}
			// MYSQL + REDIS
			STORAGE_TYPE.DATABASE -> {
				MySQLDatabase.instance
				RedisDatabase.instance
			}
			// OTHER NOT VALID MODE
			else -> {
				logger.severe("ERROR STORE MODE! PLUGIN WILL DISABLED!")
				server.pluginManager.disablePlugin(this)
			}
		}

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
