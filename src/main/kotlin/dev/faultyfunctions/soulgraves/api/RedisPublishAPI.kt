package dev.faultyfunctions.soulgraves.api

import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.database.MessageAction
import dev.faultyfunctions.soulgraves.database.MySQLDatabase
import dev.faultyfunctions.soulgraves.database.RedisDatabase
import dev.faultyfunctions.soulgraves.database.RedisPacket
import dev.faultyfunctions.soulgraves.managers.DatabaseManager
import org.bukkit.Bukkit
import java.util.UUID


/**
 * In this API, ALL Action Will Cross Server.
 * If Target Server Offline, It Will Mark To Mysql, Next Start Will Handle These Souls.
 */
object RedisPublishAPI {

    /**
     * Remove a Soul
     */
    fun deleteSoul(markerUUID: UUID) {
        Bukkit.getScheduler().runTaskAsynchronously(SoulGraves.plugin, Runnable {
            MySQLDatabase.instance.markSoulDelete(markerUUID)
            RedisDatabase.instance.publish(RedisPacket(DatabaseManager.serverName, MessageAction.REMOVE_SOUL, markerUUID.toString()))
        })
    }


    /**
     * Make a Soul Explode
     */
    fun explodeSoul(markerUUID: UUID) {
        Bukkit.getScheduler().runTaskAsynchronously(SoulGraves.plugin, Runnable {
            MySQLDatabase.instance.markSoulExplode(markerUUID)
            RedisDatabase.instance.publish(RedisPacket(DatabaseManager.serverName, MessageAction.EXPLODE_SOUL, markerUUID.toString()))
        })
    }

}