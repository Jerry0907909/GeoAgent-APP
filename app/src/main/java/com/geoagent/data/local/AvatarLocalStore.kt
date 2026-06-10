package com.geoagent.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Copies picked gallery images into app storage so Coil can load them reliably. */
class AvatarLocalStore(private val context: Context) {

    suspend fun persistFromPickerUri(uriString: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = Uri.parse(uriString)
            val dir = File(context.filesDir, "avatar").apply { mkdirs() }
            val target = File(dir, "profile.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            target.absolutePath
        }.getOrNull()
    }

    fun pathToModel(path: String): String = if (path.startsWith("/")) "file://$path" else path
}
