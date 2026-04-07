package de.kevinkaupert.gymtracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var exerciseDropdown: AutoCompleteTextView
    private lateinit var setNumberInput: TextInputEditText
    private lateinit var repsInput: TextInputEditText
    private lateinit var weightInput: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var finishButton: MaterialButton
    private lateinit var exportButton: MaterialButton
    private lateinit var oneRmResult: TextView
    private lateinit var trainingRanges: TextView
    private lateinit var sessionStatus: TextView
    private lateinit var progressionGraph: ProgressionGraphView
    private lateinit var graphTitle: TextView
    private lateinit var toggleGraphMode: MaterialButton
    
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var inputScreen: View
    private lateinit var historyScreen: View
    private lateinit var analyzeScreen: View
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter

    private var lastExercise: String? = null
    private var graphMode = ProgressionGraphView.GraphMode.ONE_RM

    private fun getTodayFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "workout_$date.json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.contentFrame)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        exerciseDropdown = findViewById(R.id.exerciseDropdown)
        setNumberInput = findViewById(R.id.setNumberInput)
        repsInput = findViewById(R.id.repsInput)
        weightInput = findViewById(R.id.weightInput)
        saveButton = findViewById(R.id.saveButton)
        finishButton = findViewById(R.id.finishButton)
        exportButton = findViewById(R.id.exportButton)
        oneRmResult = findViewById(R.id.oneRmResult)
        trainingRanges = findViewById(R.id.trainingRanges)
        sessionStatus = findViewById(R.id.sessionStatus)
        progressionGraph = findViewById(R.id.progressionGraph)
        graphTitle = findViewById(R.id.graphTitle)
        toggleGraphMode = findViewById(R.id.toggleGraphMode)
        
        bottomNavigation = findViewById(R.id.bottomNavigation)
        inputScreen = findViewById(R.id.inputScreen)
        historyScreen = findViewById(R.id.historyScreen)
        analyzeScreen = findViewById(R.id.analyzeScreen)
        historyRecyclerView = findViewById(R.id.historyRecyclerView)

        setupNavigation()
        setupHistory()
        setupGraphToggle()
        updateExerciseAdapter()

        exerciseDropdown.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val exercise = s.toString()
                updateSetNumberForExercise(exercise)
                updateProgressionView(exercise)
            }
        })

        saveButton.setOnClickListener { saveEntry() }
        finishButton.setOnClickListener { finishWorkout() }
        exportButton.setOnClickListener { exportJson() }

        exerciseDropdown.setOnItemClickListener { _, _, _, _ ->
            updateSetNumberForExercise(exerciseDropdown.text.toString())
            updateProgressionView(exerciseDropdown.text.toString())
        }

        updateSessionStatus()
    }

    private fun setupNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            inputScreen.visibility = View.GONE
            historyScreen.visibility = View.GONE
            analyzeScreen.visibility = View.GONE
            when(item.itemId) {
                R.id.nav_input -> inputScreen.visibility = View.VISIBLE
                R.id.nav_history -> {
                    historyScreen.visibility = View.VISIBLE
                    loadHistory()
                }
                R.id.nav_analyze -> {
                    analyzeScreen.visibility = View.VISIBLE
                    updateProgressionView(exerciseDropdown.text.toString())
                }
            }
            true
        }
    }

    private fun setupHistory() {
        historyAdapter = HistoryAdapter(emptyList(), 
            onEditSet = { editSet(it) }, 
            onDeleteSet = { confirmDeleteSet(it) }, 
            onDeleteSession = { confirmDeleteSession(it) }
        )
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter
    }

    private fun loadHistory() {
        val sessions = mutableListOf<WorkoutSession>()
        val files = filesDir.listFiles { _, name -> name.startsWith("workout_") && name.endsWith(".json") }
        
        files?.sortedByDescending { it.name }?.forEach { file ->
            try {
                val jsonText = file.readText()
                if (jsonText.isBlank()) return@forEach
                val jsonArray = JSONArray(jsonText)
                val sets = mutableListOf<WorkoutSet>()
                val dateFromFilename = file.name.removePrefix("workout_").removeSuffix(".json")

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    // ID-Logik: Priorität auf gespeicherte ID, Fallback auf Tag_Uhrzeit
                    val id = if (obj.has("id")) {
                        obj.getString("id")
                    } else {
                        "${obj.optString("tag", dateFromFilename)}_${obj.optString("uhrzeit", i.toString())}"
                    }

                    sets.add(WorkoutSet(
                        id = id,
                        date = obj.optString("tag", dateFromFilename),
                        time = obj.optString("uhrzeit", "--:--"),
                        exercise = obj.optString("übung", "Unbekannte Übung"),
                        setNumber = obj.optString("satz", "0"),
                        reps = obj.optInt("wiederholungen", 0),
                        weight = obj.optDouble("gewicht", 0.0),
                        oneRm = obj.optDouble("1RM", 0.0),
                        originFileName = file.name
                    ))
                }
                if (sets.isNotEmpty()) {
                    // Sätze innerhalb einer Session nach Uhrzeit sortieren
                    sets.sortBy { it.time }
                    sessions.add(WorkoutSession(sets[0].date, file.name, sets))
                } else if (file.length() == 0L) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        historyAdapter.updateData(sessions)
    }

    private fun saveEntry() {
        val exercise = exerciseDropdown.text.toString()
        val setNumberStr = setNumberInput.text.toString()
        val repsStr = repsInput.text.toString()
        val weightStr = weightInput.text.toString()

        if (exercise.isEmpty() || setNumberStr.isEmpty() || repsStr.isEmpty() || weightStr.isEmpty()) {
            Toast.makeText(this, "Bitte alle Felder ausfüllen!", Toast.LENGTH_SHORT).show()
            return
        }

        val reps = repsStr.toInt()
        val weight = weightStr.toDouble()
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        if (isSetAlreadySaved(exercise, currentDate, setNumberStr)) {
            Toast.makeText(this, "Satz $setNumberStr für $exercise heute bereits gespeichert!", Toast.LENGTH_LONG).show()
            return
        }

        val oneRm = weight * (1 + reps / 30.0)
        val entry = JSONObject().apply {
            put("id", System.currentTimeMillis().toString())
            put("tag", currentDate)
            put("uhrzeit", currentTime)
            put("übung", exercise)
            put("satz", setNumberStr)
            put("wiederholungen", reps)
            put("gewicht", weight)
            put("1RM", oneRm)
        }

        saveToJsonFile(entry)
        
        setNumberInput.setText((setNumberStr.toInt() + 1).toString())
        repsInput.text?.clear()
        
        oneRmResult.text = String.format(Locale.getDefault(), "1RM: %.2f kg", oneRm)
        updateTrainingRanges(oneRm)
        updateSessionStatus()
        updateExerciseAdapter()
        updateProgressionView(exercise)
        Toast.makeText(this, "Gespeichert!", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteSession(session: WorkoutSession) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Training löschen?")
            .setMessage("Möchtest du das gesamte Training vom ${session.date} wirklich löschen?")
            .setPositiveButton("Löschen") { _, _ -> deleteSession(session) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun deleteSession(session: WorkoutSession) {
        val file = File(filesDir, session.fileName)
        if (file.exists() && file.delete()) {
            loadHistory()
            updateSessionStatus()
            updateProgressionView(exerciseDropdown.text.toString())
            Toast.makeText(this, "Training gelöscht", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteSet(set: WorkoutSet) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Satz löschen?")
            .setMessage("Möchtest du diesen Satz wirklich löschen?")
            .setPositiveButton("Löschen") { _, _ -> deleteSet(set) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun deleteSet(set: WorkoutSet) {
        val file = File(filesDir, set.originFileName)
        if (!file.exists()) return
        
        try {
            val jsonArray = JSONArray(file.readText())
            val newArray = JSONArray()
            var found = false
            val dateFromFilename = file.name.removePrefix("workout_").removeSuffix(".json")

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = if (obj.has("id")) {
                    obj.getString("id")
                } else {
                    "${obj.optString("tag", dateFromFilename)}_${obj.optString("uhrzeit", i.toString())}"
                }

                if (id != set.id) {
                    newArray.put(obj)
                } else {
                    found = true
                }
            }
            
            if (found) {
                if (newArray.length() > 0) {
                    file.writeText(newArray.toString(4))
                } else {
                    file.delete()
                }
                loadHistory()
                updateSessionStatus()
                updateProgressionView(exerciseDropdown.text.toString())
                Toast.makeText(this, "Satz gelöscht", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Satz nicht gefunden", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler beim Löschen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun editSet(set: WorkoutSet) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_set, null)
        val editReps = dialogView.findViewById<TextInputEditText>(R.id.editReps)
        val editWeight = dialogView.findViewById<TextInputEditText>(R.id.editWeight)
        editReps.setText(set.reps.toString())
        editWeight.setText(set.weight.toString())

        MaterialAlertDialogBuilder(this)
            .setTitle("Satz bearbeiten")
            .setView(dialogView)
            .setPositiveButton("Speichern") { _, _ ->
                val r = editReps.text.toString().toIntOrNull() ?: set.reps
                val w = editWeight.text.toString().toDoubleOrNull() ?: set.weight
                updateSetInFile(set, r, w)
            }
            .setNegativeButton("Abbrechen", null).show()
    }

    private fun updateSetInFile(set: WorkoutSet, newReps: Int, newWeight: Double) {
        val file = File(filesDir, set.originFileName)
        if (!file.exists()) return
        
        try {
            val jsonArray = JSONArray(file.readText())
            val dateFromFilename = file.name.removePrefix("workout_").removeSuffix(".json")
            var found = false
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = if (obj.has("id")) {
                    obj.getString("id")
                } else {
                    "${obj.optString("tag", dateFromFilename)}_${obj.optString("uhrzeit", i.toString())}"
                }
                
                if (id == set.id) {
                    obj.put("wiederholungen", newReps)
                    obj.put("gewicht", newWeight)
                    obj.put("1RM", newWeight * (1 + newReps / 30.0))
                    found = true
                    break
                }
            }
            
            if (found) {
                file.writeText(jsonArray.toString(4))
                loadHistory()
                updateProgressionView(exerciseDropdown.text.toString())
                Toast.makeText(this, "Satz aktualisiert", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler beim Bearbeiten", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupGraphToggle() {
        toggleGraphMode.setOnClickListener {
            graphMode = if (graphMode == ProgressionGraphView.GraphMode.ONE_RM) ProgressionGraphView.GraphMode.VOLUME else ProgressionGraphView.GraphMode.ONE_RM
            graphTitle.text = if (graphMode == ProgressionGraphView.GraphMode.ONE_RM) "Analyse: 1RM" else "Analyse: Volumen"
            toggleGraphMode.text = if (graphMode == ProgressionGraphView.GraphMode.ONE_RM) "Zu Volumen" else "Zu 1RM"
            updateProgressionView(exerciseDropdown.text.toString())
        }
    }

    private fun updateExerciseAdapter() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, getRecentExercises())
        exerciseDropdown.setAdapter(adapter)
    }

    private fun getRecentExercises(): List<String> {
        val exercises = mutableListOf<String>()
        filesDir.listFiles { _, name -> name.startsWith("workout_") }?.sortedByDescending { it.name }?.forEach { file ->
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val ex = arr.getJSONObject(i).getString("übung")
                    if (!exercises.contains(ex)) exercises.add(ex)
                }
            } catch (e: Exception) {}
        }
        return (exercises + listOf("Bankdrücken", "Kniebeugen", "Kreuzheben")).distinct()
    }

    private fun updateProgressionView(exerciseName: String) {
        val map = mutableMapOf<String, MutableList<Double>>()
        filesDir.listFiles { _, name -> name.startsWith("workout_") }?.forEach { file ->
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getString("übung").equals(exerciseName, ignoreCase = true)) {
                        val date = obj.getString("tag")
                        val valStr = if (graphMode == ProgressionGraphView.GraphMode.ONE_RM) obj.getDouble("1RM") else obj.getInt("wiederholungen") * obj.getDouble("gewicht")
                        if (!map.containsKey(date)) map[date] = mutableListOf()
                        map[date]?.add(valStr)
                    }
                }
            } catch (e: Exception) {}
        }
        progressionGraph.setSessionData(map, graphMode)
    }

    private fun isSetAlreadySaved(ex: String, d: String, s: String): Boolean {
        val file = File(filesDir, "workout_$d.json")
        if (!file.exists()) return false
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("übung").equals(ex, ignoreCase = true) && obj.getString("satz") == s) return true
            }
        } catch (e: Exception) {}
        return false
    }

    private fun updateSetNumberForExercise(ex: String) {
        val file = File(filesDir, getTodayFileName())
        var max = 0
        if (file.exists()) {
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getString("übung").equals(ex, ignoreCase = true)) {
                        val s = obj.getString("satz").toIntOrNull() ?: 0
                        if (s > max) max = s
                    }
                }
            } catch (e: Exception) {}
        }
        setNumberInput.setText((max + 1).toString())
    }

    private fun updateTrainingRanges(oneRm: Double) {
        trainingRanges.text = "Trainingsbereiche (Basis 1RM):\n" +
                "Ausdauer (15-30%): ~${String.format(Locale.getDefault(), "%.1f", oneRm * 0.25)} kg\n" +
                "Kraftausdauer (30-50%): ~${String.format(Locale.getDefault(), "%.1f", oneRm * 0.40)} kg\n" +
                "Muskelaufbau (60-80%): ~${String.format(Locale.getDefault(), "%.1f", oneRm * 0.70)} kg\n" +
                "Intramusk. Koord. (90-105%): ~${String.format(Locale.getDefault(), "%.1f", oneRm * 0.95)} kg"
    }

    private fun saveToJsonFile(entry: JSONObject) {
        val file = File(filesDir, getTodayFileName())
        val arr = if (file.exists()) JSONArray(file.readText()) else JSONArray()
        arr.put(entry)
        file.writeText(arr.toString(4))
    }

    private fun exportJson() {
        val options = arrayOf("Heutiges Training", "Gesamter Verlauf (Backup)")
        MaterialAlertDialogBuilder(this).setTitle("Export wählen").setItems(options) { _, which ->
            if (which == 0) exportTodayOnly() else exportAllData()
        }.show()
    }

    private fun exportTodayOnly() {
        val file = File(filesDir, getTodayFileName())
        if (file.exists()) shareFile(file, "Heute") else Toast.makeText(this, "Keine Daten", Toast.LENGTH_SHORT).show()
    }

    private fun exportAllData() {
        val files = filesDir.listFiles { _, name -> name.startsWith("workout_") }
        if (files.isNullOrEmpty()) return
        val all = JSONArray()
        files.forEach { f ->
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) all.put(arr.get(i))
        }
        val f = File(cacheDir, "backup.json")
        f.writeText(all.toString(4))
        shareFile(f, "Backup")
    }

    private fun shareFile(f: File, t: String) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, t))
    }

    private fun finishWorkout() {
        MaterialAlertDialogBuilder(this).setTitle("Beenden?").setMessage("Alle Felder leeren?").setPositiveButton("Ja") { _, _ ->
            exerciseDropdown.text.clear()
            repsInput.text?.clear()
            weightInput.text?.clear()
            setNumberInput.setText("1")
            oneRmResult.text = "1RM: -"
            trainingRanges.text = "Bereiche: -"
            lastExercise = null
        }.setNegativeButton("Nein", null).show()
    }

    private fun updateSessionStatus() {
        val files = filesDir.listFiles { _, name -> name.startsWith("workout_") }
        var total = 0
        files?.forEach { total += JSONArray(it.readText()).length() }
        sessionStatus.text = "Gesamt: $total Sätze in ${files?.size ?: 0} Sessions"
    }
}
