package com.tiktokcare.app

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences("tiktok-care", MODE_PRIVATE) }

    private lateinit var root: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var youtubeView: WebView
    private lateinit var loading: ProgressBar
    private lateinit var brandText: TextView
    private lateinit var titleText: TextView
    private lateinit var metaText: TextView
    private lateinit var statusText: TextView
    private lateinit var retryButton: TextView
    private lateinit var likeButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var previousButton: TextView

    private var player: ExoPlayer? = null
    private var videos: List<CareVideo> = emptyList()
    private var currentIndex = 0
    private var gestureStartY = 0f
    private var gestureStartX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            buildLayout()
            setContentView(root)
            hideSystemUi()
            createPlayer()
            loadFeed()
        }.onFailure { error ->
            showStartupFallback(error)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        player?.play()
        if (::youtubeView.isInitialized) youtubeView.onResume()
    }

    override fun onPause() {
        player?.pause()
        if (::youtubeView.isInitialized) youtubeView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::playerView.isInitialized) {
            playerView.player = null
        }
        if (::youtubeView.isInitialized) {
            youtubeView.destroy()
        }
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun createPlayer() {
        player = ExoPlayer.Builder(this).build().also {
            it.repeatMode = Player.REPEAT_MODE_ONE
            it.playWhenReady = true
            playerView.player = it
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildLayout() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
        }

        playerView = PlayerView(this).apply {
            useController = false
            setShutterBackgroundColor(Color.BLACK)
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            layoutParams = FrameLayout.LayoutParams(match(), match())
        }
        root.addView(playerView)

        youtubeView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            webChromeClient = WebChromeClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            layoutParams = FrameLayout.LayoutParams(match(), match())
        }
        root.addView(youtubeView)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(10))
            layoutParams = FrameLayout.LayoutParams(match(), wrap(), Gravity.TOP)
        }

        brandText = TextView(this).apply {
            text = "TikTok Care"
            setTextColor(Color.WHITE)
            textSize = 19f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        topBar.addView(brandText)
        root.addView(topBar)

        val bottomInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(96), dp(28))
            layoutParams = FrameLayout.LayoutParams(match(), wrap(), Gravity.BOTTOM)
            setBackgroundColor(Color.TRANSPARENT)
        }

        titleText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            maxLines = 2
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        metaText = TextView(this).apply {
            setTextColor(Color.argb(235, 255, 255, 255))
            textSize = 14f
            maxLines = 2
            setPadding(0, dp(6), 0, 0)
        }
        bottomInfo.addView(titleText)
        bottomInfo.addView(metaText)
        root.addView(bottomInfo)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, dp(12), dp(94))
            layoutParams = FrameLayout.LayoutParams(wrap(), wrap(), Gravity.BOTTOM or Gravity.END)
        }
        likeButton = actionButton("♥")
        previousButton = actionButton("↑")
        nextButton = actionButton("↓")
        actions.addView(likeButton)
        actions.addView(previousButton)
        actions.addView(nextButton)
        root.addView(actions)

        loading = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER)
        }
        root.addView(loading)

        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(28), 0, dp(28), 0)
            layoutParams = FrameLayout.LayoutParams(match(), wrap(), Gravity.CENTER)
        }
        root.addView(statusText)

        retryButton = TextView(this).apply {
            text = getString(R.string.retry)
            setTextColor(Color.BLACK)
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.WHITE)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(wrap(), wrap(), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply {
                bottomMargin = dp(74)
            }
            setOnClickListener { loadFeed() }
        }
        root.addView(retryButton)

        nextButton.setOnClickListener { showNext() }
        previousButton.setOnClickListener { showPrevious() }
        likeButton.setOnClickListener { likeButton.isSelected = !likeButton.isSelected }

        root.setOnClickListener {
            if (::youtubeView.isInitialized && youtubeView.visibility == View.VISIBLE) return@setOnClickListener
            val current = player ?: return@setOnClickListener
            if (current.isPlaying) current.pause() else current.play()
        }

        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStartY = event.y
                    gestureStartX = event.x
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val deltaY = event.y - gestureStartY
                    val deltaX = event.x - gestureStartX
                    if (abs(deltaY) > dp(70) && abs(deltaY) > abs(deltaX)) {
                        if (deltaY < 0) showNext() else showPrevious()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun actionButton(label: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 30f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60)).apply {
                topMargin = dp(10)
            }
            setShadowLayer(8f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }
    }

    private fun loadFeed() {
        showLoading()
        thread(name = "feed-loader") {
            val result = runCatching {
                fetchFeedJson(BuildConfig.FEED_URL).also { json ->
                    preferences.edit().putString(CACHE_KEY_FEED_JSON, json).apply()
                }
            }.recoverCatching {
                preferences.getString(CACHE_KEY_FEED_JSON, null)
                    ?: throw it
            }.mapCatching(::parseFeed)
            runOnUiThread {
                result
                    .onSuccess { loaded ->
                        videos = loaded
                        currentIndex = 0
                        if (videos.isEmpty()) showMessage(getString(R.string.feed_empty), false) else playCurrent()
                    }
                    .onFailure {
                        showMessage(getString(R.string.feed_error), true)
                    }
            }
        }
    }

    private fun fetchFeedJson(feedUrl: String): String {
        val connection = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
        }

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Feed request failed: HTTP ${connection.responseCode}")
        }

        return connection.inputStream.bufferedReader().use(BufferedReader::readText)
    }

    private fun parseFeed(json: String): List<CareVideo> {
        val rootObject = JSONObject(json)
        val array = rootObject.getJSONArray("videos")
        val items = mutableListOf<CareVideo>()

        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            if (!item.optBoolean("active", true)) continue

            val rawVideoUrl = item.optString("videoUrl").trim()
            val rawYoutubeId = item.optString("youtubeId").trim()
            val youtubeId = rawYoutubeId.ifBlank { extractYoutubeId(rawVideoUrl).orEmpty() }
            val videoUrl = if (youtubeId.isBlank()) rawVideoUrl else ""
            if (videoUrl.isEmpty() && youtubeId.isEmpty()) continue

            items += CareVideo(
                id = item.optString("id").ifBlank { "video-$index" },
                title = item.optString("title").ifBlank { "Video aprovado" },
                videoUrl = videoUrl,
                youtubeId = youtubeId,
                category = item.optString("category"),
                sourceLabel = item.optString("sourceLabel").ifBlank { "TikTok Care" },
                caregiverNote = item.optString("caregiverNote"),
                order = item.optInt("order", index)
            )
        }

        return items.sortedWith(compareBy<CareVideo> { it.order }.thenBy { it.title })
    }

    private fun playCurrent() {
        val video = videos.getOrNull(currentIndex) ?: return
        loading.visibility = View.GONE
        statusText.visibility = View.GONE
        retryButton.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        youtubeView.visibility = View.GONE
        likeButton.isSelected = false
        likeButton.text = "♥"

        titleText.text = video.title
        metaText.text = listOf(video.sourceLabel, video.category, video.caregiverNote)
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        if (video.youtubeId.isNotBlank()) {
            playerView.visibility = View.GONE
            youtubeView.visibility = View.VISIBLE
            player?.stop()
            youtubeView.loadDataWithBaseURL(
                "https://www.youtube.com",
                youtubeHtml(video.youtubeId),
                "text/html",
                "UTF-8",
                null
            )
            return
        }

        val currentPlayer = player ?: run {
            showMessage(getString(R.string.feed_error), true)
            return
        }

        currentPlayer.apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(video.videoUrl)))
            prepare()
            playWhenReady = true
        }
    }

    private fun showNext() {
        if (videos.isEmpty()) return
        currentIndex = (currentIndex + 1) % videos.size
        playCurrent()
    }

    private fun showPrevious() {
        if (videos.isEmpty()) return
        currentIndex = if (currentIndex == 0) videos.lastIndex else currentIndex - 1
        playCurrent()
    }

    private fun showLoading() {
        loading.visibility = View.VISIBLE
        playerView.visibility = View.GONE
        statusText.visibility = View.GONE
        retryButton.visibility = View.GONE
    }

    private fun showMessage(message: String, canRetry: Boolean) {
        loading.visibility = View.GONE
        playerView.visibility = View.GONE
        youtubeView.visibility = View.GONE
        statusText.text = message
        statusText.visibility = View.VISIBLE
        retryButton.visibility = if (canRetry) View.VISIBLE else View.GONE
    }

    private fun hideSystemUi() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun showStartupFallback(error: Throwable) {
        val fallback = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        val message = TextView(this).apply {
            text = "TikTok Care\n\nNao foi possivel iniciar.\n${error.javaClass.simpleName}"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            layoutParams = FrameLayout.LayoutParams(match(), wrap(), Gravity.CENTER)
        }
        fallback.addView(message)
        setContentView(fallback)
    }

    private fun extractYoutubeId(value: String): String? {
        if (value.isBlank()) return null
        val trimmed = value.trim()
        if (trimmed.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return trimmed

        return runCatching {
            val uri = Uri.parse(trimmed)
            when {
                uri.host.orEmpty().contains("youtu.be") -> uri.lastPathSegment
                uri.host.orEmpty().contains("youtube.com") && uri.path.orEmpty().startsWith("/shorts/") ->
                    uri.pathSegments.getOrNull(1)
                uri.host.orEmpty().contains("youtube.com") && uri.path.orEmpty().startsWith("/embed/") ->
                    uri.pathSegments.getOrNull(1)
                uri.host.orEmpty().contains("youtube.com") -> uri.getQueryParameter("v")
                else -> null
            }?.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{11}$")) }
        }.getOrNull()
    }

    private fun youtubeHtml(youtubeId: String): String {
        return """
            <!doctype html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                  html, body, iframe {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    background: #000;
                  }
                </style>
              </head>
              <body>
                <iframe
                  src="https://www.youtube.com/embed/$youtubeId?autoplay=1&playsinline=1&rel=0&modestbranding=1"
                  frameborder="0"
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                  allowfullscreen>
                </iframe>
              </body>
            </html>
        """.trimIndent()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun match(): Int = FrameLayout.LayoutParams.MATCH_PARENT
    private fun wrap(): Int = FrameLayout.LayoutParams.WRAP_CONTENT

    private data class CareVideo(
        val id: String,
        val title: String,
        val videoUrl: String,
        val youtubeId: String,
        val category: String,
        val sourceLabel: String,
        val caregiverNote: String,
        val order: Int
    )

    companion object {
        private const val CACHE_KEY_FEED_JSON = "feed_json"
    }
}
