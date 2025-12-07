package com.bilidownloader.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bilidownloader.app.data.repository.SettingsRepository
import com.bilidownloader.app.data.repository.ThemeMode
import com.bilidownloader.app.ui.components.AgreementDialog
import com.bilidownloader.app.ui.components.PermissionDialog
import com.bilidownloader.app.ui.theme.BiliDownloaderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    private val settingsRepository = SettingsRepository()
    private var lastBackPressTime = 0L
    private val backPressInterval = 2000L
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionResult?.invoke(isGranted)
        onPermissionResult = null
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                this,
                "未授予通知权限，下载完成后将无法通知",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupBackPressHandler()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            var showAgreement by remember { mutableStateOf(false) }
            var showPermissionDialog by remember { mutableStateOf(false) }
            var isAgreed by remember { mutableStateOf(false) }
            var isInitialized by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                themeMode = settingsRepository.getThemeMode().first()
                isAgreed = settingsRepository.getAgreementAccepted().first()
                if (!isAgreed) {
                    showAgreement = true
                } else {
                    val hasPermission = checkStoragePermission()
                    if (!hasPermission) {
                        val alreadyRequested = settingsRepository.getPermissionRequested().first()
                        if (!alreadyRequested) {
                            showPermissionDialog = true
                        }
                    }
                }
                isInitialized = true
            }

            LaunchedEffect(Unit) {
                settingsRepository.getThemeMode().collect { mode ->
                    themeMode = mode
                }
            }

            BiliDownloaderTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isInitialized && isAgreed) {
                        MainScreen()
                    }
                }

                if (showAgreement) {
                    AgreementDialog(
                        onAccept = {
                            lifecycleScope.launch {
                                settingsRepository.setAgreementAccepted(true)
                                isAgreed = true
                                showAgreement = false
                                val hasPermission = checkStoragePermission()
                                if (!hasPermission) {
                                    showPermissionDialog = true
                                }
                            }
                        },
                        onDecline = {
                            finish()
                            exitProcess(0)
                        }
                    )
                }

                if (showPermissionDialog) {
                    PermissionDialog(
                        onConfirm = {
                            showPermissionDialog = false
                            requestStoragePermissionWithCallback { granted ->
                                lifecycleScope.launch {
                                    settingsRepository.setPermissionRequested(true)
                                    if (!granted) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "未授予存储权限，下载功能可能受限",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        onDismiss = {
                            showPermissionDialog = false
                            lifecycleScope.launch {
                                settingsRepository.setPermissionRequested(true)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < backPressInterval) {
                    finish()
                    exitProcess(0)
                } else {
                    lastBackPressTime = currentTime
                    Toast.makeText(
                        this@MainActivity,
                        "再按一次退出应用",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun checkStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> true
        }
    }

    private fun requestStoragePermissionWithCallback(callback: (Boolean) -> Unit) {
        val permission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                Manifest.permission.READ_MEDIA_VIDEO
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
            else -> {
                callback(true)
                return
            }
        }

        onPermissionResult = callback
        requestPermissionLauncher.launch(permission)
    }
}