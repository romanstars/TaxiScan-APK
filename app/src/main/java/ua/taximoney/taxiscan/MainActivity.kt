package ua.taximoney.taxiscan

import android.content.Intent
import android.content.ComponentName
import android.provider.Settings
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings.Secure
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import java.text.NumberFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvSubTitle: TextView
    private lateinit var tvSubStatus: TextView
    private lateinit var tvPriceValue: TextView
    private lateinit var tvCommissionValue: TextView
    private lateinit var etTripAmount: EditText
    private lateinit var etTripDistance: EditText
    private lateinit var layoutCalcResult: View
    private lateinit var tvNetResult: TextView
    private lateinit var tvOrderBreakdown: TextView
    private lateinit var tvPerKm: TextView
    private lateinit var tvNotificationAccessStatus: TextView
    private lateinit var tvUklonStatus: TextView
    private lateinit var tvBoltStatus: TextView
    private lateinit var tvUberStatus: TextView
    private lateinit var tvOverlayAccess: TextView
    private lateinit var tvScreenAccessStatus: TextView
    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvPermissionsProgress: TextView
    private lateinit var tvPermissionsTitle: TextView
    private lateinit var progressPermissions: ProgressBar
    private lateinit var tvRadarTitle: TextView
    private lateinit var tvRadarBadge: TextView
    private lateinit var tvRadarSwitchLabel: TextView
    private lateinit var switchRadar: SwitchMaterial
    private lateinit var tvTodayRevenue: TextView
    private lateinit var tvTodayTrips: TextView
    private lateinit var tvHourlyRevenue: TextView
    private lateinit var homeScroll: androidx.core.widget.NestedScrollView
    private var isSubscribed = false
    private val preferences by lazy { getSharedPreferences("taxiscan_settings", MODE_PRIVATE) }
    private val numberFormat by lazy {
        NumberFormat.getNumberInstance(Locale.forLanguageTag("uk-UA")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    private val orderReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action != RideCapture.ACTION_ORDER_CAPTURED) return
            applyCapturedOrder(
                intent.getStringExtra(RideCapture.EXTRA_SERVICE).orEmpty(),
                intent.getDoubleExtra(RideCapture.EXTRA_FARE, -1.0).takeIf { it > 0 },
                intent.getDoubleExtra(RideCapture.EXTRA_DISTANCE, -1.0).takeIf { it > 0 }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSubTitle = findViewById(R.id.tvSubTitle)
        tvSubStatus = findViewById(R.id.tvSubStatus)
        tvPriceValue = findViewById(R.id.tvPriceValue)
        tvCommissionValue = findViewById(R.id.tvCommissionValue)
        etTripAmount = findViewById(R.id.etTripAmount)
        etTripDistance = findViewById(R.id.etTripDistance)
        layoutCalcResult = findViewById(R.id.layoutCalcResult)
        tvNetResult = findViewById(R.id.tvNetResult)
        tvOrderBreakdown = findViewById(R.id.tvOrderBreakdown)
        tvPerKm = findViewById(R.id.tvPerKm)
        tvNotificationAccessStatus = findViewById(R.id.tvNotificationAccessStatus)
        tvUklonStatus = findViewById(R.id.tvUklonStatus)
        tvBoltStatus = findViewById(R.id.tvBoltStatus)
        tvUberStatus = findViewById(R.id.tvUberStatus)
        tvOverlayAccess = findViewById(R.id.btnOverlayAccess)
        tvScreenAccessStatus = findViewById(R.id.tvScreenAccessStatus)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        tvPermissionsProgress = findViewById(R.id.tvPermissionsProgress)
        tvPermissionsTitle = findViewById(R.id.tvPermissionsTitle)
        progressPermissions = findViewById(R.id.progressPermissions)
        tvRadarTitle = findViewById(R.id.tvRadarTitle)
        tvRadarBadge = findViewById(R.id.tvRadarBadge)
        tvRadarSwitchLabel = findViewById(R.id.tvRadarSwitchLabel)
        switchRadar = findViewById(R.id.switchRadar)
        tvTodayRevenue = findViewById(R.id.tvTodayRevenue)
        tvTodayTrips = findViewById(R.id.tvTodayTrips)
        tvHourlyRevenue = findViewById(R.id.tvHourlyRevenue)
        homeScroll = findViewById(R.id.homeScroll)
        val brand = SpannableString("TaxiScan")
        brand.setSpan(ForegroundColorSpan(Color.parseColor("#FFD600")), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        brand.setSpan(ForegroundColorSpan(Color.parseColor("#32D98A")), 4, brand.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        findViewById<TextView>(R.id.tvBrand).text = brand
        updateSettingsLabels()

        switchRadar.setOnCheckedChangeListener { _, checked ->
            val p = DriverPrefs.prefs(this)
            val serviceKeys = listOf(DriverPrefs.BOLT, DriverPrefs.UKLON, DriverPrefs.UBER)
            if (checked && serviceKeys.none { p.getBoolean(it, true) }) {
                p.edit().putBoolean(DriverPrefs.BOLT, true).putBoolean(DriverPrefs.UKLON, true)
                    .putBoolean(DriverPrefs.UBER, true).apply()
            }
            p.edit().putBoolean(DriverPrefs.MONITORING, checked).apply()
            updateRadarState()
        }

        findViewById<MaterialButton>(R.id.btnCalculate).setOnClickListener {
            calculateTrip()
        }

        findViewById<View>(R.id.itemPrice).setOnClickListener {
            showNumberSettingDialog(
                title = getString(R.string.car_cost_dialog_title),
                preferenceKey = KEY_COST_PER_KM,
                currentValue = costPerKm,
                minimum = 0.0,
                maximum = 100_000.0
            )
        }

        findViewById<View>(R.id.itemCommission).setOnClickListener {
            showNumberSettingDialog(
                title = getString(R.string.commission_dialog_title),
                preferenceKey = KEY_COMMISSION,
                currentValue = commissionPercent,
                minimum = 0.0,
                maximum = 100.0
            )
        }

        findViewById<MaterialCardView>(R.id.cardSubscription).setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }

        findViewById<MaterialCardView>(R.id.cardUklon).setOnClickListener {
            openServiceApp("Uklon", "ua.com.uklon.uklondriver")
        }

        findViewById<MaterialCardView>(R.id.cardBolt).setOnClickListener {
            openServiceApp("Bolt", "ee.mtakso.driver")
        }
        findViewById<MaterialCardView>(R.id.cardUber).setOnClickListener {
            openServiceApp("Uber", "com.ubercab.driver")
        }

        findViewById<MaterialButton>(R.id.btnNotificationAccess).setOnClickListener {
            showAccessChoices()
        }
        tvOverlayAccess.setOnClickListener { showOverlayDisclosure() }
        findViewById<MaterialButton>(R.id.btnSystemPermissions).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        findViewById<View>(R.id.btnDriverTools).setOnClickListener {
            startActivity(Intent(this, DriverToolsActivity::class.java))
        }

        if (savedInstanceState == null && preferences.getBoolean(DriverPrefs.AUTO_LAUNCH_BOLT, false)) {
            openServiceApp("Bolt", "ee.mtakso.driver")
        }

        findViewById<TextView>(R.id.tvJoinGroup).setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
        findViewById<View>(R.id.navHome).setOnClickListener { selectBottomTab(R.id.navHome); homeScroll.smoothScrollTo(0, 0) }
        findViewById<View>(R.id.navCalculator).setOnClickListener {
            selectBottomTab(R.id.navCalculator)
            val calculator = findViewById<View>(R.id.cardCalculator)
            val location = IntArray(2)
            calculator.getLocationInWindow(location)
            homeScroll.smoothScrollTo(0, homeScroll.scrollY + location[1] - homeScroll.top)
            etTripAmount.requestFocus()
        }
        findViewById<View>(R.id.navStatistics).setOnClickListener { selectBottomTab(R.id.navStatistics); showHomeStats() }
        findViewById<View>(R.id.navSettings).setOnClickListener { selectBottomTab(R.id.navSettings); startActivity(Intent(this, DriverToolsActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        updateSubscriptionCard()
        updateIntegrationStatus()
        updateDailyStats()
        RideCapture.takePending(this)?.let { (service, fare, distance) ->
            applyCapturedOrder(service, fare, distance)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            orderReceiver,
            android.content.IntentFilter(RideCapture.ACTION_ORDER_CAPTURED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(orderReceiver)
        super.onStop()
    }

    private fun updateSubscriptionCard() {
        // In production check via PlayBillingManager or SharedPreferences / Firestore
        if (isSubscribed) {
            tvSubTitle.text = getString(R.string.subscription_active)
            tvSubStatus.text = "Підписка діє"
            tvSubStatus.setTextColor(Color.parseColor("#32BB78"))
        } else {
            tvSubTitle.text = getString(R.string.subscription_title)
            tvSubStatus.text = getString(R.string.subscription_inactive) + " • Натисніть, щоб оформити"
            tvSubStatus.setTextColor(Color.parseColor("#B0BEC5"))
        }
    }

    private val costPerKm: Double
        get() = preferences.getFloat(KEY_COST_PER_KM, DEFAULT_COST_PER_KM.toFloat()).toDouble()

    private val commissionPercent: Double
        get() = preferences.getFloat(KEY_COMMISSION, DEFAULT_COMMISSION.toFloat()).toDouble()

    private fun updateSettingsLabels() {
        tvPriceValue.text = "${formatNumber(costPerKm)} ₴/км"
        tvCommissionValue.text = "${formatNumber(commissionPercent)}%"
    }

    private fun showNumberSettingDialog(
        title: String,
        preferenceKey: String,
        currentValue: Double,
        minimum: Double,
        maximum: Double
    ) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(currentValue.toString())
            setSelection(text.length)
            hint = "0"
            setPadding(48, 32, 48, 24)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = parseNumber(input.text.toString())
                if (value == null || value < minimum || value > maximum) {
                    Toast.makeText(this, getString(R.string.invalid_setting), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                preferences.edit().putFloat(preferenceKey, value.toFloat()).apply()
                updateSettingsLabels()
                Toast.makeText(this, getString(R.string.value_saved), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun calculateTrip() {
        val fare = parseNumber(etTripAmount.text?.toString().orEmpty())
        val distance = parseNumber(etTripDistance.text?.toString().orEmpty())
        if (fare == null || distance == null || fare <= 0.0 || distance <= 0.0) {
            etTripAmount.error = if (fare == null || fare <= 0.0) getString(R.string.invalid_trip) else null
            etTripDistance.error = if (distance == null || distance <= 0.0) getString(R.string.invalid_trip) else null
            return
        }

        etTripAmount.error = null
        etTripDistance.error = null
        val commission = fare * commissionPercent / 100.0
        val vehicleCost = distance * costPerKm
        val net = fare - commission - vehicleCost

        tvNetResult.text = formatMoney(net)
        tvNetResult.setTextColor(getColor(if (net >= 0.0) R.color.primary else R.color.error))
        tvOrderBreakdown.text = getString(
            R.string.calculator_breakdown,
            formatMoney(commission),
            formatMoney(vehicleCost)
        )
        tvPerKm.text = getString(R.string.net_per_km, formatMoney(net / distance))
        layoutCalcResult.visibility = View.VISIBLE
    }

    private fun parseNumber(raw: String): Double? =
        raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun formatNumber(value: Double): String = numberFormat.format(value)

    private fun formatMoney(value: Double): String = "${numberFormat.format(value)} ₴"

    private fun showAccessChoices() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.accessibility_disclosure_title)
            .setMessage(R.string.accessibility_disclosure)
            .setPositiveButton(R.string.enable_screen_reading) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNeutralButton(R.string.enable_notifications) { _, _ -> showNotificationDisclosure() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNotificationDisclosure() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notification_listener_label)
            .setMessage(R.string.notification_access_disclosure)
            .setPositiveButton(R.string.enable_notifications) { _, _ ->
                runCatching { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
                    .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showOverlayDisclosure() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.overlay_disclosure_title)
            .setMessage(R.string.overlay_disclosure)
            .setPositiveButton(R.string.allow_overlay) { _, _ ->
                if (!Settings.canDrawOverlays(this)) {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openServiceApp(name: String, packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.app_not_installed, name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.install) { _, _ ->
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                runCatching { startActivity(market) }.onFailure {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
                }
            }
            .show()
    }

    private fun updateIntegrationStatus() {
        val screenReaderOn = isAccessibilityEnabled()
        val notificationsOn = isNotificationAccessEnabled()
        val overlayOn = Settings.canDrawOverlays(this)
        setPermissionRow(tvScreenAccessStatus, screenReaderOn, "Доступ до екрана")
        setPermissionRow(tvNotificationStatus, notificationsOn, "Сповіщення")
        tvNotificationAccessStatus.text = if (screenReaderOn || notificationsOn) getString(R.string.notification_access_on) else getString(R.string.notification_access_off)
        tvNotificationAccessStatus.setTextColor(if (screenReaderOn || notificationsOn) getColor(R.color.primary) else getColor(R.color.on_surface_variant))
        tvUklonStatus.text = installedStatus("ua.com.uklon.uklondriver")
        tvBoltStatus.text = installedStatus("ee.mtakso.driver")
        tvUberStatus.text = installedStatus("com.ubercab.driver")
        setPermissionRow(tvOverlayAccess, overlayOn, "Плаваючий розрахунок")
        val ready = listOf(screenReaderOn, notificationsOn, overlayOn).count { it }
        val percent = (ready * 100) / 3
        tvPermissionsTitle.text = "Налаштування • $ready з 3"
        tvPermissionsProgress.text = "$percent%"
        progressPermissions.progress = ready
        tvScreenAccessStatus.setOnClickListener { showAccessChoices() }
        tvNotificationStatus.setOnClickListener { showNotificationDisclosure() }
        tvBoltStatus.setTextColor(getColor(if (tvBoltStatus.text.toString().startsWith("●")) R.color.primary else R.color.on_surface_variant))
        tvUklonStatus.setTextColor(getColor(if (tvUklonStatus.text.toString().startsWith("●")) R.color.primary else R.color.on_surface_variant))
        tvUberStatus.setTextColor(getColor(if (tvUberStatus.text.toString().startsWith("●")) R.color.primary else R.color.on_surface_variant))
        updateRadarState()
    }

    private fun setPermissionRow(view: TextView, enabled: Boolean, label: String) {
        view.text = "${if (enabled) "✓" else "○"}  $label"
        view.setTextColor(getColor(if (enabled) R.color.primary else if (label.contains("Плаваючий")) R.color.secondary else R.color.on_surface_variant))
    }

    private fun updateRadarState() {
        val enabled = DriverPrefs.prefs(this).getBoolean(DriverPrefs.MONITORING, true)
        if (switchRadar.isChecked != enabled) switchRadar.isChecked = enabled
        tvRadarTitle.text = if (enabled) "Радар увімкнено" else "Радар на паузі"
        tvRadarBadge.text = if (enabled) "●  АКТИВНИЙ" else "●  ПАУЗА"
        tvRadarBadge.setTextColor(getColor(if (enabled) R.color.primary else R.color.on_surface_variant))
        tvRadarSwitchLabel.text = if (enabled) "Увімк." else "Вимк."
    }

    private fun updateDailyStats() {
        val p = DriverPrefs.prefs(this)
        val revenue = listOf("bolt", "uklon", "uber").sumOf { p.getFloat("accepted_stats_today_${it}_revenue", 0f).toDouble() }
        val trips = listOf("bolt", "uklon", "uber").sumOf { p.getInt("accepted_stats_today_${it}_trips", 0) }
        val dayStart = p.getLong("accepted_stats_day_started_at", 0L)
        val hours = if (dayStart > 0L) ((System.currentTimeMillis() - dayStart) / 3_600_000.0).coerceAtLeast(1.0 / 60.0) else 0.0
        val perHour = if (hours > 0.0) revenue / hours else 0.0
        tvTodayRevenue.text = "${revenue.toInt()} ₴"
        tvTodayTrips.text = trips.toString()
        tvHourlyRevenue.text = "${perHour.toInt()} ₴"
    }

    private fun showHomeStats() {
        val p = DriverPrefs.prefs(this)
        val last = p.getString(DriverPrefs.LAST_ACCEPTED_SUMMARY, "Ще немає прийнятих поїздок")
        val rows = listOf("Bolt", "Uklon", "Uber").map { service ->
            val key = service.lowercase(Locale.ROOT)
            "$service: ${p.getInt("accepted_stats_${key}_trips", 0)} поїздок • ${p.getFloat("accepted_stats_${key}_revenue", 0f).toInt()} ₴"
        }
        MaterialAlertDialogBuilder(this).setTitle("Статистика поїздок")
            .setMessage(rows.joinToString("\n") + "\n\nОстання: $last")
            .setPositiveButton("Відкрити таймери") { _, _ -> startActivity(Intent(this, RideTimersActivity::class.java)) }
            .setNegativeButton("Закрити", null).show()
    }

    private fun selectBottomTab(selectedId: Int) {
        val items = listOf(
            Triple(R.id.navHome, R.id.navMarkHome, R.color.primary),
            Triple(R.id.navCalculator, R.id.navMarkCalculator, R.color.on_surface_variant),
            Triple(R.id.navStatistics, R.id.navMarkStatistics, R.color.on_surface_variant),
            Triple(R.id.navSettings, R.id.navMarkSettings, R.color.on_surface_variant)
        )
        items.forEach { (itemId, markerId, _) ->
            val item = findViewById<LinearLayout>(itemId)
            val active = itemId == selectedId
            findViewById<View>(markerId).visibility = if (active) View.VISIBLE else View.INVISIBLE
            val color = getColor(if (active) R.color.primary else R.color.on_surface_variant)
            for (i in 1 until item.childCount) (item.getChildAt(i) as? TextView)?.setTextColor(color)
        }
    }

    private fun installedStatus(packageName: String): String = try {
        packageManager.getApplicationInfo(packageName, 0)
        "●  Встановлено"
    } catch (_: Exception) {
        "○  Не встановлено"
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Secure.getString(contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val component = ComponentName(this, RideAccessibilityService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val enabled = Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        val component = ComponentName(this, RideNotificationListener::class.java).flattenToString()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private fun applyCapturedOrder(service: String, fare: Double?, distance: Double?) {
        if (fare != null) etTripAmount.setText(formatNumber(fare))
        if (distance != null) etTripDistance.setText(formatNumber(distance))
        if (fare != null && distance != null) calculateTrip()
        Toast.makeText(
            this,
            getString(if (fare != null || distance != null) R.string.order_values_detected else R.string.order_detected, service),
            Toast.LENGTH_LONG
        ).show()
    }

    companion object {
        private const val KEY_COST_PER_KM = "cost_per_km"
        private const val KEY_COMMISSION = "commission_percent"
        private const val DEFAULT_COST_PER_KM = 9.0
        private const val DEFAULT_COMMISSION = 15.0
    }
}
