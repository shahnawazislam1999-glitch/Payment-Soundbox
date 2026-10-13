package com.soundbox.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    /** One line of the setup checklist (see row_check.xml). */
    private class Row(val root: View) {
        val icon: TextView = root.findViewById(R.id.tvIcon)
        val cb: CheckBox = root.findViewById(R.id.cbDone)
        val title: TextView = root.findViewById(R.id.tvTitle)
        val desc: TextView = root.findViewById(R.id.tvDesc)
        val btn: Button = root.findViewById(R.id.btnAction)
    }

    companion object {
        private const val REQ_AUTO = 1      // asked automatically on first launch
        private const val REQ_BUTTON = 2    // asked by the checklist button
        private const val FLAG_RESTRICTED = "restricted_done"
        private const val FLAG_AUTOSTART = "autostart_done"
        private const val FLAG_LOCK = "lock_done"
        private const val FLAG_PAYAPP_PREFIX = "payapp_"   // + package name
        private const val GREEN = "#2E7D32"
        private const val RED = "#C62828"
    }

    private lateinit var tvStatus: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var tvProgress: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var rowRestricted: Row
    private lateinit var rowAccess: Row
    private lateinit var rowNotif: Row
    private lateinit var rowBattery: Row
    private lateinit var rowAutostart: Row
    private lateinit var rowUnused: Row
    private lateinit var rowLock: Row
    private lateinit var llPayApps: LinearLayout
    private lateinit var tvNoPayApps: TextView
    private var installedPayApps: List<Pair<String, String>> = emptyList() // package to name
    private val adapter = PaymentAdapter()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { refreshHistory() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvProgress = findViewById(R.id.tvProgress)
        rvHistory = findViewById(R.id.rvHistory)
        llPayApps = findViewById(R.id.llPayApps)
        tvNoPayApps = findViewById(R.id.tvNoPayApps)
        val btnEnable: Button = findViewById(R.id.btnEnable)
        val btnTest: Button = findViewById(R.id.btnTest)
        val rgLang: RadioGroup = findViewById(R.id.rgLang)
        val rbEnglish: RadioButton = findViewById(R.id.rbEnglish)
        val rbHindi: RadioButton = findViewById(R.id.rbHindi)

        TTSManager.init(applicationContext)

        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        // ===== ANCHOR: LANGUAGE_SWITCH =====
        if (AppPrefs.getLang(this) == "hi") rbHindi.isChecked = true else rbEnglish.isChecked = true
        rgLang.setOnCheckedChangeListener { _, checkedId ->
            AppPrefs.setLang(this, if (checkedId == R.id.rbHindi) "hi" else "en")
        }
        // ===== END ANCHOR: LANGUAGE_SWITCH =====

        // ===== ANCHOR: ENABLE_ACCESS_BUTTON =====
        btnEnable.setOnClickListener { openNotificationAccessSettings() }
        // ===== END ANCHOR: ENABLE_ACCESS_BUTTON =====

        // ===== ANCHOR: TEST_SOUND_BUTTON (200 Rs) =====
        btnTest.setOnClickListener {
            TTSManager.speak(this, "200", "Google Pay")
            AppPrefs.addPayment(this, Payment("200", "Google Pay (test)", System.currentTimeMillis()))
        }
        // ===== END ANCHOR: TEST_SOUND_BUTTON =====

        // ===== ANCHOR: SETUP_CHECKLIST_ROWS (add / edit checklist items here) =====
        rowRestricted = bindRow(
            R.id.rowRestricted, "1. Allow restricted settings",
            "Android 13+ APK installs: App info → ⋮ (top right) → Allow restricted settings. Tick when done.",
            "Open", FLAG_RESTRICTED
        ) { openAppDetails() }

        rowAccess = bindRow(
            R.id.rowAccess, "2. Notification access",
            "Lets the app read PhonePe / GPay / Paytm notifications.",
            "Enable", null
        ) { openNotificationAccessSettings() }

        rowNotif = bindRow(
            R.id.rowNotif, "3. Show notifications",
            "Keeps the small \"Soundbox is running\" notification visible.",
            "Allow", null
        ) { askNotificationPermission() }

        rowBattery = bindRow(
            R.id.rowBattery, "4. Battery: Unrestricted",
            "Stops Android from killing the app in the background.",
            "Allow", null
        ) { askBatteryExemption() }

        rowAutostart = bindRow(
            R.id.rowAutostart, "5. Autostart / background",
            "Xiaomi, Oppo, Vivo, Realme, Huawei: allow Autostart + background activity. Tick when done.",
            "Open", FLAG_AUTOSTART
        ) { openAutostartSettings() }

        rowUnused = bindRow(
            R.id.rowUnused, "6. Don't pause app if unused",
            "Android 11+: turn OFF \"Pause app activity if unused\" / \"Remove permissions\" so access is never revoked.",
            "Open", null
        ) { openAutoRevokeSettings() }

        rowLock = bindRow(
            R.id.rowLock, "7. Lock app in Recents",
            "Prevents \"clear all\" from killing the app. Tick when done.",
            "How", FLAG_LOCK
        ) { showLockHelp() }
        // Row 8 (payment app notifications) is built in buildPayAppRows()
        // ===== END ANCHOR: SETUP_CHECKLIST_ROWS =====

        if (Build.VERSION.SDK_INT < 33) rowRestricted.root.visibility = View.GONE
        if (Build.VERSION.SDK_INT < 30) rowUnused.root.visibility = View.GONE

        // Ask for notification permission once on first launch (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_AUTO
            )
        }
    }

    override fun onStart() {
        super.onStart()
        AppPrefs.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onStop() {
        super.onStop()
        AppPrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        buildPayAppRows()
        updateChecklist()
        refreshHistory()
        try {
            ContextCompat.startForegroundService(this, Intent(this, KeepAliveService::class.java))
        } catch (e: Exception) { /* ignore */ }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        // If the user denied after tapping the checklist button, send them to settings
        if (requestCode == REQ_BUTTON && !granted) openAppNotificationSettings()
        updateChecklist()
    }

    // ===== ANCHOR: NOTIFICATION_ACCESS_STATUS =====
    private fun isAccessEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun updateStatus() {
        if (isAccessEnabled()) {
            tvStatus.text = "Notification access: ENABLED ✓"
            tvStatus.setTextColor(Color.parseColor(GREEN))
        } else {
            tvStatus.text = "Notification access: DISABLED ✗"
            tvStatus.setTextColor(Color.parseColor(RED))
        }
    }
    // ===== END ANCHOR: NOTIFICATION_ACCESS_STATUS =====

    // ===== ANCHOR: CHECKLIST_LOGIC (status of every checklist row) =====
    private fun bindRow(
        id: Int, title: String, desc: String, btnText: String,
        manualKey: String?, onClick: () -> Unit
    ): Row {
        val r = Row(findViewById(id))
        r.title.text = title
        r.desc.text = desc
        r.btn.text = btnText
        r.btn.setOnClickListener { onClick() }
        if (manualKey != null) {
            // Android cannot tell us if this is done, so the user ticks it
            r.icon.visibility = View.GONE
            r.cb.visibility = View.VISIBLE
            r.cb.setOnCheckedChangeListener { _, checked ->
                AppPrefs.setFlag(this, manualKey, checked)
                updateChecklist()
            }
        }
        return r
    }

    private fun setAuto(r: Row, ok: Boolean) {
        r.icon.text = if (ok) "✓" else "✗"
        r.icon.setTextColor(Color.parseColor(if (ok) GREEN else RED))
        r.btn.isEnabled = !ok
        r.btn.alpha = if (ok) 0.4f else 1f
    }

    private fun setManual(r: Row, done: Boolean) {
        if (r.cb.isChecked != done) r.cb.isChecked = done
    }

    private fun isNotifOk(): Boolean =
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    private fun isBatteryOk(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun updateChecklist() {
        val access = isAccessEnabled()
        val notif = isNotifOk()
        val battery = isBatteryOk()

        // If access is ON, the restricted-settings step is obviously done
        if (access && !AppPrefs.getFlag(this, FLAG_RESTRICTED)) {
            AppPrefs.setFlag(this, FLAG_RESTRICTED, true)
        }
        val restricted = AppPrefs.getFlag(this, FLAG_RESTRICTED)
        val autostart = AppPrefs.getFlag(this, FLAG_AUTOSTART)

        setAuto(rowAccess, access)
        setAuto(rowNotif, notif)
        setAuto(rowBattery, battery)
        setManual(rowRestricted, restricted)
        setManual(rowAutostart, autostart)
        setManual(rowLock, AppPrefs.getFlag(this, FLAG_LOCK))

        val items = mutableListOf(access, notif, battery, autostart, AppPrefs.getFlag(this, FLAG_LOCK))
        if (Build.VERSION.SDK_INT >= 33) items.add(restricted)
        if (Build.VERSION.SDK_INT >= 30) {
            val unusedOk = isAutoRevokeExempt()
            setAuto(rowUnused, unusedOk)
            items.add(unusedOk)
        }
        // Row 8: at least one payment app installed and all of them ticked
        items.add(
            installedPayApps.isNotEmpty() &&
                installedPayApps.all { AppPrefs.getFlag(this, FLAG_PAYAPP_PREFIX + it.first) }
        )
        val done = items.count { it }
        if (done == items.size) {
            tvProgress.text = "All set ✓ Soundbox will keep running"
            tvProgress.setTextColor(Color.parseColor(GREEN))
        } else {
            tvProgress.text = "$done of ${items.size} done — finish the ✗ items"
            tvProgress.setTextColor(Color.parseColor(RED))
        }
    }

    private fun isAutoRevokeExempt(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) packageManager.isAutoRevokeWhitelisted else true

    // ===== ANCHOR: PAY_APP_ROWS (one checklist row per installed payment app) =====
    @Suppress("DEPRECATION")
    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0); true
    } catch (e: Exception) { false }

    private fun buildPayAppRows() {
        installedPayApps = PaymentNotificationService.APP_NAMES
            .filter { isInstalled(it.key) }
            .map { it.key to it.value }

        llPayApps.removeAllViews()
        tvNoPayApps.visibility = if (installedPayApps.isEmpty()) View.VISIBLE else View.GONE

        for ((pkg, name) in installedPayApps) {
            val v = LayoutInflater.from(this).inflate(R.layout.row_check, llPayApps, false)
            val r = Row(v)
            r.title.text = name
            r.desc.text = "Notifications ON for $name. Tick when done."
            r.btn.text = "Open"
            r.btn.setOnClickListener { openOtherAppNotificationSettings(pkg) }
            r.icon.visibility = View.GONE
            r.cb.visibility = View.VISIBLE
            r.cb.isChecked = AppPrefs.getFlag(this, FLAG_PAYAPP_PREFIX + pkg)   // set before listener
            r.cb.setOnCheckedChangeListener { _, checked ->
                AppPrefs.setFlag(this, FLAG_PAYAPP_PREFIX + pkg, checked)
                updateChecklist()
            }
            llPayApps.addView(v)
        }
    }
    // ===== END ANCHOR: PAY_APP_ROWS =====
    // ===== END ANCHOR: CHECKLIST_LOGIC =====

    // ===== ANCHOR: CHECKLIST_ACTIONS (open the right settings screens) =====
    private fun openNotificationAccessSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openAppDetails() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun openAppNotificationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (e: Exception) {
            openAppDetails()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_BUTTON
            )
        } else {
            openAppNotificationSettings()
        }
    }

    @SuppressLint("BatteryLife")
    private fun askBatteryExemption() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                openAppDetails()
            }
        }
    }

    private fun openOtherAppNotificationSettings(pkg: String) {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            )
        } catch (e: Exception) {
            try {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                )
            } catch (e2: Exception) { /* nothing more we can do */ }
        }
    }

    private fun openAutoRevokeSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startActivity(
                    Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.parse("package:$packageName"))
                )
                return
            }
        } catch (e: Exception) { /* fall through */ }
        openAppDetails() // App info → Permissions → turn OFF "Pause app activity if unused"
    }

    private fun showLockHelp() {
        AlertDialog.Builder(this)
            .setTitle("Lock app in Recents")
            .setMessage(
                "1. Open Recents (recent apps screen).\n" +
                "2. Long-press the Payment Soundbox card (or tap its icon / ⋮ menu).\n" +
                "3. Choose Lock / Keep open.\n\n" +
                "Then come back and tick this box."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // Known Autostart screens of popular phone brands (add more if your phone isn't listed)
    private val autostartScreens = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
    )

    private fun openAutostartSettings() {
        for ((pkg, cls) in autostartScreens) {
            try {
                startActivity(Intent().setComponent(ComponentName(pkg, cls)))
                return
            } catch (e: Exception) { /* not this brand, try next */ }
        }
        openAppDetails() // fallback: App info → Battery / Autostart
    }
    // ===== END ANCHOR: CHECKLIST_ACTIONS =====

    // ===== ANCHOR: HISTORY_LIST =====
    private fun refreshHistory() {
        val list = AppPrefs.getHistory(this)
        adapter.submit(list)
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
    // ===== END ANCHOR: HISTORY_LIST =====
}
