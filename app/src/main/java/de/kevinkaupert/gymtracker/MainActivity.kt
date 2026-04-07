package de.kevinkaupert.gymtracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
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
    private lateinit var exportButton: MaterialButton
    private lateinit var oneRmResult: TextView
    private lateinit var trainingRanges: TextView
    private lateinit var sessionStatus: TextView
    private lateinit var progressionGraph: ProgressionGraphView

    private var lastExercise: String? = null

    private fun getTodayFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "workout_$date.json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        exerciseDropdown = findViewById(R.id.exerciseDropdown)
        setNumberInput = findViewById(R.id.setNumberInput)
        repsInput = findViewById(R.id.repsInput)
        weightInput = findViewById(R.id.weightInput)
        saveButton = findViewById(R.id.saveButton)
        exportButton = findViewById(R.id.exportButton)
        oneRmResult = findViewById(R.id.oneRmResult)
        trainingRanges = findViewById(R.id.trainingRanges)
        sessionStatus = findViewById(R.id.sessionStatus)
        progressionGraph = findViewById(R.id.progressionGraph)

        updateExerciseAdapter()

        exerciseDropdown.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateProgressionView(s.toString())
            }
        })

        saveButton.setOnClickListener {
            saveEntry()
        }

        exerciseDropdown.setOnItemClickListener { _, _, _, _ ->
            updateSetNumberForExercise(exerciseDropdown.text.toString())
            updateProgressionView(exerciseDropdown.text.toString())
        }

        exportButton.setOnClickListener {
            exportJson()
        }

        updateSessionStatus()
    }

    private fun updateExerciseAdapter() {
        val exerciseList = getRecentExercises()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, exerciseList)
        exerciseDropdown.setAdapter(adapter)
    }

    private fun getRecentExercises(): List<String> {
        val exercisesInOrder = mutableListOf<String>()
        val files = filesDir.listFiles { _, name -> name.startsWith("workout_") && name.endsWith(".json") }
        files?.sortedByDescending { it.name }?.forEach { file ->
            try {
                val jsonArray = JSONArray(file.readText())
                for (i in 0 until jsonArray.length()) {
                    val exercise = jsonArray.getJSONObject(i).getString("übung")
                    if (!exercisesInOrder.contains(exercise)) {
                        exercisesInOrder.add(exercise)
                    }
                }
            } catch (e: Exception) {}
        }

        val defaults = listOf("Bankdrücken", "Kniebeugen", "Kreuzheben", "Schulterdrücken", "Klimmzüge", "Rudern")
        return (exercisesInOrder + defaults).distinct()
    }

    private fun updateProgressionView(exerciseName: String) {
        val sessionMap = mutableMapOf<String, MutableList<Double>>()
        val files = filesDir.listFiles { _, name -> name.startsWith("workout_") && name.endsWith(".json") }
        
        files?.forEach { file ->
            try {
                val jsonArray = JSONArray(file.readText())
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.getString("übung").equals(exerciseName, ignoreCase = true)) {
                        val date = obj.getString("tag")
                        val oneRmValue = obj.getDouble("1RM")
                        if (!sessionMap.containsKey(date)) {
                            sessionMap[date] = mutableListOf()
                        }
                        sessionMap[date]?.add(oneRmValue)
                    }
                }
            } catch (e: Exception) {}
        }
        progressionGraph.setSessionData(sessionMap)
    }

    private fun saveEntry() {
        val exercise = exerciseDropdown.text.toString()
        val setNumberStr = setNumberInput.text.toString()
        val repsStr = repsInput.text.toString()
        val weightStr = weightInput.text.toString()

        if (exercise.isEmpty() || setNumberStr.isEmpty() || repsStr.isEmpty() || weightStr.isEmpty()) {
            Toast.makeText(this, getString(R.string.fill_fields_error), Toast.LENGTH_SHORT).show()
            return
        }

        val reps = repsStr.toInt()
        val weight = weightStr.toDouble()
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Check if set already exists
        if (isSetAlreadySaved(exercise, currentDate, setNumberStr)) {
            Toast.makeText(this, "Satz $setNumberStr für $exercise heute bereits gespeichert!", Toast.LENGTH_LONG).show()
            return
        }

        // 1RM Calculation (Epley Formula)
        val oneRm = weight * (1 + reps / 30.0)

        oneRmResult.text = getString(R.string.one_rm_format, oneRm)
        updateTrainingRanges(oneRm)

        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val entry = JSONObject().apply {
            put("tag", currentDate)
            put("uhrzeit", currentTime)
            put("übung", exercise)
            put("satz", setNumberStr)
            put("wiederholungen", reps)
            put("gewicht", weight)
            put("1RM", oneRm)
        }

        saveToJsonFile(entry)

        // Set next set number
        val nextSet = setNumberStr.toIntOrNull()?.plus(1) ?: 2
        setNumberInput.setText(nextSet.toString())
        lastExercise = exercise

        Toast.makeText(this, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()

        repsInput.text?.clear()
        updateSessionStatus()
        updateExerciseAdapter()
        updateProgressionView(exercise)
    }

    private fun isSetAlreadySaved(exercise: String, date: String, set: String): Boolean {
        val file = File(filesDir, getTodayFileName())
        if (!file.exists()) return false
        try {
            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("tag") == date &&
                    obj.getString("übung").equals(exercise, ignoreCase = true) &&
                    obj.getString("satz") == set
                ) {
                    return true
                }
            }
        } catch (e: Exception) { }
        return false
    }

    private fun updateSetNumberForExercise(exercise: String) {
        val file = File(filesDir, getTodayFileName())
        var maxSet = 0
        if (file.exists()) {
            try {
                val jsonArray = JSONArray(file.readText())
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.getString("übung").equals(exercise, ignoreCase = true)) {
                        val s = obj.getString("satz").toIntOrNull() ?: 0
                        if (s > maxSet) maxSet = s
                    }
                }
            } catch (e: Exception) { }
        }
        setNumberInput.setText((maxSet + 1).toString())
    }

    private fun updateTrainingRanges(oneRm: Double) {
        val ausdauer = oneRm * 0.25
        val kraftausdauer = oneRm * 0.40
        val muskelaufbau = oneRm * 0.70
        val intramusk = oneRm * 0.95

        val rangesText = "Trainingsbereiche (Basis 1RM):\n" +
                "Ausdauer (15-30%): ~${String.format(Locale.getDefault(), "%.1f", ausdauer)} kg\n" +
                "Kraftausdauer (30-50%): ~${String.format(Locale.getDefault(), "%.1f", kraftausdauer)} kg\n" +
                "Muskelaufbau (60-80%): ~${String.format(Locale.getDefault(), "%.1f", muskelaufbau)} kg\n" +
                "Intramusk. Koord. (90-105%): ~${String.format(Locale.getDefault(), "%.1f", intramusk)} kg"
        
        trainingRanges.text = rangesText
    }

    private fun saveToJsonFile(entry: JSONObject) {
        val file = File(filesDir, getTodayFileName())
        val jsonArray = if (file.exists()) {
            val content = file.readText()
            try {
                JSONArray(content)
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }

        jsonArray.put(entry)
        file.writeText(jsonArray.toString(4))
    }

    private fun exportJson() {
        val files = filesDir.listFiles { _, name -> name.startsWith("workout_") && name.endsWith(".json") }
        if (files.isNullOrEmpty()) {
            Toast.makeText(this, "Keine Daten zum Exportieren gefunden.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val allData = JSONArray()
            files.sortedBy { it.name }.forEach { file ->
                val sessionArray = JSONArray(file.readText())
                for (i in 0 until sessionArray.length()) {
                    allData.put(sessionArray.get(i))
                }
            }

            val exportFile = File(cacheDir, "gym_tracker_full_backup.json")
            exportFile.writeText(allData.toString(4))

            val contentUri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                exportFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Alle Trainingsdaten exportieren"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSessionStatus() {
        val files = filesDir.listFiles { _, name -> name.startsWith("workout_") && name.endsWith(".json") }
        var totalSets = 0
        files?.forEach { file ->
            try {
                totalSets += JSONArray(file.readText()).length()
            } catch (e: Exception) {}
        }
        sessionStatus.text = "Gesamt: $totalSets Sätze in ${files?.size ?: 0} Sessions"
    }
}
