package ua.taximoney.taxiscan

import android.content.Context
import android.content.SharedPreferences

object DriverPrefs {
    const val FILE = "taxiscan_settings"
    const val MONITORING = "monitoring_enabled"
    const val BOLT = "monitor_bolt"
    const val UKLON = "monitor_uklon"
    const val UBER = "monitor_uber"
    const val FILTER_ENABLED = "filter_enabled"
    const val FILTER_MIN_FARE = "filter_min_fare"
    const val FILTER_MIN_NET_KM = "filter_min_net_per_km"
    const val FILTER_MAX_DISTANCE = "filter_max_distance"
    const val AUTO_ACCEPT_ENABLED = "auto_accept_enabled"
    const val AUTO_REJECT_OUTSIDE_RADIUS = "auto_reject_outside_radius"
    const val AUTO_FILTER_MIN_FARE = "auto_filter_min_fare"
    const val AUTO_FILTER_MIN_PER_KM = "auto_filter_min_per_km"
    const val FILTER_MAX_PICKUP = "auto_filter_max_pickup"
    const val FILTER_MIN_HOURLY = "auto_filter_min_hourly"
    const val FILTER_MIN_RATING = "auto_filter_min_rating"
    const val FILTER_REQUIRED_ADDRESSES = "auto_filter_required_addresses"
    const val FILTER_EXCLUDED_ADDRESSES = "auto_filter_excluded_addresses"
    const val FILTER_TEMPLATE = "auto_filter_template"
    const val FILTER_SCROLL_STEP = "auto_filter_scroll_step"
    const val AUTO_ACTIONS_COUNT = "auto_accept_actions_count"
    const val LAST_OFFER_FARE = "last_offer_fare"
    const val LAST_OFFER_DISTANCE = "last_offer_distance"
    const val LAST_OFFER_PICKUP = "last_offer_pickup"
    const val LAST_OFFER_HOURLY = "last_offer_hourly"
    const val LAST_OFFER_RATING = "last_offer_rating"
    const val LAST_OFFER_SERVICE = "last_offer_service"
    const val LAST_ACCEPTED_SUMMARY = "last_accepted_summary"
    const val LAST_ACCEPTED_AT = "last_accepted_at"
    const val TIMER_ACTIVE_SERVICE = "timer_active_service"
    const val TIMER_ACCEPTED_AT = "timer_accepted_at"
    const val TIMER_MEETING_AT = "timer_meeting_at"
    const val TIMER_IDLE_AT = "timer_idle_at"
    const val TIMER_WAIT_AT = "timer_wait_at"
    const val TIMER_GENERAL_STARTED_AT = "timer_general_started_at"
    const val TIMER_SIMULATION = "timer_simulation_enabled"
    const val SOUND_ENABLED = "sound_enabled"
    const val SOUND_STREAM = "sound_stream"
    const val OVERLAY_MODE = "overlay_mode"
    const val OVERLAY_COLOR = "overlay_color"
    const val OVERLAY_ALPHA = "overlay_alpha"
    const val OVERLAY_TEXT_SIZE = "overlay_text_size"
    const val AUTO_LAUNCH_BOLT = "auto_launch_bolt"
    const val AUTO_ACCEPT_SCHEDULED = "auto_accept_scheduled"
    const val LOGS_PAUSED = "logs_paused"
    const val LOG_LINES = "order_log_lines"
    const val AUTO_CLICK_ENABLED = "auto_click_enabled"
    const val AUTO_CLICK_TEXT = "auto_click_text"
    const val AUTO_CLICK_LAST_TIME = "auto_click_last_time"
    const val AI_ENDPOINT = "ai_endpoint"
    const val AI_API_KEY = "ai_api_key"
    const val TIMER_ACCEPTED = "timer_accepted_min"
    const val TIMER_MEETING = "timer_meeting_min"
    const val TIMER_IDLE = "timer_idle_min"
    const val TIMER_WAIT = "timer_wait_min"
    const val TIMER_GENERAL = "timer_general_min"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isMonitoringAllowed(context: Context, service: String): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(MONITORING, true)) return false
        return when (service) {
            "Bolt" -> prefs.getBoolean(BOLT, true)
            "Uklon" -> prefs.getBoolean(UKLON, true)
            "Uber" -> prefs.getBoolean(UBER, true)
            else -> false
        }
    }

    fun orderPassesFilter(context: Context, fare: Double, distance: Double): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(FILTER_ENABLED, false)) return true
        if (distance <= 0.0 || fare < prefs.getFloat(FILTER_MIN_FARE, 0f)) return false
        if (distance > prefs.getFloat(FILTER_MAX_DISTANCE, 50f)) return false
        val commission = prefs.getFloat("commission_percent", 15f).toDouble()
        val cost = prefs.getFloat("cost_per_km", 9f).toDouble()
        val netPerKm = (fare - fare * commission / 100.0 - distance * cost) / distance
        return netPerKm >= prefs.getFloat(FILTER_MIN_NET_KM, 0f)
    }

    enum class AutoAction { ACCEPT, REJECT_OUTSIDE_RADIUS, IGNORE }

    fun autoAction(context: Context, service: String, offer: RideCapture.Values, visibleText: String): AutoAction {
        val p = prefs(context)
        if (!p.getBoolean(AUTO_ACCEPT_ENABLED, false) || !isMonitoringAllowed(context, service)) return AutoAction.IGNORE
        val pickup = offer.pickupDistance
        val maxPickup = p.getFloat(FILTER_MAX_PICKUP, 2f).toDouble()
        if (p.getBoolean(AUTO_REJECT_OUTSIDE_RADIUS, false) && pickup != null && pickup > maxPickup) {
            return AutoAction.REJECT_OUTSIDE_RADIUS
        }
        val fare = offer.fare ?: return AutoAction.IGNORE
        val distance = offer.distance ?: return AutoAction.IGNORE
        if (fare < p.getFloat(AUTO_FILTER_MIN_FARE, 150f) || pickup == null || pickup > maxPickup) return AutoAction.IGNORE
        val minPerKm = p.getFloat(AUTO_FILTER_MIN_PER_KM, 30f).toDouble()
        if (fare / distance < minPerKm) return AutoAction.IGNORE
        val minimumHourly = p.getFloat(FILTER_MIN_HOURLY, 300f).toDouble()
        val minutes = offer.durationMinutes?.takeIf { it > 0.0 } ?: return AutoAction.IGNORE
        if (fare * 60.0 / minutes < minimumHourly) return AutoAction.IGNORE
        val minimumRating = p.getFloat(FILTER_MIN_RATING, 0f).toDouble()
        if (minimumRating > 0.0 && (offer.rating == null || offer.rating < minimumRating)) return AutoAction.IGNORE
        val normalizedText = visibleText.lowercase(java.util.Locale.ROOT)
        val required = p.getString(FILTER_REQUIRED_ADDRESSES, "").orEmpty()
            .split(',', ';', '\n').map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() }
        if (required.isNotEmpty() && required.none { normalizedText.contains(it) }) return AutoAction.IGNORE
        val excluded = p.getString(FILTER_EXCLUDED_ADDRESSES, "").orEmpty()
            .split(',', ';', '\n').map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() }
        if (excluded.any { normalizedText.contains(it) }) return AutoAction.IGNORE
        return AutoAction.ACCEPT
    }
}
