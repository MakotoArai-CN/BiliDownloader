package com.bilidownloader.app.ui

import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.bilidownloader.app.data.repository.SettingsRepository
import com.bilidownloader.app.data.repository.ThemeMode
import com.bilidownloader.app.data.repository.WebViewUA
import com.bilidownloader.app.ui.theme.BiliDownloaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WebViewActivity : ComponentActivity() {

    private val settingsRepository = SettingsRepository()
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            var webViewUA by remember { mutableStateOf(WebViewUA.PC) }

            LaunchedEffect(Unit) {
                themeMode = settingsRepository.getThemeMode().first()
                webViewUA = settingsRepository.getWebViewUA().first()
            }

            BiliDownloaderTheme(themeMode = themeMode) {
                WebViewLoginScreen(
                        webViewUA = webViewUA,
                        onBack = { finish() },
                        onSaveCookie = { cookie ->
                            lifecycleScope.launch {
                                settingsRepository.setCookie(cookie)
                                Toast.makeText(
                                                this@WebViewActivity,
                                                "登录成功，Cookie 已保存",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                                finish()
                            }
                        },
                        onWebViewCreated = { webView = it }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewLoginScreen(
        webViewUA: WebViewUA,
        onBack: () -> Unit,
        onSaveCookie: (String) -> Unit,
        onWebViewCreated: (WebView) -> Unit
) {
    var currentUrl by remember { mutableStateOf("") }
    var isLoggedIn by remember { mutableStateOf(false) }
    var loginUserName by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var currentCookie by remember { mutableStateOf("") }

    val userAgent =
            remember(webViewUA) {
                when (webViewUA) {
                    WebViewUA.PC ->
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    WebViewUA.MOBILE ->
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
            }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn && !showSuccessDialog) {
            showSuccessDialog = true
        }
    }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Column {
                                Text("登录哔哩哔哩")
                                if (isLoggedIn && loginUserName.isNotEmpty()) {
                                    Text(
                                            text = "已登录: $loginUserName",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                            }
                        },
                        actions = {
                            TextButton(
                                    onClick = {
                                        val cookieManager = CookieManager.getInstance()
                                        val cookie =
                                                cookieManager.getCookie("https://www.bilibili.com")
                                                        ?: ""
                                        if (cookie.contains("SESSDATA") &&
                                                        cookie.contains("bili_jct")
                                        ) {
                                            onSaveCookie(cookie)
                                        } else {
                                            onSaveCookie(cookie)
                                        }
                                    }
                            ) {
                                Icon(
                                        if (isLoggedIn) Icons.Default.CheckCircle
                                        else Icons.Default.Save,
                                        contentDescription = null,
                                        tint =
                                                if (isLoggedIn) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                        text = if (isLoggedIn) "完成" else "保存",
                                        color =
                                                if (isLoggedIn) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                )
            }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                        visible = isLoggedIn,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.primaryContainer
                                    )
                    ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = "登录成功",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                        text = "点击右上角「完成」按钮保存登录状态",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Button(
                                    onClick = {
                                        val cookieManager = CookieManager.getInstance()
                                        val cookie =
                                                cookieManager.getCookie("https://www.bilibili.com")
                                                        ?: ""
                                        onSaveCookie(cookie)
                                    },
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor =
                                                            MaterialTheme.colorScheme.primary
                                            )
                            ) { Text("完成") }
                        }
                    }
                }

                if (currentUrl.isNotEmpty()) {
                    Text(
                            text = currentUrl,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                    )
                }

                AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    userAgentString = userAgent
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient =
                                        object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                    view: WebView?,
                                                    request: WebResourceRequest?
                                            ): Boolean {
                                                return false
                                            }

                                            override fun onPageFinished(
                                                    view: WebView?,
                                                    url: String?
                                            ) {
                                                super.onPageFinished(view, url)
                                                currentUrl = url ?: ""
                                                checkLoginStatus()
                                            }
                                        }

                                onWebViewCreated(this)
                                loadUrl("https://passport.bilibili.com/login")
                            }
                        },
                        update = { webView ->
                            kotlinx.coroutines.MainScope().launch {
                                while (isActive) {
                                    delay(2000)
                                    checkLoginStatusFromWebView(webView) {
                                            loggedIn,
                                            userName,
                                            cookie ->
                                        if (loggedIn && !isLoggedIn) {
                                            isLoggedIn = true
                                            loginUserName = userName
                                            currentCookie = cookie
                                            Log.d(
                                                    "WebViewActivity",
                                                    "Login detected! User: $userName"
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                icon = {
                    Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                    )
                },
                title = { Text("登录成功") },
                text = {
                    Column {
                        Text("检测到您已成功登录哔哩哔哩账号。")
                        if (loginUserName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text = "用户: $loginUserName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("点击「保存并返回」将登录状态保存到应用中。")
                    }
                },
                confirmButton = {
                    Button(
                            onClick = {
                                showSuccessDialog = false
                                val cookieManager = CookieManager.getInstance()
                                val cookie =
                                        cookieManager.getCookie("https://www.bilibili.com") ?: ""
                                onSaveCookie(cookie)
                            }
                    ) { Text("保存并返回") }
                },
                dismissButton = {
                    TextButton(onClick = { showSuccessDialog = false }) { Text("继续浏览") }
                }
        )
    }
}

@Suppress("UNUSED_PARAMETER")
private fun checkLoginStatusFromWebView(
        webView: WebView,
        callback: (Boolean, String, String) -> Unit
) {
    try {
        val cookieManager = CookieManager.getInstance()
        val cookie = cookieManager.getCookie("https://www.bilibili.com") ?: ""

        val hasSessionData = cookie.contains("SESSDATA")
        val hasBiliJct = cookie.contains("bili_jct")
        val isLoggedIn = hasSessionData && hasBiliJct

        var userName = ""
        if (isLoggedIn) {
            val dedeUserIdMatch = Regex("DedeUserID=(\\d+)").find(cookie)
            userName = dedeUserIdMatch?.groupValues?.getOrNull(1) ?: ""
        }

        callback(isLoggedIn, userName, cookie)
    } catch (e: Exception) {
        Log.e("WebViewActivity", "Error checking login status", e)
        callback(false, "", "")
    }
}

private fun checkLoginStatus() {
    try {
        val cookieManager = CookieManager.getInstance()
        val cookie = cookieManager.getCookie("https://www.bilibili.com") ?: ""

        Log.d("WebViewActivity", "Cookie check: ${cookie.take(100)}...")
        Log.d("WebViewActivity", "Has SESSDATA: ${cookie.contains("SESSDATA")}")
        Log.d("WebViewActivity", "Has bili_jct: ${cookie.contains("bili_jct")}")
    } catch (e: Exception) {
        Log.e("WebViewActivity", "Error checking login", e)
    }
}
