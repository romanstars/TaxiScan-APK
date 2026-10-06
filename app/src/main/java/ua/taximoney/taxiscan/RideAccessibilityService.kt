package ua.taximoney.taxiscan

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale

class RideAccessibilityService : AccessibilityService() {
    private var lastSignature = ""
    private var lastSentAt = 0L
    private var lastAutoActionSignature = ""
    private var lastAutoActionAt = 0L
    private var overlayView: TextView? = null
    private lateinit var windowManager: WindowManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        val serviceName = RideCapture.serviceName(packageName)
        if (serviceName.isBlank()) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) removeOverlay()
            return
        }
        if (!DriverPrefs.isMonitoringAllowed(this, serviceName)) { removeOverlay(); return }

        val text = buildString {
            event.text.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it).append(' ') }
            appendVisibleText(rootInActiveWindow, this, 0)
        }
        val values = RideCapture.parse(text)
        storeLatestOffer(serviceName, values)
        tryAutoAction(serviceName, rootInActiveWindow, values, text)
        val overlayMode = DriverPrefs.prefs(this).getInt(DriverPrefs.OVERLAY_MODE, 1)
        if (overlayMode == 2) {
            val p = DriverPrefs.prefs(this)
            val activeTrip = p.getString(DriverPrefs.TIMER_ACTIVE_SERVICE, "") == serviceName &&
                p.getLong(DriverPrefs.TIMER_ACCEPTED_AT, 0L) > 0L &&
                p.getString(DriverPrefs.LAST_ACCEPTED_SERVICE, "") == serviceName
            val acceptedFare = p.getFloat(DriverPrefs.LAST_ACCEPTED_FARE, 0f).toDouble()
            val acceptedDistance = p.getFloat(DriverPrefs.LAST_ACCEPTED_DISTANCE, 0f).toDouble()
            if (activeTrip && acceptedFare > 0.0 && acceptedDistance > 0.0) showOverlay(serviceName, acceptedFare, acceptedDistance)
            else removeOverlay()
        } else if (values.fare != null && values.distance != null) {
            if (DriverPrefs.orderPassesFilter(this, values.fare, values.distance)) showOverlay(serviceName, values.fare, values.distance)
            else removeOverlay()
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            removeOverlay()
        }
        if (values.fare == null && values.distance == null) return

        val signature = "$serviceName:${values.fare}:${values.distance}"
        val now = System.currentTimeMillis()
        if (signature == lastSignature && now - lastSentAt < 5_000) return
        lastSignature = signature
        lastSentAt = now
        val p = DriverPrefs.prefs(this)
        val statsKey = "stats_${serviceName.lowercase(Locale.ROOT)}_last_signature"
        val wasNew = values.fare != null && values.distance != null &&
            DriverPrefs.orderPassesFilter(this, values.fare, values.distance) &&
            p.getString(statsKey, null) != "${values.fare}:${values.distance}"
        RideCapture.saveAndBroadcast(this, serviceName, values)
        if (wasNew && p.getBoolean(DriverPrefs.SOUND_ENABLED, false)) {
            val stream = if (p.getInt(DriverPrefs.SOUND_STREAM, 0) == 1) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
            runCatching { ToneGenerator(stream, 75).also { tone -> tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180); tone.release() } }
        }
    }

    private fun appendVisibleText(node: AccessibilityNodeInfo?, target: StringBuilder, depth: Int) {
        if (node == null || depth > 18) return
        node.text?.let { target.append(it).append(' ') }
        node.contentDescription?.let { target.append(it).append(' ') }
        for (i in 0 until node.childCount.coerceAtMost(80)) {
            appendVisibleText(node.getChild(i), target, depth + 1)
        }
    }

    private fun tryAutoAction(service: String, root: AccessibilityNodeInfo?, offer: RideCapture.Values, text: String) {
        val action = DriverPrefs.autoAction(this, service, offer, text)
        if (action == DriverPrefs.AutoAction.IGNORE) return
        val signature = "$service:${offer.fare}:${offer.distance}:${offer.pickupDistance}:$action"
        val now = System.currentTimeMillis()
        if (signature == lastAutoActionSignature && now - lastAutoActionAt < 30_000L) return
        val clicked = clickMatchingAction(root, action, 0)
        if (clicked) {
            lastAutoActionSignature = signature
            lastAutoActionAt = now
            val p = DriverPrefs.prefs(this)
            p.edit().putInt(DriverPrefs.AUTO_ACTIONS_COUNT, p.getInt(DriverPrefs.AUTO_ACTIONS_COUNT, 0) + 1)
                .putString("auto_accept_last_action", action.name).apply()
            RideCapture.logAutoAction(this, "$service • автодія: ${if (action == DriverPrefs.AutoAction.ACCEPT) "натиснуто прийняття" else "відхилено поза радіусом"}")
        }
    }

    private fun storeLatestOffer(service: String, offer: RideCapture.Values) {
        val p = DriverPrefs.prefs(this)
        val edit = p.edit()
        if (offer.fare != null || offer.distance != null || offer.pickupDistance != null) {
            edit.putString(DriverPrefs.LAST_OFFER_SERVICE, service)
        }
        offer.distance?.let { edit.putString(DriverPrefs.LAST_OFFER_TRIP_DISTANCE, String.format(Locale.US, "%.2f", it)) }
        offer.fare?.let { edit.putString(DriverPrefs.LAST_OFFER_FARE, String.format(Locale.US, "%.0f", it)) }
        offer.pickupDistance?.let { edit.putString(DriverPrefs.LAST_OFFER_PICKUP, String.format(Locale.US, "%.1f", it)) }
        if (offer.fare != null && offer.distance != null && offer.distance > 0.0) {
            edit.putString(DriverPrefs.LAST_OFFER_DISTANCE, String.format(Locale.US, "%.1f", offer.fare / offer.distance))
        }
        if (offer.fare != null && offer.durationMinutes != null && offer.durationMinutes > 0.0) {
            edit.putString(DriverPrefs.LAST_OFFER_HOURLY, String.format(Locale.US, "%.0f", offer.fare * 60.0 / offer.durationMinutes))
        }
        offer.rating?.let { edit.putString(DriverPrefs.LAST_OFFER_RATING, String.format(Locale.US, "%.1f", it)) }
        edit.apply()
    }

    private fun clickMatchingAction(node: AccessibilityNodeInfo?, action: DriverPrefs.AutoAction, depth: Int): Boolean {
        if (node == null || depth > 18) return false
        val raw = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ")
            .trim().lowercase(Locale.ROOT)
        val needles = if (action == DriverPrefs.AutoAction.ACCEPT)
            listOf("прийняти", "принять", "accept")
        else listOf("відхилити", "відхил", "reject", "decline", "пропустити")
        if (needles.any(raw::startsWith)) {
            var target: AccessibilityNodeInfo? = node
            repeat(5) {
                if (target?.isClickable == true) return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                target = target?.parent
            }
        }
        for (i in 0 until node.childCount.coerceAtMost(80)) {
            if (clickMatchingAction(node.getChild(i), action, depth + 1)) return true
        }
        return false
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun showOverlay(service: String, fare: Double, distance: Double) {
        if (!Settings.canDrawOverlays(this)) return
        val prefs = DriverPrefs.prefs(this)
        val commissionRate = prefs.getFloat("commission_percent", 15f).toDouble()
        val costPerKm = prefs.getFloat("cost_per_km", 9f).toDouble()
        val commission = fare * commissionRate / 100.0
        val net = fare - commission - distance * costPerKm
        val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("uk-UA")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val text = "$service  •  ${format.format(fare)} ₴  •  ${format.format(distance)} км\n" +
            "Чистими: ${format.format(net)} ₴  (${format.format(net / distance)} ₴/км)"

        val color = runCatching { Color.parseColor(prefs.getString(DriverPrefs.OVERLAY_COLOR, "#32BB78")) }.getOrDefault(Color.rgb(50,187,120))
        val view = overlayView ?: TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(20, 28, 25))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), color)
            }
            elevation = dp(8).toFloat()
            overlayView = this
        }
        view.text = text
        view.textSize = prefs.getFloat(DriverPrefs.OVERLAY_TEXT_SIZE, 14f).coerceIn(10f, 28f)
        view.alpha = prefs.getFloat(DriverPrefs.OVERLAY_ALPHA, 1f).coerceIn(.2f, 1f)
        (view.background as? GradientDrawable)?.setStroke(dp(1), color)
        if (view.parent == null) {
            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(72)
                width = resources.displayMetrics.widthPixels - dp(24)
            }
            runCatching { windowManager.addView(view, params) }
                .onFailure { overlayView = null }
        } else {
            runCatching { windowManager.updateViewLayout(view, view.layoutParams) }
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view -> runCatching { windowManager.removeView(view) } }
        overlayView = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
