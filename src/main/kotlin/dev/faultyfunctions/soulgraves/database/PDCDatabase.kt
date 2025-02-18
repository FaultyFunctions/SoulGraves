package dev.faultyfunctions.soulgraves.database

import com.jeff_media.morepersistentdatatypes.DataType
import dev.faultyfunctions.soulgraves.*
import dev.faultyfunctions.soulgraves.SoulGraves.Companion.soulList
import dev.faultyfunctions.soulgraves.utils.Soul
import dev.faultyfunctions.soulgraves.utils.SpigotCompatUtils
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.entity.Marker
import org.bukkit.inventory.ItemStack

class PDCDatabase {

    companion object {
        val instance by lazy { PDCDatabase().apply { initSouls() } }
    }

    private fun initSouls() {
        for (world in Bukkit.getWorlds()) {
            if (!world.persistentDataContainer.has(soulChunksKey)) {
                world.persistentDataContainer.set(soulChunksKey, DataType.asList(DataType.LONG), mutableListOf<Long>())
            } else {
                // GET CHUNK KEY LIST AND MAKE SURE THEY ARE UNIQUE
                val chunkKeyList: List<Long>? = world.persistentDataContainer.get(
                    soulChunksKey, DataType.asList(
                        DataType.LONG))?.distinct()
                // RESET CHUNK LIST IN WORLD PDC
                world.persistentDataContainer.set(soulChunksKey, DataType.asList(DataType.LONG), mutableListOf<Long>())

                if (chunkKeyList != null) {
                    for (chunkKey in chunkKeyList) {
                        val chunk: Chunk = SpigotCompatUtils.getChunkAt(chunkKey, world)
                        for (entity in chunk.entities) {
                            if (entity.persistentDataContainer.has(soulKey)) {
                                val markerEntity = entity as Marker
                                val player = markerEntity.persistentDataContainer.get(soulOwnerKey, DataType.UUID)!!
                                val inventory: MutableList<ItemStack?> = (markerEntity.persistentDataContainer.get(
                                    soulInvKey, DataType.ITEM_STACK_ARRAY)!!).toMutableList()
                                val xp: Int = markerEntity.persistentDataContainer.get(soulXpKey, DataType.INTEGER)!!
                                val timeLeft: Int = markerEntity.persistentDataContainer.get(soulTimeLeftKey, DataType.INTEGER)!!
                                val soul = Soul(player, markerEntity.uniqueId, markerEntity.location, inventory, xp, timeLeft)
                                soulList.add(soul)
                            }
                        }
                    }
                }
            }
        }
    }
}