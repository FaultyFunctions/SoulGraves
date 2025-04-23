package dev.faultyfunctions.soulgraves

import dev.faultyfunctions.soulgraves.commands.BackCommand
import dev.faultyfunctions.soulgraves.commands.ReloadCommand
import dev.faultyfunctions.soulgraves.compatibilities.ExcellentEnchantsHook
import dev.faultyfunctions.soulgraves.compatibilities.VaneEnchantmentsHook
import dev.faultyfunctions.soulgraves.compatibilities.WorldGuardHook
import dev.faultyfunctions.soulgraves.database.MySQLDatabase
import dev.faultyfunctions.soulgraves.database.PDCDatabase
import dev.faultyfunctions.soulgraves.database.RedisDatabase
import dev.faultyfunctions.soulgraves.listeners.PlayerConnectionEvent
import dev.faultyfunctions.soulgraves.listeners.PlayerDeathListener
import dev.faultyfunctions.soulgraves.managers.*
import dev.faultyfunctions.soulgraves.utils.Soul
import dev.faultyfunctions.soulgraves.utils.SpigotCompatUtils
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bstats.bukkit.Metrics
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

	override fun onLoad() {
		// Compatibilities
		if (SpigotCompatUtils.isPluginLoaded("WorldGuard")) {
			WorldGuardHook.instance.registerFlags()
		}
	}

	override fun onEnable() {
		plugin = this
		plugin.adventure = BukkitAudiences.create(plugin)

		// LOAD CONFIG
		ConfigManager.loadConfig()
		MessageManager.loadMessages()
		DatabaseManager.loadConfig()

		// INIT SOULS
		when (STORAGE_MODE) {
			// PDC
			StorageType.PDC  -> {
				PDCDatabase.instance
			}
			// MYSQL + REDIS
			StorageType.CROSS_SERVER -> {
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
		server.pluginManager.registerEvents(PlayerConnectionEvent(), this)

		// Compatibilities
		if (SpigotCompatUtils.isPluginLoaded("WorldGuard")) {
			WorldGuardHook.instance.registerEvents()
		}

		if (SpigotCompatUtils.isPluginLoaded("vane-enchantments")) {
			VaneEnchantmentsHook.instance.registerEvents()
		}

		if (SpigotCompatUtils.isPluginLoaded("ExcellentEnchants")) {
			ExcellentEnchantsHook.instance.registerEvents()
		}

		// COMMANDS
		getCommand("soulgraves")?.setExecutor(ReloadCommand())
		getCommand("soulgraves")?.tabCompleter = ReloadCommand()
		getCommand("back")?.setExecutor(BackCommand())
		getCommand("back")?.tabCompleter = BackCommand()

		// SET UP BSTATS
		val pluginId = 23436
		Metrics(this, pluginId)

		logger.info("Enabled!")
	}

	override fun onDisable() {
		this.adventure.close()
		if (STORAGE_MODE == StorageType.CROSS_SERVER) { RedisDatabase.instance.shutdown() }
		logger.info("Disabled!")
	}
}
