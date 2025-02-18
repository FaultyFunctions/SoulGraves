package dev.faultyfunctions.soulgraves.managers

import dev.dejvokep.boostedyaml.YamlDocument
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings
import dev.faultyfunctions.soulgraves.SoulGraves
import org.bukkit.Bukkit
import java.io.File
import java.io.IOException
import kotlin.properties.Delegates

enum class STORE_MODE {
    PDC, DATABASE
}

object DatabaseManager {

    lateinit var databaseConfig: YamlDocument

    // CONFIG VALUES
    var storeMode by Delegates.notNull<STORE_MODE>()
    var serverName by Delegates.notNull<String>()

    fun loadConfig() {
        try {
            databaseConfig = YamlDocument.create(
                File(SoulGraves.plugin.dataFolder, "database.yml"),
                SoulGraves.plugin.getResource("database.yml")!!,
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(true).build(),
                DumperSettings.DEFAULT,
                UpdaterSettings.builder().setVersioning(BasicVersioning("file-version")).setOptionSorting(
                    UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS).build()
            )
            databaseConfig.update()
            databaseConfig.save()
        } catch (e: IOException) {
            SoulGraves.plugin.logger.severe("Failed to load database.yml! The plugin will now shut down.")
            Bukkit.getServer().pluginManager.disablePlugin(SoulGraves.plugin)
        }

        // LOAD VALUES
        storeMode = STORE_MODE.valueOf(databaseConfig.getString("storage-mode", "PDC"))
        serverName = databaseConfig.getString("server-name")
    }

}

