package dev.faultyfunctions.soulgraves.database

import com.saicone.rtag.item.ItemTagStream
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.faultyfunctions.soulgraves.SoulGraves
import dev.faultyfunctions.soulgraves.managers.ConfigManager
import dev.faultyfunctions.soulgraves.managers.DatabaseManager
import dev.faultyfunctions.soulgraves.managers.SERVER_NAME
import dev.faultyfunctions.soulgraves.utils.Soul
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.*
import kotlin.collections.ArrayList

class MySQLDatabase private constructor() {

    // DATABASE VALUES
    private var dataSource: HikariDataSource
    private var jdbcUrl: String
    private var jdbcDriver: String
    private var username: String
    private var password: String
    private val databaseName: String = "soul_grave"

    init {
        val config = DatabaseManager.databaseConfig

        jdbcUrl = config.getString("MySQL.jdbc-url")!!
        jdbcDriver = config.getString("MySQL.jdbc-class")!!
        username = config.getString("MySQL.properties.user")!!
        password = config.getString("MySQL.properties.password")!!

        val hikariConfig = HikariConfig()
        hikariConfig.jdbcUrl = jdbcUrl
        hikariConfig.driverClassName = jdbcDriver
        hikariConfig.username = username
        hikariConfig.password = password
        dataSource = HikariDataSource(hikariConfig)
    }

    companion object {
        val instance: MySQLDatabase by lazy { MySQLDatabase().apply {
            createTable()
            initCurrentServerSouls()
            SoulGraves.plugin.logger.info("Connected to MySQL Database!")
        } }
    }

    // Create Table
    private fun createTable() {
        val connection = dataSource.connection
        val sql = "CREATE TABLE IF NOT EXISTS $databaseName (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "ownerUUID VARCHAR(255), " +
                "markerUUID VARCHAR(255), " +
                "serverName VARCHAR(255), " +
                "world VARCHAR(255), " +
                "x INT, " +
                "y INT, " +
                "z INT, " +
                "inventory TEXT, " +
                "xp INT, " +
                "deathTime BIGINT, " +
                "expireTime BIGINT, " +
                "isDeleted BIT(1) DEFAULT 0" +
                ")"
        val statement = connection.prepareStatement(sql)
        try {
            statement.executeUpdate(sql)
            println("Table '$databaseName' created successfully.")
        } catch (e: Exception) {
            e.printStackTrace()
            println("Error while creating table: ${e.message}")
        } finally {
            statement.close()
            connection.close()
        }
    }

