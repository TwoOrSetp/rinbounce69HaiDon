/*
 * RinBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/rattermc/rinbounce69
 */
package net.ccbluex.liquidbounce.script

import net.ccbluex.liquidbounce.file.FileManager.dir
import net.ccbluex.liquidbounce.script.remapper.Remapper
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import java.io.File
import java.io.FileFilter

private val scripts = mutableListOf<Script>()

object ScriptManager : List<Script> by scripts {

    val scriptsFolder = File(dir, "scripts")

    private val SCRIPT_FILE_FILTER = FileFilter {
        it.extension.lowercase() == "js"
    }

    /**
     * Only includes files in the root directory ([scriptsFolder])
     */
    val availableScriptFiles: Array<File>
        get() = scriptsFolder.listFiles(SCRIPT_FILE_FILTER) ?: emptyArray()

    /**
     * Loads all scripts inside the scripts folder.
     */
    fun loadScripts() {
        if (!scriptsFolder.exists())
            scriptsFolder.mkdir()

        availableScriptFiles.forEach(::loadScript)
    }

    /**
     * Unloads all scripts.
     */
    fun unloadScripts() = scripts.clear()

    /**
     * Loads a script from a file.
     */
    fun loadScript(scriptFile: File) {
        try {
            if (!Remapper.mappingsLoaded) {
                error("The mappings were not loaded, re-start and check your internet connection.")
            }

            val script = Script(scriptFile)
            script.initScript()
            scripts += script
        } catch (t: Throwable) {
            LOGGER.error("[ScriptAPI] Failed to load script '${scriptFile.name}'.", t)
        }
    }

    /**
     * Enables all scripts.
     */
    fun enableScripts() = scripts.forEach { it.onEnable() }

    /**
     * Disables all scripts.
     */
    fun disableScripts() = scripts.forEach { it.onDisable() }

    /**
     * Imports a script.
     * @param file JavaScript file to be imported.
     */
    fun importScript(file: File) {
        val scriptFile = File(scriptsFolder, file.name)
        file.copyTo(scriptFile)

        loadScript(scriptFile)
        LOGGER.info("[ScriptAPI] Successfully imported script '${scriptFile.name}'.")
    }

    /**
     * Deletes a script.
     * @param script Script to be deleted.
     */
    fun deleteScript(script: Script) {
        script.onDisable()
        scripts.remove(script)
        script.scriptFile.delete()

        LOGGER.info("[ScriptAPI] Successfully deleted script '${script.scriptFile.name}'.")
    }

    /**
     * Reloads all scripts.
     */
    fun reloadScripts() {
        disableScripts()
        unloadScripts()
        loadScripts()
        enableScripts()

        LOGGER.info("[ScriptAPI] Successfully reloaded scripts.")
    }
}