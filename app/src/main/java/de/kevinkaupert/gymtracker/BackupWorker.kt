package de.kevinkaupert.gymtracker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val backupContent = generateBackupJson()
            if (backupContent != null) {
                val backupDir = File(applicationContext.filesDir, "backups")
                if (!backupDir.exists()) backupDir.mkdirs()
                
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val backupFile = File(backupDir, "internal_backup_$date.json")
                backupFile.writeText(backupContent)
                
                // Clean up old backups (keep last 7 days)
                val backupFiles = backupDir.listFiles { _, name -> name.startsWith("internal_backup_") }
                if (backupFiles != null && backupFiles.size > 7) {
                    val sortedFiles = backupFiles.sortedBy { it.name }
                    for (i in 0 until sortedFiles.size - 7) {
                        sortedFiles[i].delete()
                    }
                }
                
                Log.d("BackupWorker", "Internal backup created: ${backupFile.name}")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Backup failed", e)
            Result.failure()
        }
    }

    private fun generateBackupJson(): String? {
        val files = applicationContext.filesDir.listFiles { _, name -> name.startsWith("workout") && name.endsWith(".json") }
        if (files.isNullOrEmpty()) return null
        
        val backupObj = JSONObject()
        val sessionsArr = JSONArray()
        
        val sortedWorkoutFiles = files.sortedBy { it.name }
        sortedWorkoutFiles.forEach { f ->
            try {
                val content = f.readText()
                if (content.startsWith("{")) {
                    sessionsArr.put(JSONObject(content))
                } else if (content.startsWith("[")) {
                    sessionsArr.put(JSONArray(content))
                }
            } catch (e: Exception) {}
        }
        
        val exercisesFile = File(applicationContext.filesDir, "exercises.json")
        if (exercisesFile.exists()) {
            try {
                backupObj.put("master_exercises", JSONArray(exercisesFile.readText()))
            } catch (e: Exception) {}
        }
        
        backupObj.put("sessions", sessionsArr)
        backupObj.put("backup_date", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()))
        backupObj.put("version", 2)
        
        return backupObj.toString(4)
    }
}
