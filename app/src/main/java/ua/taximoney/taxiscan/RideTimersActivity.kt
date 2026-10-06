package ua.taximoney.taxiscan

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Manual trip timer dashboard. It records driver-confirmed stages and never infers acceptance. */
class RideTimersActivity : AppCompatActivity() {
    private val prefs by lazy { DriverPrefs.prefs(this) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var summary: TextView
    private lateinit var stage: TextView
    private lateinit var serviceLabel: TextView
    private var selectedService = "Bolt"
    private val tick = object : Runnable {
        override fun run() {
            if (::summary.isInitialized) refresh()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedService = prefs.getString(DriverPrefs.TIMER_ACTIVE_SERVICE, "Bolt").orEmpty().ifBlank { "Bolt" }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(16))
            setBackgroundColor(Color.rgb(10, 18, 33))
        }
        page.addView(TextView(this).apply {
            text = "⏱  Таймери поїздки"
            textSize = 23f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(12))
        })
        summary = panel("Загальний час: 00:00:00\nПрийнято: 0 поїздок • 0 ₴")
        page.addView(summary, matchWrap())
        serviceLabel = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.rgb(68, 215, 163))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(5), dp(14), dp(5), dp(6))
        }
        page.addView(serviceLabel)
        val serviceButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Bolt", "Uklon", "Uber").forEach { service ->
            serviceButtons.addView(button(service, Color.rgb(31, 44, 62)) {
                if (activeService().isNotBlank() && activeService() != service) {
                    toast("Завершіть активну поїздку ${activeService()} перед перемиканням")
                } else {
                    selectedService = service
                    prefs.edit().putString(DriverPrefs.TIMER_ACTIVE_SERVICE, service).apply()
                    refresh()
                }
            }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(6) })
        }
        page.addView(serviceButtons)
        stage = panel("")
        page.addView(stage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val actions = listOf(
            "✅  Прийняв" to Color.rgb(17, 112, 64),
            "🚗  Зустрів" to Color.rgb(15, 73, 112),
            "⏳  Простій" to Color.rgb(124, 91, 22),
            "⌛  Очікування" to Color.rgb(81, 52, 130),
            "🏁  Завершив поїздку" to Color.rgb(153, 40, 49)
        )
        actions.forEachIndexed { index, pair ->
            page.addView(button(pair.first, pair.second) { onStage(index) }, buttonParams())
        }
        page.addView(button("🧪  Імітація тригерів: ${if (prefs.getBoolean(DriverPrefs.TIMER_SIMULATION, false)) "УВІМКНЕНО" else "ВИМКНЕНО"}", Color.rgb(37, 61, 75)) {
            val next = !prefs.getBoolean(DriverPrefs.TIMER_SIMULATION, false)
            prefs.edit().putBoolean(DriverPrefs.TIMER_SIMULATION, next).apply()
            (it as Button).text = "🧪  Імітація тригерів: ${if (next) "УВІМКНЕНО" else "ВИМКНЕНО"}"
            toast(if (next) "Тестові натискання записуватимуть ті самі етапи" else "Імітацію вимкнено")
        }, buttonParams())
        page.addView(button("🧹  Скинути таймери", Color.rgb(48, 55, 66)) { confirmReset() }, buttonParams())
        page.addView(button("Готово", Color.rgb(17, 112, 64)) { finish() }, buttonParams())
        val scroll = ScrollView(this).apply { addView(page) }
        setContentView(scroll)
        refresh()
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }

    private fun onStage(action: Int) {
        val now = System.currentTimeMillis()
        val edit = prefs.edit()
        when (action) {
            0 -> {
                if (activeService().isNotBlank() && activeService() != selectedService) {
                    toast("Спершу завершіть активну поїздку ${activeService()}"); return
                }
                if (activeService() == selectedService && prefs.getLong(DriverPrefs.TIMER_ACCEPTED_AT, 0L) > 0L) {
                    toast("Таймер цієї поїздки вже працює"); return
                }
                edit.putString(DriverPrefs.TIMER_ACTIVE_SERVICE, selectedService)
                    .putLong(DriverPrefs.TIMER_ACCEPTED_AT, now)
                    .putLong(DriverPrefs.TIMER_MEETING_AT, 0L)
                    .putLong(DriverPrefs.TIMER_IDLE_AT, 0L)
                    .putLong(DriverPrefs.TIMER_WAIT_AT, 0L)
                if (prefs.getLong(DriverPrefs.TIMER_GENERAL_STARTED_AT, 0L) == 0L)
                    edit.putLong(DriverPrefs.TIMER_GENERAL_STARTED_AT, now)
                recordAcceptedTrip(edit, now)
                toast("Поїздку ${selectedService} позначено як прийняту")
            }
            1 -> if (requireActive()) edit.putLong(DriverPrefs.TIMER_MEETING_AT, now)
                .putLong(DriverPrefs.TIMER_WAIT_AT, 0L).putLong(DriverPrefs.TIMER_IDLE_AT, 0L) else return
            2 -> if (requireActive()) edit.putLong(DriverPrefs.TIMER_IDLE_AT, now)
                .putLong(DriverPrefs.TIMER_WAIT_AT, 0L) else return
            3 -> if (requireActive()) edit.putLong(DriverPrefs.TIMER_WAIT_AT, now)
                .putLong(DriverPrefs.TIMER_IDLE_AT, 0L) else return
            4 -> {
                if (!requireActive()) return
                val accepted = prefs.getLong(DriverPrefs.TIMER_ACCEPTED_AT, 0L)
                val minutes = if (accepted > 0L) ((now - accepted) / 60_000L).coerceAtLeast(0L) else 0L
                val summary = prefs.getString(DriverPrefs.LAST_ACCEPTED_SUMMARY, selectedService).orEmpty()
                edit.putString(DriverPrefs.LAST_ACCEPTED_SUMMARY, "$summary • ${minutes} хв • завершено ${clock(now)}")
                    .putString(DriverPrefs.TIMER_ACTIVE_SERVICE, "")
                    .putLong(DriverPrefs.TIMER_ACCEPTED_AT, 0L)
                    .putLong(DriverPrefs.TIMER_MEETING_AT, 0L)
                    .putLong(DriverPrefs.TIMER_IDLE_AT, 0L)
                    .putLong(DriverPrefs.TIMER_WAIT_AT, 0L)
                    .putLong(DriverPrefs.TIMER_GENERAL_STARTED_AT, 0L)
                    .putInt("timer_${selectedService.lowercase(Locale.ROOT)}_completed", prefs.getInt("timer_${selectedService.lowercase(Locale.ROOT)}_completed", 0) + 1)
                toast("Поїздку завершено")
            }
        }
        edit.apply()
        refresh()
    }

    private fun recordAcceptedTrip(edit: android.content.SharedPreferences.Editor, now: Long) {
        val lastService = prefs.getString(DriverPrefs.LAST_OFFER_SERVICE, null)
        val fare = prefs.getString(DriverPrefs.LAST_OFFER_FARE, null)?.replace(',', '.')?.toFloatOrNull()
        val distance = prefs.getString(DriverPrefs.LAST_OFFER_TRIP_DISTANCE, null)?.replace(',', '.')?.toFloatOrNull()
        val amount = if (lastService == selectedService && fare != null) fare else null
        val key = selectedService.lowercase(Locale.ROOT)
        val tripsKey = "accepted_stats_${key}_trips"
        edit.putInt(tripsKey, prefs.getInt(tripsKey, 0) + 1)
        val dateToken = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(now))
        if (prefs.getString("accepted_stats_day", "") != dateToken) {
            edit.putString("accepted_stats_day", dateToken).putLong("accepted_stats_day_started_at", now)
            listOf("bolt", "uklon", "uber").forEach { serviceKey ->
                edit.putFloat("accepted_stats_today_${serviceKey}_revenue", 0f)
                    .putInt("accepted_stats_today_${serviceKey}_trips", 0)
            }
        }
        val todayTripsKey = "accepted_stats_today_${key}_trips"
        edit.putInt(todayTripsKey, prefs.getInt(todayTripsKey, 0) + 1)
        if (amount != null) {
            val revenueKey = "accepted_stats_${key}_revenue"
            val todayRevenueKey = "accepted_stats_today_${key}_revenue"
            edit.putFloat(revenueKey, prefs.getFloat(revenueKey, 0f) + amount)
                .putFloat(todayRevenueKey, prefs.getFloat(todayRevenueKey, 0f) + amount)
                .putString(DriverPrefs.LAST_ACCEPTED_SUMMARY, "$selectedService • ${amount.toInt()} ₴ • ${clock(now)}")
                .putLong(DriverPrefs.LAST_ACCEPTED_AT, now)
                .putString(DriverPrefs.LAST_ACCEPTED_SERVICE, selectedService)
                .putFloat(DriverPrefs.LAST_ACCEPTED_FARE, amount)
            if (lastService == selectedService && distance != null && distance > 0f)
                edit.putFloat(DriverPrefs.LAST_ACCEPTED_DISTANCE, distance)
        } else {
            edit.putString(DriverPrefs.LAST_ACCEPTED_SUMMARY, "$selectedService • прийнято ${clock(now)} (сума невідома)")
                .putLong(DriverPrefs.LAST_ACCEPTED_AT, now)
                .putString(DriverPrefs.LAST_ACCEPTED_SERVICE, selectedService)
                .putFloat(DriverPrefs.LAST_ACCEPTED_FARE, 0f)
                .putFloat(DriverPrefs.LAST_ACCEPTED_DISTANCE, 0f)
        }
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        val generalStart = prefs.getLong(DriverPrefs.TIMER_GENERAL_STARTED_AT, 0L)
        val acceptedCount = listOf("bolt", "uklon", "uber").sumOf { prefs.getInt("accepted_stats_${it}_trips", 0) }
        val revenue = listOf("bolt", "uklon", "uber").sumOf { prefs.getFloat("accepted_stats_${it}_revenue", 0f).toDouble() }
        summary.text = "⏱ Загальний час: ${if (generalStart > 0) elapsed(now - generalStart) else "00:00:00"}\n💰 Прийнято: $acceptedCount поїздок • ${revenue.toInt()} ₴\n${prefs.getString(DriverPrefs.LAST_ACCEPTED_SUMMARY, "Останньої поїздки ще немає") ?: "Останньої поїздки ще немає"}"
        val active = activeService()
        selectedService = active.ifBlank { selectedService }
        serviceLabel.text = "${selectedService.uppercase(Locale.ROOT)}${if (active.isNotBlank()) "  •  АКТИВНА ПОЇЗДКА" else "  •  очікує старту"}"
        val accepted = prefs.getLong(DriverPrefs.TIMER_ACCEPTED_AT, 0L)
        val meeting = prefs.getLong(DriverPrefs.TIMER_MEETING_AT, 0L)
        val idle = prefs.getLong(DriverPrefs.TIMER_IDLE_AT, 0L)
        val wait = prefs.getLong(DriverPrefs.TIMER_WAIT_AT, 0L)
        val current = when {
            active.isBlank() -> "Статус: не активна"
            wait > 0L -> "⌛ Очікування: ${elapsed(now - wait)}"
            idle > 0L -> "⏳ Простій: ${elapsed(now - idle)}"
            meeting > 0L -> "🚗 Після зустрічі: ${elapsed(now - meeting)}"
            accepted > 0L -> "✅ Після прийняття: ${elapsed(now - accepted)}"
            else -> "Статус: не активна"
        }
        val lastOffer = prefs.getString(DriverPrefs.LAST_OFFER_FARE, null)?.let { "$it ₴" } ?: "—"
        stage.text = "$current\n\nОстання пропозиція: $lastOffer • ${prefs.getString(DriverPrefs.LAST_OFFER_SERVICE, "—") ?: "—"}\nТаймери рахуються після ручного натискання етапу."
    }

    private fun requireActive(): Boolean {
        if (activeService() == selectedService && prefs.getLong(DriverPrefs.TIMER_ACCEPTED_AT, 0L) > 0L) return true
        toast("Спочатку позначте поїздку як прийняту")
        return false
    }

    private fun activeService() = prefs.getString(DriverPrefs.TIMER_ACTIVE_SERVICE, "").orEmpty()

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this).setTitle("Скинути активний таймер?")
            .setMessage("Лічильники поїздок і суми не видаляться.")
            .setNegativeButton("Скасувати", null)
            .setPositiveButton("Скинути") { _, _ ->
                prefs.edit().putString(DriverPrefs.TIMER_ACTIVE_SERVICE, "")
                    .putLong(DriverPrefs.TIMER_ACCEPTED_AT, 0L).putLong(DriverPrefs.TIMER_MEETING_AT, 0L)
                    .putLong(DriverPrefs.TIMER_IDLE_AT, 0L).putLong(DriverPrefs.TIMER_WAIT_AT, 0L)
                    .putLong(DriverPrefs.TIMER_GENERAL_STARTED_AT, 0L).apply()
                refresh()
            }.show()
    }

    private fun panel(textValue: String) = TextView(this).apply {
        text = textValue; textSize = 16f; setTextColor(Color.rgb(209, 222, 237))
        setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(Color.rgb(29, 42, 62), Color.rgb(51, 68, 92))
    }
    private fun button(title: String, color: Int, action: (android.view.View) -> Unit) = Button(this).apply {
        text = title; textSize = 15f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
        background = rounded(color, color); setOnClickListener(action)
    }
    private fun buttonParams() = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply { setColor(fill); cornerRadius = dp(16).toFloat(); setStroke(dp(1), stroke) }
    private fun elapsed(ms: Long): String { val sec=(ms/1000L).coerceAtLeast(0); return String.format(Locale.ROOT,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60) }
    private fun clock(at: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
