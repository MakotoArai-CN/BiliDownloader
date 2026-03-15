package com.bilidownloader.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.bilidownloader.app.R
import com.bilidownloader.app.data.repository.SettingsRepository
import com.bilidownloader.app.data.repository.ThemeMode
import com.bilidownloader.app.data.repository.WebViewUA
import com.bilidownloader.app.ui.theme.BiliDownloaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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
                                                getString(R.string.login_success_cookie_saved),
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
    val isMobileUA = webViewUA == WebViewUA.MOBILE
    var qrCodeUrl by remember { mutableStateOf<String?>(null) }
    var showNativeOverlay by remember { mutableStateOf(false) }

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
                                Text(stringResource(R.string.login_bilibili))
                                if (isLoggedIn && loginUserName.isNotEmpty()) {
                                    Text(
                                            text = stringResource(R.string.logged_in_as, loginUserName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                                        text = if (isLoggedIn) stringResource(R.string.done) else stringResource(R.string.save),
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
                                        text = stringResource(R.string.login_success),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                        text = stringResource(R.string.login_save_hint),
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
                            ) { Text(stringResource(R.string.done)) }
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

                                                // Extract QR code for mobile UA
                                                if (isMobileUA && view != null) {
                                                    view.postDelayed({
                                                        extractQRCode(view) { extractedUrl ->
                                                            if (extractedUrl != null) {
                                                                qrCodeUrl = extractedUrl
                                                                showNativeOverlay = true
                                                            }
                                                        }
                                                    }, 1500)
                                                }
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
                                            showNativeOverlay = false
                                            Log.d(
                                                    "WebViewActivity",
                                                    "Login detected! User: $userName"
                                            )
                                        }
                                    }
                                    // Re-extract QR code for mobile UA
                                    if (isMobileUA && !isLoggedIn) {
                                        extractQRCode(webView) { extractedUrl ->
                                            if (extractedUrl != null && extractedUrl != qrCodeUrl) {
                                                qrCodeUrl = extractedUrl
                                                showNativeOverlay = true
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                )
            }

            // Native QR code overlay for mobile UA
            AnimatedVisibility(
                visible = showNativeOverlay && isMobileUA && !isLoggedIn && qrCodeUrl != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                NativeLoginOverlay(
                    qrCodeUrl = qrCodeUrl,
                    onDismiss = { showNativeOverlay = false }
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
                title = { Text(stringResource(R.string.login_success)) },
                text = {
                    Column {
                        Text(stringResource(R.string.login_success_detected))
                        if (loginUserName.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text = stringResource(R.string.login_user, loginUserName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.login_save_prompt))
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
                    ) { Text(stringResource(R.string.save_and_return)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSuccessDialog = false }) { Text(stringResource(R.string.continue_browsing)) }
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

private fun extractQRCode(webView: WebView, callback: (String?) -> Unit) {
    val js = """
        (function() {
            var img = document.querySelector('.login-scan-box img')
                   || document.querySelector('[class*="qrcode"] img')
                   || document.querySelector('img[src*="qrcode"]')
                   || document.querySelector('.qr-image img')
                   || document.querySelector('img[class*="qr"]');
            if (img && img.src) {
                return img.src;
            }
            var canvas = document.querySelector('.login-scan-box canvas')
                      || document.querySelector('[class*="qrcode"] canvas')
                      || document.querySelector('canvas');
            if (canvas) {
                try { return canvas.toDataURL('image/png'); } catch(e) {}
            }
            var allImgs = document.querySelectorAll('img');
            for (var i = 0; i < allImgs.length; i++) {
                var src = allImgs[i].src || '';
                if (src.indexOf('qrcode') >= 0 || src.indexOf('qr') >= 0) {
                    return src;
                }
                var w = allImgs[i].naturalWidth || allImgs[i].width;
                var h = allImgs[i].naturalHeight || allImgs[i].height;
                if (w > 80 && h > 80 && Math.abs(w - h) < 20 && src.startsWith('data:image')) {
                    return src;
                }
            }
            return null;
        })()
    """.trimIndent()

    webView.evaluateJavascript(js) { result ->
        val url = result?.removeSurrounding("\"")?.takeIf { it != "null" && it.isNotEmpty() }
        callback(url)
    }
}

@Composable
private fun NativeLoginOverlay(
    qrCodeUrl: String?,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.scan_qr_to_login),
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (qrCodeUrl != null) {
                QRCodeImage(
                    url = qrCodeUrl,
                    modifier = Modifier.size(240.dp)
                )
            } else {
                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.qr_loading),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.scan_qr_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.use_webview_instead))
            }
        }
    }
}

@Composable
private fun QRCodeImage(url: String, modifier: Modifier = Modifier) {
    if (url.startsWith("data:image")) {
        val bitmap = remember(url) {
            try {
                val base64Data = url.substringAfter("base64,")
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                Log.e("QRCodeImage", "Failed to decode base64 image", e)
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = modifier,
                contentScale = ContentScale.Fit
            )
        } else {
            QRCodePlaceholder(modifier)
        }
    } else {
        var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
        var loading by remember(url) { mutableStateOf(true) }

        LaunchedEffect(url) {
            loading = true
            bitmap = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    val bytes = response.body?.bytes()
                    response.close()
                    bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } catch (e: Exception) {
                    Log.e("QRCodeImage", "Failed to load QR image from URL", e)
                    null
                }
            }
            loading = false
        }

        if (loading) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = modifier,
                contentScale = ContentScale.Fit
            )
        } else {
            QRCodePlaceholder(modifier)
        }
    }
}

@Composable
private fun QRCodePlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.QrCode2,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
