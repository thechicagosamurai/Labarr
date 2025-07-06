package com.labarr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.TextWatcher
import android.text.Editable
import android.view.KeyEvent
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedCallback

class CredentialsActivity : AppCompatActivity() {
    private lateinit var jsonDataManager: JsonDataManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CredentialsAdapter
    private val credentialsList = mutableListOf<CredentialItem>()
    private var activityInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credentials)
        jsonDataManager = JsonDataManager(this)
        if (!checkPinAccess()) {
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (jsonDataManager.isPinSet()) {
                    // Go to settings menu
                    val intent = Intent(this@CredentialsActivity, SettingsActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                } else {
                    finish()
                }
            }
        })
    }

    // Handle configuration changes to prevent screen rotation issues
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Don't recreate the activity on configuration changes
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("CredentialsActivity", "onResume called, activityInitialized: $activityInitialized")
        setButtonToWhite()
        if (!activityInitialized && (jsonDataManager.isPinSet() || jsonDataManager.hasDeclinedPin())) {
            android.util.Log.d("CredentialsActivity", "Initializing activity...")
            initializeActivity()
            activityInitialized = true
        } else if (activityInitialized) {
            // Refresh credentials list when returning to the activity
            android.util.Log.d("CredentialsActivity", "Activity already initialized, refreshing credentials...")
            refreshCredentials()
        }
        val backButton = findViewById<View?>(R.id.back_button)
        backButton?.setOnClickListener { finish() }
    }

    private fun setButtonToWhite() {
        val whiteColor = android.graphics.Color.WHITE
        val blackColor = android.graphics.Color.BLACK
        val addButton = findViewById<Button>(R.id.add_credential_button)
        val pinButton = findViewById<Button>(R.id.pin_management_button)
        addButton?.backgroundTintList = android.content.res.ColorStateList.valueOf(whiteColor)
        addButton?.setTextColor(blackColor)
        addButton?.isEnabled = true
        pinButton?.backgroundTintList = android.content.res.ColorStateList.valueOf(whiteColor)
        pinButton?.setTextColor(blackColor)
        pinButton?.isEnabled = true
    }

    private fun loadCredentials() {
        credentialsList.clear()
        val allCredentials = jsonDataManager.loadCredentials()
        
        android.util.Log.d("CredentialsActivity", "Loading credentials: ${allCredentials.size} total credentials")
        
        // Group credentials by full site string (including port and path)
        val credentialsBySite = allCredentials.groupBy { it.site }
        
        credentialsBySite.forEach { (site, credentials) ->
            android.util.Log.d("CredentialsActivity", "Site: $site, Credentials: ${credentials.size}")
            credentials.forEachIndexed { index, credential ->
                credentialsList.add(CredentialItem(
                    site = site,
                    username = credential.username,
                    password = credential.password,
                    name = credential.name,
                    timestamp = System.currentTimeMillis(), // We don't store timestamps in JSON, so use current time
                    index = index
                ))
            }
        }
        
        // Sort alphabetically by full site string
        credentialsList.sortBy { it.site }
        
        android.util.Log.d("CredentialsActivity", "Final credentialsList size: ${credentialsList.size}")
    }

    private fun showDeleteConfirmation(credential: CredentialItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Credential")
            .setMessage("Are you sure you want to delete the credential for ${credential.site}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteCredential(credential)
                refreshCredentials()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showDesktopToggleDialog(credential: CredentialItem) {
        val currentSetting = jsonDataManager.loadSiteDesktopView(credential.site)
        
        val options = arrayOf("Use Global Setting", "Desktop View", "Mobile View")
        val currentIndex = when (currentSetting) {
            null -> 0
            true -> 1
            false -> 2
        }
        
        AlertDialog.Builder(this)
            .setTitle("Desktop View for ${credential.site}")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val newSetting = when (which) {
                    0 -> null // Use global setting
                    1 -> true // Desktop view
                    2 -> false // Mobile view
                    else -> null // Default to global setting
                }
                
                jsonDataManager.saveSiteDesktopView(credential.site, newSetting)
                refreshCredentials() // Refresh to update the icon
                dialog.dismiss()
                
                val settingText = when (newSetting) {
                    null -> "global setting"
                    true -> "desktop view"
                    false -> "mobile view"
                }
                Toast.makeText(this, "Set to $settingText", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddCredentialDialog() {
        val dialogView = layoutInflater.inflate(R.layout.credential_dialog, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.name_edit)
        val usernameEdit = dialogView.findViewById<EditText>(R.id.username_edit)
        val passwordEdit = dialogView.findViewById<EditText>(R.id.password_edit)
        val siteEdit = dialogView.findViewById<EditText>(R.id.site_edit)
        val passwordToggle = dialogView.findViewById<ImageButton>(R.id.password_toggle)
        
        // Setup password toggle functionality
        var isPasswordVisible = false
        passwordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordEdit.inputType = android.text.InputType.TYPE_CLASS_TEXT
                passwordToggle.setImageResource(R.drawable.ic_eye_open)
            } else {
                passwordEdit.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                passwordToggle.setImageResource(R.drawable.ic_eye_closed)
            }
            passwordEdit.setSelection(passwordEdit.text.length)
        }
        passwordToggle.setImageResource(R.drawable.ic_eye_closed)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Credentials")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameEdit.text.toString().trim()
                val username = usernameEdit.text.toString().trim()
                val password = passwordEdit.text.toString().trim()
                val site = siteEdit.text.toString().trim()
                
                if (username.isNotEmpty() && password.isNotEmpty() && site.isNotEmpty()) {
                    saveCredential(site, username, password, name)
                    refreshCredentials()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        dialog.show()
    }

    private fun showEditCredentialDialog(credential: CredentialItem) {
        val dialogView = layoutInflater.inflate(R.layout.credential_dialog, null)
        val nameEdit = dialogView.findViewById<EditText>(R.id.name_edit)
        val usernameEdit = dialogView.findViewById<EditText>(R.id.username_edit)
        val passwordEdit = dialogView.findViewById<EditText>(R.id.password_edit)
        val siteEdit = dialogView.findViewById<EditText>(R.id.site_edit)
        val passwordToggle = dialogView.findViewById<ImageButton>(R.id.password_toggle)
        
        nameEdit.setText(credential.name)
        usernameEdit.setText(credential.username)
        passwordEdit.setText(credential.password)
        siteEdit.setText(credential.site)
        
        // Setup password toggle functionality
        var isPasswordVisible = false
        passwordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordEdit.inputType = android.text.InputType.TYPE_CLASS_TEXT
                passwordToggle.setImageResource(R.drawable.ic_eye_open)
            } else {
                passwordEdit.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                passwordToggle.setImageResource(R.drawable.ic_eye_closed)
            }
            passwordEdit.setSelection(passwordEdit.text.length)
        }
        passwordToggle.setImageResource(R.drawable.ic_eye_closed)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit Credentials")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val newName = nameEdit.text.toString().trim()
                val newUsername = usernameEdit.text.toString().trim()
                val newPassword = passwordEdit.text.toString().trim()
                val newSite = siteEdit.text.toString().trim()
                
                if (newUsername.isNotEmpty() && newPassword.isNotEmpty() && newSite.isNotEmpty()) {
                    updateCredential(credential, newSite, newUsername, newPassword, newName)
                    refreshCredentials()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)
        dialog.show()
    }

    private fun saveCredential(site: String, username: String, password: String, name: String = "") {
        jsonDataManager.addCredential(JsonDataManager.Credential(site, username, password, name))
        Toast.makeText(this, "Credentials saved for $site", Toast.LENGTH_SHORT).show()
    }

    private fun updateCredential(credential: CredentialItem, newSite: String, newUsername: String, newPassword: String, newName: String) {
        // Remove old credential silently
        jsonDataManager.removeCredential(credential.site, credential.username)
        
        // Add new credential
        saveCredential(newSite, newUsername, newPassword, newName)
        
        Toast.makeText(this, "Credentials updated", Toast.LENGTH_SHORT).show()
    }

    private fun deleteCredential(credential: CredentialItem) {
        jsonDataManager.removeCredential(credential.site, credential.username)
        Toast.makeText(this, "Credential deleted", Toast.LENGTH_SHORT).show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Credential", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun refreshCredentials() {
        android.util.Log.d("CredentialsActivity", "Refreshing credentials...")
        loadCredentials()
        adapter.notifyDataSetChanged()
        android.util.Log.d("CredentialsActivity", "Credentials refreshed, adapter notified")
    }

    private fun checkPinAccess(): Boolean {
        if (!jsonDataManager.isPinSet() && !jsonDataManager.hasDeclinedPin()) {
            // First time - show PIN setup dialog
            showPinSetupDialog()
            return false
        } else if (jsonDataManager.isPinSet()) {
            // Always require PIN entry if set
            showPinVerificationDialog()
            return false
        } else {
            // User has declined PIN - continue normally
            return true
        }
    }
    
    private fun showPinSetupDialog() {
        val dialogView = layoutInflater.inflate(R.layout.pin_setup_dialog, null)
        val pinDigits1 = listOf(
            dialogView.findViewById<EditText>(R.id.pin1_digit_1),
            dialogView.findViewById<EditText>(R.id.pin1_digit_2),
            dialogView.findViewById<EditText>(R.id.pin1_digit_3),
            dialogView.findViewById<EditText>(R.id.pin1_digit_4),
            dialogView.findViewById<EditText>(R.id.pin1_digit_5),
            dialogView.findViewById<EditText>(R.id.pin1_digit_6)
        )
        val pinDigits2 = listOf(
            dialogView.findViewById<EditText>(R.id.pin2_digit_1),
            dialogView.findViewById<EditText>(R.id.pin2_digit_2),
            dialogView.findViewById<EditText>(R.id.pin2_digit_3),
            dialogView.findViewById<EditText>(R.id.pin2_digit_4),
            dialogView.findViewById<EditText>(R.id.pin2_digit_5),
            dialogView.findViewById<EditText>(R.id.pin2_digit_6)
        )
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val errorMessage = dialogView.findViewById<TextView>(R.id.error_message)
        val pinLabel1 = dialogView.findViewById<TextView>(R.id.pin_label_1)
        val pinLabel2 = dialogView.findViewById<TextView>(R.id.pin_label_2)
        val pinBoxesContainer2 = dialogView.findViewById<LinearLayout>(R.id.pin_boxes_container_2)
        val infoText = dialogView.findViewById<TextView>(R.id.info_text)
        cancelButton.visibility = View.VISIBLE
        infoText?.visibility = View.VISIBLE

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        var isConfirmingPin = false
        var firstPin = ""
        val pin1Backing = CharArray(6) { ' ' }
        val pin2Backing = CharArray(6) { ' ' }

        fun getPin1(): String = pin1Backing.concatToString().replace(" ", "")
        fun getPin2(): String = pin2Backing.concatToString().replace(" ", "")
        fun showError() {
            errorMessage.visibility = View.VISIBLE
            (if (isConfirmingPin) pinDigits2 else pinDigits1).forEach { it.setBackgroundResource(R.drawable.pin_box_error) }
        }
        fun clearError() {
            errorMessage.visibility = View.GONE
            pinDigits1.forEach { it.setBackgroundResource(R.drawable.pin_box_empty) }
            pinDigits2.forEach { it.setBackgroundResource(R.drawable.pin_box_empty) }
        }
        fun switchToConfirmation() {
            isConfirmingPin = true
            pinLabel1.text = "PIN Entered"
            pinLabel2.visibility = View.VISIBLE
            pinBoxesContainer2.visibility = View.VISIBLE
            pinDigits2[0].requestFocus()
            clearError()
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                // Go to settings menu
                val intent = Intent(this, SettingsActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
                true
            } else {
                false
            }
        }
        dialog.setOnShowListener {
            pinDigits1[0].requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(pinDigits1[0], android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Hide the credentials list to prevent it from showing behind the dialog
        val setupHideRecyclerView = findViewById<RecyclerView>(R.id.credentials_recycler_view)
        setupHideRecyclerView?.visibility = View.GONE

        cancelButton.setOnClickListener {
            dialog.dismiss()
            jsonDataManager.setPinDeclined()
            // Show the credentials list again
            val cancelRecyclerView = findViewById<RecyclerView>(R.id.credentials_recycler_view)
            cancelRecyclerView?.visibility = View.VISIBLE
            initializeActivity()
        }

        setupPinBoxes(pinDigits1, pin1Backing) {
            if (!isConfirmingPin) {
                val pin = getPin1()
                if (pin.length == 6) {
                    firstPin = pin
                    switchToConfirmation()
                }
            }
        }
        setupPinBoxes(pinDigits2, pin2Backing) {
            if (isConfirmingPin) {
                val pin = getPin2()
                if (pin.length == 6) {
                    if (pin == firstPin) {
                                            if (jsonDataManager.setPin(pin)) {
                        Toast.makeText(this@CredentialsActivity, "PIN set successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        // Show the credentials list again
                        val setupRecyclerView = findViewById<RecyclerView>(R.id.credentials_recycler_view)
                        setupRecyclerView?.visibility = View.VISIBLE
                        activityInitialized = false // force re-init onResume
                        onResume()
                        setupPinManagementButton()
                    } else {
                            showError()
                            pinDigits2.forEach { it.text.clear() }
                            pin2Backing.fill(' ')
                            pinDigits2[0].requestFocus()
                        }
                    } else {
                        showError()
                        pinDigits1.forEach { it.text.clear() }
                        pin1Backing.fill(' ')
                        pinDigits2.forEach { it.text.clear() }
                        pin2Backing.fill(' ')
                        pinDigits1[0].requestFocus()
                        isConfirmingPin = false
                    }
                }
            }
        }
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
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                // Go to settings menu
                val intent = Intent(this, SettingsActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
                true
            } else {
                false
            }
        }
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
                // Show the credentials list again
                val verifyRecyclerView = findViewById<RecyclerView>(R.id.credentials_recycler_view)
                verifyRecyclerView?.visibility = View.VISIBLE
                activityInitialized = false // force re-init onResume
                onResume()
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
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(pinDigits[0], android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        // Hide the activity's back button and credentials list while the dialog is showing
        val backButton = findViewById<View?>(R.id.back_button)
        backButton?.visibility = View.GONE
        
        // Hide the credentials list to prevent it from showing behind the dialog
        val verifyHideRecyclerView = findViewById<RecyclerView>(R.id.credentials_recycler_view)
        verifyHideRecyclerView?.visibility = View.GONE

        setupPinBoxes(pinDigits, pinBacking) {
            val pin = pinBacking.concatToString().replace(" ", "")
            if (pin.length == 6) {
                verifyPin(pin)
            }
        }
    }
    
    private fun initializeActivity() {
        recyclerView = findViewById(R.id.credentials_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        loadCredentials()
        
        adapter = CredentialsAdapter(
            credentialsList,
            onEditClick = { credential -> showEditCredentialDialog(credential) },
            onDeleteClick = { credential -> showDeleteConfirmation(credential) },
            onDesktopToggleClick = { credential -> showDesktopToggleDialog(credential) }
        )
        recyclerView.adapter = adapter

        // Setup add button
        findViewById<Button>(R.id.add_credential_button).setOnClickListener {
            showAddCredentialDialog()
        }
        
        // Setup PIN management button
        setupPinManagementButton()
        
        // Set button to white
        setButtonToWhite()
    }
    
    private fun setupPinManagementButton() {
        val pinButton = findViewById<Button>(R.id.pin_management_button)
        
        if (jsonDataManager.isPinSet()) {
            pinButton.text = "Edit Pin"
        } else if (jsonDataManager.hasDeclinedPin()) {
            pinButton.text = "Set Pin"
        } else {
            pinButton.text = "Set Pin"
        }
        
        pinButton.setOnClickListener {
            if (jsonDataManager.isPinSet()) {
                showPinEditDialog()
            } else {
                // Clear the declined flag so the user can set a PIN now
                jsonDataManager.clearPinDeclined()
                showPinSetupDialog()
            }
        }
    }
    
    private fun showPinEditDialog() {
        // Step 1: Verify current PIN
        val verifyDialogView = layoutInflater.inflate(R.layout.pin_verify_dialog, null)
        val pinDigits = listOf(
            verifyDialogView.findViewById<EditText>(R.id.pin_digit_1),
            verifyDialogView.findViewById<EditText>(R.id.pin_digit_2),
            verifyDialogView.findViewById<EditText>(R.id.pin_digit_3),
            verifyDialogView.findViewById<EditText>(R.id.pin_digit_4),
            verifyDialogView.findViewById<EditText>(R.id.pin_digit_5),
            verifyDialogView.findViewById<EditText>(R.id.pin_digit_6)
        )
        val errorMessage = verifyDialogView.findViewById<TextView>(R.id.error_message)
        val pinBacking = CharArray(6) { ' ' }

        val verifyDialog = AlertDialog.Builder(this)
            .setView(verifyDialogView)
            .setCancelable(false)
            .create()
        verifyDialog.setCanceledOnTouchOutside(false)

        fun getPin(): String = pinBacking.concatToString().replace(" ", "")
        fun showError() {
            errorMessage.visibility = View.VISIBLE
            pinDigits.forEach { it.setBackgroundResource(R.drawable.pin_box_error) }
        }
        fun clearError() {
            errorMessage.visibility = View.GONE
            pinDigits.forEach { it.setBackgroundResource(R.drawable.pin_box_empty) }
        }
        fun onPinVerified() {
            verifyDialog.dismiss()
            showPinChangeOrRemoveDialog()
        }
        pinDigits.forEach { suppressAutofillAndSuggestions(it) }
        verifyDialog.setOnShowListener {
            pinDigits[0].requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(pinDigits[0], android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        verifyDialog.show()
        verifyDialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        setupPinBoxes(pinDigits, pinBacking) {
            val pin = pinBacking.concatToString().replace(" ", "")
            if (pin.length == 6) {
                if (jsonDataManager.verifyPin(pin)) {
                    onPinVerified()
                } else {
                    showError()
                    pinDigits.forEach { it.text.clear() }
                    pinBacking.fill(' ')
                    pinDigits[0].requestFocus()
                }
            }
        }
    }

    private fun showPinChangeOrRemoveDialog() {
        val dialogView = layoutInflater.inflate(R.layout.pin_setup_dialog, null)
        val pinDigits1 = listOf(
            dialogView.findViewById<EditText>(R.id.pin1_digit_1),
            dialogView.findViewById<EditText>(R.id.pin1_digit_2),
            dialogView.findViewById<EditText>(R.id.pin1_digit_3),
            dialogView.findViewById<EditText>(R.id.pin1_digit_4),
            dialogView.findViewById<EditText>(R.id.pin1_digit_5),
            dialogView.findViewById<EditText>(R.id.pin1_digit_6)
        )
        val pinDigits2 = listOf(
            dialogView.findViewById<EditText>(R.id.pin2_digit_1),
            dialogView.findViewById<EditText>(R.id.pin2_digit_2),
            dialogView.findViewById<EditText>(R.id.pin2_digit_3),
            dialogView.findViewById<EditText>(R.id.pin2_digit_4),
            dialogView.findViewById<EditText>(R.id.pin2_digit_5),
            dialogView.findViewById<EditText>(R.id.pin2_digit_6)
        )
        val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)
        val errorMessage = dialogView.findViewById<TextView>(R.id.error_message)
        val pinLabel1 = dialogView.findViewById<TextView>(R.id.pin_label_1)
        val pinLabel2 = dialogView.findViewById<TextView>(R.id.pin_label_2)
        val pinBoxesContainer2 = dialogView.findViewById<LinearLayout>(R.id.pin_boxes_container_2)
        val infoText = dialogView.findViewById<TextView>(R.id.info_text)
        cancelButton.visibility = View.GONE
        infoText?.visibility = View.GONE

        // Add Remove PIN button
        val removePinButton = Button(this).apply {
            text = "Remove PIN"
            setTextColor(android.graphics.Color.BLACK)
            setBackgroundResource(R.drawable.rounded_button_background)
            textSize = 16f
        }
        (dialogView as LinearLayout).addView(removePinButton)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        var isConfirmingPin = false
        var firstPin = ""
        val pin1Backing = CharArray(6) { ' ' }
        val pin2Backing = CharArray(6) { ' ' }

        fun getPin1(): String = pin1Backing.concatToString().replace(" ", "")
        fun getPin2(): String = pin2Backing.concatToString().replace(" ", "")
        fun showError() {
            errorMessage.visibility = View.VISIBLE
            (if (isConfirmingPin) pinDigits2 else pinDigits1).forEach { it.setBackgroundResource(R.drawable.pin_box_error) }
        }
        fun clearError() {
            errorMessage.visibility = View.GONE
            pinDigits1.forEach { it.setBackgroundResource(R.drawable.pin_box_empty) }
            pinDigits2.forEach { it.setBackgroundResource(R.drawable.pin_box_empty) }
        }
        fun switchToConfirmation() {
            isConfirmingPin = true
            pinLabel1.text = "PIN Entered"
            pinLabel2.visibility = View.VISIBLE
            pinBoxesContainer2.visibility = View.VISIBLE
            pinDigits2[0].requestFocus()
            clearError()
        }
        (pinDigits1 + pinDigits2).forEach { suppressAutofillAndSuggestions(it) }
        dialog.setOnShowListener {
            pinDigits1[0].requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(pinDigits1[0], android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        setupPinBoxes(pinDigits1, pin1Backing) {
            if (!isConfirmingPin) {
                val pin = getPin1()
                if (pin.length == 6) {
                    firstPin = pin
                    switchToConfirmation()
                }
            }
        }
        setupPinBoxes(pinDigits2, pin2Backing) {
            if (isConfirmingPin) {
                val pin = getPin2()
                if (pin.length == 6) {
                    if (pin == firstPin) {
                        if (jsonDataManager.setPin(pin)) {
                            Toast.makeText(this@CredentialsActivity, "PIN set successfully", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            activityInitialized = false // force re-init onResume
                            onResume()
                            setupPinManagementButton()
                        } else {
                            showError()
                            pinDigits2.forEach { it.text.clear() }
                            pin2Backing.fill(' ')
                            pinDigits2[0].requestFocus()
                        }
                    } else {
                        showError()
                        pinDigits1.forEach { it.text.clear() }
                        pin1Backing.fill(' ')
                        pinDigits2.forEach { it.text.clear() }
                        pin2Backing.fill(' ')
                        pinDigits1[0].requestFocus()
                        isConfirmingPin = false
                    }
                }
            }
        }

        removePinButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Remove PIN?")
                .setMessage("Are you sure you want to remove your security PIN? This will make your saved credentials less secure.")
                .setPositiveButton("Remove") { _, _ ->
                    if (jsonDataManager.removePin()) {
                        Toast.makeText(this, "PIN removed", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        jsonDataManager.setPinDeclined()
                        activityInitialized = false // force re-init onResume
                        onResume()
                        setupPinManagementButton()
                    } else {
                        Toast.makeText(this, "Failed to remove PIN", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    data class CredentialItem(
        val site: String,
        val username: String,
        val password: String,
        val name: String,
        val timestamp: Long,
        val index: Int
    )

    private fun suppressAutofillAndSuggestions(editText: EditText) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            editText.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        editText.setAutofillHints("")
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        editText.isLongClickable = false
        editText.setTextIsSelectable(false)
        editText.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?) = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
    }

    private fun setupPinBoxes(pinBoxes: List<EditText>, backing: CharArray, onComplete: () -> Unit) {
        pinBoxes.forEachIndexed { i, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val input = s?.toString() ?: ""
                    if (input.isNotEmpty()) {
                        val digit = input.last()
                        if (digit.isDigit()) {
                            backing[i] = digit
                            editText.removeTextChangedListener(this)
                            editText.setText("•")
                            editText.setSelection(1)
                            if (i < pinBoxes.size - 1) {
                                pinBoxes[i + 1].requestFocus()
                            }
                            editText.addTextChangedListener(this)
                        } else {
                            // Remove non-digit input
                            editText.removeTextChangedListener(this)
                            editText.setText("")
                            editText.addTextChangedListener(this)
                            backing[i] = ' '
                        }
                    } else {
                        backing[i] = ' '
                    }
                    if (pinBoxes.all { it.text.isNotEmpty() }) {
                        onComplete()
                    }
                }
            })
            editText.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL) {
                    if (editText.text.isEmpty() && i > 0) {
                        pinBoxes[i - 1].setText("")
                        pinBoxes[i - 1].requestFocus()
                    }
                }
                false
            }
        }
    }
}

class CredentialsAdapter(
    private val credentials: List<CredentialsActivity.CredentialItem>,
    private val onEditClick: (CredentialsActivity.CredentialItem) -> Unit,
    private val onDeleteClick: (CredentialsActivity.CredentialItem) -> Unit,
    private val onDesktopToggleClick: (CredentialsActivity.CredentialItem) -> Unit
) : RecyclerView.Adapter<CredentialsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.name_text)
        val siteText: TextView = view.findViewById(R.id.site_text)
        val usernameText: TextView = view.findViewById(R.id.username_text)
        val passwordText: TextView = view.findViewById(R.id.password_text)
        val desktopToggleButton: ImageButton = view.findViewById(R.id.desktop_toggle_button)
        val editButton: ImageButton = view.findViewById(R.id.edit_button)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.credential_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val credential = credentials[position]
        
        // Set the name text - use the actual name if available, otherwise use site name
        val displayName = if (credential.name.isNotEmpty()) {
            credential.name
        } else {
            credential.site
        }
        holder.nameText.text = displayName
        
        holder.siteText.text = credential.site
        holder.usernameText.text = credential.username
        holder.passwordText.text = "•".repeat(credential.password.length)
        
        // Set desktop toggle button icon based on current setting
        updateDesktopToggleIcon(holder.desktopToggleButton, credential.site)
        
        holder.desktopToggleButton.setOnClickListener {
            onDesktopToggleClick(credential)
        }
        
        holder.editButton.setOnClickListener {
            onEditClick(credential)
        }
        
        holder.deleteButton.setOnClickListener {
            onDeleteClick(credential)
        }
    }
    
    private fun updateDesktopToggleIcon(button: ImageButton, site: String) {
        val jsonDataManager = JsonDataManager(button.context)
        val siteSetting = jsonDataManager.loadSiteDesktopView(site)
        
        when (siteSetting) {
            null -> {
                // Use global setting - show circle with slash
                button.setImageResource(R.drawable.ic_circle_slash)
                button.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY)
            }
            true -> {
                // Desktop view - show monitor icon
                button.setImageResource(R.drawable.ic_monitor)
                button.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            }
            false -> {
                // Mobile view - show phone icon
                button.setImageResource(R.drawable.ic_phone)
                button.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            }
        }
    }

    override fun getItemCount() = credentials.size
} 