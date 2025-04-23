package dev.faultyfunctions.soulgraves.commands

import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.api.SoulGraveAPI
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player

class BackCommand : CommandExecutor, TabExecutor {
    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String>? {
        val completionList: MutableList<String> = mutableListOf()

        if (args.isEmpty()) {
            completionList.add("back")
            return completionList
        }

        return null
    }

    val miniMessage = MiniMessage.miniMessage()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            SoulGraves.plugin.adventure().sender(sender).sendMessage(miniMessage.deserialize("<red>✖ Only living souls may use this command!"))
            return true
        }

        // GET THE NEWEST SOUL FOR THE PLAYER
        val soul = SoulGraveAPI.getPlayerSouls(sender.uniqueId).maxByOrNull { it.deathTime }

        if (soul == null) {
            SoulGraves.plugin.adventure().sender(sender).sendMessage(miniMessage.deserialize("<dark_aqua>☠ No lingering soul could be found..."))
            return true
        }

        // CHECK IF THE SOUL STILL VALID
        if (!soul.isValid(false)) {
            SoulGraves.plugin.adventure().sender(sender).sendMessage(miniMessage.deserialize("<red>✖ The soul you seek has faded into the void!"))
            return true
        }

        // TELEPORTING PLAYER
        sender.teleport(soul.location)

        // THERE NO TELEPORT ASYNC SO I CHECK IT MANUALLY
        if (sender.location == soul.location) SoulGraves.plugin.adventure().sender(sender).sendMessage(miniMessage.deserialize("<green>✦ You've been drawn back to your soul's echo."))

        return true
    }
}