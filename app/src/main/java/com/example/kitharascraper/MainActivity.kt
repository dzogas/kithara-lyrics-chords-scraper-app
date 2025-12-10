package com.example.kithara

import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var scrapeBtn: Button
    private lateinit var downloadBtn: Button

    private var lastChordPro = ""
    private var lastTitle = ""
    private var lastArtist = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        scrapeBtn = findViewById(R.id.scrapeBtn)
        downloadBtn = findViewById(R.id.downloadBtn)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Toast.makeText(this@MainActivity, "✅ Σελίδα φορτώθηκε!", Toast.LENGTH_SHORT).show()
            }
        }

        webView.loadUrl("https://kithara.to/")

        scrapeBtn.setOnClickListener {
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
    }

    private suspend fun scrapeSong(html: String) {
        val doc = withContext(Dispatchers.IO) { Jsoup.parse(html) }

        lastTitle = doc.select("h1.ti").text()
        lastArtist = doc.select("h2.ar").text()
        val key = doc.select("summary .til").text()

        val chordPro = StringBuilder()
        chordPro.append("{title: $lastTitle}\n{artist: $lastArtist}\n{key: $key}\n\n")

        val textDiv = doc.selectFirst("div#text")
        val children = textDiv?.children() ?: return

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
                // ⚠️ Δεν χρησιμοποιούμε chMono γιατί δεν έχει σωστό spacing
            }
        }

        lastChordPro = chordPro.toString()

        withContext(Dispatchers.Main) {
            Toast.makeText(this@MainActivity, "✅ Έγινε αντιγραφή στο πρόχειρο!", Toast.LENGTH_SHORT).show()
            copyToClipboard(lastChordPro)
        }
    }

    // Φτιάχνει τη γραμμή συγχορδιών με spacing από div.ch
    private fun buildChordLineFromCh(chDiv: Element): String {
        val sb = StringBuilder()
        for (node in chDiv.childNodes()) {
            when (node) {
                is TextNode -> sb.append(node.wholeText) // κρατάει τα κενά
                is Element -> {
                    when (node.tagName()) {
                        "span" -> {
                            for (tn in node.textNodes()) {
                                sb.append(tn.wholeText) // spaces
                            }
                        }
                        "a" -> {
                            sb.append(node.text()) // chord token
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

    private suspend fun saveChordProToDownloads(content: String) {
        val filename = "${lastArtist.ifBlank { "Unknown Artist" }} - ${lastTitle.ifBlank { "Unknown Title" }}.txt"

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
                Toast.makeText(this@MainActivity, "✅ Αποθηκεύτηκε στο Downloads/Chords: $filename", Toast.LENGTH_LONG).show()
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
        startActivity(Intent.createChooser(shareIntent, "📤 Μοιράσου το αρχείο"))
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ChordPro", text)
        clipboard.setPrimaryClip(clip)
    }
}
