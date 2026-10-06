package ua.taximoney.taxiscan

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class DriverToolsActivity : AppCompatActivity() {
    private val prefs by lazy { DriverPrefs.prefs(this) }
    private lateinit var list: LinearLayout
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(::writeBackup) }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::readBackup) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(18,18,18)) }
        val header = TextView(this).apply { text = "Налаштування водія"; textSize = 23f; setTextColor(Color.WHITE); setPadding(dp(20),dp(22),dp(16),dp(16)) }
        root.addView(header)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),dp(4),dp(12),dp(24)) }
        scroll.addView(list); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
        addSwitch("Робота з Bolt", "Відстежувати пропозиції Bolt", DriverPrefs.BOLT, true)
        addSwitch("Робота з Uklon", "Відстежувати пропозиції Uklon", DriverPrefs.UKLON, true)
        addSwitch("Робота з Uber", "Відстежувати пропозиції Uber Driver", DriverPrefs.UBER, true)
        addRow("⚡ Фільтр автоприйняття", "Дохід • подача • ₴/км • ₴/год • рейтинг • адреси") {
            startActivity(Intent(this, AutoAcceptFilterActivity::class.java))
        }
        addRow("💰 Ціна 1 км вашого авто", "Паливо та інші витрати на кілометр") { numberDialog("Вартість авто за км (₴)", "cost_per_km", 9f, 0f, 100000f) }
        addRow("📊 Установити розмір комісії", "Поточна комісія: ${prefs.getFloat("commission_percent",15f).toInt()}%") { numberDialog("Комісія (%)", "commission_percent", 15f, 0f, 100f) }
        addSwitch("🔊 Налаштування звуку", "Сигнал при новій пропозиції", DriverPrefs.SOUND_ENABLED, false)
        addRow("🔈 Вихід звуку", "Медіа / сигналізація") { choiceDialog("Канал звуку", arrayOf("Медіа","Сигналізація"), DriverPrefs.SOUND_STREAM, 0) }
        addRow("🪟 Налаштування плаваючих вікон", "Режим, колір, розмір і прозорість") { overlayDialog() }
        addRow("ℹ️ Інформація", "Статистика та швидкі дії") { showStats() }
        addRow("⏱ Таймери поїздки", "Прийняв • зустрів • простій • очікування • завершив") { startActivity(Intent(this, RideTimersActivity::class.java)) }
        addRow("💾 Бекап налаштувань", "Зберегти або відновити параметри") { backupDialog() }
        addRow("🔄 Оновлення програми", "Відкрити сторінку збірок TaxiScan") { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/romanstars/TaxiScan-APK/actions"))) }
        addRow("🤖 AI Assistant — API", "Зберегти endpoint і ключ; тест прогнозу") { aiDialog() }
        addRow("⏻ Вимк/Увімк програми", "Керування моніторингом Bolt, Uklon та Uber") { addSwitchDialog() }
        addRow("🎨 Налаштування плаваючих кнопок", "Колір • розмір • прозорість") { overlayDialog() }
        addRow("📜 Логи програми", "Перегляд • пауза • очистити • скопіювати") { showLogs() }
        addRow("🖱 Авто кліки", "Зберегти сценарій; виконання вимкнене за замовчуванням") { clickScenarioDialog() }
        addSwitch("🚀 Запускати Bolt?", "Відкривати Bolt при запуску TaxiScan", DriverPrefs.AUTO_LAUNCH_BOLT, false)
        addRow("🕒 Bolt автоприйом попередніх", "Параметри відбору та запланованих замовлень") { scheduledDialog() }
    }

    private fun addRow(title: String, subtitle: String, action: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(14)); background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(29,29,31)); cornerRadius=dp(18).toFloat(); setStroke(dp(1),Color.rgb(63,63,67)) }; isClickable=true; isFocusable=true; setOnClickListener { action() } }
        box.addView(TextView(this).apply { text=title; textSize=18f; setTextColor(Color.rgb(53,204,137)); setTypeface(null,1) })
        box.addView(TextView(this).apply { text=subtitle; textSize=14f; setTextColor(Color.LTGRAY); setPadding(0,dp(4),0,0) })
        list.addView(box, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }
    private fun addSwitch(title:String, subtitle:String, key:String, default:Boolean, inverse:Boolean=false, click:(()->Unit)?=null) {
        val box=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),dp(10),dp(8),dp(10)); background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(29,29,31)); cornerRadius=dp(16).toFloat(); setStroke(dp(1),Color.rgb(63,63,67)) } }
        val texts=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text=title; textSize=17f; setTextColor(Color.rgb(53,204,137)); setTypeface(null,1) })
        texts.addView(TextView(this).apply { text=subtitle; textSize=13f; setTextColor(Color.LTGRAY); setPadding(0,dp(3),0,0) })
        box.addView(texts,LinearLayout.LayoutParams(0,-2,1f))
        val sw=Switch(this).apply { isChecked=prefs.getBoolean(key,default); setOnCheckedChangeListener { _,checked ->
            val serviceKeys = listOf(DriverPrefs.BOLT, DriverPrefs.UKLON, DriverPrefs.UBER)
            val anyEnabled = serviceKeys.any { serviceKey -> if (serviceKey == key) checked else prefs.getBoolean(serviceKey, true) }
            prefs.edit().putBoolean(key,checked).putBoolean(DriverPrefs.MONITORING,anyEnabled).apply()
        } }
        box.addView(sw); if(click!=null) box.setOnClickListener { click() }
        list.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }
    private fun filterDialog() {
        val input=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),0,dp(20),0) }
        val min=field("Мінімальна ціна замовлення (₴)", prefs.getFloat(DriverPrefs.FILTER_MIN_FARE,0f))
        val net=field("Мінімум чистими за км (₴)",prefs.getFloat(DriverPrefs.FILTER_MIN_NET_KM,0f))
        val max=field("Максимальна відстань (км)",prefs.getFloat(DriverPrefs.FILTER_MAX_DISTANCE,50f))
        input.addView(min);input.addView(net);input.addView(max)
        MaterialAlertDialogBuilder(this).setTitle("Фільтр пропозицій").setView(input).setMessage("Фільтр лише показує пропозиції, що відповідають умовам. Замовлення автоматично не приймаються.").setNegativeButton("Скасувати",null).setPositiveButton("Зберегти") { _,_->
            prefs.edit().putFloat(DriverPrefs.FILTER_MIN_FARE,min.text.toString().toFloatOrNull()?:0f).putFloat(DriverPrefs.FILTER_MIN_NET_KM,net.text.toString().toFloatOrNull()?:0f).putFloat(DriverPrefs.FILTER_MAX_DISTANCE,max.text.toString().toFloatOrNull()?:50f).putBoolean(DriverPrefs.FILTER_ENABLED,true).apply()
        }.show()
    }
    private fun numberDialog(title:String,key:String,current:Float,min:Float,max:Float) { val e=field(title,current); MaterialAlertDialogBuilder(this).setTitle(title).setView(e).setNegativeButton("Скасувати",null).setPositiveButton("Зберегти"){_,_-> val v=e.text.toString().replace(',','.').toFloatOrNull(); if(v!=null&&v in min..max) prefs.edit().putFloat(key,v).apply() }.show() }
    private fun field(hint:String,value:Float):EditText=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; this.hint=hint; setText(value.toString()) }
    private fun choiceDialog(title:String,options:Array<String>,key:String,default:Int){ MaterialAlertDialogBuilder(this).setTitle(title).setSingleChoiceItems(options,prefs.getInt(key,default)){d,which->prefs.edit().putInt(key,which).apply();d.dismiss()}.setNegativeButton("Закрити",null).show() }
    private fun overlayDialog(){
        val e=EditText(this).apply { hint="Колір, наприклад #32BB78";setText(prefs.getString(DriverPrefs.OVERLAY_COLOR,"#32BB78")) }
        val size=field("Розмір тексту (sp)",prefs.getFloat(DriverPrefs.OVERLAY_TEXT_SIZE,14f));val alpha=field("Прозорість (0.2–1)",prefs.getFloat(DriverPrefs.OVERLAY_ALPHA,1f))
        val modes=arrayOf("Кожна пропозиція","Остання пропозиція","Лише прийняті (потрібне розпізнавання статусу)")
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),0,dp(16),0)};box.addView(e);box.addView(size);box.addView(alpha)
        MaterialAlertDialogBuilder(this).setTitle("Плаваюче вікно").setView(box).setItems(modes){_,i->prefs.edit().putInt(DriverPrefs.OVERLAY_MODE,i).apply()}.setPositiveButton("Зберегти"){_,_->prefs.edit().putString(DriverPrefs.OVERLAY_COLOR,e.text.toString()).putFloat(DriverPrefs.OVERLAY_TEXT_SIZE,size.text.toString().toFloatOrNull()?:14f).putFloat(DriverPrefs.OVERLAY_ALPHA,(alpha.text.toString().toFloatOrNull()?:1f).coerceIn(.2f,1f)).apply()}.setNegativeButton("Закрити",null).show()
    }
    private fun timersDialog(){val keys=listOf(DriverPrefs.TIMER_ACCEPTED,DriverPrefs.TIMER_MEETING,DriverPrefs.TIMER_IDLE,DriverPrefs.TIMER_WAIT,DriverPrefs.TIMER_GENERAL);val labels=listOf("Після прийняття","До зустрічі","Простій","Очікування","Загальний");val fields=keys.indices.map{field(labels[it]+" (хв)",prefs.getFloat(keys[it],0f))};val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),0)};fields.forEach(box::addView);MaterialAlertDialogBuilder(this).setTitle("Таймери").setView(box).setPositiveButton("Зберегти"){_,_->val ed=prefs.edit();fields.forEachIndexed{i,e->ed.putFloat(keys[i],e.text.toString().toFloatOrNull()?:0f)};ed.apply()}.setNegativeButton("Скасувати",null).show()}
    private fun showStats(){
        val rows=listOf("Bolt","Uklon","Uber").map{service->
            val key=service.lowercase(java.util.Locale.ROOT)
            "$service: ${prefs.getInt("accepted_stats_${key}_trips",0)} прийнято • ${prefs.getFloat("accepted_stats_${key}_revenue",0f).toInt()} ₴"
        }
        val last=prefs.getString(DriverPrefs.LAST_ACCEPTED_SUMMARY,"Ще немає прийнятих поїздок")
        MaterialAlertDialogBuilder(this).setTitle("Інформація • статистика")
            .setMessage(rows.joinToString("\n")+"\n\nОстання: $last\n\nСтатистика оновлюється, коли ви вручну позначаєте замовлення як «Прийняв» у Таймерах.")
            .setPositiveButton("Відкрити таймери"){_,_->startActivity(Intent(this,RideTimersActivity::class.java))}
            .setNegativeButton("Готово",null).show()
    }
    private fun backupDialog(){MaterialAlertDialogBuilder(this).setTitle("Бекап налаштувань").setItems(arrayOf("Зберегти копію","Відновити копію")){_,i->if(i==0)export.launch("taxiscan-settings.json") else importBackup.launch(arrayOf("application/json"))}.show()}
    private fun writeBackup(uri:Uri){val json=JSONObject();prefs.all.forEach{(k,v)->json.put(k,v)};contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(json.toString(2))};Toast.makeText(this,"Копію збережено",Toast.LENGTH_SHORT).show()}
    private fun readBackup(uri:Uri){runCatching{val raw=contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()};val obj=JSONObject(raw);val e=prefs.edit();obj.keys().forEach{key->when(val v=obj.get(key)){is Boolean->e.putBoolean(key,v);is Int->e.putInt(key,v);is Number->e.putFloat(key,v.toFloat());is String->e.putString(key,v)}};e.apply()}.onSuccess{Toast.makeText(this,"Налаштування відновлено",Toast.LENGTH_SHORT).show()}.onFailure{Toast.makeText(this,"Не вдалося прочитати копію",Toast.LENGTH_LONG).show()}}
    private fun aiDialog(){val endpoint=EditText(this).apply{hint="HTTPS endpoint API";setText(prefs.getString(DriverPrefs.AI_ENDPOINT,""))};val key=EditText(this).apply{hint="API key";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;setText(prefs.getString(DriverPrefs.AI_API_KEY,""))};val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),0);addView(endpoint);addView(key)};MaterialAlertDialogBuilder(this).setTitle("AI Assistant API").setMessage("Потрібен сумісний endpoint, який повертає JSON з estimated_minutes.").setView(box).setNeutralButton("Тест"){_,_->prefs.edit().putString(DriverPrefs.AI_ENDPOINT,endpoint.text.toString()).putString(DriverPrefs.AI_API_KEY,key.text.toString()).apply();testAi(endpoint.text.toString(),key.text.toString())}.setPositiveButton("Зберегти"){_,_->prefs.edit().putString(DriverPrefs.AI_ENDPOINT,endpoint.text.toString()).putString(DriverPrefs.AI_API_KEY,key.text.toString()).apply()}.setNegativeButton("Скасувати",null).show()}
    private fun testAi(endpoint:String,key:String){Thread{val result=runCatching{val c=URL(endpoint).openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=8000;c.readTimeout=8000;c.doOutput=true;c.setRequestProperty("Content-Type","application/json");if(key.isNotBlank())c.setRequestProperty("Authorization","Bearer $key");c.outputStream.use{it.write(JSONObject().put("task","estimate_eta").toString().toByteArray())};BufferedReader(InputStreamReader(c.inputStream)).use{it.readText()}};runOnUiThread{Toast.makeText(this,if(result.isSuccess)"API відповідає: ${result.getOrNull()}" else "Помилка API: ${result.exceptionOrNull()?.message}",Toast.LENGTH_LONG).show()}}.start()}
    private fun addSwitchDialog(){val selected=booleanArrayOf(prefs.getBoolean(DriverPrefs.BOLT,true),prefs.getBoolean(DriverPrefs.UKLON,true),prefs.getBoolean(DriverPrefs.UBER,true));val keys=listOf(DriverPrefs.BOLT,DriverPrefs.UKLON,DriverPrefs.UBER);val checks=arrayOf("Bolt","Uklon","Uber");MaterialAlertDialogBuilder(this).setTitle("Моніторинг сервісів").setMultiChoiceItems(checks,selected){_,i,on->selected[i]=on}.setPositiveButton("Зберегти"){_,_->val e=prefs.edit();keys.forEachIndexed{i,k->e.putBoolean(k,selected[i])};e.putBoolean(DriverPrefs.MONITORING,selected.any{it}).apply()}.show()}
    private fun showLogs(){
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),dp(8))}
        val warning=TextView(this).apply{
            text="💡 Журнал зберігає до 50 останніх подій на телефоні. Увімкніть запис лише на час пошуку помилки."
            textSize=14f;setTextColor(Color.rgb(255,190,83));setPadding(dp(12),dp(12),dp(12),dp(12))
            background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.rgb(61,42,21));cornerRadius=dp(14).toFloat();setStroke(dp(2),Color.rgb(255,177,74))}
        }
        val logText=TextView(this).apply{textSize=13f;setTextColor(Color.LTGRAY);setPadding(dp(8),dp(10),dp(8),dp(10))}
        val logScroll=ScrollView(this).apply{addView(logText);visibility=View.GONE}
        fun updateLogs(){logText.text=prefs.getString(DriverPrefs.LOG_LINES,"")?.ifBlank{"Журнал поки порожній"}?:"Журнал порожній"}
        val show=Button(this).apply{text="📖  Показати логи";setOnClickListener{updateLogs();logScroll.visibility=if(logScroll.visibility==View.VISIBLE)View.GONE else View.VISIBLE;text=if(logScroll.visibility==View.VISIBLE)"📖  Сховати логи" else "📖  Показати логи"}}
        val toggle=Button(this).apply{text=if(prefs.getBoolean(DriverPrefs.LOGS_PAUSED,false))"▶️  Запустити логування" else "⏸  Призупинити логування";setOnClickListener{val next=!prefs.getBoolean(DriverPrefs.LOGS_PAUSED,false);prefs.edit().putBoolean(DriverPrefs.LOGS_PAUSED,next).apply();text=if(next)"▶️  Запустити логування" else "⏸  Призупинити логування";Toast.makeText(this@DriverToolsActivity,if(next)"Запис призупинено" else "Запис увімкнено",Toast.LENGTH_SHORT).show()}}
        val clear=Button(this).apply{text="🗑  Очистити логи";setOnClickListener{prefs.edit().remove(DriverPrefs.LOG_LINES).apply();updateLogs();Toast.makeText(this@DriverToolsActivity,"Журнал очищено",Toast.LENGTH_SHORT).show()}}
        val copy=Button(this).apply{text="📋  Скопіювати логи";setOnClickListener{val value=prefs.getString(DriverPrefs.LOG_LINES,"").orEmpty();if(value.isBlank())Toast.makeText(this@DriverToolsActivity,"Журнал порожній",Toast.LENGTH_SHORT).show() else {(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("TaxiScan logs",value));Toast.makeText(this@DriverToolsActivity,"Логи скопійовано",Toast.LENGTH_SHORT).show()}}}
        content.addView(warning);content.addView(show);content.addView(logScroll,LinearLayout.LayoutParams(-1,dp(190)));content.addView(toggle);content.addView(clear);content.addView(copy)
        MaterialAlertDialogBuilder(this).setTitle("📜  Логи програми").setView(content).setPositiveButton("Закрити",null).show()
    }
    private fun clickScenarioDialog(){val e=EditText(this).apply{hint="Текст елемента для сценарію";setText(prefs.getString(DriverPrefs.AUTO_CLICK_TEXT,""))};MaterialAlertDialogBuilder(this).setTitle("Сценарій кліків").setMessage("Збережіть опис сценарію. Автоматичне натискання кнопок замовлення не запускається.").setView(e).setPositiveButton("Зберегти"){_,_->prefs.edit().putString(DriverPrefs.AUTO_CLICK_TEXT,e.text.toString()).putBoolean(DriverPrefs.AUTO_CLICK_ENABLED,false).apply()}.setNegativeButton("Скасувати",null).show()}
    private fun scheduledDialog(){val enabled=CheckBox(this).apply{text="Включати заплановані пропозиції у фільтр";isChecked=prefs.getBoolean(DriverPrefs.AUTO_ACCEPT_SCHEDULED,false)};MaterialAlertDialogBuilder(this).setTitle("Bolt • заплановані замовлення").setMessage("TaxiScan відбирає пропозиції для показу. Автоматичне прийняття поїздок не виконується.").setView(enabled).setPositiveButton("Зберегти"){_,_->prefs.edit().putBoolean(DriverPrefs.AUTO_ACCEPT_SCHEDULED,enabled.isChecked).apply()}.setNegativeButton("Скасувати",null).show()}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
}
