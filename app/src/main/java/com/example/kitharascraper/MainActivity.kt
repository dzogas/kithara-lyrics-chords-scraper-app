package com.example.kitharascraper

import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.app.DownloadManager
import android.app.AlertDialog
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
// import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.kithara.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private companion object {
        const val HOME_URL = "https://www.google.com/cse?cx=34db0576810b64cd3"
        const val ALLOWED_HOST = "kithara.to"
        const val GOOGLE_HOST = "google.com"
        const val LATEST_RELEASE_API = "https://api.github.com/repos/dzogas/kithara-lyrics-chords-scraper-app/releases/latest"
    }

    private lateinit var webView: WebView
    private lateinit var webViewContainer: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var backBtn: Button
    private lateinit var scrapeBtn: Button
    private lateinit var downloadBtn: Button
    private lateinit var reloadBtn: Button
    private lateinit var statusState: TextView
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var songKey: TextView

    private var lastChordPro = ""
    private var lastTitle = ""
    private var lastArtist = ""
    private var lastKey = ""
    private var mainFrameHttpError: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Σωστή κλήση χωρίς παραμέτρους
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.splash_screen)
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                showMainScreen()
            }
        }, 700L)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showMainScreen() {
        window.setBackgroundDrawableResource(android.R.color.white)
        setContentView(R.layout.activity_main)

        val appHeader = findViewById<android.view.View>(R.id.appHeader)
        val initialTopPadding = appHeader.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(appHeader) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                view.paddingLeft,
                initialTopPadding + statusBarInset,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(appHeader)

        webViewContainer = findViewById(R.id.webViewContainer)
        progressBar = findViewById(R.id.progressBar)
        backBtn = findViewById(R.id.backBtn)
        scrapeBtn = findViewById(R.id.scrapeBtn)
        downloadBtn = findViewById(R.id.downloadBtn)
        reloadBtn = findViewById(R.id.reloadBtn)
        statusState = findViewById(R.id.statusState)
        songTitle = findViewById(R.id.songTitle)
        songArtist = findViewById(R.id.songArtist)
        songKey = findViewById(R.id.songKey)
        findViewById<TextView>(R.id.headerVersion).text = getString(
            R.string.version_label,
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        )

        scrapeBtn.isEnabled = false
        window.decorView.post { initializeWebView() }
        Toast.makeText(this, getString(R.string.checking_for_updates), Toast.LENGTH_SHORT).show()
        checkForUpdates()

        backBtn.setOnClickListener {
            if (::webView.isInitialized && webView.canGoBack()) {
                webView.goBack()
            }
        }

        scrapeBtn.setOnClickListener {
            if (!::webView.isInitialized) return@setOnClickListener
            webView.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { html ->
                val cleanHtml = html
                    ?.replace("\\u003C", "<")
                    ?.replace("\\\"", "\"")
                    ?.replace("\\n", "\n")
                    ?.trim('"')
                if (cleanHtml != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        scrapeSong(cleanHtml)
                    }
                }
            }
        }

        downloadBtn.setOnClickListener {
            if (lastChordPro.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    saveChordProToDownloads(lastChordPro)
                }
            }
        }

        reloadBtn.setOnClickListener {
            if (isOnline()) {
                progressBar.visibility = ProgressBar.VISIBLE
                reloadBtn.visibility = Button.GONE
                updateStatus(getString(R.string.status_loading))
                if (!::webView.isInitialized || webView.url.isNullOrBlank()) {
                    if (!::webView.isInitialized) {
                        initializeWebView()
                    } else {
                        webView.loadUrl(HOME_URL)
                    }
                } else {
                    webView.reload()
                }
            } else {
                updateStatus(getString(R.string.status_offline))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initializeWebView() {
        if (::webView.isInitialized || isFinishing || isDestroyed) return

        webView = WebView(this)
        webViewContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // --- Recommended Secure WebView Settings ---
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(true)
        }
        webView.setBackgroundColor(Color.TRANSPARENT)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                mainFrameHttpError = null
                progressBar.visibility = ProgressBar.VISIBLE
                reloadBtn.visibility = Button.GONE
                scrapeBtn.isEnabled = false
                updateStatus(getString(R.string.status_loading))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = ProgressBar.GONE
                backBtn.isEnabled = webView.canGoBack()

                val httpError = mainFrameHttpError
                if (httpError == null) {
                    scrapeBtn.isEnabled = true
                    reloadBtn.visibility = Button.GONE
                    updateStatus(getString(R.string.status_ready))
                    return
                }

                view?.evaluateJavascript("document.querySelector('div#text') !== null") { result ->
                    if (result == "true") {
                        mainFrameHttpError = null
                        scrapeBtn.isEnabled = true
                        reloadBtn.visibility = Button.GONE
                        updateStatus(getString(R.string.status_ready))
                    } else {
                        scrapeBtn.isEnabled = false
                        reloadBtn.visibility = Button.VISIBLE
                        updateStatus(getString(R.string.status_http_error, httpError))
                    }
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return true
                if (isAllowedUrl(url)) {
                    return false
                }
                updateStatus(getString(R.string.status_blocked_navigation))
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = ProgressBar.GONE
                    scrapeBtn.isEnabled = false
                    reloadBtn.visibility = Button.VISIBLE
                    updateStatus(getString(R.string.status_page_error))
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) {
                    mainFrameHttpError = errorResponse?.statusCode ?: 0
                }
            }
        }

        if (isOnline()) {
            webView.loadUrl(HOME_URL)
        } else {
            progressBar.visibility = ProgressBar.GONE
            scrapeBtn.isEnabled = false
            reloadBtn.visibility = Button.VISIBLE
            updateStatus(getString(R.string.status_offline))
        }

    }

    private fun isAllowedUrl(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        val isAllowedHost = host == ALLOWED_HOST ||
            host.endsWith(".$ALLOWED_HOST") ||
            host == GOOGLE_HOST ||
            host.endsWith(".$GOOGLE_HOST")
        return uri.scheme == "https" && isAllowedHost
    }

    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun scrapeSong(html: String) {
        val doc = withContext(Dispatchers.IO) { Jsoup.parse(html) }

        lastTitle = doc.select("h1.ti").text()
        lastArtist = doc.select("h2.ar").text()
        lastKey = doc.select("summary .til").text()

        val textDiv = doc.selectFirst("div#text")
        val children = textDiv?.children() ?: return

        if (lastKey.isBlank()) {
            lastKey = children
                .asSequence()
                .filter { it.hasClass("ch") }
                .map { buildChordLineFromCh(it) }
                .mapNotNull(::extractFirstChord)
                .firstOrNull()
                .orEmpty()
        }

        val chordPro = StringBuilder()
        chordPro.append("{title: $lastTitle}\n{artist: $lastArtist}\n{key: $lastKey}\n\n")

        var pendingChordLine: String? = null

        for (child in children) {
            when {
                child.hasClass("ch") -> {
                    pendingChordLine = buildChordLineFromCh(child)
                }
                child.hasClass("te") -> {
                    if (pendingChordLine != null) {
                        chordPro.append(pendingChordLine).append("\n")
                    }
                    chordPro.append(child.text()).append("\n")
                    pendingChordLine = null
                }
                child.hasClass("no") -> {
                    chordPro.append("\n")
                    pendingChordLine = null
                }
            }
        }

        lastChordPro = chordPro.toString()

        withContext(Dispatchers.Main) {
            songTitle.text = lastTitle.ifBlank { getString(R.string.status_no_song) }
            songArtist.text = getString(R.string.status_artist, lastArtist.ifBlank { getString(R.string.unknown_artist) })
            songKey.text = getString(R.string.status_key, lastKey.ifBlank { getString(R.string.status_unknown) })
            updateStatus(getString(R.string.status_scraped))
            copyToClipboard(lastChordPro)
        }
    }

    private fun updateStatus(message: String) {
        if (::statusState.isInitialized) {
            statusState.text = message
        }
    }

    private fun checkForUpdates() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = fetchAvailableUpdate()
            withContext(Dispatchers.Main) {
                if (result.update != null) {
                    showUpdateDialog(result.update.first, result.update.second)
                } else if (result.succeeded) {
                    Toast.makeText(this@MainActivity, getString(R.string.version_up_to_date), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchAvailableUpdate(): UpdateCheckResult {
        return try {
            val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "KitharaScraper")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode !in 200..299) return UpdateCheckResult(null, false)

            val release = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val latestVersion = release.optString("tag_name").removePrefix("v").trim()
            val apkUrl = release.optJSONArray("assets")
                ?.let { assets ->
                    (0 until assets.length())
                        .map { assets.optJSONObject(it) }
                        .firstOrNull { it?.optString("name")?.endsWith(".apk", ignoreCase = true) == true }
                        ?.optString("browser_download_url")
                }
                .orEmpty()

            if (latestVersion.isBlank() || apkUrl.isBlank()) {
                UpdateCheckResult(null, false)
            } else if (!isNewerVersion(latestVersion)) {
                UpdateCheckResult(null, true)
            } else {
                UpdateCheckResult(latestVersion to apkUrl, true)
            }
        } catch (_: Exception) {
            UpdateCheckResult(null, false)
        }
    }

    private data class UpdateCheckResult(
        val update: Pair<String, String>?,
        val succeeded: Boolean
    )

    private fun isNewerVersion(remoteVersion: String): Boolean {
        val installedVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val remoteParts = remoteVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val installedParts = installedVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val maxParts = maxOf(remoteParts.size, installedParts.size)

        for (index in 0 until maxParts) {
            val remotePart = remoteParts.getOrElse(index) { 0 }
            val installedPart = installedParts.getOrElse(index) { 0 }
            if (remotePart != installedPart) return remotePart > installedPart
        }
        return false
    }

    private fun showUpdateDialog(version: String, apkUrl: String) {
        if (isFinishing || isDestroyed) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available_title))
            .setMessage(getString(R.string.update_available_message, version))
            .setNegativeButton(R.string.update_cancel, null)
            .setPositiveButton(R.string.update_download) { _, _ ->
                downloadUpdate(apkUrl, version)
            }
            .show()
    }

    private fun downloadUpdate(apkUrl: String, version: String) {
        val request = DownloadManager.Request(apkUrl.toUri())
            .setTitle(getString(R.string.update_download_title, version))
            .setDescription(getString(R.string.update_download_description))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "kithara-v$version.apk"
            )

        getSystemService(DownloadManager::class.java).enqueue(request)
        finishAndRemoveTask()
    }

    private fun extractFirstChord(chordLine: String): String? {
        return Regex("\\b[A-G][#b]?(?:m|min|maj|sus|dim|aug|add)?[0-9]*(?:/[A-G][#b]?)?\\b")
            .find(chordLine)
            ?.value
    }

    private fun buildChordLineFromCh(chDiv: Element): String {
        val sb = StringBuilder()
        for (node in chDiv.childNodes()) {
            when (node) {
                is TextNode -> sb.append(node.wholeText)
                is Element -> {
                    when (node.tagName()) {
                        "span" -> {
                            for (tn in node.textNodes()) {
                                sb.append(tn.wholeText)
                            }
                        }
                        "a" -> {
                            sb.append(node.text())
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

    private suspend fun saveChordProToDownloads(content: String) {
        val filename = "${lastArtist.ifBlank { getString(R.string.unknown_artist) }} - ${lastTitle.ifBlank { getString(R.string.unknown_title) }}.txt"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Chords")
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
            withContext(Dispatchers.Main) {
                updateStatus(getString(R.string.status_saved, filename))
                openShareSheet(it)
            }
        }
    }

    private fun openShareSheet(uri: Uri) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_file)))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ChordPro", text)
        clipboard.setPrimaryClip(clip)
    }
}
