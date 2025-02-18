package dev.faultyfunctions.soulgraves.commands
import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.api.RedisPublishAPI
import dev.faultyfunctions.soulgraves.api.SoulGraveAPI
import dev.faultyfunctions.soulgraves.database.MySQLDatabase
import dev.faultyfunctions.soulgraves.managers.ConfigManager
import dev.faultyfunctions.soulgraves.managers.DatabaseManager
import dev.faultyfunctions.soulgraves.managers.MessageManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

class ReloadCommand: CommandExecutor, TabExecutor {
	override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String>? {
		val completionList: MutableList<String> = mutableListOf()

		if (args.size == 1) {
			completionList.add("reload")
			return completionList
		}

		return null
	}

	override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
		if (args.isEmpty()) {
			Bukkit.dispatchCommand(sender, "version SoulGraves")
		}

		if (args.size == 1) {
			if (args[0].equals("reload", ignoreCase = true)) {
				ConfigManager.loadConfig()
				MessageManager.loadMessages()
				if (sender is Player) {
					sender.sendMessage("[SoulGraves] Config reloaded!")
				}

				SoulGraves.plugin.logger.info("Config reloaded!")
			}
		}

		return true
	}
}