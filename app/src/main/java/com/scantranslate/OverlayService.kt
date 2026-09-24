package com.scantranslate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var ball: TextView
    private var scanBox: ScanBoxView? = null
    private var translationView: TextView? = null
    private lateinit var boxParams: WindowManager.LayoutParams
    private lateinit var translationParams: WindowManager.LayoutParams
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    @Volatile private var latestBitmap: Bitmap? = null
    private var scanJob: Job? = null
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastText = ""
    private var translator: Translator? = null
    private val httpClient = OkHttpClient()
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            latestBitmap?.recycle()
            latestBitmap = null
            projection = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification())
        addBall()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val data = intent.getParcelableExtraCompat<Intent>(EXTRA_DATA)
            if (data != null) startProjection(resultCode, data)
        }
        return START_STICKY
    }

    private fun addBall() {
        ball = TextView(this).apply {
            text = "译"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(79, 70, 229))
            }
            setOnClickListener { toggleScanBox() }
            elevation = 12f
        }
        val p = overlayParams(64, 64).apply { x = 18; y = 240; gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(ball, p)
    }

    private fun toggleScanBox() {
        if (scanBox != null) {
            scanBox?.let { windowManager.removeView(it) }
            scanBox = null
            return
        }
        val view = ScanBoxView(this, { confirmBox() }) { dx, dy, dw, dh ->
            boxParams.x += dx; boxParams.y += dy
            boxParams.width = (boxParams.width + dw).coerceIn(260, screenWidth())
            boxParams.height = (boxParams.height + dh).coerceIn(100, screenHeight())
            boxParams.x = boxParams.x.coerceIn(0, max(0, screenWidth() - boxParams.width))
            boxParams.y = boxParams.y.coerceIn(0, max(0, screenHeight() - boxParams.height))
            scanBox?.let { windowManager.updateViewLayout(it, boxParams) }
        }
        scanBox = view
        boxParams = overlayParams(AppPrefs.boxW(this), AppPrefs.boxH(this)).apply {
            x = AppPrefs.boxX(this@OverlayService); y = AppPrefs.boxY(this@OverlayService)
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(view, boxParams)
    }

    private fun confirmBox() {
        val box = scanBox ?: return
        AppPrefs.saveBox(this, boxParams.x, boxParams.y, boxParams.width, boxParams.height)
        windowManager.removeView(box); scanBox = null
        startScanning()
    }

    private fun startScanning() {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            val recognizer = if (AppPrefs.source(this@OverlayService) == "zh") {
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            } else TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                while (isActive) {
                    latestBitmap?.let { bitmap ->
                        val crop = cropBitmap(bitmap)
                        if (crop != null) {
                            val result = recognizer.process(InputImage.fromBitmap(crop, 0)).await()
                            val text = result.text.trim().replace(Regex("\\s+"), " ")
                            if (text.isNotEmpty() && text != lastText) {
                                lastText = text
                                val translated = translate(text)
                                showTranslation(translated)
                            }
                        }
                    }
                    delay(AppPrefs.interval(this@OverlayService))
                }
            } finally { recognizer.close() }
        }
    }

    private suspend fun translate(text: String): String {
        val source = AppPrefs.source(this); val target = AppPrefs.target(this)
        if (source == target) return text
        return if (AppPrefs.engine(this) == "deepl" && AppPrefs.deeplKey(this).isNotBlank()) {
            deepL(text, source, target)
        } else {
            try {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.fromLanguageTag(source) ?: TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.fromLanguageTag(target) ?: TranslateLanguage.CHINESE)
                    .build()
                translator?.close(); translator = Translation.getClient(options)
                translator!!.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
                translator!!.translate(text).await()
            } catch (_: Exception) { text }
        }
    }

    private suspend fun deepL(text: String, source: String, target: String): String = withContext(Dispatchers.IO) {
        try {
            val body = "text=${java.net.URLEncoder.encode(text, "UTF-8")}&source_lang=${source.uppercase()}&target_lang=${target.uppercase()}"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder().url("https://api-free.deepl.com/v2/translate")
                .addHeader("Authorization", "DeepL-Auth-Key ${AppPrefs.deeplKey(this@OverlayService)}")
                .post(body).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext text
                val value = JSONObject(response.body?.string().orEmpty()).getJSONArray("translations").getJSONObject(0).getString("text")
                value
            }
        } catch (_: Exception) { text }
    }

    private fun showTranslation(text: String) {
        mainHandler.post {
            translationView?.let { windowManager.removeView(it) }
            val view = TextView(this).apply {
                this.text = text
                textSize = AppPrefs.fontSize(this@OverlayService).toFloat()
                setTextColor(AppPrefs.fontColor(this@OverlayService))
                setShadowLayer(5f, 2f, 2f, Color.BLACK)
                setPadding(10, 5, 10, 5)
                setBackgroundColor(Color.argb(180, 0, 0, 0))
            }
            val x = AppPrefs.boxX(this); val y = max(0, AppPrefs.boxY(this) - dp(56))
            translationParams = overlayParams(-2, -2).apply { this.x = x; this.y = y; gravity = Gravity.TOP or Gravity.START }
            translationView = view; windowManager.addView(view, translationParams)
            mainHandler.postDelayed({ translationView?.let { windowManager.removeView(it); translationView = null } }, AppPrefs.duration(this))
        }
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        if (projection != null) return
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        projection!!.registerCallback(projectionCallback, mainHandler)
        val metrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        imageReader!!.setOnImageAvailableListener({ reader ->
            reader.acquireLatestImage()?.use { image ->
                val plane = image.planes[0]; val buffer = plane.buffer
                val width = image.width; val height = image.height
                val bitmap = Bitmap.createBitmap(width + plane.rowStride / plane.pixelStride - width, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                // Keep the latest frame in memory. The OCR coroutine may still be reading
                // the previous bitmap, so it is intentionally not recycled here.
                latestBitmap = bitmap
            }
        }, mainHandler)
        virtualDisplay = projection!!.createVirtualDisplay("scan_translate", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, mainHandler)
    }

    private fun cropBitmap(source: Bitmap): Bitmap? {
        val x = AppPrefs.boxX(this).coerceIn(0, source.width - 1)
        val y = AppPrefs.boxY(this).coerceIn(0, source.height - 1)
        val w = AppPrefs.boxW(this).coerceAtMost(source.width - x)
        val h = AppPrefs.boxH(this).coerceAtMost(source.height - y)
        return if (w > 4 && h > 4) Bitmap.createBitmap(source, x, y, w, h) else null
    }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(width, height, if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
    private fun screenWidth() = resources.displayMetrics.widthPixels
    private fun screenHeight() = resources.displayMetrics.heightPixels
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        scanJob?.cancel(); serviceJob.cancel(); translator?.close(); virtualDisplay?.release()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop(); imageReader?.close(); latestBitmap?.recycle()
        listOf<View?>(ball, scanBox, translationView).forEach { view -> if (view != null) runCatching { windowManager.removeView(view) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "字幕宝悬浮服务", NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(): Notification {
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL).setContentTitle("字幕宝正在运行").setContentText("点击悬浮球打开扫描框").setSmallIcon(R.drawable.ic_launcher_foreground).setContentIntent(pending).setOngoing(true).build()
    }

    companion object {
        const val ACTION_START = "com.scantranslate.START"
        const val ACTION_STOP = "com.scantranslate.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        private const val CHANNEL = "scan_translate"
        private const val NOTIFICATION_ID = 42
    }
}

private inline fun <reified T : android.os.Parcelable> Intent.getParcelableExtraCompat(name: String): T? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(name, T::class.java) else @Suppress("DEPRECATION") getParcelableExtra(name)
