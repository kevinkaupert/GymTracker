package de.kevinkaupert.gymtracker

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var exerciseDropdown: AutoCompleteTextView
    private lateinit var setNumberInput: TextInputEditText
    private lateinit var repsInput: TextInputEditText
    private lateinit var weightInput: TextInputEditText
    private lateinit var bodyweightCheckbox: MaterialCheckBox
    private lateinit var saveButton: MaterialButton
    private lateinit var finishButton: MaterialButton
    private lateinit var oneRmResult: TextView
    private lateinit var trainingRanges: TextView
    private lateinit var progressionGraph: ProgressionGraphView
    private lateinit var graphTitle: TextView
    
    // Analyze Screen
    private lateinit var analyzeExerciseDropdown: AutoCompleteTextView
    private lateinit var analyzeModeDropdown: AutoCompleteTextView
    private lateinit var sessionStatus: TextView

    // Settings Screen
    private lateinit var settingsExportAll: MaterialButton
    private lateinit var settingsExportToday: MaterialButton
    
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var inputScreen: View
    private lateinit var historyScreen: View
    private lateinit var analyzeScreen: View
    private lateinit var settingsScreen: View
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var historyAdapter: HistoryAdapter

    private var lastRefOneRm: Double = 0.0
    private var lastRefWeight: Double = 0.0
    private var lastRefIsBodyweight: Boolean = false

    private var graphMode = ProgressionGraphView.GraphMode.ONE_RM

    private fun getTodayFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return "workout_$date.json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Restore state
        var restoredSelection = R.id.nav_input
        savedInstanceState?.let {
            val modeName = it.getString("graph_mode")
            if (modeName != null) {
                graphMode = ProgressionGraphView.GraphMode.valueOf(modeName)
            }
            restoredSelection = it.getInt("selected_item_id", R.id.nav_input)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.contentFrame)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Init UI
        exerciseDropdown = findViewById(R.id.exerciseDropdown)
        setNumberInput = findViewById(R.id.setNumberInput)
        repsInput = findViewById(R.id.repsInput)
        weightInput = findViewById(R.id.weightInput)
        bodyweightCheckbox = findViewById(R.id.bodyweightCheckbox)
        saveButton = findViewById(R.id.saveButton)
        finishButton = findViewById(R.id.finishButton)
        oneRmResult = findViewById(R.id.oneRmResult)
        trainingRanges = findViewById(R.id.trainingRanges)
        
        analyzeExerciseDropdown = findViewById(R.id.analyzeExerciseDropdown)
        analyzeModeDropdown = findViewById(R.id.analyzeModeDropdown)
        progressionGraph = findViewById(R.id.progressionGraph)
        graphTitle = findViewById(R.id.graphTitle)
        sessionStatus = findViewById(R.id.sessionStatus)

        settingsExportAll = findViewById(R.id.settingsExportAll)
        settingsExportToday = findViewById(R.id.settingsExportToday)
        
        bottomNavigation = findViewById(R.id.bottomNavigation)
        inputScreen = findViewById(R.id.inputScreen)
        historyScreen = findViewById(R.id.historyScreen)
        analyzeScreen = findViewById(R.id.analyzeScreen)
        settingsScreen = findViewById(R.id.settingsScreen)
        historyRecyclerView = findViewById(R.id.historyRecyclerView)

        setupNavigation()
        setupHistory()
        setupAnalyzeScreen()
        setupSettingsScreen()
        updateExerciseAdapters()

        // WICHTIG: Setze die Auswahl UND aktualisiere die Sichtbarkeit manuell.
        // Das System stellt die ID zwar oft selbst wieder her, triggert aber den Listener nicht.
        bottomNavigation.selectedItemId = restoredSelection
        applyNavigation(restoredSelection)

        exerciseDropdown.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val exercise = s.toString()
                updateSetNumberForExercise(exercise)
                loadLastExerciseData(exercise)
                // Synchronize Analyze Screen with Current Exercise only if it's not being cleared
                if (exercise.isNotBlank()) {
                    analyzeExerciseDropdown.setText(exercise, false)
                    refreshAnalyzeView()
                }
            }
        })

        saveButton.setOnClickListener { saveEntry() }
        finishButton.setOnClickListener { finishWorkout() }

        bodyweightCheckbox.setOnCheckedChangeListener { _, isChecked ->
            weightInput.isEnabled = !isChecked
            if (isChecked) weightInput.setText("0")
        }

        updateSessionStatus()
    }

    private fun setupNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            applyNavigation(item.itemId)
            true
        }
    }

    private fun applyNavigation(itemId: Int) {
        inputScreen.visibility = View.GONE
        historyScreen.visibility = View.GONE
        analyzeScreen.visibility = View.GONE
        settingsScreen.visibility = View.GONE
        when(itemId) {
            R.id.nav_input -> inputScreen.visibility = View.VISIBLE
            R.id.nav_history -> {
                historyScreen.visibility = View.VISIBLE
                loadHistory()
            }
            R.id.nav_analyze -> {
                analyzeScreen.visibility = View.VISIBLE
                refreshAnalyzeView()
            }
            R.id.nav_settings -> settingsScreen.visibility = View.VISIBLE
        }
    }

    private fun setupHistory() {
        historyAdapter = HistoryAdapter(emptyList(), 
            onEditSet = { editSet(it) }, 
            onDeleteSet = { confirmDeleteSet(it) }, 
            onDeleteSession = { confirmDeleteSession(it) },
            onExportSession = { exportSession(it) }
        )
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val session = historyAdapter.getSessionAt(position)
                confirmDeleteSession(session)
                historyAdapter.notifyItemChanged(position)
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val paint = Paint()
                    val icon: Drawable? = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_delete)
                    
                    if (dX < 0) { // Swiping to the left
                        paint.color = ContextCompat.getColor(this@MainActivity, R.color.error)
                        val background = RectF(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                        c.drawRect(background, paint)

                        icon?.let {
                            val iconMargin = (itemView.height - it.intrinsicHeight) / 2
                            val iconTop = itemView.top + (itemView.height - it.intrinsicHeight) / 2
                            val iconBottom = iconTop + it.intrinsicHeight
                            val iconLeft = itemView.right - iconMargin - it.intrinsicWidth
                            val iconRight = itemView.right - iconMargin
                            it.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            it.draw(c)
                        }
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(historyRecyclerView)
    }

    private fun setupAnalyzeScreen() {
        val modes = listOf(getString(R.string.mode_1rm), getString(R.string.mode_volume))
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modes)
        analyzeModeDropdown.setAdapter(modeAdapter)
        
        val currentModeText = if (graphMode == ProgressionGraphView.GraphMode.ONE_RM) modes[0] else modes[1]
        analyzeModeDropdown.setText(currentModeText, false)
        
        analyzeModeDropdown.setOnItemClickListener { _, _, position, _ ->
            graphMode = if (position == 0) ProgressionGraphView.GraphMode.ONE_RM else ProgressionGraphView.GraphMode.VOLUME
            refreshAnalyzeView()
        }

        analyzeExerciseDropdown.setOnItemClickListener { _, _, _, _ ->
            refreshAnalyzeView()
        }
    }

    private fun setupSettingsScreen() {
        settingsExportAll.setOnClickListener { exportAllData() }
        settingsExportToday.setOnClickListener { exportTodayOnly() }
    }

    private fun refreshAnalyzeView() {
        val exercise = analyzeExerciseDropdown.text.toString()
        val modeLabel = if (graphMode == ProgressionGraphView.GraphMode.ONE_RM) getString(R.string.mode_1rm) else getString(R.string.mode_volume)
        graphTitle.text = getString(R.string.graph_title_format, getString(R.string.analyze_title), modeLabel, exercise)
        updateProgressionView(exercise)
        updateSessionStatus()
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
                    val id = if (obj.has("id")) obj.getString("id") else "${obj.optString("tag", dateFromFilename)}_${obj.optString("uhrzeit", i.toString())}"

                    sets.add(WorkoutSet(
                        id = id,
                        date = obj.optString("tag", dateFromFilename),
                        time = obj.optString("uhrzeit", "--:--"),
                        exercise = obj.optString("übung", getString(R.string.unknown_exercise)),
                        setNumber = obj.optString("satz", "0"),
                        reps = obj.optInt("wiederholungen", 0),
                        weight = obj.optDouble("gewicht", 0.0),
                        oneRm = obj.optDouble("1RM", 0.0),
                        isBodyweight = obj.optBoolean("isBodyweight", false),
                        originFileName = file.name
                    ))
                }
                if (sets.isNotEmpty()) {
                    sets.sortBy { it.time }
                    sessions.add(WorkoutSession(sets[0].date, file.name, sets))
                }
            } catch (_: Exception) {}
        }
        updateSessionStatus()
        historyAdapter.updateData(sessions)
    }

    private fun saveEntry() {
        val exercise = exerciseDropdown.text.toString()
        val setNumberStr = setNumberInput.text.toString()
        val repsStr = repsInput.text.toString()
        val weightStr = weightInput.text.toString()
        val isBodyweight = bodyweightCheckbox.isChecked

        if (exercise.isEmpty() || setNumberStr.isEmpty() || repsStr.isEmpty() || (weightStr.isEmpty() && !isBodyweight)) {
            Toast.makeText(this, R.string.fill_fields_error, Toast.LENGTH_SHORT).show()
            return
        }

        val reps = repsStr.toInt()
        val weight = if (isBodyweight) 0.0 else weightStr.toDouble()
        
        // Validation: Warn on extreme deviations (> 50%)
        if (lastRefOneRm > 0) {
            val currentOneRm = if (isBodyweight) reps.toDouble() else weight * (1 + reps / 30.0)
            val deviation = abs(currentOneRm - lastRefOneRm) / lastRefOneRm
            
            // If the BW status changed, a deviation is expected, but we still warn if it's huge
            if (deviation > 0.5) {
                val unit = if (isBodyweight) getString(R.string.unit_reps_bw) else getString(R.string.unit_kg_1rm)
                val refUnit = if (lastRefIsBodyweight) getString(R.string.unit_reps_bw) else getString(R.string.unit_kg_1rm)
                
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.deviation_title)
                    .setMessage(getString(R.string.deviation_message, currentOneRm, unit, lastRefOneRm, refUnit))
                    .setPositiveButton(R.string.save_button) { _, _ -> performSave(exercise, setNumberStr, reps, weight, isBodyweight) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                return
            }
        }

        performSave(exercise, setNumberStr, reps, weight, isBodyweight)
    }

    private fun performSave(exercise: String, setNumberStr: String, reps: Int, weight: Double, isBodyweight: Boolean) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val oneRm = if (isBodyweight) reps.toDouble() else weight * (1 + reps / 30.0)
        
        val entry = JSONObject().apply {
            put("id", System.currentTimeMillis().toString())
            put("tag", currentDate)
            put("uhrzeit", currentTime)
            put("übung", exercise)
            put("satz", setNumberStr)
            put("wiederholungen", reps)
            put("gewicht", weight)
            put("1RM", oneRm)
            put("isBodyweight", isBodyweight)
        }

        saveToJsonFile(entry)
        
        setNumberInput.setText((setNumberStr.toInt() + 1).toString())
        repsInput.text?.clear()
        
        oneRmResult.text = if (isBodyweight) 
            getString(R.string.current_basis_wdh, oneRm)
        else 
            getString(R.string.current_1rm, oneRm)
            
        updateTrainingRanges(oneRm, isBodyweight)
        updateSessionStatus()
        updateExerciseAdapters()
        Toast.makeText(this, R.string.saved_success, Toast.LENGTH_SHORT).show()
    }

    private fun exportSession(session: WorkoutSession) {
        val file = File(filesDir, session.fileName)
        if (file.exists()) {
            shareFile(file, "Workout from ${session.date}")
        }
    }

    private fun confirmDeleteSession(session: WorkoutSession) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_session_title)
            .setMessage(getString(R.string.delete_session_message, session.date))
            .setPositiveButton(R.string.delete) { _, _ -> deleteSession(session) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSession(session: WorkoutSession) {
        val file = File(filesDir, session.fileName)
        if (file.exists() && file.delete()) {
            loadHistory()
            updateSessionStatus()
            refreshAnalyzeView()
            Toast.makeText(this, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteSet(set: WorkoutSet) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_set_title)
            .setMessage(R.string.delete_set_message)
            .setPositiveButton(R.string.delete) { _, _ -> deleteSet(set) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSet(set: WorkoutSet) {
        val file = File(filesDir, set.originFileName)
        if (!file.exists()) return
        try {
            val jsonArray = JSONArray(file.readText())
            val newArray = JSONArray()
            var found = false
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = if (obj.has("id")) obj.getString("id") else "${obj.optString("tag", "")}_${obj.optString("uhrzeit", "")}"
                if (id != set.id) newArray.put(obj) else found = true
            }
            if (found) {
                if (newArray.length() > 0) file.writeText(newArray.toString(4)) else file.delete()
                loadHistory()
                updateSessionStatus()
                refreshAnalyzeView()
                Toast.makeText(this, getString(R.string.saved_success), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    private fun editSet(set: WorkoutSet) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_set, null)
        val editReps = dialogView.findViewById<TextInputEditText>(R.id.editReps)
        val editWeight = dialogView.findViewById<TextInputEditText>(R.id.editWeight)
        val editIsBodyweight = dialogView.findViewById<MaterialCheckBox>(R.id.editIsBodyweight)

        editReps.setText(set.reps.toString())
        editWeight.setText(set.weight.toString())
        editIsBodyweight.isChecked = set.isBodyweight
        editWeight.isEnabled = !set.isBodyweight

        editIsBodyweight.setOnCheckedChangeListener { _, isChecked ->
            editWeight.isEnabled = !isChecked
            if (isChecked) editWeight.setText("0")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_set_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save_button) { _, _ ->
                val r = editReps.text.toString().toIntOrNull() ?: set.reps
                val isBW = editIsBodyweight.isChecked
                val w = if (isBW) 0.0 else (editWeight.text.toString().toDoubleOrNull() ?: set.weight)
                updateSetInFile(set, r, w, isBW)
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun updateSetInFile(set: WorkoutSet, newReps: Int, newWeight: Double, isBodyweight: Boolean) {
        val file = File(filesDir, set.originFileName)
        try {
            val jsonArray = JSONArray(file.readText())
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = if (obj.has("id")) obj.getString("id") else "${obj.optString("tag", "")}_${obj.optString("uhrzeit", "")}"
                if (id == set.id) {
                    obj.put("wiederholungen", newReps)
                    obj.put("gewicht", newWeight)
                    obj.put("isBodyweight", isBodyweight)
                    // 1RM calculation logic consistent with saveEntry
                    val oneRm = if (isBodyweight) newReps.toDouble() else newWeight * (1 + newReps / 30.0)
                    obj.put("1RM", oneRm)
                    break
                }
            }
            file.writeText(jsonArray.toString(4))
            loadHistory()
            refreshAnalyzeView()
        } catch (_: Exception) {}
    }

    private fun updateExerciseAdapters() {
        val exercises = getRecentExercises()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, exercises)
        exerciseDropdown.setAdapter(adapter)
        analyzeExerciseDropdown.setAdapter(adapter)

        // UX: Pre-select the first exercise in Analyze if nothing is selected
        if (analyzeExerciseDropdown.text.isNullOrBlank() && exercises.isNotEmpty()) {
            analyzeExerciseDropdown.setText(exercises[0], false)
            refreshAnalyzeView()
        }
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
            } catch (_: Exception) {}
        }
        return (exercises + listOf("Bankdrücken", "Kniebeugen", "Kreuzheben")).distinct()
    }

    private fun updateProgressionView(exerciseName: String) {
        val map = mutableMapOf<String, MutableList<Double>>()
        val sessionMaxMap = mutableMapOf<String, Double>()
        filesDir.listFiles { _, name -> name.startsWith("workout_") }?.forEach { file ->
            try {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getString("übung").equals(exerciseName, ignoreCase = true)) {
                        val date = obj.getString("tag")
                        val current1RM = obj.getDouble("1RM")
                        
                        // Track max 1RM per session for dynamic zones
                        val existingMax = sessionMaxMap[date] ?: 0.0
                        if (current1RM > existingMax) sessionMaxMap[date] = current1RM
                        
                    val valStr = if (graphMode == ProgressionGraphView.GraphMode.ONE_RM) {
                            current1RM
                        } else {
                            if (obj.optBoolean("isBodyweight", false)) obj.getInt("wiederholungen").toDouble()
                            else obj.getInt("wiederholungen") * obj.getDouble("gewicht")
                        }
                        if (!map.containsKey(date)) map[date] = mutableListOf()
                        map[date]?.add(valStr)
                    }
                }
            } catch (e: Exception) {}
        }
        progressionGraph.setSessionData(map, graphMode, sessionMaxMap)
    }

    private fun loadLastExerciseData(ex: String) {
        if (ex.isBlank()) return
        
        lastRefIsBodyweight = false
        lastRefWeight = 0.0
        lastRefOneRm = 0.0
        var found = false

        filesDir.listFiles { _, name -> name.startsWith("workout_") }?.sortedByDescending { it.name }?.firstOrNull { file ->
            try {
                val arr = JSONArray(file.readText())
                for (i in arr.length() - 1 downTo 0) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getString("übung").equals(ex, ignoreCase = true)) {
                        lastRefIsBodyweight = obj.optBoolean("isBodyweight", false)
                        lastRefWeight = obj.optDouble("gewicht", 0.0)
                        lastRefOneRm = obj.optDouble("1RM", 0.0)
                        found = true
                        return@firstOrNull true
                    }
                }
            } catch (e: Exception) {}
            false
        }

        if (found) {
            bodyweightCheckbox.isChecked = lastRefIsBodyweight
            if (!lastRefIsBodyweight && weightInput.text.isNullOrBlank()) {
                weightInput.setText(lastRefWeight.toString())
            }
            
            // Update 1RM display with the LAST value as reference
            oneRmResult.text = if (lastRefIsBodyweight) 
                getString(R.string.last_basis_wdh, lastRefOneRm)
            else 
                getString(R.string.last_1rm, lastRefOneRm)
            
            updateTrainingRanges(lastRefOneRm, lastRefIsBodyweight)
        } else {
            // Defaults if exercise is new or unknown
            // Note: We don't necessarily want to clear weight if the user is just typing
            oneRmResult.text = getString(R.string.one_rm_basis)
            trainingRanges.text = getString(R.string.ranges_basis)
        }
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

    private fun updateTrainingRanges(oneRm: Double, isBodyweight: Boolean = false) {
        val unit = if (isBodyweight) "Reps" else "kg"
        val f = Locale.getDefault()
        
        // Increased field width to 28 for labels and 7.1 for weights to ensure perfect alignment
        val line1 = String.format(f, "%-28s ~%7.1f %s", getString(R.string.range_endurance), oneRm * 0.45, unit)
        val line2 = String.format(f, "%-28s ~%7.1f %s", getString(R.string.range_strength_endurance), oneRm * 0.60, unit)
        val line3 = String.format(f, "%-28s ~%7.1f %s", getString(R.string.range_hypertrophy), oneRm * 0.75, unit)
        val line4 = String.format(f, "%-28s ~%7.1f %s", getString(R.string.range_maximal), oneRm * 0.95, unit)

        trainingRanges.text = "${getString(R.string.training_ranges_title)}\n$line1\n$line2\n$line3\n$line4"
    }

    private fun saveToJsonFile(entry: JSONObject) {
        val file = File(filesDir, getTodayFileName())
        val arr = if (file.exists()) JSONArray(file.readText()) else JSONArray()
        arr.put(entry)
        file.writeText(arr.toString(4))
    }

    private fun exportTodayOnly() {
        val file = File(filesDir, getTodayFileName())
        if (file.exists()) shareFile(file, "Today") else Toast.makeText(this, R.string.no_data_today, Toast.LENGTH_SHORT).show()
    }

    private fun exportAllData() {
        val files = filesDir.listFiles { _, name -> name.startsWith("workout_") }
        if (files.isNullOrEmpty()) {
            Toast.makeText(this, R.string.no_data_export, Toast.LENGTH_SHORT).show()
            return
        }
        val all = JSONArray()
        files.forEach { f ->
            try {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) all.put(arr.get(i))
            } catch (e: Exception) {}
        }
        val f = File(cacheDir, "gymtracker_backup.json")
        f.writeText(all.toString(4))
        shareFile(f, getString(R.string.backup_filename))
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
        MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_finish_title).setMessage(R.string.dialog_finish_message).setPositiveButton(R.string.yes) { _, _ ->
            exerciseDropdown.text.clear()
            repsInput.text?.clear()
            weightInput.text?.clear()
            setNumberInput.setText("1")
            oneRmResult.text = getString(R.string.one_rm_basis)
            trainingRanges.text = getString(R.string.ranges_basis)
            bodyweightCheckbox.isChecked = false
            updateSessionStatus()
        }.setNegativeButton(R.string.no, null).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("graph_mode", graphMode.name)
        outState.putInt("selected_item_id", bottomNavigation.selectedItemId)
    }

    private fun updateSessionStatus() {
        val files = filesDir.listFiles { _, name -> name.startsWith("workout_") }
        var total = 0
        files?.forEach { try { total += JSONArray(it.readText()).length() } catch(e: Exception) {} }
        sessionStatus.text = getString(R.string.database_status, total, files?.size ?: 0)
    }
}
