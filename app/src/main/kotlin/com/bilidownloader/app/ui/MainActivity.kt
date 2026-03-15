package com.bilidownloader.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bilidownloader.app.R
import com.bilidownloader.app.data.repository.SettingsRepository
import com.bilidownloader.app.data.repository.ThemeMode
import com.bilidownloader.app.ui.components.AgreementDialog
import com.bilidownloader.app.ui.components.PermissionWizardDialog
import com.bilidownloader.app.ui.components.StoragePermissionRevokedDialog
import com.bilidownloader.app.ui.theme.BiliDownloaderTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {
    private val settingsRepository = SettingsRepository()
    private var lastBackPressTime = 0L
    private val backPressInterval = 2000L
    private var wizardPermissionCallback: ((Boolean) -> Unit)? = null

    private val storagePermissionRevoked = MutableStateFlow(false)

    private val wizardPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        wizardPermissionCallback?.invoke(isGranted)
        wizardPermissionCallback = null
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        storagePermissionRevoked.value = !isGranted && !checkStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupBackPressHandler()

        // Monitor storage permission on each resume
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val alreadyRequested = settingsRepository.getPermissionRequested().first()
                if (alreadyRequested && !checkStoragePermission()) {
                    storagePermissionRevoked.value = true
                }
            }
        }

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            var showAgreement by remember { mutableStateOf(false) }
            var showPermissionWizard by remember { mutableStateOf(false) }
            var isAgreed by remember { mutableStateOf(false) }
            var isInitialized by remember { mutableStateOf(false) }
            val showStorageRevoked by storagePermissionRevoked.collectAsState()

            LaunchedEffect(Unit) {
                themeMode = settingsRepository.getThemeMode().first()
                isAgreed = settingsRepository.getAgreementAccepted().first()
                if (!isAgreed) {
                    showAgreement = true
                } else {
                    val alreadyRequested = settingsRepository.getPermissionRequested().first()
                    if (!alreadyRequested && needsAnyPermission()) {
                        showPermissionWizard = true
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
                                if (needsAnyPermission()) {
                                    showPermissionWizard = true
                                }
                            }
                        },
                        onDecline = {
                            finish()
                            exitProcess(0)
                        }
                    )
                }

                if (showPermissionWizard) {
                    PermissionWizardDialog(
                        needsNotification = needsNotificationPermission(),
                        needsStorage = !checkStoragePermission(),
                        onRequestNotification = { callback ->
                            wizardPermissionCallback = callback
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                wizardPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                callback(true)
                            }
                        },
                        onRequestStorage = { callback ->
                            wizardPermissionCallback = callback
                            val perm = when {
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                    Manifest.permission.READ_MEDIA_VIDEO
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                else -> {
                                    callback(true)
                                    return@PermissionWizardDialog
                                }
                            }
                            wizardPermissionLauncher.launch(perm)
                        },
                        onComplete = {
                            showPermissionWizard = false
                            lifecycleScope.launch {
                                settingsRepository.setPermissionRequested(true)
                            }
                        },
                        onDismiss = {
                            showPermissionWizard = false
                            lifecycleScope.launch {
                                settingsRepository.setPermissionRequested(true)
                            }
                        }
                    )
                }

                if (showStorageRevoked && !showPermissionWizard && !showAgreement) {
                    StoragePermissionRevokedDialog(
                        onRequestPermission = {
                            val perm = when {
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                    Manifest.permission.READ_MEDIA_VIDEO
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                else -> {
                                    storagePermissionRevoked.value = false
                                    return@StoragePermissionRevokedDialog
                                }
                            }
                            storagePermissionLauncher.launch(perm)
                        },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        },
                        onDismiss = {
                            storagePermissionRevoked.value = false
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
                        getString(R.string.press_back_again),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun checkStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+: READ_MEDIA_VIDEO or READ_MEDIA_VISUAL_USER_SELECTED
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
            else -> true
        }
    }

    private fun needsNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        }
        return false
    }

    private fun needsAnyPermission(): Boolean {
        return needsNotificationPermission() || !checkStoragePermission()
    }
}