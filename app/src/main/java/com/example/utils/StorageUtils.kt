package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.Category
import com.example.data.Note
import com.example.data.QuickNote
import com.example.data.ShiftHandover
import com.example.data.ShiftStation
import com.example.data.ShiftTask
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class ImportedZipData(
    val categories: List<Category>,
    val notes: List<Note>,
    val quickNotes: List<QuickNote> = emptyList(),
    val shiftStations: List<ShiftStation> = emptyList(),
    val shiftTasks: List<ShiftTask> = emptyList(),
    val shiftHandovers: List<ShiftHandover> = emptyList()
)

object StorageUtils {

    /**
     * Copies any Uri (image, pdf, txt, etc.) to private internal storage and returns the local path.
     */
    fun copyUriToInternalStorage(context: Context, uri: Uri, prefix: String = "img", extension: String = "jpg"): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val FileDir = context.filesDir
            val destFile = File(FileDir, "${prefix}_${System.currentTimeMillis()}.$extension")
            destFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Serializes all categories and notes to a standard JSON backup string
     */
    fun exportDataToJson(categories: List<Category>, notes: List<Note>): String {
        val root = JSONObject()
        val categoriesObj = JSONObject()

        // Organize notes inside they category keys
        val notesGrouped = notes.groupBy { it.categoryName }
        for (category in categories) {
            val notesArray = JSONArray()
            notesGrouped[category.name]?.forEach { note ->
                val noteJson = JSONObject().apply {
                    put("id", note.id)
                    put("text", note.text)
                    put("pinned", note.pinned)
                    put("createdAt", note.createdAt)
                    put("updatedAt", note.updatedAt)
                    put("reminderTime", note.reminderTime ?: JSONObject.NULL)
                    put("isLocked", note.isLocked)
                    put("passwordHash", note.passwordHash ?: JSONObject.NULL)
                    put("voicePath", note.voicePath ?: JSONObject.NULL)
                    put("audioDurationMs", note.audioDurationMs)

                    // Convert lists to JSON arrays
                    put("images", JSONArray(note.images))
                    put("tags", JSONArray(note.tags))
                }
                notesArray.put(noteJson)
            }
            categoriesObj.put(category.name, notesArray)
        }
        root.put("categories", categoriesObj)
        return root.toString(2)
    }

    /**
     * Parses a JSON backup string into a list of parsed Categories and Notes.
     */
    fun importDataFromJson(jsonString: String): Pair<List<Category>, List<Note>> {
        val categories = mutableListOf<Category>()
        val notes = mutableListOf<Note>()

        val root = JSONObject(jsonString)
        if (root.has("categories")) {
            val categoriesObj = root.optJSONObject("categories") ?: JSONObject()
            val keys = categoriesObj.keys()
            while (keys.hasNext()) {
                val categoryName = keys.next()
                categories.add(Category(name = categoryName))

                val notesArray = categoriesObj.optJSONArray(categoryName) ?: JSONArray()
                for (i in 0 until notesArray.length()) {
                    val noteJson = notesArray.optJSONObject(i) ?: continue
                    
                    val imagesList = mutableListOf<String>()
                    val imagesArray = noteJson.optJSONArray("images")
                    if (imagesArray != null) {
                        for (k in 0 until imagesArray.length()) {
                            imagesList.add(imagesArray.optString(k))
                        }
                    }

                    val tagsList = mutableListOf<String>()
                    val tagsArray = noteJson.optJSONArray("tags")
                    if (tagsArray != null) {
                        for (k in 0 until tagsArray.length()) {
                            tagsList.add(tagsArray.optString(k))
                        }
                    }

                    val note = Note(
                        id = noteJson.optString("id", java.util.UUID.randomUUID().toString()),
                        categoryName = categoryName,
                        text = noteJson.optString("text", ""),
                        images = imagesList,
                        tags = tagsList,
                        pinned = noteJson.optBoolean("pinned", false),
                        createdAt = noteJson.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = noteJson.optLong("updatedAt", System.currentTimeMillis()),
                        reminderTime = if (noteJson.isNull("reminderTime")) null else noteJson.optLong("reminderTime"),
                        isLocked = noteJson.optBoolean("isLocked", false),
                        passwordHash = if (noteJson.isNull("passwordHash")) null else noteJson.optString("passwordHash"),
                        voicePath = if (noteJson.isNull("voicePath")) null else noteJson.optString("voicePath"),
                        audioDurationMs = noteJson.optLong("audioDurationMs", 0L)
                    )
                    notes.add(note)
                }
            }
        }
        return Pair(categories, notes)
    }

