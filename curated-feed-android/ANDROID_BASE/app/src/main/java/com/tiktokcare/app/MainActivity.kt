package com.tiktokcare.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
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
    private lateinit var importButton: TextView
    private lateinit var likeButton: TextView
    private lateinit var likeCountText: TextView
    private lateinit var commentButton: TextView
    private lateinit var commentCountText: TextView
    private lateinit var favoriteButton: TextView
    private lateinit var favoriteCountText: TextView
    private lateinit var shareButton: TextView
    private lateinit var shareCountText: TextView
    private lateinit var profileButton: TextView
    private lateinit var followButton: TextView
    private lateinit var discButton: TextView
    private lateinit var nextButton: TextView
    private lateinit var previousButton: TextView

    private var player: ExoPlayer? = null
    private var videos: List<CareVideo> = emptyList()
    private var playQueue: List<CareVideo> = emptyList()
    private var likedVideoIds = mutableSetOf<String>()
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
            likedVideoIds = preferences.getStringSet(PREF_LIKED_IDS, emptySet()).orEmpty().toMutableSet()
            loadLocalVideosOrFeed()
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
            text = "Live   Seguindo   Para voce   Buscar"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(match(), wrap())
            setShadowLayer(8f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }
        topBar.addView(brandText)
        root.addView(topBar)

        val bottomInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(98), dp(84))
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
            setPadding(0, 0, dp(10), dp(88))
            layoutParams = FrameLayout.LayoutParams(wrap(), wrap(), Gravity.BOTTOM or Gravity.END)
        }
        profileButton = profileCircle("TC")
        followButton = followBadge()
        likeButton = actionIconButton("♥")
        likeCountText = actionCount("0")
        commentButton = actionIconButton("●")
        commentCountText = actionCount("0")
        favoriteButton = actionIconButton("■")
        favoriteCountText = actionCount("0")
        shareButton = actionIconButton("↗")
        shareCountText = actionCount("0")
        discButton = profileCircle("♪")
        previousButton = actionIconButton("↑")
        nextButton = actionIconButton("↓")
        actions.addView(profileButton)
        actions.addView(followButton)
        actions.addView(likeButton)
        actions.addView(likeCountText)
        actions.addView(commentButton)
        actions.addView(commentCountText)
        actions.addView(favoriteButton)
        actions.addView(favoriteCountText)
        actions.addView(shareButton)
        actions.addView(shareCountText)
        actions.addView(discButton)
        root.addView(actions)

        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.argb(210, 0, 0, 0))
            layoutParams = FrameLayout.LayoutParams(match(), dp(64), Gravity.BOTTOM)
        }
        listOf("Inicio", "Amigos", "+", "Mensagens", "Perfil").forEach { label ->
            bottomNav.addView(TextView(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = if (label == "+") 30f else 12f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, match(), 1f)
            })
        }
        root.addView(bottomNav)

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
            setOnClickListener { loadLocalVideosOrFeed() }
        }
        root.addView(retryButton)

        importButton = TextView(this).apply {
            text = getString(R.string.import_videos)
            setTextColor(Color.BLACK)
            textSize = 15f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = FrameLayout.LayoutParams(wrap(), wrap(), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(68)
                rightMargin = dp(14)
            }
            setOnClickListener { openVideoImporter() }
        }
        root.addView(importButton)

        nextButton.setOnClickListener { showNext() }
        previousButton.setOnClickListener { showPrevious() }
        likeButton.setOnClickListener { toggleLike() }

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

    private fun actionIconButton(label: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 32f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(44)).apply {
                topMargin = dp(8)
            }
            setShadowLayer(8f, 0f, 2f, Color.argb(190, 0, 0, 0))
        }
    }

    private fun actionCount(value: String): TextView {
        return TextView(this).apply {
            text = value
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(18))
            setShadowLayer(6f, 0f, 2f, Color.argb(180, 0, 0, 0))
        }
    }

    private fun profileCircle(label: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = circleBackground(Color.argb(230, 32, 42, 58), Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                topMargin = dp(8)
                bottomMargin = dp(2)
            }
        }
    }

    private fun followBadge(): TextView {
        return TextView(this).apply {
            text = "+"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = circleBackground(Color.rgb(255, 47, 87), Color.rgb(255, 47, 87))
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                bottomMargin = dp(8)
            }
        }
    }

    private fun circleBackground(fillColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            setStroke(dp(2), strokeColor)
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
                        setVideos(loaded)
                        if (videos.isEmpty()) showMessage(getString(R.string.feed_empty), false) else playCurrent()
                    }
                    .onFailure {
                        showMessage(getString(R.string.feed_error), true)
                    }
            }
        }
    }

    private fun loadLocalVideosOrFeed() {
        showLoading()
        if (!hasVideoPermission()) {
            requestPermissions(arrayOf(videoPermission()), REQUEST_VIDEO_PERMISSION)
            return
        }

        thread(name = "local-feed-loader") {
            val localVideos = runCatching { queryLocalVideos() }.getOrDefault(emptyList())
            runOnUiThread {
                if (localVideos.isNotEmpty()) {
                    setVideos(localVideos)
                    playCurrent()
                } else {
                    showMessage(getString(R.string.local_feed_empty), true)
                }
            }
        }
    }

    private fun openVideoImporter() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "application/zip", "application/x-zip-compressed"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT_VIDEOS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_IMPORT_VIDEOS || resultCode != RESULT_OK || data == null) return

        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) {
                uris += clip.getItemAt(index).uri
            }
        }
        data.data?.let { uris += it }

        if (uris.isEmpty()) return

        showLoading()
        thread(name = "video-importer") {
            val imported = uris.flatMap { uri ->
                runCatching { importVideoOrZip(uri) }.getOrDefault(emptyList())
            }
            runOnUiThread {
                if (imported.isNotEmpty()) {
                    setVideos(queryImportedVideos())
                    playCurrent()
                } else {
                    showMessage(getString(R.string.import_failed), true)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_VIDEO_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                loadLocalVideosOrFeed()
            } else {
                loadFeed()
            }
        }
    }

    private fun hasVideoPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            true
        } else {
            checkSelfPermission(videoPermission()) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun videoPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    private fun queryLocalVideos(): List<CareVideo> {
        val imported = queryImportedVideos()
        if (imported.isNotEmpty()) return imported

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED
        )

        val selection: String
        val selectionArgs: Array<String>

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            selectionArgs = arrayOf("%Movies/TikTokCare%", "%Download/TikTokCare%")
        } else {
            @Suppress("DEPRECATION")
            selection = "${MediaStore.Video.Media.DATA} LIKE ? OR ${MediaStore.Video.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/Movies/TikTokCare/%", "%/Download/TikTokCare/%")
        }

        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val items = mutableListOf<CareVideo>()

        contentResolver.query(collection, projection, selection, selectionArgs, sort)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn).orEmpty()
                val uri = Uri.withAppendedPath(collection, id.toString()).toString()

                items += CareVideo(
                    id = "local-$id",
                    title = name.substringBeforeLast('.').ifBlank { "Video local" },
                    videoUrl = uri,
                    youtubeId = "",
                    category = "Local",
                    sourceLabel = "Videos do celular",
                    caregiverNote = "",
                    order = items.size
                )
            }
        }

        return items
    }

    private fun queryImportedVideos(): List<CareVideo> {
        val directory = importedVideoDirectory()
        val files = directory
            .listFiles { file -> file.isFile && file.extension.lowercase() in VIDEO_EXTENSIONS }
            .orEmpty()
            .sortedByDescending { it.lastModified() }

        return files.mapIndexed { index, file ->
            CareVideo(
                id = "imported-${file.nameWithoutExtension}",
                title = file.nameWithoutExtension.ifBlank { "Video importado" },
                videoUrl = Uri.fromFile(file).toString(),
                youtubeId = "",
                category = "Local",
                sourceLabel = "Importado da galeria",
                caregiverNote = "",
                order = index
            )
        }
    }

    private fun importVideoOrZip(uri: Uri): List<File> {
        val displayName = displayName(uri)
        val mimeType = contentResolver.getType(uri).orEmpty()
        return if (mimeType.contains("zip", ignoreCase = true) || displayName.endsWith(".zip", ignoreCase = true)) {
            extractZipVideos(uri)
        } else {
            listOf(copyImportedVideo(uri, displayName))
        }
    }

    private fun copyImportedVideo(uri: Uri, displayName: String = displayName(uri)): File {
        val directory = importedVideoDirectory().apply { mkdirs() }
        val extension = videoExtension(displayName)
            ?: contentResolver.getType(uri)?.substringAfterLast('/')?.takeIf { it.isNotBlank() && it != "mp4v-es" }
            ?: "mp4"
        val baseName = safeBaseName(displayName.substringBeforeLast('.', "video"))
        val file = File(directory, uniqueFileName("$baseName.$extension"))

        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open imported video" }
            file.outputStream().use { output -> input.copyTo(output) }
        }

        return file
    }

    private fun extractZipVideos(uri: Uri): List<File> {
        val directory = importedVideoDirectory().apply { mkdirs() }
        val imported = mutableListOf<File>()

        contentResolver.openInputStream(uri).use { rawInput ->
            requireNotNull(rawInput) { "Unable to open zip" }
            ZipInputStream(rawInput.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                    val extension = videoExtension(name)

                    if (!entry.isDirectory && extension != null) {
                        val baseName = safeBaseName(name.substringBeforeLast('.', "video"))
                        val target = File(directory, uniqueFileName("$baseName.$extension"))
                        target.outputStream().use { output -> zip.copyTo(output) }
                        imported += target
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        return imported
    }

    private fun displayName(uri: Uri): String {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }

    private fun videoExtension(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension.takeIf { it in VIDEO_EXTENSIONS }
    }

    private fun safeBaseName(value: String): String {
        return value
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(48)
            .ifBlank { "video" }
    }

    private fun uniqueFileName(preferredName: String): String {
        val directory = importedVideoDirectory()
        val base = preferredName.substringBeforeLast('.', "video")
        val extension = preferredName.substringAfterLast('.', "mp4")
        var candidate = "$base.$extension"
        var index = 1

        while (File(directory, candidate).exists()) {
            candidate = "$base-$index.$extension"
            index += 1
        }

        return candidate
    }

    private fun importedVideoDirectory(): File {
        return File(filesDir, "imported-videos")
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
        val video = playQueue.getOrNull(currentIndex) ?: return
        loading.visibility = View.GONE
        statusText.visibility = View.GONE
        retryButton.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        youtubeView.visibility = View.GONE
        updateActionState(video)

        titleText.text = video.sourceLabel.ifBlank { "TikTok Care" }
        metaText.text = listOf(video.title, video.category, video.caregiverNote)
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

    private fun toggleLike() {
        val video = playQueue.getOrNull(currentIndex) ?: return
        if (likedVideoIds.contains(video.id)) {
            likedVideoIds.remove(video.id)
        } else {
            likedVideoIds.add(video.id)
        }
        preferences.edit().putStringSet(PREF_LIKED_IDS, likedVideoIds).apply()
        updateActionState(video)
    }

    private fun updateActionState(video: CareVideo) {
        val liked = likedVideoIds.contains(video.id)
        likeButton.text = "♥"
        likeButton.setTextColor(if (liked) Color.rgb(255, 47, 87) else Color.WHITE)
        likeCountText.text = formatCount(baseCount(video.id, 3200, 82000) + if (liked) 1 else 0)
        commentCountText.text = formatCount(baseCount("${video.id}-comments", 80, 7600))
        favoriteCountText.text = formatCount(baseCount("${video.id}-favorites", 40, 3200))
        shareCountText.text = formatCount(baseCount("${video.id}-shares", 20, 1800))
        profileButton.text = initials(video.sourceLabel.ifBlank { "TC" })
    }

    private fun baseCount(seed: String, min: Int, max: Int): Int {
        val range = (max - min).coerceAtLeast(1)
        return min + (abs(seed.hashCode()) % range)
    }

    private fun formatCount(value: Int): String {
        return if (value >= 1000) {
            val rounded = value / 100.0
            String.format(java.util.Locale.US, "%.1f mil", rounded / 10.0)
        } else {
            value.toString()
        }
    }

    private fun initials(value: String): String {
        val words = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "TC" }
    }

    private fun showNext() {
        if (videos.isEmpty()) return
        currentIndex += 1
        if (currentIndex >= playQueue.size) {
            reshuffleQueue()
            currentIndex = 0
        }
        playCurrent()
    }

    private fun showPrevious() {
        if (videos.isEmpty()) return
        currentIndex = if (currentIndex == 0) playQueue.lastIndex else currentIndex - 1
        playCurrent()
    }

    private fun setVideos(newVideos: List<CareVideo>) {
        videos = newVideos
        reshuffleQueue()
        currentIndex = 0
    }

    private fun reshuffleQueue() {
        playQueue = if (videos.size <= 1) videos else videos.shuffled()
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
        private const val PREF_LIKED_IDS = "liked_video_ids"
        private const val REQUEST_VIDEO_PERMISSION = 101
        private const val REQUEST_IMPORT_VIDEOS = 102
        private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "m4v", "webm", "mkv", "3gp")
    }
}
