package com.onezcodes.ochords.services

import android.content.Context
import com.onezcodes.ochords.model.Setlist
import com.onezcodes.ochords.model.Song
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * A guest's songs and setlists, kept on this phone (app-private files) since there's no account to sync to.
 * When the guest signs up or logs in, their songs move to the account and this is cleared.
 */
class GuestStore(context: Context) {
    private val directory = File(context.filesDir, "Guest")

    val library: List<Song> get() = load("library.json", ListSerializer(Song.serializer())) ?: emptyList()
    val setlists: List<Setlist> get() = load("setlists.json", ListSerializer(Setlist.serializer())) ?: emptyList()

    fun saveLibrary(library: List<Song>) = store(library, ListSerializer(Song.serializer()), "library.json")
    fun saveSetlists(setlists: List<Setlist>) = store(setlists, ListSerializer(Setlist.serializer()), "setlists.json")
    fun clear() { directory.deleteRecursively() }

    private fun <T> load(name: String, serializer: KSerializer<T>): T? {
        val f = File(directory, name)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(serializer, f.readText()) }.getOrNull()
    }

    private fun <T> store(value: T, serializer: KSerializer<T>, name: String) {
        directory.mkdirs()
        val tmp = File(directory, "$name.tmp")
        tmp.writeText(json.encodeToString(serializer, value))
        tmp.renameTo(File(directory, name))
    }
}