    // Read Souls in Current Server
    private fun initCurrentServerSouls(serverName: String = SERVER_NAME) {
        val connection = dataSource.connection
        val sql = "SELECT * FROM $databaseName WHERE serverName = ?"
        val statement = connection.prepareStatement(sql)
        statement.setString(1, serverName)

        try {
            val resultSet = statement.executeQuery()
            while (resultSet.next()) {
                val soul = Soul(
                    ownerUUID = UUID.fromString(resultSet.getString("ownerUUID")),
                    markerUUID = UUID.fromString(resultSet.getString("markerUUID")),
                    location = Location(Bukkit.getWorld(resultSet.getString("world")), resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z")),
                    inventory = ItemTagStream.INSTANCE.listFromBase64(resultSet.getString("inventory")),
                    xp = resultSet.getInt("xp"),
                    timeLeft = ((resultSet.getLong("expireTime") - System.currentTimeMillis()) / 1000).toInt(),
                    serverId = resultSet.getString("serverName")
                )
                // IF FOUND DELETED TAG, WILL REMOVE.
                if (resultSet.getBoolean("isDeleted")) soul.delete()
                // IF SOUL EXPIRE
                if (soul.timeLeft <= 0) soul.explode()

                soul.startTasks()
                SoulGraves.soulList.add(soul)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement.close()
            connection.close()
        }
    }


    // Save Soul to Database
    fun saveSoul(soul: Soul) {
        val now = System.currentTimeMillis()
        val connection = dataSource.connection
        val sql = "INSERT INTO $databaseName (ownerUUID, markerUUID, serverName, world, x, y, z, inventory, xp, expireTime, isDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val statement = connection.prepareStatement(sql)

        statement.setString(1, soul.ownerUUID.toString())
        statement.setString(2, soul.markerUUID.toString())
        statement.setString(3, soul.serverId)
        statement.setString(4, soul.location.world?.name)
        statement.setInt(5, soul.location.x.toInt())
        statement.setInt(6, soul.location.y.toInt())
        statement.setInt(7, soul.location.z.toInt())
        statement.setString(8, ItemTagStream.INSTANCE.listToBase64(soul.inventory))
        statement.setInt(9, soul.xp)
        statement.setLong(10, (ConfigManager.timeStable + ConfigManager.timeUnstable) * 1000L + now)
        statement.setBoolean(11, false)

        try {
            statement.executeUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement.close()
            connection.close()
        }
    }


    // Read Souls in Current Server
    fun getAllSouls() : MutableList<Soul> {
        val souls = ArrayList<Soul>()

        val connection = dataSource.connection
        val sql = "SELECT * FROM $databaseName WHERE serverName != $SERVER_NAME"
        val statement = connection.prepareStatement(sql)

        try {
            val resultSet = statement.executeQuery()
            while (resultSet.next()) {
                // IF DELETED OR EXPIRE SOULS.
                if (resultSet.getBoolean("isDeleted")) continue
                if (resultSet.getLong("expireTime") <= System.currentTimeMillis()) continue
                val soul = Soul(
                    ownerUUID = UUID.fromString(resultSet.getString("ownerUUID")),
                    markerUUID = UUID.fromString(resultSet.getString("markerUUID")),
                    inventory = ItemTagStream.INSTANCE.listFromBase64(resultSet.getString("inventory")),
                    xp = resultSet.getInt("xp"),
                    location = Location(Bukkit.getWorld(resultSet.getString("world")), resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z")),
                    timeLeft = ((resultSet.getLong("expireTime") - System.currentTimeMillis()) / 1000).toInt(),
                    serverId = resultSet.getString("serverName"),
                    expireTime = resultSet.getLong("expireTime")
                )
                souls.add(soul)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement.close()
            connection.close()
        }
        souls.addAll(SoulGraves.soulList)
        return souls
    }


    // Read Souls From Database
    fun getPlayerSouls(playerUUID: UUID) : MutableList<Soul> {
        val souls = ArrayList<Soul>()

        // CURRENT SERVER SOULS
        val currentServerSouls = SoulGraves.soulList.stream().filter { it.ownerUUID == playerUUID }.toList()
        souls.addAll(currentServerSouls)

        val connection = dataSource.connection
        val sql = "SELECT * FROM $databaseName WHERE ownerUUID = ? AND serverName != $SERVER_NAME"
        val statement = connection.prepareStatement(sql)
        statement.setString(1, playerUUID.toString())

        try {
            val resultSet = statement.executeQuery()
            while (resultSet.next()) {
                // IF DELETED OR EXPIRE SOULS.
                if (resultSet.getBoolean("isDeleted")) continue
                if (resultSet.getLong("expireTime") <= System.currentTimeMillis()) continue
                val soul = Soul(
                    ownerUUID = UUID.fromString(resultSet.getString("ownerUUID")),
                    markerUUID = UUID.fromString(resultSet.getString("markerUUID")),
                    inventory = ItemTagStream.INSTANCE.listFromBase64(resultSet.getString("inventory")),
                    xp = resultSet.getInt("xp"),
                    location = Location(Bukkit.getWorld(resultSet.getString("world")), resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z")),
                    timeLeft = ((resultSet.getLong("expireTime") - System.currentTimeMillis()) / 1000).toInt(),
                    serverId = resultSet.getString("serverName"),
                    expireTime = resultSet.getLong("expireTime")
                )
                souls.add(soul)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement.close()
            connection.close()
        }
        return souls
    }


    // Read Soul From Database
    fun getSoul(markerUUID: UUID) : Soul? {
        // IF IN CURRENT SERVER SOULS
        val currentServerSouls = SoulGraves.soulList.stream().filter { it.markerUUID == markerUUID }.toList()
        if (currentServerSouls.isNotEmpty()) return currentServerSouls[0]

        val connection = dataSource.connection
        val sql = "SELECT * FROM $databaseName WHERE markerUUID = ?"
        val statement = connection.prepareStatement(sql)
        statement.setString(1, markerUUID.toString())

        try {
            val resultSet = statement.executeQuery()
            while (resultSet.next()) {
                // IF DELETED OR EXPIRE SOULS.
                if (resultSet.getBoolean("isDeleted")) continue
                if (resultSet.getLong("expireTime") <= System.currentTimeMillis()) continue

                return Soul(
                    ownerUUID = UUID.fromString(resultSet.getString("ownerUUID")),
                    markerUUID = UUID.fromString(resultSet.getString("markerUUID")),
                    inventory = ItemTagStream.INSTANCE.listFromBase64(resultSet.getString("inventory")),
                    xp = resultSet.getInt("xp"),
                    location = Location(Bukkit.getWorld(resultSet.getString("world")), resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z")),
                    timeLeft = ((resultSet.getLong("expireTime") - System.currentTimeMillis()) / 1000).toInt(),
                    serverId = resultSet.getString("serverName"),
                    expireTime = resultSet.getLong("expireTime")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement.close()
            connection.close()
        }
        return null
    }


    // Mark a Soul Deleted
    fun markSoulDelete(makerUUID: UUID) {
        val connection = dataSource.connection
        val sql = "UPDATE $databaseName SET isDeleted = 1 WHERE markerUUID = ?"
        val statement = connection.prepareStatement(sql)
        statement.setString(1, makerUUID.toString())

        try {
            statement.executeUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement.close()
            connection.close()
        }
    }


    // Mark a Soul Explode
    fun markSoulExplode(makerUUID: UUID) {
        val connection = dataSource.connection
        val sql = "UPDATE $databaseName SET expireTime = 1 WHERE markerUUID = ?"
        val statement = connection.prepareStatement(sql)
        statement.setString(1, makerUUID.toString())

        try {
            statement.executeUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement.close()
            connection.close()
        }
    }


    // Delete Soul From Database
    fun deleteSoul(soul: Soul) {
        val uuid = soul.markerUUID.toString()
        val connection = dataSource.connection
        SoulGraves.plugin.logger.warning("delete maker $uuid")
        val sql = "DELETE FROM $databaseName WHERE markerUUID = ?"
        val statement = connection.prepareStatement(sql)
        statement.setString(1, uuid)

        try {
            statement.executeUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement.close()
            connection.close()
        }
    }

}