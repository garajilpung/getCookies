package com.smg

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.widget.Toast
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.activity.addCallback
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlSpinner: Spinner
    private lateinit var btnMenu: ImageView
    private lateinit var btnGetInfo: Button
    private lateinit var btnClearCookie: Button
    private lateinit var btnSendCookie: Button

    private val synologyBaseUrl = "https://garajilpung.synology.me:5001"
    private val synologyAccount = "garajilpung"
    private val synologyPassword = "Gara_may'n0"
    private val synologyDownloadFolder = "down"

    private companion object {
        private const val MENU_GET_INFO = 1001
        private const val MENU_CLEAR_COOKIE = 1002
        private const val MENU_SEND_COOKIE = 1003
    }

    private val urlList = listOf(
        "https://nyaa.land",
        "https://update.spotv24.com",
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                onBackPressedDispatcher.onBackPressed()
//                super.onBackPressed()
            }
        }

        webView = findViewById(R.id.webView)
        btnMenu = findViewById<ImageView>(R.id.btnMenu)
        urlSpinner = findViewById(R.id.urlSpinner)
        btnGetInfo = findViewById(R.id.btnGetInfo)
        btnClearCookie = findViewById(R.id.btnClearCookie)
        btnSendCookie = findViewById(R.id.btnSendCookie)

        // Hide unused UI elements
        btnGetInfo.visibility = android.view.View.GONE
        btnClearCookie.visibility = android.view.View.GONE
        btnSendCookie.visibility = android.view.View.GONE

        setupWebView()
        setupUrlDropdown()
        setupButtons()

        btnMenu.setOnClickListener {
            val popup = PopupMenu(this, btnMenu)

            popup.menu.add(0, MENU_GET_INFO, 0, "정보 가져오기")
            popup.menu.add(0, MENU_CLEAR_COOKIE, 1, "쿠키 삭제")
            popup.menu.add(0, MENU_SEND_COOKIE, 2, "쿠키 전송")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_GET_INFO -> btnGetInfo.performClick()
                    MENU_CLEAR_COOKIE -> btnClearCookie.performClick()
                    MENU_SEND_COOKIE -> btnSendCookie.performClick()
                }
                true
            }

            popup.show()
        }

        webView.loadUrl(urlList.first())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_GET_INFO, Menu.NONE, "정보 가져오기")
        menu.add(Menu.NONE, MENU_CLEAR_COOKIE, Menu.NONE, "쿠키 삭제")
        menu.add(Menu.NONE, MENU_SEND_COOKIE, Menu.NONE, "쿠키 전송")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_GET_INFO -> {
                btnGetInfo.performClick()
                true
            }
            MENU_CLEAR_COOKIE -> {
                btnClearCookie.performClick()
                true
            }
            MENU_SEND_COOKIE -> {
                btnSendCookie.performClick()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                if (url.startsWith("magnet:?", ignoreCase = true)) {
                    sendMagnetToSynology(url)
                    return true
                }
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val targetUrl = url.orEmpty()
                if (targetUrl.startsWith("magnet:?", ignoreCase = true)) {
                    sendMagnetToSynology(targetUrl)
                    return true
                }
                return false
            }
        }
        webView.webChromeClient = WebChromeClient()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }
    }

    private fun setupUrlDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, urlList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        urlSpinner.adapter = adapter

        urlSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedUrl = urlList[position]
                if (webView.url != selectedUrl) {
                    webView.loadUrl(selectedUrl)
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
            }
        }

    }

    private fun setupButtons() {
        btnGetInfo.setOnClickListener {
            val fullUrl = webView.url ?: ""
            val currentUrl = Uri.parse(fullUrl).host ?: ""
            val cookieUrl = if (fullUrl.isNotEmpty()) fullUrl else currentUrl
            val userAgent = webView.settings.userAgentString ?: ""
            val cookie = CookieManager.getInstance().getCookie(cookieUrl) ?: "쿠키 없음"

            val baseMessage = buildString {
                append("현재 URL:\n$currentUrl\n\n")
                append("User-Agent:\n$userAgent\n\n")
                append("Cookie:\n$cookie")
            }

            webView.evaluateJavascript(
                "(function() { return document.cookie; })();",
                ValueCallback { jsCookie ->
                    val finalMessage = buildString {
                        append(baseMessage)
                        append("\n\n[document.cookie]\n$jsCookie")
                    }
                    showResultPopup("쿠키 정보", finalMessage)
                }
            )
        }
        btnClearCookie.setOnClickListener {

            val cookieManager = CookieManager.getInstance()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.removeAllCookies(null)
                cookieManager.flush()
            } else {
                @Suppress("DEPRECATION")
                cookieManager.removeAllCookie()
            }

            // WebView cache + history clear (optional but recommended)
            webView.clearCache(true)
            webView.clearHistory()

            showResultPopup("쿠키 삭제", "쿠키 및 WebView 데이터 삭제 완료")
        }

        btnSendCookie.setOnClickListener {
            val fullUrl = webView.url ?: ""
            val currentUrl = Uri.parse(fullUrl).host ?: ""
            val cookieUrl = if (fullUrl.isNotEmpty()) fullUrl else currentUrl

            val userAgent = webView.settings.userAgentString ?: ""
            val cookie = if (cookieUrl.isNotEmpty()) {
                CookieManager.getInstance().getCookie(cookieUrl) ?: ""
            } else {
                ""
            }

            Thread {
                try {
                    val targetUrl = URL("https://garajilpung.synology.me/api/v1/urlcookie.php")
                    val connection = targetUrl.openConnection() as HttpURLConnection

                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connection.setRequestProperty("Accept", "application/json")

                    val jsonBody = JSONObject().apply {
                        put("url", currentUrl)
                        put("userAgent", userAgent)
                        put("cookie", cookie)
                    }

                    BufferedWriter(OutputStreamWriter(connection.outputStream, Charsets.UTF_8)).use { writer ->
                        writer.write(jsonBody.toString())
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    val responseText = try {
                        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                        stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    } catch (_: Exception) {
                        ""
                    } finally {
                        connection.disconnect()
                    }

                    runOnUiThread {
                        val message = buildString {
                            append("전송 완료\n\n")
                            append("POST URL:\nhttps://garajilpung.synology.me/api/v1/urlcookie.php\n\n")
                            append("Response Code:\n$responseCode\n\n")
                            append("Response:\n$responseText")
                        }
                        showResultPopup("쿠키 전송", message)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        showResultPopup("쿠키 전송 실패", e.message ?: "알 수 없는 오류")
                    }
                }
            }.start()
        }
    }

    private fun showResultPopup(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun sendMagnetToSynology(magnetUrl: String) {
        Toast.makeText(this, "magnet 링크를 Synology로 전송 중입니다.", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val sid = synologyLogin()
                val response = createSynologyDownloadTask(sid, magnetUrl)

                runOnUiThread {
                    Toast.makeText(this, "Download Station 등록 완료", Toast.LENGTH_SHORT).show()
                    Log.d("SynologyMagnet", response)
                }
            } catch (e: Exception) {
                Log.e("SynologyMagnet", "magnet 전송 실패", e)
                runOnUiThread {
                    Toast.makeText(this, "전송 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun synologyLogin(): String {
        val loginUrl = buildString {
            append(synologyBaseUrl)
            append("/webapi/auth.cgi")
            append("?api=SYNO.API.Auth")
            append("&version=7")
            append("&method=login")
            append("&account=")
            append(URLEncoder.encode(synologyAccount, Charsets.UTF_8.name()))
            append("&passwd=")
            append(URLEncoder.encode(synologyPassword, Charsets.UTF_8.name()))
            append("&session=DownloadStation")
            append("&format=sid")
        }

        val connection = (URL(loginUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
        }

        return try {
            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(responseText)
            if (!json.optBoolean("success")) {
                throw IllegalStateException("Synology 로그인 실패: $responseText")
            }
            json.getJSONObject("data").getString("sid")
        } finally {
            connection.disconnect()
        }
    }

    private fun createSynologyDownloadTask(sid: String, magnetUrl: String): String {
        val taskUrl = buildString {
            append(synologyBaseUrl)
            append("/webapi/DownloadStation/task.cgi")
            append("?api=SYNO.DownloadStation.Task")
            append("&version=1")
            append("&method=create")
            append("&uri=")
            append(URLEncoder.encode(magnetUrl, Charsets.UTF_8.name()))
            append("&destination=")
            append(URLEncoder.encode(synologyDownloadFolder, Charsets.UTF_8.name()))
            append("&_sid=")
            append(URLEncoder.encode(sid, Charsets.UTF_8.name()))
        }

        val connection = (URL(taskUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
        }

        return try {
            val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(responseText)
            if (!json.optBoolean("success")) {
                throw IllegalStateException("Download Station 등록 실패: $responseText")
            }
            responseText
        } finally {
            connection.disconnect()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

}