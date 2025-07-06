package com.labarr

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class SettingsActivity : AppCompatActivity() {
    private lateinit var jsonDataManager: JsonDataManager
    
    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1002
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        jsonDataManager = JsonDataManager(this)
        val ipEditText = findViewById<EditText>(R.id.ip_edit_text)
        val fallbackUrlEditText = findViewById<EditText>(R.id.fallback_url_edit_text)
        val trustedWifiEditText = findViewById<EditText>(R.id.trusted_wifi_edit_text)
        val wifiSsidEnabledCheckBox = findViewById<CheckBox>(R.id.wifi_ssid_enabled_checkbox)
        val wifiSsidButtonsContainer = findViewById<LinearLayout>(R.id.wifi_ssid_buttons_container)
        val vibrationEnabledCheckBox = findViewById<CheckBox>(R.id.vibration_enabled_checkbox)
        val autoRotateEnabledCheckBox = findViewById<CheckBox>(R.id.auto_rotate_enabled_checkbox)
        val showBackButtonCheckBox = findViewById<CheckBox>(R.id.show_back_button_checkbox)
        val autoFillCredentialsCheckBox = findViewById<CheckBox>(R.id.auto_fill_credentials_checkbox)
        val enableAutomaticLoginSystemCheckBox = findViewById<CheckBox>(R.id.enable_automatic_login_system_checkbox)
        
        // Load current settings
        val settings = jsonDataManager.loadSettings()
        val currentIp = settings.homelabIp
        val currentFallbackUrl = settings.fallbackUrl
        val currentTrustedWifi = settings.trustedWifi
        val currentWifiSsidEnabled = settings.wifiSsidEnabled
        val currentVibrationEnabled = settings.vibrationEnabled
        val currentAutoRotateEnabled = settings.autoRotateEnabled
        val currentShowBackButton = settings.showBackButton
        val currentAutoFillCredentials = settings.autoFillCredentials
        val currentEnableAutomaticLoginSystem = settings.enableAutomaticLoginSystem
        
        ipEditText.setText(currentIp)
        fallbackUrlEditText.setText(currentFallbackUrl)
        trustedWifiEditText.setText(currentTrustedWifi)
        wifiSsidEnabledCheckBox.isChecked = currentWifiSsidEnabled
        vibrationEnabledCheckBox.isChecked = currentVibrationEnabled
        autoRotateEnabledCheckBox.isChecked = currentAutoRotateEnabled
        showBackButtonCheckBox.isChecked = currentShowBackButton
        autoFillCredentialsCheckBox.isChecked = currentAutoFillCredentials
        enableAutomaticLoginSystemCheckBox.isChecked = currentEnableAutomaticLoginSystem
        
        // Set initial visibility and enabled state of trusted WiFi input and buttons based on checkbox
        trustedWifiEditText.visibility = if (currentWifiSsidEnabled) View.VISIBLE else View.GONE
        trustedWifiEditText.isEnabled = currentWifiSsidEnabled
        wifiSsidButtonsContainer.visibility = if (currentWifiSsidEnabled) View.VISIBLE else View.GONE
        
        // Set initial visibility and enabled state of auto-fill credentials checkbox based on auto login system
        autoFillCredentialsCheckBox.visibility = if (currentEnableAutomaticLoginSystem) View.VISIBLE else View.GONE
        autoFillCredentialsCheckBox.isEnabled = currentEnableAutomaticLoginSystem
        
        // Set up checkbox listener to show/hide and enable/disable trusted WiFi input and buttons
        wifiSsidEnabledCheckBox.setOnCheckedChangeListener { _, isChecked ->
            trustedWifiEditText.visibility = if (isChecked) View.VISIBLE else View.GONE
            trustedWifiEditText.isEnabled = isChecked
            wifiSsidButtonsContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            // Save the checkbox state immediately
            val currentSettings = jsonDataManager.loadSettings()
            val updatedSettings = currentSettings.copy(wifiSsidEnabled = isChecked)
            jsonDataManager.saveSettings(updatedSettings)
        }
        
        // Set up vibration checkbox listener
        vibrationEnabledCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val currentSettings = jsonDataManager.loadSettings()
            val updatedSettings = currentSettings.copy(vibrationEnabled = isChecked)
            jsonDataManager.saveSettings(updatedSettings)
        }
        
        // Set up auto-rotate checkbox listener
        autoRotateEnabledCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val currentSettings = jsonDataManager.loadSettings()
            val updatedSettings = currentSettings.copy(autoRotateEnabled = isChecked)
            jsonDataManager.saveSettings(updatedSettings)
        }
        
        // Set up show back button checkbox listener
        showBackButtonCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val currentSettings = jsonDataManager.loadSettings()
            val updatedSettings = currentSettings.copy(showBackButton = isChecked)
            jsonDataManager.saveSettings(updatedSettings)
        }

        // Set up auto-fill credentials checkbox listener
        autoFillCredentialsCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val currentSettings = jsonDataManager.loadSettings()
            val updatedSettings = currentSettings.copy(autoFillCredentials = isChecked)
            jsonDataManager.saveSettings(updatedSettings)
        }

        // Set up enable automatic login system checkbox listener
        enableAutomaticLoginSystemCheckBox.setOnCheckedChangeListener { _, isChecked ->
            val currentSettings = jsonDataManager.loadSettings()
            val updatedSettings = currentSettings.copy(enableAutomaticLoginSystem = isChecked)
            jsonDataManager.saveSettings(updatedSettings)
            
            // Show/hide and enable/disable auto-fill credentials checkbox
            autoFillCredentialsCheckBox.visibility = if (isChecked) View.VISIBLE else View.GONE
            autoFillCredentialsCheckBox.isEnabled = isChecked
            
            // If auto login system is disabled, also disable auto-fill to prevent orphaned settings
            if (!isChecked) {
                autoFillCredentialsCheckBox.isChecked = false
                val settingsWithDisabledAutoFill = updatedSettings.copy(autoFillCredentials = false)
                jsonDataManager.saveSettings(settingsWithDisabledAutoFill)
            }
        }
        
        // Add additional action buttons
        val manageCredentialsButton = findViewById<Button>(R.id.manage_credentials_button)
        val clearCacheButton = findViewById<Button>(R.id.clear_cache_button)
        val wipeDataButton = findViewById<Button>(R.id.wipe_data_button)
        val backupRestoreButton = findViewById<Button>(R.id.backup_restore_button)
        val saveIpButton = findViewById<Button>(R.id.save_ip_button)
        val deleteIpButton = findViewById<Button>(R.id.delete_ip_button)
        val saveWifiSsidButton = findViewById<Button>(R.id.save_wifi_ssid_button)
        val deleteWifiSsidButton = findViewById<Button>(R.id.delete_wifi_ssid_button)
        val saveFallbackUrlButton = findViewById<Button>(R.id.save_fallback_url_button)
        val deleteFallbackUrlButton = findViewById<Button>(R.id.delete_fallback_url_button)
        
        // Set all buttons to white
        setButtonsToWhite()
        
        manageCredentialsButton?.setOnClickListener {
            startActivity(Intent(this, CredentialsActivity::class.java))
        }
        
        clearCacheButton?.setOnClickListener {
            showClearCacheAndCookiesConfirmation()
        }
        
        wipeDataButton?.setOnClickListener {
            showWipeDataConfirmation()
        }
        
        backupRestoreButton?.setOnClickListener {
            showBackupRestoreDialog()
        }
        
        saveIpButton?.setOnClickListener {
            val newIp = ipEditText.text.toString().trim()
            if (newIp.isNotEmpty()) {
                val currentSettings = jsonDataManager.loadSettings()
                val updatedSettings = currentSettings.copy(homelabIp = newIp)
                jsonDataManager.saveSettings(updatedSettings)
                Toast.makeText(this, "IP address saved", Toast.LENGTH_SHORT).show()
                
                // Send signal to main activity that IP was changed (but don't return)
                val intent = Intent()
                intent.putExtra("ip_changed", true)
                setResult(RESULT_OK, intent)
            }
        }
        
        deleteIpButton?.setOnClickListener {
            val currentSettings = jsonDataManager.loadSettings()
            val updatedSettings = currentSettings.copy(homelabIp = "")
            jsonDataManager.saveSettings(updatedSettings)
            ipEditText.setText("")
            Toast.makeText(this, "IP address deleted", Toast.LENGTH_SHORT).show()
        }
        
        saveWifiSsidButton?.setOnClickListener {
            val newWifiSsid = trustedWifiEditText.text.toString().trim()
            if (newWifiSsid.isNotEmpty()) {
                val currentSettings = jsonDataManager.loadSettings()
                val updatedSettings = currentSettings.copy(trustedWifi = newWifiSsid)
                jsonDataManager.saveSettings(updatedSettings)
                Toast.makeText(this, "WiFi SSID saved", Toast.LENGTH_SHORT).show()
            }
        }
        
        deleteWifiSsidButton?.setOnClickListener {
            val currentSettings = jsonDataManager.loadSettings()
            val updatedSettings = currentSettings.copy(trustedWifi = "")
            jsonDataManager.saveSettings(updatedSettings)
            trustedWifiEditText.setText("")
            Toast.makeText(this, "WiFi SSID deleted", Toast.LENGTH_SHORT).show()
        }
        
        saveFallbackUrlButton?.setOnClickListener {
            val newFallbackUrl = fallbackUrlEditText.text.toString().trim()
            if (newFallbackUrl.isNotEmpty()) {
                val currentSettings = jsonDataManager.loadSettings()
                val updatedSettings = currentSettings.copy(fallbackUrl = newFallbackUrl)
                jsonDataManager.saveSettings(updatedSettings)
                Toast.makeText(this, "Fallback URL saved", Toast.LENGTH_SHORT).show()
                
                // Send signal to main activity that URL was added (but don't return)
                val intent = Intent()
                intent.putExtra("url_added", true)
                setResult(RESULT_OK, intent)
            }
        }
        
        // Add listener to save trusted WiFi when text changes
        trustedWifiEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val newTrustedWifi = s?.toString()?.trim() ?: ""
                val currentSettings = jsonDataManager.loadSettings()
                val updatedSettings = currentSettings.copy(trustedWifi = newTrustedWifi)
                jsonDataManager.saveSettings(updatedSettings)
            }
        })
        
        deleteFallbackUrlButton?.setOnClickListener {
            val currentSettings = jsonDataManager.loadSettings()
            val updatedSettings = currentSettings.copy(fallbackUrl = "")
            jsonDataManager.saveSettings(updatedSettings)
            fallbackUrlEditText.setText("")
            Toast.makeText(this, "Fallback URL deleted", Toast.LENGTH_SHORT).show()
        }

    }

    private fun showClearCacheAndCookiesConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear Cache and Cookies")
            .setMessage("This will clear all cached web pages, cookies, and data. The app will need to reload pages on next visit. Continue?")
            .setPositiveButton("Clear") { _, _ ->
                // Send signal to main activity to clear cache and cookies
                val intent = Intent()
                intent.putExtra("clear_cache", true)
                setResult(RESULT_OK, intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWipeDataConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Wipe All Data")
            .setMessage("This will delete ALL Labarr data including:\n\n" +
                       "• Saved URLs and settings\n" +
                       "• All saved login credentials\n" +
                       "• PIN protection settings\n" +
                       "• All backup files in Labarr/Backups/\n" +
                       "• Site-specific desktop view settings\n\n" +
                       "This action cannot be undone. Continue?")
            .setPositiveButton("Wipe All") { _, _ ->
                if (jsonDataManager.isPinSet()) {
                    showWipeDataPinVerification()
                } else {
                    performWipeData()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showWipeDataPinVerification() {
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
                performWipeData()
            } else {
                showError()
                pinDigits.forEach { it.text.clear() }
                pinBacking.fill(' ')
                pinDigits[0].requestFocus()
            }
        }
        
        pinDigits.forEach { suppressAutofillAndSuggestions(it) }
        dialog.setOnShowListener {
            pinDigits[0].requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(pinDigits[0], InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        setupPinBoxes(pinDigits, pinBacking) {
            val pin = pinBacking.concatToString().replace(" ", "")
            if (pin.length == 6) {
                verifyPin(pin)
            }
        }
    }
    
    private fun performWipeData() {
        // Clear all data first
        jsonDataManager.clearAllData()
        
        // Clear app cache directory
        try {
            cacheDir.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Clear external cache directory
        try {
            externalCacheDir?.deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Clear any temporary files
        try {
            val tempDir = File(cacheDir, "temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Then save default settings
        jsonDataManager.saveSettings(JsonDataManager.Settings())
        jsonDataManager.saveCredentials(emptyList())
        
        // Send signal to main activity to reload
        val intent = Intent()
        intent.putExtra("data_wiped", true)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun showBackupRestoreDialog() {
        val dialogView = layoutInflater.inflate(R.layout.backup_restore_dialog, null)
        
        val createBackupButton = dialogView.findViewById<Button>(R.id.create_backup_button)
        val restoreBackupButton = dialogView.findViewById<Button>(R.id.restore_backup_button)
        val manageBackupsButton = dialogView.findViewById<Button>(R.id.manage_backups_button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        
        createBackupButton.setOnClickListener {
            dialog.dismiss()
            if (checkStoragePermission()) {
                createBackup()
            } else {
                requestStoragePermission()
            }
        }
        
        restoreBackupButton.setOnClickListener {
            dialog.dismiss()
            if (checkStoragePermission()) {
                showRestoreDialog()
            } else {
                requestStoragePermission()
            }
        }
        
        manageBackupsButton.setOnClickListener {
            dialog.dismiss()
            if (checkStoragePermission()) {
                showManageBackupsDialog()
            } else {
                requestStoragePermission()
            }
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - check for MANAGE_EXTERNAL_STORAGE
            Environment.isExternalStorageManager()
        } else {
            // Android 10 and below - check for WRITE_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - show dialog to guide user to settings
            val dialog = AlertDialog.Builder(this)
                .setTitle("Storage Permission Required")
                .setMessage("To create and manage backups, please grant storage permission in Settings.\n\nGo to Settings > Apps > Labarr > Permissions > Files and media > Allow management of all files")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .create()
            
            dialog.window?.setBackgroundDrawableResource(android.R.color.black)
            dialog.show()
        } else {
            // Android 10 and below - request permission directly
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun createBackup() {
        val backupFile = jsonDataManager.createBackup()
        if (backupFile != null) {
            val encryptionStatus = if (jsonDataManager.isPinSet()) "encrypted" else "unencrypted"
            val message = "Backup created successfully!\n\n" +
                         "File: ${backupFile.name}\n" +
                         "Location: Labarr/Backups/\n" +
                         "Protection: $encryptionStatus"
            
            val dialog = AlertDialog.Builder(this)
                .setTitle("Backup Created")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .create()
            
            dialog.window?.setBackgroundDrawableResource(android.R.color.black)
            dialog.show()
        } else {
            Toast.makeText(this, "Failed to create backup", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showRestoreDialog() {
        val backupFiles = jsonDataManager.getBackupFiles()
        if (backupFiles.isEmpty()) {
            Toast.makeText(this, "No backup files found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.backup_selection_dialog, null)
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val buttonsContainer = dialogView.findViewById<LinearLayout>(R.id.backup_buttons_container)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        
        titleTextView.text = "Select Backup to Restore"
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        
        // Create buttons for each backup file
        backupFiles.forEach { backupFile ->
            val button = Button(this)
            button.text = backupFile.name
            button.background = getDrawable(R.drawable.rounded_button_background)
            button.setTextColor(android.graphics.Color.BLACK)
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
            
            button.setOnClickListener {
                dialog.dismiss()
                showRestoreConfirmation(backupFile)
            }
            
            buttonsContainer.addView(button)
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showRestoreConfirmation(backupFile: File) {
        val encryptionStatus = if (jsonDataManager.isPinSet()) "encrypted" else "unencrypted"
        val message = "This will replace all current settings and credentials with the backup data.\n\n" +
                     "File: ${backupFile.name}\n" +
                     "Current PIN Protection: $encryptionStatus\n\n" +
                     "Continue with restore?"
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Restore Backup")
            .setMessage(message)
            .setPositiveButton("Restore") { _, _ ->
                if (jsonDataManager.restoreBackup(backupFile)) {
                    Toast.makeText(this, "Backup restored successfully", Toast.LENGTH_SHORT).show()
                    
                    // Send signal to main activity to restart
                    val intent = Intent()
                    intent.putExtra("restore_completed", true)
                    setResult(RESULT_OK, intent)
                    finish()
                } else {
                    Toast.makeText(this, "Failed to restore backup", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        dialog.show()
    }
    
    private fun showManageBackupsDialog() {
        val backupFiles = jsonDataManager.getBackupFiles()
        if (backupFiles.isEmpty()) {
            Toast.makeText(this, "No backup files found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.backup_selection_dialog, null)
        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val buttonsContainer = dialogView.findViewById<LinearLayout>(R.id.backup_buttons_container)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        
        titleTextView.text = "Manage Backups"
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        
        // Create buttons for each backup file
        backupFiles.forEach { backupFile ->
            val button = Button(this)
            button.text = backupFile.name
            button.background = getDrawable(R.drawable.rounded_button_background)
            button.setTextColor(android.graphics.Color.BLACK)
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8
            }
            
            button.setOnClickListener {
                dialog.dismiss()
                showBackupOptionsDialog(backupFile)
            }
            
            buttonsContainer.addView(button)
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showBackupOptionsDialog(backupFile: File) {
        val dialogView = layoutInflater.inflate(R.layout.backup_options_dialog, null)
        
        val restoreButton = dialogView.findViewById<Button>(R.id.restore_button)
        val deleteButton = dialogView.findViewById<Button>(R.id.delete_button)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        
        restoreButton.setOnClickListener {
            dialog.dismiss()
            showRestoreConfirmation(backupFile)
        }
        
        deleteButton.setOnClickListener {
            dialog.dismiss()
            showDeleteBackupConfirmation(backupFile)
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showDeleteBackupConfirmation(backupFile: File) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete Backup")
            .setMessage("Are you sure you want to delete this backup file?")
            .setPositiveButton("Delete") { _, _ ->
                if (jsonDataManager.deleteBackup(backupFile)) {
                    Toast.makeText(this, "Backup deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to delete backup", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        dialog.show()
    }

    private fun setButtonsToWhite() {
        try {
            val dimWhiteColor = android.graphics.Color.rgb(200, 200, 200) // Dimmer white
            val blackColor = android.graphics.Color.BLACK
            
            val buttons = listOf(
                findViewById<Button>(R.id.manage_credentials_button),
                findViewById<Button>(R.id.clear_cache_button),
                findViewById<Button>(R.id.backup_restore_button),
                findViewById<Button>(R.id.save_ip_button),
                findViewById<Button>(R.id.delete_ip_button),
                findViewById<Button>(R.id.save_fallback_url_button),
                findViewById<Button>(R.id.delete_fallback_url_button)
            )
            
            buttons.forEach { button ->
                button?.backgroundTintList = android.content.res.ColorStateList.valueOf(dimWhiteColor)
                button?.setTextColor(blackColor)
            }
            
            // Set edit text borders to dim white
            val editTexts = listOf(
                findViewById<EditText>(R.id.ip_edit_text),
                findViewById<EditText>(R.id.fallback_url_edit_text),
                findViewById<EditText>(R.id.trusted_wifi_edit_text)
            )
            
            editTexts.forEach { editText ->
                editText?.backgroundTintList = android.content.res.ColorStateList.valueOf(dimWhiteColor)
            }
        } catch (e: Exception) {
            // Handle any errors
        }
    }
    
    private fun suppressAutofillAndSuggestions(editText: EditText) {
        editText.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }
    
    private fun setupPinBoxes(pinBoxes: List<EditText>, backing: CharArray, onComplete: () -> Unit) {
        pinBoxes.forEachIndexed { i, editText ->
            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DEL) {
                        if (editText.text.isEmpty() && i > 0) {
                            backing[i - 1] = ' '
                            pinBoxes[i - 1].setText("")
                            pinBoxes[i - 1].requestFocus()
                            pinBoxes[i - 1].setSelection(pinBoxes[i - 1].text.length)
                        } else if (editText.text.isNotEmpty()) {
                            backing[i] = ' '
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
                    val input = s?.toString() ?: ""
                    if (input.isNotEmpty()) {
                        backing[i] = input[0]
                        editText.setText("•")
                        editText.setSelection(1)
                        
                        if (i < pinBoxes.size - 1) {
                            pinBoxes[i + 1].requestFocus()
                        } else {
                            onComplete()
                        }
                    }
                }
            })
        }
    }
}