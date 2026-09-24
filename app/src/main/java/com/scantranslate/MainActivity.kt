package com.scantranslate

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var source: Spinner
    private lateinit var target: Spinner
    private lateinit var engine: Spinner
    private lateinit var apiKey: EditText
    private lateinit var fontSize: EditText
    private lateinit var color: Spinner
    private lateinit var interval: SeekBar
    private lateinit var duration: SeekBar
    private lateinit var status: TextView
    private lateinit var intervalLabel: TextView
    private lateinit var durationLabel: TextView

    // Keep this list in sync with ML Kit TranslateLanguage (translate:17.0.3).
    // These are the language tags accepted by the on-device translation model.
    private val languages = listOf(
        "English" to "en",
        "简体中文" to "zh",
        "日本語" to "ja",
        "한국어" to "ko",
        "Français" to "fr",
        "Deutsch" to "de",
        "Español" to "es",
        "Afrikaans" to "af",
        "Shqip" to "sq",
        "العربية" to "ar",
        "Беларуская" to "be",
        "বাংলা" to "bn",
        "Български" to "bg",
        "Català" to "ca",
        "Hrvatski" to "hr",
        "Čeština" to "cs",
        "Dansk" to "da",
        "Nederlands" to "nl",
        "Esperanto" to "eo",
        "Eesti" to "et",
        "Suomi" to "fi",
        "Galego" to "gl",
        "ქართული" to "ka",
        "Ελληνικά" to "el",
        "ગુજરાતી" to "gu",
        "Kreyòl ayisyen" to "ht",
        "עברית" to "he",
        "हिन्दी" to "hi",
        "Magyar" to "hu",
        "Íslenska" to "is",
        "Bahasa Indonesia" to "id",
        "Gaeilge" to "ga",
        "Italiano" to "it",
        "ಕನ್ನಡ" to "kn",
        "Lietuvių" to "lt",
        "Latviešu" to "lv",
        "Македонски" to "mk",
        "मराठी" to "mr",
        "Bahasa Melayu" to "ms",
        "Malti" to "mt",
        "Norsk" to "no",
        "فارسی" to "fa",
        "Polski" to "pl",
        "Português" to "pt",
        "Română" to "ro",
        "Русский" to "ru",
        "Slovenčina" to "sk",
        "Slovenščina" to "sl",
        "Svenska" to "sv",
        "Kiswahili" to "sw",
        "Filipino" to "tl",
        "தமிழ்" to "ta",
        "తెలుగు" to "te",
        "ไทย" to "th",
        "Türkçe" to "tr",
        "Українська" to "uk",
        "اردو" to "ur",
        "Tiếng Việt" to "vi",
        "Cymraeg" to "cy"
    )
    private val colors = listOf("白色" to Color.WHITE, "黄色" to Color.YELLOW, "绿色" to Color.rgb(134, 239, 172), "青色" to Color.CYAN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(28))
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.surface))
        }
        val title = TextView(this).apply { text = "字幕宝"; textSize = 30f; setTextColor(ContextCompat.getColor(context, R.color.ink)) }
        root.addView(title)
        root.addView(TextView(this).apply { text = getString(R.string.app_subtitle); textSize = 14f; setPadding(0, 0, 0, dp(14)) })
        root.addView(TextView(this).apply { text = getString(R.string.permission_hint); textSize = 13f; setPadding(0, 0, 0, dp(16)) })

        root.addView(TextView(this).apply { text = "源语言" })
        source = spinner(languages.map { it.first }, languages.indexOfFirst { it.second == AppPrefs.source(this) }.coerceAtLeast(0))
        root.addView(source)
        root.addView(TextView(this).apply { text = "目标语言" })
        target = spinner(languages.map { it.first }, languages.indexOfFirst { it.second == AppPrefs.target(this) }.coerceAtLeast(1))
        root.addView(target)
        root.addView(TextView(this).apply { text = "翻译引擎" })
        engine = spinner(listOf("安卓本地翻译（离线）", "DeepL API"), if (AppPrefs.engine(this) == "deepl") 1 else 0)
        root.addView(engine)

        apiKey = edit(getString(R.string.deepl_api_key), AppPrefs.deeplKey(this), false).also { root.addView(it) }
        fontSize = edit(getString(R.string.font_size), AppPrefs.fontSize(this).toString(), true).also { root.addView(it) }
        root.addView(TextView(this).apply { text = "文字颜色" })
        color = spinner(colors.map { it.first }, colors.indexOfFirst { it.second == AppPrefs.fontColor(this) }.coerceAtLeast(0)).also { root.addView(it) }
        interval = seekRow(root, getString(R.string.ocr_interval), 10, 1000, AppPrefs.interval(this).toInt()).also { intervalLabel = it.tag as TextView }
        duration = seekRow(root, getString(R.string.display_duration), 500, 10000, AppPrefs.duration(this).toInt()).also { durationLabel = it.tag as TextView }

        val save = Button(this).apply { text = getString(R.string.save_settings); setOnClickListener { saveSettings() } }
        root.addView(save, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) })
        val start = Button(this).apply { text = getString(R.string.start_overlay); setOnClickListener { startOverlay() } }
        root.addView(start, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        val stop = Button(this).apply { text = getString(R.string.stop_overlay); setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)); status.text = "悬浮球已关闭" } }
        root.addView(stop, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        status = TextView(this).apply { textSize = 13f; setPadding(0, dp(14), 0, 0) }
        root.addView(status)
        scroll.addView(root)
        return scroll
    }

    private fun spinner(items: List<String>, selected: Int): Spinner {
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        spinner.setSelection(selected)
        spinner.setPadding(0, dp(4), 0, dp(8))
        return spinner
    }

    private fun edit(hint: String, value: String, numeric: Boolean): EditText = EditText(this).apply {
        this.hint = hint; setText(value); textSize = 15f; setSingleLine(true)
        if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun seekRow(root: LinearLayout, label: String, min: Int, max: Int, value: Int): SeekBar {
        val valueLabel = TextView(this).apply { text = "$label：${value}ms" }
        root.addView(valueLabel)
        val bar = SeekBar(this).apply { this.max = max - min; progress = (value - min).coerceIn(0, max - min) }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                valueLabel.text = "$label：${progress + min}ms"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        bar.tag = valueLabel
        root.addView(bar)
        return bar
    }

    private fun saveSettings() {
        val size = fontSize.text.toString().toIntOrNull()?.coerceIn(10, 80) ?: 20
        AppPrefs.prefs(this).edit()
            .putString("source", languages[source.selectedItemPosition].second)
            .putString("target", languages[target.selectedItemPosition].second)
            .putString("engine", if (engine.selectedItemPosition == 1) "deepl" else "system")
            .putString("deepl_key", apiKey.text.toString().trim())
            .putInt("font_size", size)
            .putInt("font_color", colors[color.selectedItemPosition].second)
            .putLong("ocr_interval", (interval.progress + 10).toLong())
            .putLong("display_duration", (duration.progress + 500).toLong()).apply()
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
    }

    private fun startOverlay() {
        saveSettings()
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            status.text = "请允许悬浮窗权限后再次点击启动"
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
                putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
                putExtra(OverlayService.EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            status.text = "悬浮球已启动（未激活）：点击开始识别和翻译，再次点击停止"
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { private const val REQUEST_CAPTURE = 701 }
}
