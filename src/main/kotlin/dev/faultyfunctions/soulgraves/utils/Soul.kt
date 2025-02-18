package dev.faultyfunctions.soulgraves.utils

import com.jeff_media.morepersistentdatatypes.DataType
import dev.faultyfunctions.soulgraves.*
import dev.faultyfunctions.soulgraves.api.RedisPublishAPI
import dev.faultyfunctions.soulgraves.database.MySQLDatabase
import dev.faultyfunctions.soulgraves.managers.ConfigManager
import dev.faultyfunctions.soulgraves.managers.DatabaseManager
import dev.faultyfunctions.soulgraves.managers.STORE_MODE
import dev.faultyfunctions.soulgraves.tasks.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Marker
import org.bukkit.inventory.ItemStack
import java.util.UUID

enum class SoulState {
	NORMAL, PANIC, EXPLODING
}

class Soul(
	val ownerUUID: UUID,
	var markerUUID: UUID?,
	val location: Location,
	var inventory:MutableList<ItemStack?>,
	var xp: Int,
	var timeLeft: Int,
	val serverId: String = DatabaseManager.serverName,
	val expireTime: Long = System.currentTimeMillis() + ((ConfigManager.timeStable + ConfigManager.timeUnstable) * 1000)
) {
	var state: Enum<SoulState> = SoulState.NORMAL
	var implosion: Boolean = false

	private val explodeTask: SoulExplodeTask = SoulExplodeTask(this)
	private val particleTask: SoulParticleTask = SoulParticleTask(this)
	private val pickupTask: SoulPickupTask = SoulPickupTask(this)
	private val renderTask: SoulRenderTask = SoulRenderTask(this)
	private val soundTask: SoulSoundTask = SoulSoundTask(this)
	private val stateTask: SoulStateTask = SoulStateTask(this)

	private var isStarted: Boolean = false

	/**
	 * Start Soul Tasks.
	 */
	fun startTasks() {
		if (serverId != DatabaseManager.serverName) return
		if (isStarted) return

		markerUUID ?: this.delete() // IF SOUL DO NOT HAVE UUID MEAN WORLD IS NOT EXIST, DATA WILL REMOVE
		explodeTask.runTaskTimer(SoulGraves.plugin, 0, 20)
		particleTask.runTaskTimer(SoulGraves.plugin, 0, 50)
		pickupTask.runTaskTimer(SoulGraves.plugin, 0, 4)
		renderTask.runTaskTimer(SoulGraves.plugin, 0, 1)
		soundTask.runTaskTimer(SoulGraves.plugin, 0, 50)
		stateTask.runTaskTimer(SoulGraves.plugin, 0, 20)
		isStarted = true
	}


	/**
	 * Spawn a Marker Entity upon Soul Created. If Entity of MarkerUUID is Exist, Marker Will DO NOT Spawn.
	 */
	 fun spawnMarker(): Entity? {
		if (serverId != DatabaseManager.serverName) return null
		if (location.world == null) return null
		location.chunk.load()
		markerUUID?.let { Bukkit.getEntity(it)?.let { return null } }

		location.world?.let {
			val marker = it.spawnEntity(location, EntityType.MARKER) as Marker
			marker.isPersistent = true
			marker.isSilent = true
			marker.isInvulnerable = true
			// STORE DATA
			marker.persistentDataContainer.set(soulKey, DataType.BOOLEAN, true)
			marker.persistentDataContainer.set(soulOwnerKey, DataType.UUID, ownerUUID)
			marker.persistentDataContainer.set(soulInvKey, DataType.ITEM_STACK_ARRAY, inventory.toTypedArray())
			marker.persistentDataContainer.set(soulXpKey, DataType.INTEGER, xp)
			marker.persistentDataContainer.set(soulTimeLeftKey, DataType.INTEGER, timeLeft)
			markerUUID = marker.uniqueId
			return marker
		}
		return null
	}


	/**
	 * Make Soul Explode Now, Will Drop Exp And Items.
	 */
	fun explodeNow() {
		if (serverId == DatabaseManager.serverName) {
			this.state = SoulState.EXPLODING
			this.implosion = true
		} else {
			markerUUID?.let { RedisPublishAPI.explodeSoul(it) }
		}
	}


	/**
	 * Delete Soul, Stop All Task of Soul, Will Drop Nothing.
	 */
	fun delete() {
		if (serverId == DatabaseManager.serverName) {
			explodeTask.cancel()
			particleTask.cancel()
			pickupTask.cancel()
			renderTask.cancel()
			soundTask.cancel()
			stateTask.cancel()

			SoulGraves.soulList.remove(this)
			when (DatabaseManager.storeMode) {
				// PDC - REMOVE CHUNK FROM LOAD LIST IF POSSIBLE
				STORE_MODE.PDC -> {
					var removeChunk = true
					for (entityInChunk in location.chunk.entities) {
						if (entityInChunk.persistentDataContainer.has(soulKey) && markerUUID != entityInChunk.uniqueId) {
							removeChunk = false
							break
						}
					}
					if (removeChunk) {
						val chunkList: MutableList<Long>? = location.world?.persistentDataContainer?.get(soulChunksKey, DataType.asList(DataType.LONG))
						if (chunkList != null) {
							chunkList.remove(SpigotCompatUtils.getChunkKey(location.chunk))
							location.world?.persistentDataContainer?.set(soulChunksKey, DataType.asList(DataType.LONG), chunkList)
						}
					}
				}
				// MYSQL
				STORE_MODE.DATABASE -> {
					Bukkit.getScheduler().runTaskAsynchronously(SoulGraves.plugin, Runnable { MySQLDatabase.instance.deleteSoul(this) })
				}
			}

			markerUUID?.let { uuid ->
				(Bukkit.getEntity(uuid) as? Marker)?.remove()
			}
		// IF NOT THIS SERVER, IT WILL PUB TO CHANNEL.
		} else {
			markerUUID?.let { RedisPublishAPI.deleteSoul(it) }
		}
	}


	/**
	 * Save to Database
	 */
	fun saveData(marker: Entity?) {
		if (serverId == DatabaseManager.serverName) {
			SoulGraves.soulList.add(this)
			when (DatabaseManager.storeMode) {
				// PDC
				STORE_MODE.PDC -> {
					marker?.let {
						val chunkList: MutableList<Long>? = marker.world.persistentDataContainer.get(soulChunksKey, DataType.asList(DataType.LONG))
						if (chunkList != null && !chunkList.contains(SpigotCompatUtils.getChunkKey(marker.location.chunk))) {
							chunkList.add(SpigotCompatUtils.getChunkKey(marker.location.chunk))
							marker.world.persistentDataContainer.set(soulChunksKey, DataType.asList(DataType.LONG), chunkList)
						} }
				}
				// MYSQL + REDIS
				STORE_MODE.DATABASE -> {
					marker?.let {
						Bukkit.getScheduler().runTaskAsynchronously(SoulGraves.plugin, Runnable {
						MySQLDatabase.instance.saveSoul(this, DatabaseManager.serverName)
					}) }
				}
			}
		}
	}

}