    /**
     * Exports all notes, categories, shift stations, tasks, handovers, image attachments, and voice recordings into a portable ZIP file.
     */
    fun exportDataToZip(
        context: Context,
        categories: List<Category>,
        notes: List<Note>,
        quickNotes: List<QuickNote> = emptyList(),
        shiftStations: List<ShiftStation> = emptyList(),
        shiftTasks: List<ShiftTask> = emptyList(),
        shiftHandovers: List<ShiftHandover> = emptyList()
    ): File? {
        return try {
            val cacheDir = context.cacheDir
            val zipFile = File(cacheDir, "MultiCategoryNotes_Backup.zip")
            if (zipFile.exists()) {
                zipFile.delete()
            }
            
            val zipOut = java.util.zip.ZipOutputStream(java.io.FileOutputStream(zipFile))
            
            // 1. Prepare JSON structure with relative paths
            val root = JSONObject()
            val categoriesObj = JSONObject()
            val notesGrouped = notes.groupBy { it.categoryName }
            
            val filesToPack = mutableListOf<String>()
            
            for (category in categories) {
                val notesArray = JSONArray()
                notesGrouped[category.name]?.forEach { note ->
                    val noteJson = JSONObject().apply {
                        put("id", note.id)
                        put("text", note.text)
                        put("pinned", note.pinned)
                        put("createdAt", note.createdAt)
                        put("updatedAt", note.updatedAt)
                        put("reminderTime", note.reminderTime ?: JSONObject.NULL)
                        put("isLocked", note.isLocked)
                        put("passwordHash", note.passwordHash ?: JSONObject.NULL)
                        
                        val zipImagesList = mutableListOf<String>()
                        note.images.forEach { imgPath ->
                            val file = File(imgPath)
                            if (file.exists()) {
                                zipImagesList.add(file.name)
                                filesToPack.add(imgPath)
                            }
                        }
                        put("images", JSONArray(zipImagesList))
                        put("tags", JSONArray(note.tags))
                        
                        if (note.voicePath != null) {
                            val vFile = File(note.voicePath)
                            if (vFile.exists()) {
                                put("voicePath", vFile.name)
                                filesToPack.add(note.voicePath)
                            } else {
                                put("voicePath", JSONObject.NULL)
                            }
                        } else {
                            put("voicePath", JSONObject.NULL)
                        }
                        put("audioDurationMs", note.audioDurationMs)
                    }
                    notesArray.put(noteJson)
                }
                categoriesObj.put(category.name, notesArray)
            }
            root.put("categories", categoriesObj)

            // Pack Quick Notes
            val quickNotesArray = JSONArray()
            quickNotes.forEach { qn ->
                val qnJson = JSONObject().apply {
                    put("id", qn.id)
                    put("text", qn.text)
                    put("pinned", qn.pinned)
                    put("createdAt", qn.createdAt)
                    put("updatedAt", qn.updatedAt)
                    put("hasNotification", qn.hasNotification)
                    put("reminderTime", qn.reminderTime ?: JSONObject.NULL)
                    put("isNotificationTriggered", qn.isNotificationTriggered)
                }
                quickNotesArray.put(qnJson)
            }
            root.put("quickNotes", quickNotesArray)

            // Pack Shift Management Data
            val stationsArray = JSONArray()
            shiftStations.forEach { station ->
                val sJson = JSONObject().apply {
                    put("id", station.id)
                    put("name", station.name)
                    put("icon", station.icon)
                    put("description", station.description)
                    put("qrCode", station.qrCode)
                    put("isActive", station.isActive)
                }
                stationsArray.put(sJson)
            }
            root.put("shiftStations", stationsArray)

            val tasksArray = JSONArray()
            shiftTasks.forEach { task ->
                val tJson = JSONObject().apply {
                    put("id", task.id)
                    put("stationId", task.stationId)
                    put("timeScheduled", task.timeScheduled)
                    put("title", task.title)
                    put("instruction", task.instruction)
                    put("priority", task.priority)
                    put("requiresConfirmation", task.requiresConfirmation)
                    put("isCompleted", task.isCompleted)
                    put("completedAt", task.completedAt ?: JSONObject.NULL)
                    put("hasLoudAlarm", task.hasLoudAlarm)
                    put("isHidden", task.isHidden)
                    put("actionType", task.actionType)
                    put("targetPackageName", task.targetPackageName)
                    put("targetAppName", task.targetAppName)
                }
                tasksArray.put(tJson)
            }
            root.put("shiftTasks", tasksArray)

            val handoversArray = JSONArray()
            shiftHandovers.forEach { handover ->
                val hJson = JSONObject().apply {
                    put("id", handover.id)
                    put("stationId", handover.stationId)
                    put("stationName", handover.stationName)
                    put("shiftTitle", handover.shiftTitle)
                    put("handoverContent", handover.handoverContent)
                    put("senderName", handover.senderName)
                    put("timestamp", handover.timestamp)
                }
                handoversArray.put(hJson)
            }
            root.put("shiftHandovers", handoversArray)
            
            // 2. Write backup.json entry
            val backupJsonBytes = root.toString(2).toByteArray(Charsets.UTF_8)
            val jsonEntry = java.util.zip.ZipEntry("backup.json")
            zipOut.putNextEntry(jsonEntry)
            zipOut.write(backupJsonBytes)
            zipOut.closeEntry()
            
            // 3. Write individual file entries
            filesToPack.distinct().forEach { parentPath ->
                val file = File(parentPath)
                if (file.exists()) {
                    val fileEntry = java.util.zip.ZipEntry(file.name)
                    zipOut.putNextEntry(fileEntry)
                    java.io.FileInputStream(file).use { fInput ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (fInput.read(buffer).also { bytesRead = it } != -1) {
                            zipOut.write(buffer, 0, bytesRead)
                        }
                    }
                    zipOut.closeEntry()
                }
            }
            
            zipOut.close()
            zipFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Imports data from a portably packed backup ZIP file, restoring both text metadata, shift management data, and binary audio/image files.
     */
    fun importDataFromZip(context: Context, zipUri: Uri): ImportedZipData? {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(zipUri) ?: return null
            val zipIn = java.util.zip.ZipInputStream(inputStream)
            
            var entry: java.util.zip.ZipEntry? = zipIn.nextEntry
            var backupJsonString: String? = null
            
            while (entry != null) {
                if (entry.name == "backup.json") {
                    backupJsonString = zipIn.bufferedReader(Charsets.UTF_8).readText()
                } else {
                    val destFile = File(context.filesDir, entry.name)
                    java.io.FileOutputStream(destFile).use { fOut ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                            fOut.write(buffer, 0, bytesRead)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
            zipIn.close()
            
            if (backupJsonString == null) return null
            
            val categories = mutableListOf<Category>()
            val notes = mutableListOf<Note>()
            val quickNotes = mutableListOf<QuickNote>()
            val shiftStations = mutableListOf<ShiftStation>()
            val shiftTasks = mutableListOf<ShiftTask>()
            val shiftHandovers = mutableListOf<ShiftHandover>()
            
            val root = JSONObject(backupJsonString)
            if (root.has("categories")) {
                val categoriesObj = root.optJSONObject("categories") ?: JSONObject()
                val keys = categoriesObj.keys()
                while (keys.hasNext()) {
                    val categoryName = keys.next()
                    categories.add(Category(name = categoryName))
                    
                    val notesArray = categoriesObj.optJSONArray(categoryName) ?: JSONArray()
                    for (i in 0 until notesArray.length()) {
                        val noteJson = notesArray.optJSONObject(i) ?: continue
                        
                        val imagesList = mutableListOf<String>()
                        val imagesArray = noteJson.optJSONArray("images")
                        if (imagesArray != null) {
                            for (k in 0 until imagesArray.length()) {
                                val filename = imagesArray.optString(k)
                                val localFile = File(context.filesDir, filename)
                                imagesList.add(localFile.absolutePath)
                            }
                        }
                        
                        val tagsList = mutableListOf<String>()
                        val tagsArray = noteJson.optJSONArray("tags")
                        if (tagsArray != null) {
                            for (k in 0 until tagsArray.length()) {
                                tagsList.add(tagsArray.optString(k))
                            }
                        }
                        
                        val relativeVoicePath = if (noteJson.isNull("voicePath")) null else noteJson.optString("voicePath")
                        val voicePath = if (relativeVoicePath != null) {
                            File(context.filesDir, relativeVoicePath).absolutePath
                        } else null
                        
                        val note = Note(
                            id = noteJson.optString("id", java.util.UUID.randomUUID().toString()),
                            categoryName = categoryName,
                            text = noteJson.optString("text", ""),
                            images = imagesList,
                            tags = tagsList,
                            pinned = noteJson.optBoolean("pinned", false),
                            createdAt = noteJson.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = noteJson.optLong("updatedAt", System.currentTimeMillis()),
                            reminderTime = if (noteJson.isNull("reminderTime")) null else noteJson.optLong("reminderTime"),
                            isLocked = noteJson.optBoolean("isLocked", false),
                            passwordHash = if (noteJson.isNull("passwordHash")) null else noteJson.optString("passwordHash"),
                            voicePath = voicePath,
                            audioDurationMs = noteJson.optLong("audioDurationMs", 0L)
                        )
                        notes.add(note)
                    }
                }
            }

            // Parse Quick Notes
            val quickNotesArray = root.optJSONArray("quickNotes")
            if (quickNotesArray != null) {
                for (i in 0 until quickNotesArray.length()) {
                    val qObj = quickNotesArray.optJSONObject(i) ?: continue
                    val remTime = if (qObj.isNull("reminderTime")) null else qObj.optLong("reminderTime")
                    quickNotes.add(
                        QuickNote(
                            id = qObj.optString("id", java.util.UUID.randomUUID().toString()),
                            text = qObj.optString("text", ""),
                            pinned = qObj.optBoolean("pinned", false),
                            createdAt = qObj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = qObj.optLong("updatedAt", System.currentTimeMillis()),
                            hasNotification = qObj.optBoolean("hasNotification", false),
                            reminderTime = remTime,
                            isNotificationTriggered = qObj.optBoolean("isNotificationTriggered", false)
                        )
                    )
                }
            }

            // Parse Shift Stations
            val stationsArray = root.optJSONArray("shiftStations")
            if (stationsArray != null) {
                for (i in 0 until stationsArray.length()) {
                    val sObj = stationsArray.optJSONObject(i) ?: continue
                    shiftStations.add(
                        ShiftStation(
                            id = sObj.optString("id", java.util.UUID.randomUUID().toString()),
                            name = sObj.optString("name", "Vị trí"),
                            icon = sObj.optString("icon", "🏭"),
                            description = sObj.optString("description", ""),
                            qrCode = sObj.optString("qrCode", ""),
                            isActive = sObj.optBoolean("isActive", true)
                        )
                    )
                }
            }

            // Parse Shift Tasks
            val tasksArray = root.optJSONArray("shiftTasks")
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val tObj = tasksArray.optJSONObject(i) ?: continue
                    shiftTasks.add(
                        ShiftTask(
                            id = tObj.optString("id", java.util.UUID.randomUUID().toString()),
                            stationId = tObj.optString("stationId", ""),
                            timeScheduled = tObj.optString("timeScheduled", "08:00"),
                            title = tObj.optString("title", "Công việc"),
                            instruction = tObj.optString("instruction", ""),
                            priority = tObj.optString("priority", "Bình thường"),
                            requiresConfirmation = tObj.optBoolean("requiresConfirmation", false),
                            isCompleted = tObj.optBoolean("isCompleted", false),
                            completedAt = if (tObj.isNull("completedAt")) null else tObj.optLong("completedAt"),
                            hasLoudAlarm = tObj.optBoolean("hasLoudAlarm", false),
                            isHidden = tObj.optBoolean("isHidden", false),
                            actionType = tObj.optString("actionType", ""),
                            targetPackageName = tObj.optString("targetPackageName", ""),
                            targetAppName = tObj.optString("targetAppName", "")
                        )
                    )
                }
            }

            // Parse Shift Handovers
            val handoversArray = root.optJSONArray("shiftHandovers")
            if (handoversArray != null) {
                for (i in 0 until handoversArray.length()) {
                    val hObj = handoversArray.optJSONObject(i) ?: continue
                    shiftHandovers.add(
                        ShiftHandover(
                            id = hObj.optString("id", java.util.UUID.randomUUID().toString()),
                            stationId = hObj.optString("stationId", ""),
                            stationName = hObj.optString("stationName", ""),
                            shiftTitle = hObj.optString("shiftTitle", ""),
                            handoverContent = hObj.optString("handoverContent", ""),
                            senderName = hObj.optString("senderName", "Người giao ca"),
                            timestamp = hObj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            ImportedZipData(
                categories = categories,
                notes = notes,
                quickNotes = quickNotes,
                shiftStations = shiftStations,
                shiftTasks = shiftTasks,
                shiftHandovers = shiftHandovers
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves the zip file to the public Downloads folder.
     * Returns the name of the saved file or null if failed.
     */
    fun saveZipToDownloads(context: Context, zipFile: File): String? {
        return try {
            val resolver = context.contentResolver
            val filename = "MultiCategoryNotes_Backup_${System.currentTimeMillis() / 1000}.zip"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(zipFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    filename
                } else {
                    null
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val destFile = File(downloadsDir, filename)
                FileInputStream(zipFile).use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                filename
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
