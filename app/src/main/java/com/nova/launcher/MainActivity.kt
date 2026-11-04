package com.nova.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private data class LaunchableApp(val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable)

    private lateinit var appGrid: GridLayout
    private lateinit var search: EditText
    private lateinit var island: LinearLayout
    private lateinit var islandTitle: TextView
    private lateinit var islandDetail: TextView
    private lateinit var islandActions: LinearLayout
    private lateinit var clock: TextView
    private var islandExpanded = false
    private var allApps: List<LaunchableApp> = emptyList()
    private val prefs by lazy { getSharedPreferences("nova_launcher", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            if (::clock.isInitialized) clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 30_000)
        }
    }
    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = renderIsland()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 11, 23)
        window.navigationBarColor = Color.rgb(8, 11, 23)
        buildScreen()
        loadApps()
        renderIsland()
        val filter = IntentFilter(IslandState.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(updateReceiver, filter)
        handler.post(clockTick)
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::appGrid.isInitialized) loadApps()
        if (::islandTitle.isInitialized) renderIsland()
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockTick)
        try { unregisterReceiver(updateReceiver) } catch (_: IllegalArgumentException) { }
        super.onDestroy()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(7, 10, 22), Color.rgb(25, 33, 65)))
        }
        setContentView(root)

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("N O V A", 15f, ACCENT, true))
        header.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(text("⚙", 23f, Color.WHITE, false).apply {
            contentDescription = "Nova settings"
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { showSettings() }
        })
        root.addView(header)

        clock = text("00:00", 48f, Color.WHITE, false).apply { setPadding(0, dp(12), 0, 0) }
        root.addView(clock)
        root.addView(text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), 14f, Color.LTGRAY, false))

        island = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = rounded(Color.BLACK, 36)
            isClickable = true
            isFocusable = true
            setOnClickListener { islandExpanded = !islandExpanded; renderIsland() }
        }
        root.addView(island, LinearLayout.LayoutParams(-1, dp(76)).apply { topMargin = dp(20); bottomMargin = dp(18) })
        islandTitle = text("NOVA ISLAND", 14f, Color.WHITE, true).apply { gravity = Gravity.CENTER; maxLines = 1 }
        islandDetail = text("Tap to expand • Notification access optional", 11f, Color.LTGRAY, false).apply { gravity = Gravity.CENTER; maxLines = 2 }
        islandActions = LinearLayout(this).apply { gravity = Gravity.CENTER; visibility = View.GONE }
        island.addView(islandTitle)
        island.addView(islandDetail)
        island.addView(islandActions)

        val section = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        section.addView(text("YOUR APPS", 12f, Color.rgb(180, 195, 230), true))
        section.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        section.addView(text("Wallpaper", 12f, ACCENT, true).apply {
            setPadding(dp(10), dp(8), 0, dp(8))
            setOnClickListener { chooseWallpaper() }
        })
        root.addView(section)

        search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 15f
            singleLine = true
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(Color.rgb(36, 43, 69), 17)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10); bottomMargin = dp(8) })
        val scroll = ScrollView(this).apply { isFillViewport = true }
        appGrid = GridLayout(this).apply { columnCount = 4; useDefaultMargins = false }
        scroll.addView(appGrid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = showApps(s?.toString().orEmpty())
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    private fun loadApps() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") packageManager.queryIntentActivities(launcherIntent, 0)
        }
        allApps = resolved.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.takeIf(String::isNotBlank) ?: packageName
            LaunchableApp(packageName, label, info.loadIcon(packageManager))
        }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
        showApps(search.text?.toString().orEmpty())
    }

    private fun showApps(query: String) {
        if (!::appGrid.isInitialized) return
        appGrid.removeAllViews()
        val visibleApps = allApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        visibleApps.forEach { app ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
                isClickable = true
                isFocusable = true
                contentDescription = app.label
                setOnClickListener { launchApp(app.packageName) }
                setOnLongClickListener { showAppMenu(app); true }
            }
            cell.addView(ImageView(this).apply {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = app.label
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            cell.addView(text(app.label, 10f, Color.WHITE, false).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, dp(34)))
            appGrid.addView(cell, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(82)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        if (visibleApps.isEmpty()) {
            appGrid.addView(text("No apps found", 14f, Color.LTGRAY, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(24), 0, dp(24))
            }, GridLayout.LayoutParams().apply { columnSpec = GridLayout.spec(0, 4) })
        }
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) Toast.makeText(this, "Unable to open app", Toast.LENGTH_SHORT).show()
        else try { startActivity(intent) } catch (_: Exception) {
            Toast.makeText(this, "Unable to open app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppMenu(app: LaunchableApp) {
        AlertDialog.Builder(this).setTitle(app.label)
            .setItems(arrayOf("Open", "App info", "Save folder label")) { _, which ->
                when (which) {
                    0 -> launchApp(app.packageName)
                    1 -> try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))) } catch (_: Exception) { }
                    2 -> editFolderLabel(app)
                }
            }.show()
    }

    private fun editFolderLabel(app: LaunchableApp) {
        val input = EditText(this).apply { setText(prefs.getString("folder_${app.packageName}", "")) }
        AlertDialog.Builder(this).setTitle("Folder label")
            .setMessage("Save a local label for organizing this app.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit().putString("folder_${app.packageName}", input.text.toString()).apply()
                Toast.makeText(this, "Folder label saved", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Clear") { _, _ -> prefs.edit().remove("folder_${app.packageName}").apply() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun renderIsland() {
        if (!::islandTitle.isInitialized) return
        islandTitle.text = IslandState.title
        islandDetail.text = IslandState.detail
        islandActions.removeAllViews()
        islandActions.visibility = if (islandExpanded && IslandState.actions.isNotEmpty()) View.VISIBLE else View.GONE
        if (islandExpanded) {
            IslandState.actions.take(3).forEach { action ->
                val button = TextView(this).apply {
                    text = action.label
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    isClickable = true
                    setOnClickListener {
                        try { action.intent.send() } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, "Action unavailable", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                islandActions.addView(button, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
        }
        island.layoutParams = island.layoutParams.apply { height = dp(if (islandExpanded) 126 else 76) }
        island.requestLayout()
    }

    private fun showSettings() {
        val options = arrayOf("Enable notification access", "Choose wallpaper", "Android Home settings")
        AlertDialog.Builder(this).setTitle("Nova settings").setItems(options) { _, which ->
            when (which) {
                0 -> try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {
                    Toast.makeText(this, "Notification access settings unavailable", Toast.LENGTH_SHORT).show()
                }
                1 -> chooseWallpaper()
                2 -> openHomeSettings()
            }
        }.show()
    }

    private fun openHomeSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 24) Intent(Settings.ACTION_HOME_SETTINGS) else Intent(Settings.ACTION_SETTINGS)
        try { startActivity(intent) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun chooseWallpaper() {
        try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper")) }
        catch (_: Exception) { Toast.makeText(this, "No wallpaper picker found", Toast.LENGTH_SHORT).show() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 10
        private val ACCENT = Color.rgb(145, 175, 255)
    }
}
