package dev.faultyfunctions.soulgraves.compatibilities

import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.flags.StateFlag
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry
import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.api.event.SoulPreSpawnEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener


class WorldGuardHook : Listener {

    var registry: FlagRegistry = WorldGuard.getInstance().flagRegistry
    var flagRegisteredSuccess = false

    companion object {
        val instance: WorldGuardHook by lazy { WorldGuardHook() }
        var soulGravesSpawningFlag: StateFlag? = null
    }

    fun registerFlags() {
        try {
            val flag = StateFlag("soulgraves-spawning", true)
            registry.register(flag)
            soulGravesSpawningFlag = flag
            flagRegisteredSuccess = true
        } catch (e: FlagConflictException) {
            val existing = registry.get("my-custom-flag")
            if (existing is StateFlag) {
                soulGravesSpawningFlag = existing as StateFlag
                flagRegisteredSuccess = true
            } else {
                flagRegisteredSuccess = false
                return
            }
        }
    }

    fun registerEvents() {
        if (!flagRegisteredSuccess) {
            SoulGraves.plugin.logger.warning("WorldGuard has registered the soulgraves-spawning flag!")
            return
        }
        SoulGraves.plugin.server.pluginManager.registerEvents(WorldGuardHook(), SoulGraves.plugin)
        SoulGraves.plugin.logger.info("[√] WorldGuard Hooked!")
    }


    /**
     * If current region flag "soulgraves-spawning" state is deny, cancel soul spawn.
     */
    @EventHandler
    fun onSoulPreSpawn(event: SoulPreSpawnEvent) {
        val localPlayer = WorldGuardPlugin.inst().wrapPlayer(event.player)
        val container = WorldGuard.getInstance().platform.regionContainer
        val query = container.createQuery()
        val regions = query.getApplicableRegions(localPlayer.location)

        val value = regions.queryValue(localPlayer, soulGravesSpawningFlag)
        if (value == StateFlag.State.DENY) {
            event.isCancelled = true
        }
    }

}