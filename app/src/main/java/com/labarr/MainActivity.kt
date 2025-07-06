package com.labarr

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.net.http.SslError
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.labarr.databinding.ActivityMainBinding
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.os.VibrationEffect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.TextView
import android.widget.Button
import android.text.TextWatcher
import android.text.Editable

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var jsonDataManager: JsonDataManager
    
    // Menu state
    private var isMenuOpen = false
    private var menuAutoCloseTimer: Handler? = null
    private var isDragging = false
    private var dragStartX = 0f
    
    // Back button state
    private var isBackButtonMovable = false
    private var isBackButtonLocked = false
    private var backButtonOriginalX = 0f
    private var backButtonOriginalY = 0f
    
    // WebView and PIN state
    private var webView: WebView? = null
    private var isPinVerified = false
    
    // Modern activity result launcher
    private val settingsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Settings were saved, check if URLs changed and reload if needed
            val data = result.data
            val ipChanged = data?.getBooleanExtra("ip_changed", false) ?: false
            val urlAdded = data?.getBooleanExtra("url_added", false) ?: false
            val dataWiped = data?.getBooleanExtra("data_wiped", false) ?: false
            val clearCache = data?.getBooleanExtra("clear_cache", false) ?: false

            val restoreCompleted = data?.getBooleanExtra("restore_completed", false) ?: false
            
            if (restoreCompleted) {
                // Restart the app after restore
                restartApp()
                return@registerForActivityResult
            }
            
            if (ipChanged || urlAdded) {
                // Force reload with new settings
                forceReloadWebView()
                hideNoUrlsScreen()
            }
            
            if (dataWiped) {
                // Clear WebView cache and data after wipe
                clearWebViewCache()
                
                // After wipe, check if we have any URLs and show appropriate screen
                val settings = jsonDataManager.loadSettings()
                val savedIp = settings.homelabIp
                val savedFallbackUrl = settings.fallbackUrl
                
                if (savedIp.isEmpty() && savedFallbackUrl.isEmpty()) {
                    // No URLs available after wipe, show intro settings page
                    showNoUrlsScreen()
                } else {
                    // URLs are available, reload WebView
                    forceReloadWebView()
                    hideNoUrlsScreen()
                }
            }
            
            if (clearCache) {
                // Clear WebView cache
                clearWebViewCache()
            }
            

            
            // Apply auto-rotate setting when returning from settings
            applyAutoRotateSetting()
        } else {
            // Settings activity was closed without OK result
            Log.d("Labarr", "Settings closed without OK result")
            
            // Only check if we need to show no URLs screen (don't reload if URLs exist)
            val settings = jsonDataManager.loadSettings()
            val savedIp = settings.homelabIp
            val savedFallbackUrl = settings.fallbackUrl
            
            Log.d("Labarr", "Settings closed - IP: '$savedIp', Fallback: '$savedFallbackUrl'")
            
            if (savedIp.isEmpty() && savedFallbackUrl.isEmpty()) {
                // No URLs available, show intro settings page
                Log.d("Labarr", "No URLs available, showing intro screen")
                showNoUrlsScreen()
            } else {
                // URLs are available, just hide the no URLs screen (don't reload)
                Log.d("Labarr", "URLs available, hiding no URLs screen")
                hideNoUrlsScreen()
            }
            applyAutoRotateSetting()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jsonDataManager = JsonDataManager(this)
        
        // Set up modern back press handling
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        
        // Main function that handles PIN check and app initialization
        mainAppFlow()
    }
    
    private fun mainAppFlow() {
        if (!jsonDataManager.isPinSet()) {
            // No PIN set - initialize app normally
            initializeAppWithoutPin()
        } else if (isPinVerified || isPinVerifiedInSession()) {
            // PIN already verified in this session - initialize app
            isPinVerified = true
            initializeAppWithoutPin()
        } else {
            // PIN is set but not verified - show verification dialog
            showPinVerificationDialog()
        }
    }
    
    private fun isPinVerifiedInSession(): Boolean {
        // Check if PIN was verified in this session (stored in SharedPreferences)
        val prefs = getSharedPreferences("LabarrPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("pin_verified_session", false)
    }
    
    private fun setPinVerifiedInSession() {
        // Mark PIN as verified for this session
        val prefs = getSharedPreferences("LabarrPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pin_verified_session", true).apply()
    }
    
    private fun createWebView() {
        // Dynamically create WebView
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Add WebView to container
        binding.webviewContainer.addView(webView)
    }
    
    private fun initializeAppWithoutPin() {
        // Create WebView since no PIN is required
        createWebView()
        
        // Initialize all app components
        initializeAppComponents()
        
        // Load URLs immediately since no PIN is required
        loadAppropriateUrlAfterPinVerification()
    }

    private fun hideSystemUI() {
        // Configure status bar to use system theme
        configureStatusBarForSystemTheme()
        
        // Show status bar but hide navigation bar for mostly fullscreen experience
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            window.insetsController?.let { controller ->
                // Only hide navigation bars, keep status bar visible
                controller.hide(WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // Android 10 and below
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
        
        // Prevent keyboard from affecting layout
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
    }

    private fun configureStatusBarForSystemTheme() {
        // Make status bar transparent and use system theme
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        // Set status bar icons to use system theme (light/dark)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Check if we're in dark mode
            val isDarkMode = resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            // Use modern API for Android 11+ (API 30+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    // Set light status bar icons for light mode, dark icons for dark mode
                    if (isDarkMode) {
                        controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS.inv(),
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                    } else {
                        controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                    }
                }
            } else {
                // Use deprecated API for Android 6-10 (API 23-29)
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (isDarkMode) {
                    window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                } else {
                    window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
        }
    }

    private fun setupMovableBackButton() {
        // Store original position
        binding.backButton.post {
            backButtonOriginalX = binding.backButton.x
            backButtonOriginalY = binding.backButton.y
        }

        var longPressStartTime = 0L
        var isLongPressActive = false
        var hasTriggeredLongPress = false
        var hasVibrated = false
        var vibrationHandler: Handler? = null

        // Touch listener for back button
        binding.backButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressStartTime = System.currentTimeMillis()
                    isLongPressActive = false
                    hasTriggeredLongPress = false
                    
                    // Cancel any existing vibration timer
                    vibrationHandler?.removeCallbacksAndMessages(null)
                    
                    // Set up vibration timer for 720ms
                    vibrationHandler = Handler(Looper.getMainLooper())
                    vibrationHandler?.postDelayed({
                        if (!hasVibrated) {
                            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                                vibratorManager.defaultVibrator
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                @Suppress("DEPRECATION")
                                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            } else {
                                @Suppress("DEPRECATION")
                                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(50)
                            }
                            hasVibrated = true
                        }
                    }, 720)
                    
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isLongPressActive) {
                        // Move the button to follow finger
                        val parent = binding.backButton.parent as View
                        
                        // Convert screen coordinates to parent view coordinates
                        val parentLocation = IntArray(2)
                        parent.getLocationOnScreen(parentLocation)
                        
                        val x = event.rawX - parentLocation[0] - binding.backButton.width / 2
                        val y = event.rawY - parentLocation[1] - binding.backButton.height / 2
                        
                        // Use the actual parent view bounds (which accounts for all system UI)
                        val padding = (10 * resources.displayMetrics.density).toInt() // Convert dp to pixels
                        val maxX = parent.width - binding.backButton.width - padding
                        val maxY = parent.height - binding.backButton.height - padding
                        
                        binding.backButton.x = x.coerceIn(padding.toFloat(), maxX.toFloat())
                        binding.backButton.y = y.coerceIn(padding.toFloat(), maxY.toFloat())
                        
                        // Save new position
                        runOnUiThread {
                            saveBackButtonState()
                        }
                        true
                    } else {
                        val currentTime = System.currentTimeMillis()
                        val pressDuration = currentTime - longPressStartTime
                        
                        // Enable movement at 750ms
                        if (pressDuration >= 750 && !hasTriggeredLongPress) {
                            isLongPressActive = true
                            hasTriggeredLongPress = true
                        }
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // Cancel vibration timer
                    vibrationHandler?.removeCallbacksAndMessages(null)
                    
                    if (isLongPressActive) {
                        isLongPressActive = false
                        hasTriggeredLongPress = false
                        hasVibrated = false
                        true
                    } else {
                        // Normal click
                        val currentTime = System.currentTimeMillis()
                        val pressDuration = currentTime - longPressStartTime
                        
                        if (pressDuration < 750) {
                            // Vibrate on tap
                            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                                vibratorManager.defaultVibrator
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                @Suppress("DEPRECATION")
                                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            } else {
                                @Suppress("DEPRECATION")
                                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            }
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(30)
                            }
                            
                            // Handle back navigation
                            if (webView?.canGoBack() == true) {
                                webView?.goBack()
                            }
                        }
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun vibrateOnTouch() {
        // Check if vibration is enabled in settings
        val settings = jsonDataManager.loadSettings()
        if (!settings.vibrationEnabled) {
            return
        }
        
        // Vibrate for 30ms
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(30)
        }
    }

    private fun lockBackButtonPosition() {
        isBackButtonLocked = false  // Don't lock - allow immediate repositioning
        isBackButtonMovable = false
        
        // Restore back button functionality
        binding.backButton.alpha = 0.7f
        
        // Save state
        saveBackButtonState()
    }

    private fun showNoUrlsScreen() {
        Log.d("Labarr", "showNoUrlsScreen() called")
        webView?.visibility = View.GONE
        binding.noUrlsScreen.visibility = View.VISIBLE
    }

    private fun hideNoUrlsScreen() {
        Log.d("Labarr", "hideNoUrlsScreen() called")
        webView?.visibility = View.VISIBLE
        binding.noUrlsScreen.visibility = View.GONE
    }

    private fun loadBackButtonState() {
        isBackButtonLocked = jsonDataManager.loadSettings().backButtonLocked
        val savedX = jsonDataManager.loadSettings().backButtonX
        val savedY = jsonDataManager.loadSettings().backButtonY
        
        if (savedX >= 0 && savedY >= 0) {
            // Wait for the parent view to be laid out before validating position
            (binding.backButton.parent as? View)?.post {
                // Validate saved position against current screen bounds
                val validatedPosition = validateBackButtonPosition(savedX, savedY)
                binding.backButton.x = validatedPosition.first
                binding.backButton.y = validatedPosition.second
            }
        }
    }
    
    private fun validateBackButtonPosition(x: Float, y: Float): Pair<Float, Float> {
        // Use the actual parent view bounds (which accounts for all system UI)
        val parent = binding.backButton.parent as View
        val padding = (10 * resources.displayMetrics.density).toInt() // Convert dp to pixels
        
        // Ensure parent view is properly laid out
        if (parent.width <= 0 || parent.height <= 0) {
            // Parent not laid out yet, use screen dimensions as fallback
            val displayMetrics = resources.displayMetrics
            val maxX = displayMetrics.widthPixels - binding.backButton.width - padding
            val maxY = displayMetrics.heightPixels - binding.backButton.height - padding
            
            return Pair(
                x.coerceIn(padding.toFloat(), maxX.toFloat()),
                y.coerceIn(padding.toFloat(), maxY.toFloat())
            )
        }
        
        val maxX = parent.width - binding.backButton.width - padding
        val maxY = parent.height - binding.backButton.height - padding
        
        return Pair(
            x.coerceIn(padding.toFloat(), maxX.toFloat()),
            y.coerceIn(padding.toFloat(), maxY.toFloat())
        )
    }

    private fun saveBackButtonState() {
        jsonDataManager.saveSettings(
            jsonDataManager.loadSettings().copy(
                backButtonLocked = isBackButtonLocked,
                backButtonX = binding.backButton.x,
                backButtonY = binding.backButton.y
            )
        )
    }

    private fun unlockBackButton() {
        isBackButtonLocked = false
        isBackButtonMovable = false
        binding.backButton.alpha = 0.7f
        
        // Save state but don't prevent immediate repositioning
        saveBackButtonState()
    }

    private fun applyAutoRotateSetting() {
        val settings = jsonDataManager.loadSettings()
        if (settings.autoRotateEnabled) {
            // Enable auto-rotate
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            // Disable auto-rotate (portrait only)
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    
    private fun applyBackButtonVisibility() {
        val settings = jsonDataManager.loadSettings()
        binding.backButton.visibility = if (settings.showBackButton) View.VISIBLE else View.GONE
    }

    override fun onPause() {
        super.onPause()
        // Save back button state when app is paused
        saveBackButtonState()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clear PIN session when app is properly destroyed (not just rotated)
        if (isFinishing) {
            val prefs = getSharedPreferences("LabarrPrefs", Context.MODE_PRIVATE)
            prefs.edit().remove("pin_verified_session").apply()
        }
    }

    private fun setupSlideMenu() {
        // Setup trigger bar touch detection with vertical bounds checking and drag feedback
        binding.triggerBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isMenuOpen) {
                        // Calculate menu's expected position (centered vertically)
                        val screenHeight = resources.displayMetrics.heightPixels
                        val statusBarHeight = getStatusBarHeight()
                        val availableHeight = screenHeight - statusBarHeight
                        val menuHeight = (5 * 64 + 4 * 16 + 32).toFloat() // 5 buttons * 64dp + 4 margins * 16dp + 32dp padding
                        val menuHeightPixels = (menuHeight * resources.displayMetrics.density).toInt()
                        val menuTop = statusBarHeight + (availableHeight - menuHeightPixels) / 2
                        val menuBottom = menuTop + menuHeightPixels
                        
                        // Check if touch is within menu's vertical bounds
                        val touchY = event.rawY
                        if (touchY >= menuTop && touchY <= menuBottom) {
                            // Vibrate on touch if enabled
                            vibrateOnTouch()
                            
                            // Start drag feedback
                            startDragFeedback()
                            dragStartX = event.rawX
                            isDragging = true
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dragDistance = event.rawX - dragStartX
                        val maxDragDistance = (120 * resources.displayMetrics.density).toFloat() // Menu width
                        val progress = (dragDistance / maxDragDistance).coerceIn(0f, 1f)
                        updateDragFeedback(progress)
                        
                        // Smoothly move the menu with the drag
                        if (progress > 0f) {
                            if (!isMenuOpen) {
                                isMenuOpen = true
                                binding.slideMenu.visibility = View.VISIBLE
                            }
                            val menuTranslation = (progress * maxDragDistance) - maxDragDistance
                            binding.slideMenu.translationX = menuTranslation
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        hideDragFeedback()
                        
                        // Get final drag progress
                        val dragDistance = event.rawX - dragStartX
                        val maxDragDistance = (120 * resources.displayMetrics.density).toFloat()
                        val progress = (dragDistance / maxDragDistance).coerceIn(0f, 1f)
                        
                        // Complete or reverse the menu animation based on progress
                        if (progress >= 0.5f) {
                            // Complete the opening animation
                            completeMenuOpen()
                        } else {
                            // Reverse the animation and close
                            closeSlideMenu()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        // Setup menu button click listeners
        binding.menuSettings.setOnClickListener {
            closeSlideMenu()
            openSettings()
        }

        binding.menuDesktopToggle.setOnClickListener {
            closeSlideMenu()
            toggleDesktopView()
        }
        
        // Update desktop mode icon based on current state
        updateDesktopToggleAppearance()
        
        // Setup settings button for no URLs screen
        binding.settingsButton.setOnClickListener {
            openSettings()
        }

        // Home button - go to configured IP/fallback URL
        binding.menuHome.setOnClickListener {
            closeSlideMenu()
            loadAppropriateUrl()
        }

        // Refresh button - simple page reload
        binding.menuReload.setOnClickListener {
            closeSlideMenu()
            webView?.reload()
        }

        binding.menuForward.setOnClickListener {
            closeSlideMenu()
            if (webView?.canGoForward() == true) {
                webView?.goForward()
            }
        }

        // Cancel timer when touching the menu itself
        binding.slideMenu.setOnTouchListener { _, _ ->
            if (isMenuOpen) {
                cancelMenuAutoCloseTimer()
            }
            false
        }
        
        // Close menu when touching outside
        webView?.setOnTouchListener { _, event ->
            if (isMenuOpen && event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x
                // Convert menu width + margin to pixels
                val menuWidthPixels = (120 * resources.displayMetrics.density).toInt()
                val marginPixels = (20 * resources.displayMetrics.density).toInt()
                if (x > (menuWidthPixels + marginPixels)) {
                    closeSlideMenu()
                }
            }
            false
        }
    }

    private fun openSlideMenu() {
        isMenuOpen = true
        binding.slideMenu.visibility = View.VISIBLE
        
        val animator = binding.slideMenu.animate()
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
        
        // Feedback line is already hidden by this point
        
        animator.start()
        
        // Start auto-close timer (1.25 seconds)
        startMenuAutoCloseTimer()
    }
    
    private fun completeMenuOpen() {
        val animator = binding.slideMenu.animate()
            .translationX(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
        
        animator.start()
        
        // Start auto-close timer (1.25 seconds)
        startMenuAutoCloseTimer()
    }

    private fun closeSlideMenu() {
        isMenuOpen = false
        
        // Cancel auto-close timer
        cancelMenuAutoCloseTimer()
        
        // Convert 120dp to pixels for proper animation
        val menuWidthPixels = (120 * resources.displayMetrics.density).toInt()
        
        val animator = binding.slideMenu.animate()
            .translationX(-menuWidthPixels.toFloat())
            .setDuration(300)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
        
        // No feedback line animation on close - just let it stay hidden
        
        animator.withEndAction {
            binding.slideMenu.visibility = View.GONE
            // Reset translation to ensure it's properly hidden
            binding.slideMenu.translationX = -menuWidthPixels.toFloat()
        }.start()
    }
    
    private fun startMenuAutoCloseTimer() {
        // Cancel any existing timer
        cancelMenuAutoCloseTimer()
        
        // Create new timer that closes menu after 1.5 seconds
        menuAutoCloseTimer = Handler(Looper.getMainLooper())
        menuAutoCloseTimer?.postDelayed({
            if (isMenuOpen) {
                closeSlideMenu()
            }
        }, 1500) // 1.5 seconds
    }
    
    private fun cancelMenuAutoCloseTimer() {
        menuAutoCloseTimer?.removeCallbacksAndMessages(null)
        menuAutoCloseTimer = null
    }
    
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }
    
    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }
    
    private fun startDragFeedback() {
        // Set the feedback line height to match the menu
        val menuHeight = (5 * 64 + 4 * 16 + 32).toFloat()
        val menuHeightPixels = (menuHeight * resources.displayMetrics.density).toInt()
        val feedbackParams = binding.dragFeedbackLine.layoutParams
        feedbackParams.height = menuHeightPixels
        binding.dragFeedbackLine.layoutParams = feedbackParams
        
        // Show the feedback line at starting position with fade-in
        binding.dragFeedbackLine.visibility = View.VISIBLE
        binding.dragFeedbackLine.alpha = 0f
        binding.dragFeedbackLine.translationX = -4f
        
        // Fade in to darker opacity
        binding.dragFeedbackLine.animate()
            .alpha(0.8f)
            .setDuration(100)
            .start()
    }
    
    private fun updateDragFeedback(progress: Float) {
        // Move the line as drag progresses
        val maxTranslation = (120 * resources.displayMetrics.density).toFloat() // Menu width
        val translation = progress * maxTranslation
        binding.dragFeedbackLine.translationX = translation - 4f
        
        // Fade out completely by 75% progress
        val fadeProgress = (progress * 1.33f).coerceAtMost(1f) // Scale progress to reach 1.0 at 75% drag
        binding.dragFeedbackLine.alpha = (0.8f - (fadeProgress * 0.8f)).coerceAtLeast(0f)
        
        // Hide completely when fully faded (at 75% drag progress)
        if (fadeProgress >= 1f) {
            binding.dragFeedbackLine.visibility = View.GONE
        }
    }
    
    private fun hideDragFeedback() {
        binding.dragFeedbackLine.visibility = View.GONE
    }

    private fun openSettings() {
        try {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "Settings not available", Toast.LENGTH_SHORT).show()
        }
    }


    
    private fun checkForSavedCredentials() {
        // Don't automatically show dialog - wait for user interaction
    }

    private var isCredentialDetectionInjected = false
    
    private fun detectAndPromptForCredentials() {
        // Check if automatic login system is enabled
        val settings = jsonDataManager.loadSettings()
        if (!settings.enableAutomaticLoginSystem) {
            Log.d("Labarr", "Automatic login system disabled - skipping credential detection")
            return
        }
        
        // Prevent multiple injections
        if (isCredentialDetectionInjected) {
            return
        }
        isCredentialDetectionInjected = true
        
        val js = """
            (function() {
                try {
                    // Prevent multiple script executions - use a more persistent flag
                    if (window.labarrCredentialDetectionLoaded && window.labarrCredentialDetectionLoaded === true) {
                        return;
                    }
                    window.labarrCredentialDetectionLoaded = true;
                
                var isMonitoring = false;
                var capturedCredentials = null;
                var hasPrompted = false;
                var credentialsAutofilled = false;
                var hasShownSavedCredentialsDialog = false;
                var credentialHandlingInProgress = false;
                
                // Comprehensive selectors for better detection
                var usernameSelectors = 'input[type="text"], input[type="email"], input[name*="user"], input[name*="login"], input[name*="email"], input[name*="account"], input[name*="username"], input[id*="user"], input[id*="login"], input[id*="email"], input[id*="account"], input[id*="username"], input[placeholder*="user"], input[placeholder*="email"], input[placeholder*="login"], input[placeholder*="account"], input[placeholder*="username"]';
                var passwordSelectors = 'input[type="password"], input[name*="pass"], input[id*="pass"], input[placeholder*="pass"]';
                
                // Simplified credential capture - no debouncing for performance
                function captureCurrentCredentials() {
                    var usernameInputs = document.querySelectorAll(usernameSelectors);
                    var passwordInputs = document.querySelectorAll(passwordSelectors);
                    
                    if (usernameInputs.length > 0 && passwordInputs.length > 0) {
                        var username = usernameInputs[0].value.trim();
                        var password = passwordInputs[0].value;
                        
                        if (username && password && username.length > 0 && password.length > 0) {
                            if (!capturedCredentials || 
                                capturedCredentials.username !== username || 
                                capturedCredentials.password !== password) {
                                
                                capturedCredentials = {
                                    username: username,
                                    password: password,
                                    site: window.location.hostname + (window.location.port ? ':' + window.location.port : '')
                                };
                            }
                        }
                    }
                }
                

                
                // Enhanced login form detection with lightweight retry
                function checkForLoginForm() {
                    if (isMonitoring) return; // Already monitoring
                    
                    var usernameInputs = document.querySelectorAll(usernameSelectors);
                    var passwordInputs = document.querySelectorAll(passwordSelectors);
                    
                    if (usernameInputs.length > 0 && passwordInputs.length > 0) {
                        isMonitoring = true;
                        
                        // Immediate interrupt - if already handling credentials, don't check again
                        if (credentialHandlingInProgress || hasShownSavedCredentialsDialog) {
                            return;
                        }
                        
                        // Set interrupt immediately before calling the function
                        credentialHandlingInProgress = true;
                        hasShownSavedCredentialsDialog = true;
                        
                        var site = window.location.hostname + (window.location.port ? ':' + window.location.port : '');
                        try {
                            if (window.AndroidInterface && window.AndroidInterface.checkForSavedCredentials) {
                                window.AndroidInterface.checkForSavedCredentials(site);
                                credentialsAutofilled = true;
                            }
                        } catch (e) {
                            console.error('Labarr: Error calling AndroidInterface:', e);
                        }
                        
                        // Add lightweight credential capture for update detection
                        startCredentialCapture();
                    } else {
                        // Lightweight retry for dynamic content - only 2 attempts
                        if (!isMonitoring) {
                            setTimeout(function() {
                                if (!isMonitoring) {
                                    checkForLoginForm();
                                }
                            }, 2000); // Wait 2 seconds before retry
                        }
                    }
                }
                
                // Lightweight credential capture for update detection
                function startCredentialCapture() {
                    // Single submit listener for credential updates
                    document.addEventListener('submit', function(e) {
                        captureAndSubmitCredentials();
                    });
                    
                    // Single click listener for login buttons - more comprehensive
                    document.addEventListener('click', function(e) {
                        var target = e.target;
                        if (target.tagName === 'BUTTON' || target.tagName === 'INPUT' || target.tagName === 'A') {
                            var text = (target.textContent || target.value || target.innerText || '').toLowerCase();
                            var className = (target.className || '').toLowerCase();
                            var id = (target.id || '').toLowerCase();
                            
                            if (text.includes('login') || text.includes('sign in') || text.includes('signin') || 
                                text.includes('submit') || text.includes('log in') || text.includes('logon') ||
                                className.includes('login') || className.includes('signin') || className.includes('submit') ||
                                id.includes('login') || id.includes('signin') || id.includes('submit') ||
                                target.type === 'submit' || target.type === 'button') {
                                captureAndSubmitCredentials();
                            }
                        }
                    });
                    
                    // Also listen for Enter key on password fields
                    document.addEventListener('keydown', function(e) {
                        if ((e.key === 'Enter' || e.keyCode === 13) && e.target.tagName === 'INPUT' && e.target.type === 'password') {
                            captureAndSubmitCredentials();
                        }
                    });
                }
                
                // Capture and submit credentials for update detection
                function captureAndSubmitCredentials() {
                    console.log('Labarr: captureAndSubmitCredentials called');
                    var usernameInputs = document.querySelectorAll(usernameSelectors);
                    var passwordInputs = document.querySelectorAll(passwordSelectors);
                    
                    if (usernameInputs.length > 0 && passwordInputs.length > 0) {
                        var username = usernameInputs[0].value.trim();
                        var password = passwordInputs[0].value;
                        
                        if (username && password && username.length > 0 && password.length > 0) {
                            var site = window.location.hostname + (window.location.port ? ':' + window.location.port : '');
                            
                            // Get stored credentials and compare
                            try {
                                if (window.AndroidInterface && window.AndroidInterface.getStoredCredentials) {
                                    var storedCredentials = window.AndroidInterface.getStoredCredentials(site);
                                    
                                    if (storedCredentials && storedCredentials !== "") {
                                        // Credentials exist, compare them
                                        var parts = storedCredentials.split('|');
                                        if (parts.length === 2) {
                                            var storedUsername = parts[0].trim();
                                            var storedPassword = parts[1].trim();
                                            
                                            // Compare credentials
                                            if (username === storedUsername && password === storedPassword) {
                                                console.log('Labarr: Credentials match, no dialog needed');
                                                return; // Same credentials, don't show dialog
                                            } else {
                                                console.log('Labarr: Credentials different, showing update dialog');
                                                // Different credentials, show update dialog
                                                window.AndroidInterface.promptForSubmittedCredentials(username, password, site, credentialsAutofilled);
                                            }
                                        }
                                    } else {
                                        console.log('Labarr: No stored credentials, showing save dialog');
                                        // No stored credentials, show save dialog
                                        window.AndroidInterface.promptForSaveCredentials(username, password, site);
                                    }
                                } else {
                                    console.error('Labarr: AndroidInterface not available');
                                }
                            } catch (e) {
                                console.error('Labarr: Error in credential comparison:', e);
                            }
                        }
                    }
                }
                

                

                
                // Initial check with lightweight monitoring
                checkForLoginForm();
                
                // Lightweight mutation observer - only watches for password fields
                var observer = new MutationObserver(function(mutations) {
                    if (isMonitoring) return; // Already found login form
                    
                    for (var i = 0; i < mutations.length; i++) {
                        var mutation = mutations[i];
                        if (mutation.type === 'childList' && mutation.addedNodes.length > 0) {
                            for (var j = 0; j < mutation.addedNodes.length; j++) {
                                var node = mutation.addedNodes[j];
                                if (node.nodeType === 1 && node.querySelector && node.querySelector('input[type="password"]')) {
                                    checkForLoginForm();
                                    return;
                                }
                            }
                        }
                    }
                });
                
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
                } catch (e) {
                    console.error('Labarr: Error in credential detection script:', e);
                }
            })();
        """.trimIndent()
        Log.d("Labarr", "Injecting optimized login detection JavaScript")
        // Add a small delay to ensure page is ready
        webView?.postDelayed({
            webView?.evaluateJavascript(js) { result ->
                Log.d("Labarr", "Login detection JavaScript injection result: $result")
            }
        }, 500)
    }

    private fun saveCredentialsForSite(site: String, username: String, password: String, name: String = "") {
        jsonDataManager.saveCredentials(site, username, password, name)
    }

    private fun getCredentialsForSite(site: String): List<Pair<String, String>> {
        return jsonDataManager.loadCredentials(site)
    }

    private fun promptForWebAddress() {
        val editText = EditText(this)
        editText.hint = "https://your-domain.com"
        AlertDialog.Builder(this)
            .setTitle("Enter Web Address")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val webAddress = editText.text.toString().trim()
                if (webAddress.isNotEmpty()) {
                    // Save as fallback URL
                    jsonDataManager.saveSettings(jsonDataManager.loadSettings().copy(fallbackUrl = webAddress))
                    webView?.loadUrl(webAddress)
                } else {
                    promptForWebAddress()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> 
                // Show blank black screen instead of finishing
                webView?.loadUrl("about:blank")
            }
            .show()
    }

    private fun forceReloadWebView() {
        webView?.let { webView ->
            // Clear all WebView data
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            
            // Reload the appropriate URL
            loadAppropriateUrl()
        }
    }
    
    private fun loadAppropriateUrl() {
        val ip = jsonDataManager.loadSettings().homelabIp
        val fallbackUrl = jsonDataManager.loadSettings().fallbackUrl
        val trustedWifi = jsonDataManager.loadSettings().trustedWifi
        val wifiSsidEnabled = jsonDataManager.loadSettings().wifiSsidEnabled
        
        // If no URLs are saved, show blank screen
        if (ip.isEmpty() && fallbackUrl.isEmpty()) {
            webView?.loadUrl("about:blank")
            return
        }
        
        val urlToLoad = when {
            // Always use IP if available (unless WiFi SSID check is enabled and we're not on trusted WiFi)
            ip.isNotEmpty() && (!wifiSsidEnabled || trustedWifi.isEmpty() || isConnectedToTrustedWifi()) -> {
                formatUrl(ip)
            }
            // Only use fallback URL if no IP is available
            fallbackUrl.isNotEmpty() -> {
                formatUrl(fallbackUrl)
            }
            // Last resort: blank screen
            else -> {
                "about:blank"
            }
        }
        
        // Clear WebView state before loading new URL
        webView?.clearHistory()
        webView?.clearFormData()
        
        // Load the URL
        webView?.loadUrl(urlToLoad)
        
        // Debug logging
        Log.d("Labarr", "Loading URL: $urlToLoad")
        Log.d("Labarr", "IP: $ip, Fallback: $fallbackUrl, Trusted WiFi: $trustedWifi, WiFi Check Enabled: $wifiSsidEnabled")
        Log.d("Labarr", "Connected to trusted WiFi: ${isConnectedToTrustedWifi()}")
        Log.d("Labarr", "WiFi connected: ${isWifiConnected()}")
    }
    
    private fun formatUrl(url: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "http:$url"
            url.startsWith("/") -> "http://$url"
            else -> "http://$url"
        }
    }

    private fun isWifiConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun isConnectedToTrustedWifi(): Boolean {
        val trustedWifi = jsonDataManager.loadSettings().trustedWifi
        if (!isWifiConnected()) return false
        
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo
        val currentSSID = wifiInfo.ssid
        
        // Remove quotes from SSID if present
        val cleanSSID = currentSSID?.removeSurrounding("\"") ?: ""
        return cleanSSID == trustedWifi
    }
    
    private fun extractFileName(contentDisposition: String?, url: String, mimeType: String): String {
        // Try to extract filename from content disposition
        if (!contentDisposition.isNullOrEmpty()) {
            // Handle RFC 5987 format: filename*=UTF-8''filename
            val rfc5987Regex = "filename\\*=UTF-8''([^;]+)".toRegex(RegexOption.IGNORE_CASE)
            val rfc5987Match = rfc5987Regex.find(contentDisposition)
            if (rfc5987Match != null) {
                val encodedFilename = rfc5987Match.groupValues[1]
                try {
                    val decodedFilename = java.net.URLDecoder.decode(encodedFilename, "UTF-8")
                    return decodedFilename
                } catch (e: Exception) {
                    Log.e("Labarr", "ExtractFileName - Error decoding RFC 5987 filename: ${e.message}")
                }
            }
            
            // Handle standard format: filename="filename" or filename=filename
            val filenameRegex = "filename[^;=\\n]*=((['\"]).*?\\2|[^;\\n]*)".toRegex(RegexOption.IGNORE_CASE)
            val match = filenameRegex.find(contentDisposition)
            if (match != null) {
                var filename = match.groupValues[1].removeSurrounding("\"", "\"")
                if (filename.isNotEmpty()) {
                    // Try to URL decode the filename if it looks encoded
                    if (filename.contains("%")) {
                        try {
                            val decodedFilename = java.net.URLDecoder.decode(filename, "UTF-8")
                            return decodedFilename
                        } catch (e: Exception) {
                            Log.e("Labarr", "ExtractFileName - Error URL decoding filename: ${e.message}")
                        }
                    }
                    
                    // Remove any leading/trailing whitespace
                    filename = filename.trim()
                    return filename
                }
            }
        }
        
        // Try to extract filename from URL
        try {
            val uri = Uri.parse(url)
            val path = uri.lastPathSegment
            if (!path.isNullOrEmpty() && path.contains(".")) {
                return path
            }
        } catch (e: Exception) {
            // Ignore parsing errors
            Log.e("Labarr", "ExtractFileName - Error parsing URL: ${e.message}")
        }
        
        // Generate filename based on mime type and timestamp
        val timestamp = System.currentTimeMillis()
        val extension = when {
            mimeType.startsWith("image/") -> ".jpg"
            mimeType.startsWith("video/") -> ".mp4"
            mimeType.startsWith("audio/") -> ".mp3"
            mimeType == "application/pdf" -> ".pdf"
            mimeType.startsWith("text/") -> ".txt"
            mimeType.startsWith("application/") -> ".bin"
            else -> ".file"
        }
        
        return "download_$timestamp$extension"
    }
    


    private fun setupWebViewForDevice() {
        webView?.let { webView ->
            val currentUrl = webView.url ?: ""
            val site = extractSiteFromUrl(currentUrl)
            
            // Check for site-specific setting first
            val siteDesktopView = if (site.isNotEmpty()) {
                jsonDataManager.loadSiteDesktopView(site)
            } else {
                null
            }
            
            // Use site-specific setting if available, otherwise use global setting
            val desktopView = siteDesktopView ?: jsonDataManager.loadSettings().desktopView
            
            if (desktopView) {
                // Enable desktop view for better compatibility
                val userAgent = webView.settings.userAgentString
                val desktopUserAgent = userAgent.replace("Mobile", "eliboM").replace("Android", "X11; Linux x86_64")
                webView.settings.userAgentString = desktopUserAgent
                
                // Set desktop viewport
                webView.settings.loadWithOverviewMode = true
                webView.settings.useWideViewPort = true
            } else {
                // Use mobile view
                webView.settings.userAgentString = webView.settings.userAgentString
                webView.settings.loadWithOverviewMode = false
                webView.settings.useWideViewPort = false
            }
        }
    }
    
    private fun extractSiteFromUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: ""
            val port = if (uri.port != -1) ":${uri.port}" else ""
            val path = uri.path ?: ""
            "$host$port$path"
        } catch (e: Exception) {
            ""
        }
    }



    private fun toggleDesktopView() {
        val currentDesktopView = jsonDataManager.loadSettings().desktopView
        val newDesktopView = !currentDesktopView
        
        jsonDataManager.saveSettings(jsonDataManager.loadSettings().copy(desktopView = newDesktopView))
        
        // Update desktop toggle button appearance
        if (newDesktopView) {
            binding.menuDesktopToggle.setImageResource(R.drawable.ic_monitor)
            binding.menuDesktopToggle.imageTintList = ColorStateList.valueOf(Color.WHITE)
        } else {
            binding.menuDesktopToggle.setImageResource(R.drawable.ic_phone)
            binding.menuDesktopToggle.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
        
        // Reapply WebView settings
        setupWebViewForDevice()
        
        // Reload current page with new view mode
        val currentUrl = webView?.url
        if (currentUrl != null) {
            webView?.reload()
        }
        
        // Show feedback
        val viewMode = if (newDesktopView) "Desktop" else "Mobile"
        Toast.makeText(this, "Switched to $viewMode view", Toast.LENGTH_SHORT).show()
    }

    private fun updateDesktopToggleAppearance() {
        val isDesktopView = jsonDataManager.loadSettings().desktopView
        if (isDesktopView) {
            binding.menuDesktopToggle.setImageResource(R.drawable.ic_monitor)
            binding.menuDesktopToggle.imageTintList = ColorStateList.valueOf(Color.WHITE)
        } else {
            binding.menuDesktopToggle.setImageResource(R.drawable.ic_phone)
            binding.menuDesktopToggle.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    private fun saveCurrentUrl() {
        val currentUrl = webView?.url ?: return
        val urlType = if (isConnectedToTrustedWifi()) "IP" else "Fallback"
        
        AlertDialog.Builder(this)
            .setTitle("Save Current URL")
            .setMessage("Save '$currentUrl' as $urlType address?")
            .setPositiveButton("Save") { _, _ ->
                if (urlType == "IP") {
                    jsonDataManager.saveSettings(jsonDataManager.loadSettings().copy(homelabIp = currentUrl))
                } else {
                    jsonDataManager.saveSettings(jsonDataManager.loadSettings().copy(fallbackUrl = currentUrl))
                }
                Toast.makeText(this, "URL saved as $urlType", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSavedUrls() {
        AlertDialog.Builder(this)
            .setTitle("Delete Saved URLs")
            .setMessage("Delete all saved IP and fallback URLs?")
            .setPositiveButton("Delete All") { _, _ ->
                jsonDataManager.saveSettings(jsonDataManager.loadSettings().copy(homelabIp = "", fallbackUrl = ""))
                Toast.makeText(this, "All URLs deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCredentialDialog() {
        showCredentialManagerDialog()
    }

    private fun showCredentialManagerDialog() {
        val sites = getSavedSites()
        val siteItems = sites.toTypedArray()
        
        if (siteItems.isEmpty()) {
            showAddCredentialDialog()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Manage Credentials")
            .setItems(siteItems) { _, which ->
                val selectedSite = siteItems[which]
                showSiteCredentialsDialog(selectedSite)
            }
            .setPositiveButton("Add New") { _, _ ->
                showAddCredentialDialog()
            }
            .setNeutralButton("Autofill Current") { _, _ ->
                triggerAutofillForCurrentPage()
            }
            .setNegativeButton("Force Check") { _, _ ->
                // Call the JavaScript interface function directly
                val currentSite = getCurrentDomain()
                if (currentSite.isNotEmpty()) {
                    webView?.evaluateJavascript("window.AndroidInterface.checkForSavedCredentials('$currentSite');", null)
                }
            }
            .show()
    }

    private fun getSavedSites(): List<String> {
        return jsonDataManager.loadCredentials().map { it.site }
    }

    private fun showSiteCredentialsDialog(site: String) {
        val credentials = getCredentialsForSite(site)
        val credentialItems = credentials.map { "${it.first} (${it.second.take(3)}***)" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Credentials for $site")
            .setItems(credentialItems) { _, which ->
                val (username, password) = credentials[which]
                showEditCredentialDialog(site, username, password, which)
            }
            .setPositiveButton("Add New") { _, _ ->
                showAddCredentialDialog(site)
            }
            .setNegativeButton("Delete All") { _, _ ->
                deleteAllCredentialsForSite(site)
            }
            .setNeutralButton("Back") { _, _ ->
                showCredentialManagerDialog()
            }
            .show()
    }

    private fun triggerAutofillForCurrentPage() {
        val currentSite = getCurrentDomain()
        if (currentSite.isNotEmpty()) {
            val credentials = getCredentialsForSite(currentSite)
            if (credentials.isNotEmpty()) {
                val (username, password) = credentials.first()
                Log.d("Labarr", "Manually triggering autofill for site: $currentSite")
                injectCredentials(username, password)
                Toast.makeText(this, "Autofill triggered for $currentSite", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No saved credentials found for $currentSite", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Could not determine current site", Toast.LENGTH_SHORT).show()
        }
    }
    

    


    private fun showAddCredentialDialog(site: String = "", username: String = "", password: String = "") {
        val dialogView = layoutInflater.inflate(R.layout.credential_dialog, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.name_edit)
        val usernameEdit = dialogView.findViewById<EditText>(R.id.username_edit)
        val passwordEdit = dialogView.findViewById<EditText>(R.id.password_edit)
        val siteEdit = dialogView.findViewById<EditText>(R.id.site_edit)
        
        // Pre-fill with provided values
        siteEdit.setText(site)
        usernameEdit.setText(username)
        passwordEdit.setText(password)
        

        
        AlertDialog.Builder(this)
            .setTitle("Add Credentials")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameEdit.text.toString().trim()
                val newUsername = usernameEdit.text.toString().trim()
                val newPassword = passwordEdit.text.toString().trim()
                val newSite = siteEdit.text.toString().trim()
                
                if (newUsername.isNotEmpty() && newPassword.isNotEmpty() && newSite.isNotEmpty()) {
                    saveCredentialsForSite(newSite, newUsername, newPassword, name)
                    Toast.makeText(this, "Credentials saved for $newSite", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCredentialDialog(site: String, username: String, password: String, index: Int) {
        val dialogView = layoutInflater.inflate(R.layout.credential_dialog, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.name_edit)
        val usernameEdit = dialogView.findViewById<EditText>(R.id.username_edit)
        val passwordEdit = dialogView.findViewById<EditText>(R.id.password_edit)
        val siteEdit = dialogView.findViewById<EditText>(R.id.site_edit)
        
        // Try to get existing credential to populate name
        val existingCredentials = jsonDataManager.getCredentialsForSite(site)
        val existingCredential = existingCredentials.find { it.username == username }
        nameEdit.setText(existingCredential?.name ?: "")
        
        usernameEdit.setText(username)
        passwordEdit.setText(password)
        siteEdit.setText(site)
        siteEdit.isEnabled = false
        
        AlertDialog.Builder(this)
            .setTitle("Edit Credentials")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = nameEdit.text.toString().trim()
                val newUsername = usernameEdit.text.toString().trim()
                val newPassword = passwordEdit.text.toString().trim()
                
                if (newUsername.isNotEmpty() && newPassword.isNotEmpty()) {
                    // Update credentials for this site (removes old ones and adds new ones)
                    jsonDataManager.updateCredentialsForSite(site, newUsername, newPassword, name)
                    Toast.makeText(this, "Credentials updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Delete") { _, _ ->
                deleteCredential(site, index)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }



    private fun deleteCredential(site: String, index: Int) {
        jsonDataManager.deleteCredentials(site, index)
        Toast.makeText(this, "Credential deleted", Toast.LENGTH_SHORT).show()
    }

    private fun deleteAllCredentialsForSite(site: String) {
        jsonDataManager.deleteCredentials(site)
        Toast.makeText(this, "All credentials deleted for $site", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        // Apply auto-rotate setting
        applyAutoRotateSetting()
        // Apply back button visibility setting
        applyBackButtonVisibility()
        // Only reload URL if WebView is null or empty (not on every resume)
        if (webView?.url.isNullOrEmpty() || webView?.url == "about:blank") {
            loadAppropriateUrl()
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun promptForCredentials() {
            runOnUiThread {
                showLoginDetectionDialog()
            }
        }
        
        @JavascriptInterface
        fun checkForSavedCredentials(site: String) {
    
            
            // Check if automatic login system is enabled
            val settings = jsonDataManager.loadSettings()
            if (!settings.enableAutomaticLoginSystem) {
                Log.d("Labarr", "Automatic login system disabled - ignoring credential check")
                return
            }
            
            // Prevent multiple rapid calls
            if (isCredentialDialogShowing) {
                Log.d("Labarr", "Credential dialog already showing, ignoring check request")
                return
            }
            
            runOnUiThread {
                val credentials = getCredentialsForSite(site)
    
                
                if (credentials.isNotEmpty()) {
                    val (username, password) = credentials.first()
                    
                    // Check if autofill is enabled in settings
                    val autofillSettings = jsonDataManager.loadSettings()
                    if (autofillSettings.autoFillCredentials) {
                        // Auto-fill is enabled - automatically inject credentials

                        injectCredentials(username, password)
                        // Reset interrupt signal after autofill
                        resetCredentialInterruptSignal()
                    } else {
                        // Auto-fill is disabled - show dialog asking user

                        showUseSavedCredentialsDialog(username, password, site)
                        // DON'T reset interrupt signal here - let the dialog handle it when dismissed
                    }
                } else {
        
                    // Reset interrupt signal if no credentials found
                    resetCredentialInterruptSignal()
                }
            }
        }

                    @JavascriptInterface
        fun promptForSubmittedCredentials(username: String, password: String, site: String, autofilled: Boolean = false) {
    
            
            // Check if automatic login system is enabled
            val settings = jsonDataManager.loadSettings()
            if (!settings.enableAutomaticLoginSystem) {
                Log.d("Labarr", "Automatic login system disabled - ignoring credential prompt")
                return
            }
            
            runOnUiThread {
                showSubmittedCredentialsDialog(username, password, site, autofilled)
            }
        }
        
        @JavascriptInterface
        fun promptForSaveCredentials(username: String, password: String, site: String) {
    
            
            // Check if automatic login system is enabled
            val settings = jsonDataManager.loadSettings()
            if (!settings.enableAutomaticLoginSystem) {
                Log.d("Labarr", "Automatic login system disabled - ignoring save credential prompt")
                return
            }
            
            runOnUiThread {
                showSubmittedCredentialsDialog(username, password, site, false)
            }
        }
        
        @JavascriptInterface
        fun getStoredCredentials(site: String): String {
            val credentials = getCredentialsForSite(site)
            return if (credentials.isNotEmpty()) {
                val (username, password) = credentials.first()
                "$username|$password"
            } else {
                ""
            }
        }
        
        @JavascriptInterface
        fun injectCredentialsRetry(username: String, password: String) {
    
            runOnUiThread {
                injectCredentials(username, password)
            }
        }
        

        
        @JavascriptInterface
        fun downloadFile(url: String, filename: String) {
            runOnUiThread {
                try {
                    val request = DownloadManager.Request(Uri.parse(url))
                    
                    // Add cookies if available
                    val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                    if (cookies != null) {
                        request.addRequestHeader("cookie", cookies)
                    }
                    
                    // Add user agent
                    request.addRequestHeader("User-Agent", webView?.settings?.userAgentString ?: "")
                    
                    request.setDescription("Downloading file...")
                    request.setTitle(filename)
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                    
                    val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    val downloadId = dm.enqueue(request)
                    
                    Log.d("Labarr", "Download started: $filename (ID: $downloadId)")
                    Toast.makeText(this@MainActivity, "Download started: $filename", Toast.LENGTH_SHORT).show()
                    
                } catch (e: Exception) {
                    Log.e("Labarr", "Download failed: ${e.message}")
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    
                    // Fallback: try to open in external browser
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e2: Exception) {
                        Toast.makeText(this@MainActivity, "Cannot download or open file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        

    }

    private fun showLoginDetectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Login Form Detected")
            .setMessage("Would you like to save credentials for this site?")
            .setPositiveButton("Save Credentials") { _, _ ->
                showAddCredentialDialog(getCurrentDomain())
            }
            .setNegativeButton("Not Now", null)
            .show()
    }
    
    private var hasShownCredentialDialog = false
    private var currentCredentialSite = ""
    private var lastCredentialDialogSite = ""
    private var isCredentialDialogShowing = false

    private fun showUseSavedCredentialsDialog(savedUsername: String, savedPassword: String, site: String) {
        // Prevent multiple dialogs for the same site
        if (hasShownCredentialDialog && currentCredentialSite == site) {
            Log.d("Labarr", "Credential dialog already shown for site: $site, skipping")
            return
        }
        
        // Additional protection - check if any dialog is currently showing
        if (isCredentialDialogShowing) {
            Log.d("Labarr", "Credential dialog already showing, skipping new request")
            return
        }
        
        hasShownCredentialDialog = true
        currentCredentialSite = site
        isCredentialDialogShowing = true
        
        showSlidingCredentialDialog(savedUsername, savedPassword, site)
    }

    private fun showSlidingCredentialDialog(username: String, password: String, site: String) {
        // Create custom dialog view
        val dialogView = layoutInflater.inflate(R.layout.credential_slide_dialog, null)
        val titleText = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageText = dialogView.findViewById<TextView>(R.id.dialog_message)
        val useButton = dialogView.findViewById<Button>(R.id.use_credentials_button)
        val noButton = dialogView.findViewById<Button>(R.id.no_thanks_button)
        
        titleText.text = "Use Saved Credentials?"
        messageText.text = "We found saved credentials for $site:\n\nUsername: $username"
        
        // Create dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Set dialog to slide in from above keyboard
        dialog.window?.setGravity(android.view.Gravity.BOTTOM)
        dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        
        useButton.setOnClickListener {
            injectCredentials(username, password)
            isCredentialDialogShowing = false
            resetCredentialInterruptSignal()
            dialog.dismiss()
        }
        
        noButton.setOnClickListener {
            // Keep flag set when user declines to prevent multiple dialogs
            isCredentialDialogShowing = false
            resetCredentialInterruptSignal()
            dialog.dismiss()
        }
        
        dialog.setOnDismissListener {
            // Reset the showing flag when dialog is dismissed
            isCredentialDialogShowing = false
            resetCredentialInterruptSignal()
        }
        
        dialog.show()
        
        // Animate slide in
        dialogView.translationY = 300f
        dialogView.animate()
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun injectCredentials(username: String, password: String) {
        val js = """
            (function() {
                console.log('Labarr: Injecting credentials - username: $username, password length: ${password.length}');
                
                // More comprehensive selectors for username fields
                var usernameSelectors = [
                    'input[type="text"]',
                    'input[type="email"]',
                    'input[name*="user"]',
                    'input[name*="login"]',
                    'input[name*="email"]',
                    'input[name*="account"]',
                    'input[name*="username"]',
                    'input[id*="user"]',
                    'input[id*="login"]',
                    'input[id*="email"]',
                    'input[id*="account"]',
                    'input[id*="username"]',
                    'input[placeholder*="user"]',
                    'input[placeholder*="email"]',
                    'input[placeholder*="login"]',
                    'input[placeholder*="account"]'
                ];
                
                // More comprehensive selectors for password fields
                var passwordSelectors = [
                    'input[type="password"]',
                    'input[name*="pass"]',
                    'input[id*="pass"]',
                    'input[placeholder*="pass"]'
                ];
                
                var usernameInputs = document.querySelectorAll(usernameSelectors.join(','));
                var passwordInputs = document.querySelectorAll(passwordSelectors.join(','));
                
                console.log('Labarr: Found username inputs:', usernameInputs.length, 'password inputs:', passwordInputs.length);
                
                // Find the best username field (prioritize by type and attributes)
                var bestUsernameField = null;
                for (var i = 0; i < usernameInputs.length; i++) {
                    var field = usernameInputs[i];
                    var name = (field.name || '').toLowerCase();
                    var id = (field.id || '').toLowerCase();
                    var placeholder = (field.placeholder || '').toLowerCase();
                    
                    // Skip if field already has a value
                    if (field.value && field.value.trim() !== '') {
                        continue;
                    }
                    
                    // Prioritize fields that look more like username fields
                    if (name.includes('username') || id.includes('username') || 
                        name.includes('user') || id.includes('user') ||
                        placeholder.includes('username') || placeholder.includes('user')) {
                        bestUsernameField = field;
                        break;
                    }
                    
                    // Fallback to first empty field
                    if (!bestUsernameField) {
                        bestUsernameField = field;
                    }
                }
                
                // Find the best password field
                var bestPasswordField = null;
                for (var i = 0; i < passwordInputs.length; i++) {
                    var field = passwordInputs[i];
                    
                    // Skip if field already has a value
                    if (field.value && field.value.trim() !== '') {
                        continue;
                    }
                    
                    bestPasswordField = field;
                    break;
                }
                
                // Fill username field
                if (bestUsernameField) {
                    console.log('Labarr: Filling username field:', bestUsernameField.name || bestUsernameField.id);
                    bestUsernameField.value = '$username';
                    
                    // Trigger events to ensure the field is properly updated (without focus)
                    bestUsernameField.dispatchEvent(new Event('input', { bubbles: true }));
                    bestUsernameField.dispatchEvent(new Event('change', { bubbles: true }));
                }
                
                // Fill password field
                if (bestPasswordField) {
                    console.log('Labarr: Filling password field:', bestPasswordField.name || bestPasswordField.id);
                    bestPasswordField.value = '$password';
                    
                    // Trigger events to ensure the field is properly updated (without focus)
                    bestPasswordField.dispatchEvent(new Event('input', { bubbles: true }));
                    bestPasswordField.dispatchEvent(new Event('change', { bubbles: true }));
                }
                
                // Blur the fields we just filled to prevent keyboard from covering login button
                if (bestUsernameField && bestUsernameField === document.activeElement) {
                    bestUsernameField.blur();
                }
                if (bestPasswordField && bestPasswordField === document.activeElement) {
                    bestPasswordField.blur();
                }
                
                // If no fields found, try again after a short delay (for dynamically loaded forms)
                if (!bestUsernameField && !bestPasswordField) {
                    console.log('Labarr: No suitable fields found, retrying in 500ms...');
                    setTimeout(function() {
                        window.AndroidInterface.injectCredentialsRetry('$username', '$password');
                    }, 500);
                }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }

    private var hasShownSaveDialog = false
    private var currentSaveSite = ""

    private fun showSubmittedCredentialsDialog(username: String, password: String, site: String, autofilled: Boolean = false) {
        // Check if credentials already exist for this site
        val existingCredentials = jsonDataManager.getCredentialsForSite(site)
        
        if (existingCredentials.isNotEmpty()) {
            // Credentials exist, check if they're different
            val (existingUsername, existingPassword) = existingCredentials.first()
            
            // Simple boolean comparison - trim whitespace and compare directly
            val credentialsMatch = username.trim() == existingUsername.trim() && 
                                 password.trim() == existingPassword.trim()
            
            if (credentialsMatch) {
                // Same credentials, don't show any dialog
                // If they were auto-filled, we might want to handle this differently
                if (autofilled) {
                    // Auto-filled credentials that match existing ones - this is expected behavior
                    // No need to show any dialog or save anything
                    return
                } else {
                    // Manually entered credentials that match existing ones - also no dialog needed
                    return
                }
            } else {
                // Different credentials, show update dialog
                // Note: This shouldn't happen with auto-filled credentials, but handle it just in case
                showCredentialUpdateDialog(existingUsername, username, password, site)
                return
            }
        }
        
        // No existing credentials, show save dialog
        // Prevent multiple save dialogs for the same site
        if (hasShownSaveDialog && currentSaveSite == site) {
            return
        }
        
        hasShownSaveDialog = true
        currentSaveSite = site
        
        // If credentials were auto-filled but no existing credentials exist, 
        // this might indicate a bug in the auto-fill system
        if (autofilled) {
            // Auto-filled credentials but no existing ones found - this shouldn't happen
            // Still show the save dialog as a fallback
        }
        
        showSlidingSaveDialog(username, password, site)
    }
    
    private fun showCredentialUpdateDialog(oldUsername: String, newUsername: String, newPassword: String, site: String) {
        // Prevent multiple update dialogs for the same site
        if (hasShownSaveDialog && currentSaveSite == site) {
            return
        }
        
        hasShownSaveDialog = true
        currentSaveSite = site
        
        // Create custom dialog view
        val dialogView = layoutInflater.inflate(R.layout.credential_save_dialog, null)
        val titleText = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageText = dialogView.findViewById<TextView>(R.id.dialog_message)
        val saveButton = dialogView.findViewById<Button>(R.id.save_credentials_button)
        val noButton = dialogView.findViewById<Button>(R.id.dont_save_button)
        
        titleText.text = "Update Saved Credentials?"
        messageText.text = "We detected different credentials for $site:\n\nOld Username: $oldUsername\nNew Username: $newUsername\n\nWould you like to update the saved credentials?"
        
        // Create dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Set dialog to slide in from above keyboard
        dialog.window?.setGravity(android.view.Gravity.BOTTOM)
        dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        
        saveButton.setOnClickListener {
            // Update credentials for this site (removes old ones and adds new ones)
            jsonDataManager.updateCredentialsForSite(site, newUsername, newPassword, "")
            Toast.makeText(this, "Credentials updated for $site", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        noButton.setOnClickListener {
            // Reset flag when user declines
            hasShownSaveDialog = false
            currentSaveSite = ""
            dialog.dismiss()
        }
        
        dialog.setOnDismissListener {
            // Reset flag when dialog is dismissed
            hasShownSaveDialog = false
            currentSaveSite = ""
        }
        
        dialog.show()
        
        // Animate slide in
        dialogView.translationY = 300f
        dialogView.animate()
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun showSlidingSaveDialog(username: String, password: String, site: String) {
        // Create custom dialog view
        val dialogView = layoutInflater.inflate(R.layout.credential_save_dialog, null)
        val titleText = dialogView.findViewById<TextView>(R.id.dialog_title)
        val messageText = dialogView.findViewById<TextView>(R.id.dialog_message)
        val saveButton = dialogView.findViewById<Button>(R.id.save_credentials_button)
        val noButton = dialogView.findViewById<Button>(R.id.dont_save_button)
        
        titleText.text = "Save Login Credentials?"
        messageText.text = "We detected you just logged in to $site.\n\nUsername: $username\n\nWould you like to save these credentials?"
        
        // Create dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Set dialog to slide in from above keyboard
        dialog.window?.setGravity(android.view.Gravity.BOTTOM)
        dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        
        saveButton.setOnClickListener {
            saveCredentialsForSite(site, username, password, "")
            Toast.makeText(this, "Credentials saved for $site", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        noButton.setOnClickListener {
            // Reset flag when user declines so they can be asked again on next login
            hasShownSaveDialog = false
            currentSaveSite = ""
            dialog.dismiss()
        }
        
        dialog.setOnDismissListener {
            // Reset flag when dialog is dismissed
            hasShownSaveDialog = false
            currentSaveSite = ""
        }
        
        dialog.show()
        
        // Animate slide in
        dialogView.translationY = 300f
        dialogView.animate()
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun getCurrentDomain(): String {
        val url = webView?.url ?: return ""
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: ""
            val port = uri.port
            if (port != -1) {
                "$host:$port"
            } else {
                host
            }
        } catch (e: Exception) {
            ""
        }
    }



    private fun resetLoginPromptState() {
        val script = """
            (function() {
                if (typeof hasPrompted !== 'undefined') {
                    hasPrompted = false;
                }
                if (typeof submittedCredentials !== 'undefined') {
                    submittedCredentials = null;
                }
                if (typeof capturedCredentials !== 'undefined') {
                    capturedCredentials = null;
                }
                if (typeof credentialsAutofilled !== 'undefined') {
                    credentialsAutofilled = false;
                }
                if (typeof hasShownSavedCredentialsDialog !== 'undefined') {
                    hasShownSavedCredentialsDialog = false;
                }
                // Reset the global flag to allow re-injection on new pages
                if (typeof window.labarrCredentialDetectionLoaded !== 'undefined') {
                    window.labarrCredentialDetectionLoaded = false;
                }
                // Reset monitoring flag for new pages
                if (typeof isMonitoring !== 'undefined') {
                    isMonitoring = false;
                }
                // Reset interrupt signal for new pages
                if (typeof credentialHandlingInProgress !== 'undefined') {
                    credentialHandlingInProgress = false;
                }
            })();
        """.trimIndent()
        
        webView?.evaluateJavascript(script, null)
        
        // Reset save dialog flags for new page
        hasShownSaveDialog = false
        currentSaveSite = ""
        
        // Reset credential dialog flags for page refresh (allow re-prompting on same site)
        hasShownCredentialDialog = false
        currentCredentialSite = ""
        lastCredentialDialogSite = getCurrentDomain()
        isCredentialDialogShowing = false
        
        // Reset injection flag for new pages
        isCredentialDetectionInjected = false
    }

    // Removed cursor color styling injection for performance
    
    private fun resetCredentialInterruptSignal() {
        val script = """
            (function() {
                if (typeof credentialHandlingInProgress !== 'undefined') {
                    credentialHandlingInProgress = false;
                }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(script, null)
    }
    
    private fun injectFilebrowserDownloadInterceptor(webView: WebView?) {
        val js = """
            (function() {
                // Intercept filebrowser download links - only for actual download links
                function interceptFilebrowserDownloads() {
                    // Only intercept links that are explicitly for downloads
                    var downloadLinks = document.querySelectorAll('a[download], button[onclick*="download"], a[href*="/api/raw/"]');
                    
                    downloadLinks.forEach(function(link) {
                                            if (!link.hasAttribute('data-labarr-intercepted')) {
                        link.setAttribute('data-labarr-intercepted', 'true');
                            
                            link.addEventListener('click', function(e) {
                                // Only intercept if this is actually a download link
                                var url = link.href || link.getAttribute('data-url') || link.getAttribute('onclick');
                                var isDownload = link.hasAttribute('download') || 
                                               (url && url.includes('/api/raw/')) ||
                                               (link.onclick && link.onclick.toString().includes('download'));
                                
                                if (!isDownload) {
                                    // Let normal navigation happen
                                    return;
                                }
                                
                                e.preventDefault();
                                e.stopPropagation();
                                
                                var filename = link.getAttribute('download') || link.textContent.trim() || 'file';
                                
                                // If it's an onclick handler, try to extract URL
                                if (url && url.includes('onclick')) {
                                    var match = url.match(/['"]([^'"]*\/api\/raw\/[^'"]*)['"]/);
                                    if (match) {
                                        url = match[1];
                                    }
                                }
                                
                                // Better filename extraction
                                if (url) {
                                    // Try to extract filename from URL path
                                    var urlParts = url.split('/');
                                    var lastPart = urlParts[urlParts.length - 1];
                                    
                                    // If the last part looks like a filename (has extension or no special chars)
                                    if (lastPart && (lastPart.includes('.') || !lastPart.includes('?'))) {
                                        // Remove query parameters
                                        var cleanFilename = lastPart.split('?')[0];
                                        if (cleanFilename && cleanFilename !== 'raw' && cleanFilename !== 'download') {
                                            // Safely decode URI component
                                            try {
                                                var decoded = decodeURIComponent(cleanFilename);
                                                filename = decoded;
                                            } catch (e) {
                                                // If decodeURIComponent fails, use the original
                                                filename = cleanFilename;
                                            }
                                        }
                                    }
                                    
                                    // If we still don't have a good filename, try to get it from the page context
                                    if (filename === 'file' || filename === 'download' || filename === 'raw') {
                                        // Look for the filename in the current page context
                                        var fileElements = document.querySelectorAll('[data-name], [data-filename], .filename, .file-name');
                                        for (var i = 0; i < fileElements.length; i++) {
                                            var element = fileElements[i];
                                            var elementFilename = element.getAttribute('data-name') || 
                                                                 element.getAttribute('data-filename') || 
                                                                 element.textContent.trim();
                                            if (elementFilename && elementFilename.includes('.')) {
                                                filename = elementFilename;
                                                break;
                                            }
                                        }
                                    }
                                }
                                
                                // Clean up filename
                                filename = filename.replace(/[<>:"/\\|?*]/g, '_');
                                
                                // Remove any leading/trailing whitespace
                                filename = filename.trim();
                                
                                if (url && url !== 'javascript:void(0)') {
                                    console.log('Labarr: Intercepted filebrowser download:', url, filename);
                                    AndroidInterface.downloadFile(url, filename);
                                }
                            });
                        }
                    });
                }
                
                // Run immediately
                interceptFilebrowserDownloads();
                
                // Only run when DOM changes if we're on a filebrowser page (debounced)
                if (window.location.href.includes('filebrowser') || window.location.href.includes('/files/')) {
                    var filebrowserTimeout = null;
                    var observer = new MutationObserver(function(mutations) {
                        if (filebrowserTimeout) {
                            clearTimeout(filebrowserTimeout);
                        }
                        filebrowserTimeout = setTimeout(function() {
                            interceptFilebrowserDownloads();
                        }, 500);
                    });
                    
                    observer.observe(document.body, {
                        childList: true,
                        subtree: true
                    });
                }
                
                // Also intercept any JavaScript download functions
                if (window.downloadFile) {
                    var originalDownload = window.downloadFile;
                    window.downloadFile = function(url, filename) {
                        console.log('Labarr: Intercepted JS download:', url, filename);
                        AndroidInterface.downloadFile(url, filename);
                    };
                }
            })();
        """.trimIndent()
        webView?.evaluateJavascript(js, null)
    }
    

    


    private fun showPinVerificationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.pin_verify_dialog, null)
        val pinDigits = listOf(
            dialogView.findViewById<EditText>(R.id.pin_digit_1),
            dialogView.findViewById<EditText>(R.id.pin_digit_2),
            dialogView.findViewById<EditText>(R.id.pin_digit_3),
            dialogView.findViewById<EditText>(R.id.pin_digit_4),
            dialogView.findViewById<EditText>(R.id.pin_digit_5),
            dialogView.findViewById<EditText>(R.id.pin_digit_6)
        )
        val errorMessage = dialogView.findViewById<TextView>(R.id.error_message)
        val pinBacking = CharArray(6) { ' ' }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.setCanceledOnTouchOutside(false)

        fun getPin(): String = pinBacking.concatToString().replace(" ", "")
        fun showError() {
            errorMessage.visibility = View.VISIBLE
            pinDigits.forEach { it.setBackgroundResource(R.drawable.pin_box_error) }
        }
        fun clearError() {
            errorMessage.visibility = View.GONE
            pinDigits.forEach { it.setBackgroundResource(R.drawable.pin_box_empty) }
        }
        fun verifyPin(pin: String) {
            if (jsonDataManager.verifyPin(pin)) {
                dialog.dismiss()
                isPinVerified = true
                setPinVerifiedInSession()
                createWebView()
                initializeAppComponents()
                loadAppropriateUrlAfterPinVerification()
            } else {
                showError()
                pinDigits.forEach { it.text.clear() }
                pinBacking.fill(' ')
                pinDigits[0].requestFocus()
            }
        }
        // Masking logic for PIN verification
        pinDigits.forEachIndexed { i, editText ->
            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DEL) {
                        if (editText.text.isEmpty() && i > 0) {
                            pinBacking[i - 1] = ' '
                            pinDigits[i - 1].setText("")
                            pinDigits[i - 1].requestFocus()
                            pinDigits[i - 1].setSelection(pinDigits[i - 1].text.length)
                        } else if (editText.text.isNotEmpty()) {
                            pinBacking[i] = ' '
                            editText.setText("")
                        }
                        return@setOnKeyListener true
                    }
                }
                false
            }
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    editText.removeTextChangedListener(this)
                    clearError()
                    val input = s?.toString() ?: ""
                    if (input.isNotEmpty()) {
                        pinBacking[i] = input[0]
                        editText.setText("•")
                        editText.setSelection(1)
                        if (i < 5) {
                            pinDigits[i + 1].requestFocus()
                        }
                    }
                    if (pinBacking.all { it != ' ' }) {
                        verifyPin(getPin())
                    }
                    editText.addTextChangedListener(this)
                }
            })
        }

        dialog.setOnShowListener {
            pinDigits[0].requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(pinDigits[0], android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
    }
    
    private fun initializeAppComponents() {
        // Always keep WebView caching enabled for better performance
        // Login persistence is controlled separately by loginPersistence setting
        
        // Handle login persistence on app start
        handleLoginPersistence()
        
        // Apply auto-rotate setting
        applyAutoRotateSetting()
        
        // Hide system UI for fullscreen
        hideSystemUI()
        
        // Setup slide menu
        setupSlideMenu()
        
        // Set initial desktop toggle state
        updateDesktopToggleAppearance()

        // Setup back button
        setupMovableBackButton()
        
        // Load back button position and state
        loadBackButtonState()
        
        // Apply back button visibility setting
        applyBackButtonVisibility()

        webView?.let { webView ->
            // Setup WebView settings first
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                databaseEnabled = true
                setGeolocationEnabled(true)
                
                // Always keep caching enabled for better performance
                cacheMode = WebSettings.LOAD_DEFAULT
                domStorageEnabled = true
                databaseEnabled = true
                
                // Additional settings for proper link handling
                loadsImagesAutomatically = true
                blockNetworkImage = false
                blockNetworkLoads = false
                mediaPlaybackRequiresUserGesture = false
                setSupportMultipleWindows(false) // Changed to false for better link handling
                javaScriptCanOpenWindowsAutomatically = false // Changed to false for better link handling
            }
            
            // Apply desktop/mobile view settings after basic settings
            setupWebViewForDevice()
            
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
                
                // Handle deprecated method for older Android versions
                @Suppress("DEPRECATION")
                @Deprecated("Use shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) instead")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return false
                }
                
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    // Apply site-specific desktop view settings
                    setupWebViewForDevice()
                    
                    // Reset prompt state for new pages
                    resetLoginPromptState()
                    
                    // CRITICAL: Detect login forms FIRST (most important)
                    detectAndPromptForCredentials()
                    
                    // Only inject filebrowser interceptor for filebrowser URLs
                    val currentUrl = url ?: ""
                    if (currentUrl.contains("filebrowser") || currentUrl.contains("/files/")) {
                        injectFilebrowserDownloadInterceptor(view)
                    }
                }
                
                // Handle SSL certificate errors for self-signed certificates
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    Log.w("Labarr", "SSL Error: ${error?.toString()}")
                    
                    // For homelab environments, we often use self-signed certificates
                    // Allow the connection to proceed for common SSL errors
                    when (error?.primaryError) {
                        SslError.SSL_DATE_INVALID,
                        SslError.SSL_EXPIRED,
                        SslError.SSL_IDMISMATCH,
                        SslError.SSL_UNTRUSTED,
                        SslError.SSL_NOTYETVALID -> {
                            Log.d("Labarr", "Allowing SSL error for homelab environment: ${error.primaryError}")
                            handler?.proceed()
                        }
                        else -> {
                            Log.e("Labarr", "SSL Error not allowed: ${error?.primaryError}")
                            handler?.cancel()
                        }
                    }
                }
                
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    Log.e("Labarr", "WebView error: ${error?.description} for URL: ${request?.url}")
                    
                    // Only show error for main frame (actual page load failures)
                    // Ignore errors for sub-resources (images, CSS, etc.) as they don't prevent page loading
                    if (request?.isForMainFrame == true) {
                        val errorDescription = error?.description?.toString() ?: ""
                        when {
                            errorDescription.contains("ERR_BLOCKED_BY_ORB", ignoreCase = true) -> {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Download blocked by security. Try opening in external browser.", Toast.LENGTH_LONG).show()
                                }
                            }
                            errorDescription.contains("ERR_CONNECTION_REFUSED", ignoreCase = true) -> {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Connection refused. Check if the server is running.", Toast.LENGTH_LONG).show()
                                }
                            }
                            errorDescription.contains("ERR_NAME_NOT_RESOLVED", ignoreCase = true) -> {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Cannot resolve hostname. Check your network connection.", Toast.LENGTH_LONG).show()
                                }
                            }
                            // Only show generic error for actual page load failures
                            else -> {
                                Log.e("Labarr", "Main frame load error: $errorDescription")
                                // Don't show toast for generic errors - let the page handle it
                            }
                        }
                    } else {
                        // Log sub-resource errors but don't show to user
                        Log.d("Labarr", "Sub-resource error (ignored): ${error?.description} for ${request?.url}")
                    }
                }
                
                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    Log.e("Labarr", "HTTP error: ${errorResponse?.statusCode} for URL: ${request?.url}")
                }
            }
            webView.webChromeClient = WebChromeClient()
            webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                try {
                    Log.d("Labarr", "Download triggered: $url")
                    
                    // Simple download implementation to avoid crashes
                    val request = DownloadManager.Request(Uri.parse(url))
                    request.setMimeType(mimeType)
                    
                    // Add cookies if available
                    val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                    if (cookies != null) {
                        request.addRequestHeader("cookie", cookies)
                    }
                    
                    // Add user agent
                    request.addRequestHeader("User-Agent", userAgent ?: webView.settings.userAgentString)
                    
                    // Generate filename
                    val fileName = extractFileName(contentDisposition, url, mimeType)
                    
                    
                    
                    request.setDescription("Downloading file...")
                    request.setTitle(fileName)
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    
                    val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    val downloadId = dm.enqueue(request)
                    
                    Log.d("Labarr", "Download started: $fileName (ID: $downloadId)")
                    Toast.makeText(this@MainActivity, "Download started: $fileName", Toast.LENGTH_SHORT).show()
                    
                } catch (e: Exception) {
                    Log.e("Labarr", "Download failed: ${e.message}")
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    
                    // Fallback: try to open in external browser
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e2: Exception) {
                        Toast.makeText(this@MainActivity, "Cannot download or open file", Toast.LENGTH_SHORT).show()
                    }
                }
            })

            // Add JavaScript interface for login detection
            webView.addJavascriptInterface(WebAppInterface(), "AndroidInterface")
        }
        
        // Menu colors are now static white
    }
    
    private fun loadAppropriateUrlAfterPinVerification() {
        // Check if we have any saved URLs
        val settings = jsonDataManager.loadSettings()
        val savedIp = settings.homelabIp
        val savedFallbackUrl = settings.fallbackUrl
        
        if (savedIp.isEmpty() && savedFallbackUrl.isEmpty()) {
            // No saved URLs, show dark gray screen with settings button
            showNoUrlsScreen()
        } else {
            // Load appropriate URL based on network state
            loadAppropriateUrl()
        }
    }

    private fun handleLoginPersistence() {
        val settings = jsonDataManager.loadSettings()
        if (!settings.loginPersistence) {
            // Clear login-related data but keep cache for performance
            webView?.clearFormData()
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            Log.d("Labarr", "Login persistence disabled - cleared login data")
        } else {
            Log.d("Labarr", "Login persistence enabled - keeping login data")
        }
    }

    private fun clearWebViewCache() {
        webView?.let { webView ->
            val currentUrl = webView.url

            
            // Clear all WebView cache and data
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
            
            // Clear cookies more thoroughly
            val cookieManager = android.webkit.CookieManager.getInstance()
            
            // Remove all cookies
            cookieManager.removeAllCookies(null)
            
            // Also try to remove cookies for specific domains if we have a current URL
            if (currentUrl != null && currentUrl != "about:blank") {
                try {
                    val uri = Uri.parse(currentUrl)
                    val domain = uri.host
                    if (domain != null) {
                        // Remove cookies for the main domain
                        cookieManager.removeAllCookies(null)
                        
                        // Note: Android's CookieManager doesn't have a direct method to clear by domain
                        // but removeAllCookies should handle this
                    }
                } catch (e: Exception) {
                    Log.e("Labarr", "Error parsing URL for cookie clearing: ${e.message}")
                }
            }
            
            // Clear local storage and session storage via JavaScript
            webView.evaluateJavascript("""
                (function() {
                    try {
                        // Clear localStorage
                        localStorage.clear();
                        console.log('localStorage cleared');
                        
                        // Clear sessionStorage
                        sessionStorage.clear();
                        console.log('sessionStorage cleared');
                        
                        // Clear IndexedDB (if supported)
                        if (window.indexedDB) {
                            indexedDB.databases().then(function(databases) {
                                databases.forEach(function(database) {
                                    indexedDB.deleteDatabase(database.name);
                                });
                            }).catch(function(error) {
                                console.log('IndexedDB clear error:', error);
                            });
                        }
                        
                        return 'Storage cleared successfully';
                    } catch (error) {
                        return 'Storage clear error: ' + error.message;
                    }
                })();
                        """.trimIndent()) { _ ->
 
            }
            
            // Show confirmation to user
            Toast.makeText(this, "Cache and storage cleared successfully", Toast.LENGTH_SHORT).show()
            
            // Reload current page to reflect cache clearing
            if (currentUrl != null && currentUrl != "about:blank") {

                webView.reload()
            }
            

        }
    }

    private fun restartApp() {
        // Clear session verification
        val prefs = getSharedPreferences("LabarrPrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("pin_verified_session").apply()
        
        // Restart the app
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
    

}
