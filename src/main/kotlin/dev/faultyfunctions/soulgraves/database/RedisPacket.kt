package dev.faultyfunctions.soulgraves.database

import com.google.gson.Gson


enum class MessageAction {
    REMOVE_SOUL,
    EXPLODE_SOUL,
    NOTIFY_SOUL_EXPLODE,
    NOTIFY_SOUL_OTHER_PICKUP
}


/**
 * Represents a data packet structure for Redis communication.
 *
 * @property senderId Unique identifier of the sender
 * @property payload Actual content payload of the message
 * @property timestamp Automatic timestamp of packet creation in milliseconds
 */
data class RedisPacket(
    val senderId: String,
    var action: MessageAction,
    var payload: String,
) {
    val timestamp: Long = System.currentTimeMillis()

    companion object {
        private val gson = Gson()  // Reusable Gson instance

        /**
         * Deserializes a JSON string to a RedisPacket object.
         * @param json Valid JSON string representation of the packet
         * @return Deserialized RedisPacket instance
         */
        fun fromJson(json: String): RedisPacket {
            return gson.fromJson(json, RedisPacket::class.java)
        }
    }

    /**
     * Serializes the packet to a JSON string.
     * @return JSON string representation of the packet
     */
    fun toJson(): String {
        return gson.toJson(this)
    }
}
