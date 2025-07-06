package com.labarr

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.os.Environment

class JsonDataManager(private val context: Context) {
    
    private val settingsFile = File(context.filesDir, "settings.json")
    private val credentialsFile = File(context.filesDir, "credentials.json")
    private val secureCredentialManager = SecureCredentialManager(context)
    
    // Settings data structure
    data class Settings(
        val homelabIp: String = "",
        val fallbackUrl: String = "",
        val trustedWifi: String = "",
        val wifiSsidEnabled: Boolean = false,
        val desktopView: Boolean = false,
        val accentColor: String = "#CCCCCC",
        val backButtonLocked: Boolean = false,
        val backButtonX: Float = -1f,
        val backButtonY: Float = -1f,
        val vibrationEnabled: Boolean = true,
        val autoRotateEnabled: Boolean = true,
        val showBackButton: Boolean = true,
        val loginPersistence: Boolean = true,
        val autoFillCredentials: Boolean = true,
        val enableAutomaticLoginSystem: Boolean = true
    )
    
    // Credential data structure
    data class Credential(
        val site: String,
        val username: String,
        val password: String,
        val name: String = "" // Optional name for the credential
    )
    
    // Settings methods
    fun saveSettings(settings: Settings) {
        try {
            val jsonObject = JSONObject().apply {
                put("homelabIp", settings.homelabIp)
                put("fallbackUrl", settings.fallbackUrl)
                put("trustedWifi", settings.trustedWifi)
                put("wifiSsidEnabled", settings.wifiSsidEnabled)
                put("desktopView", settings.desktopView)
                put("accentColor", settings.accentColor)
                put("backButtonLocked", settings.backButtonLocked)
                put("backButtonX", settings.backButtonX)
                put("backButtonY", settings.backButtonY)
                put("vibrationEnabled", settings.vibrationEnabled)
                put("autoRotateEnabled", settings.autoRotateEnabled)
                put("showBackButton", settings.showBackButton)
                put("loginPersistence", settings.loginPersistence)
                put("autoFillCredentials", settings.autoFillCredentials)
                put("enableAutomaticLoginSystem", settings.enableAutomaticLoginSystem)
            }
            
            FileWriter(settingsFile).use { writer ->
                writer.write(jsonObject.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun loadSettings(): Settings {
        return try {
            if (settingsFile.exists()) {
                val jsonString = FileReader(settingsFile).use { it.readText() }
                val jsonObject = JSONObject(jsonString)
                
                Settings(
                    homelabIp = jsonObject.optString("homelabIp", ""),
                    fallbackUrl = jsonObject.optString("fallbackUrl", ""),
                    trustedWifi = jsonObject.optString("trustedWifi", ""),
                    wifiSsidEnabled = jsonObject.optBoolean("wifiSsidEnabled", false),
                    desktopView = jsonObject.optBoolean("desktopView", false),
                    accentColor = jsonObject.optString("accentColor", "#CCCCCC"),
                    backButtonLocked = jsonObject.optBoolean("backButtonLocked", false),
                    backButtonX = jsonObject.optDouble("backButtonX", -1.0).toFloat(),
                    backButtonY = jsonObject.optDouble("backButtonY", -1.0).toFloat(),
                    vibrationEnabled = jsonObject.optBoolean("vibrationEnabled", true),
                    autoRotateEnabled = jsonObject.optBoolean("autoRotateEnabled", true),
                    showBackButton = jsonObject.optBoolean("showBackButton", true),
                    loginPersistence = jsonObject.optBoolean("loginPersistence", true),
                    autoFillCredentials = jsonObject.optBoolean("autoFillCredentials", true),
                    enableAutomaticLoginSystem = jsonObject.optBoolean("enableAutomaticLoginSystem", true)
                )
            } else {
                Settings()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Settings()
        }
    }
    
    // Credentials methods
    fun saveCredentials(credentials: List<Credential>) {
        try {
            if (secureCredentialManager.isPinSet()) {
                // Use encrypted storage
                val encryptedData = secureCredentialManager.encryptCredentials(credentials)
                FileWriter(credentialsFile).use { writer ->
                    writer.write(encryptedData)
                }
            } else {
                // Fallback to unencrypted storage (for backward compatibility)
                val jsonArray = JSONArray()
                credentials.forEach { credential ->
                    val credentialObject = JSONObject().apply {
                        put("site", credential.site)
                        put("username", credential.username)
                        put("password", credential.password)
                        put("name", credential.name)
                    }
                    jsonArray.put(credentialObject)
                }
                
                FileWriter(credentialsFile).use { writer ->
                    writer.write(jsonArray.toString())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun loadCredentials(): List<Credential> {
        return try {
            if (credentialsFile.exists()) {
                val fileContent = FileReader(credentialsFile).use { it.readText() }
                
                if (secureCredentialManager.isPinSet()) {
                    // Try to decrypt the data
                    try {
                        secureCredentialManager.decryptCredentials(fileContent)
                    } catch (e: Exception) {
                        // If decryption fails, try to read as unencrypted JSON (backward compatibility)
                        val jsonArray = JSONArray(fileContent)
                        val credentials = mutableListOf<Credential>()
                        
                        for (i in 0 until jsonArray.length()) {
                            val credentialObject = jsonArray.getJSONObject(i)
                            credentials.add(
                                Credential(
                                    site = credentialObject.getString("site"),
                                    username = credentialObject.getString("username"),
                                    password = credentialObject.getString("password"),
                                    name = credentialObject.optString("name", "")
                                )
                            )
                        }
                        credentials
                    }
                } else {
                    // Read unencrypted JSON
                    val jsonArray = JSONArray(fileContent)
                    val credentials = mutableListOf<Credential>()
                    
                    for (i in 0 until jsonArray.length()) {
                        val credentialObject = jsonArray.getJSONObject(i)
                        credentials.add(
                            Credential(
                                site = credentialObject.getString("site"),
                                username = credentialObject.getString("username"),
                                password = credentialObject.getString("password"),
                                name = credentialObject.optString("name", "")
                            )
                        )
                    }
                    credentials
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    fun addCredential(credential: Credential) {
        val currentCredentials = loadCredentials().toMutableList()
        currentCredentials.add(credential)
        saveCredentials(currentCredentials)
    }
    
    fun saveCredentials(site: String, username: String, password: String, name: String = "") {
        addCredential(Credential(site, username, password, name))
    }
    
    fun updateCredentialsForSite(site: String, username: String, password: String, name: String = "") {
        // Remove all existing credentials for this site
        deleteCredentials(site)
        // Add the new credential
        addCredential(Credential(site, username, password, name))
    }
    
    fun loadCredentials(site: String): List<Pair<String, String>> {
        return loadCredentials()
            .filter { it.site == site }
            .map { Pair(it.username, it.password) }
    }
    
    fun removeCredential(site: String, username: String) {
        val currentCredentials = loadCredentials().toMutableList()
        currentCredentials.removeAll { it.site == site && it.username == username }
        saveCredentials(currentCredentials)
    }
    
    fun deleteCredentials(site: String, index: Int) {
        val currentCredentials = loadCredentials().toMutableList()
        val siteCredentials = currentCredentials.filter { it.site == site }
        if (index < siteCredentials.size) {
            val credentialToRemove = siteCredentials[index]
            currentCredentials.removeAll { it.site == site && it.username == credentialToRemove.username }
            saveCredentials(currentCredentials)
        }
    }
    
    fun deleteCredentials(site: String) {
        val currentCredentials = loadCredentials().toMutableList()
        currentCredentials.removeAll { it.site == site }
        saveCredentials(currentCredentials)
    }
    
    fun getCredentialsForSite(site: String): List<Credential> {
        return loadCredentials().filter { it.site == site }
    }
    
    fun clearAllData() {
        try {
            // Delete main data files
            if (settingsFile.exists()) {
                settingsFile.delete()
            }
            if (credentialsFile.exists()) {
                credentialsFile.delete()
            }
            
            // Clear encrypted data (PIN, encryption keys)
            secureCredentialManager.clearAllEncryptedData()
            
            // Clear all shared preferences to reset to fresh install state
            context.getSharedPreferences("LabarrPrefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            
            // Clear any other shared preferences that might exist
            context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            
            // Clear backup folder and all backup files
            val externalStorageDir = Environment.getExternalStorageDirectory()
            val labarrDir = File(externalStorageDir, "Labarr")
            val backupsDir = File(labarrDir, "Backups")
            
            if (backupsDir.exists()) {
                // Delete all backup files (not just labarr_backup_*.zip)
                backupsDir.listFiles()?.forEach { file ->
                    file.delete()
                }
                
                // Try to delete the backups directory
                backupsDir.delete()
            }
            
            // Try to delete the Labarr directory (will only work if empty)
            if (labarrDir.exists() && labarrDir.listFiles()?.isEmpty() != false) {
                labarrDir.delete()
            }
            
            // Clear any temporary files in the app's cache
            val cacheDir = context.cacheDir
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("temp_") || file.name.endsWith(".zip")) {
                        file.delete()
                    }
                }
            }
            
            // Clear external cache directory
            val externalCacheDir = context.externalCacheDir
            if (externalCacheDir?.exists() == true) {
                externalCacheDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("temp_") || file.name.endsWith(".zip")) {
                        file.delete()
                    }
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // PIN management methods
    fun isPinSet(): Boolean {
        return secureCredentialManager.isPinSet()
    }
    
    fun setPin(pin: String): Boolean {
        return secureCredentialManager.setPin(pin)
    }
    
    fun verifyPin(pin: String): Boolean {
        return secureCredentialManager.verifyPin(pin)
    }
    
    fun removePin(): Boolean {
        return secureCredentialManager.removePin()
    }
    
    fun hasDeclinedPin(): Boolean {
        val prefs = context.getSharedPreferences("LabarrPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("pin_declined", false)
    }
    
    fun setPinDeclined() {
        val prefs = context.getSharedPreferences("LabarrPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pin_declined", true).apply()
    }
    
    fun clearPinDeclined() {
        val prefs = context.getSharedPreferences("LabarrPrefs", Context.MODE_PRIVATE)
        prefs.edit().remove("pin_declined").apply()
    }
    
    // Site-specific desktop view settings
    fun loadSiteDesktopView(site: String): Boolean? {
        val prefs = context.getSharedPreferences("LabarrPrefs", Context.MODE_PRIVATE)
        return if (prefs.contains("site_desktop_$site")) {
            prefs.getBoolean("site_desktop_$site", false)
        } else {
            null // Use global setting
        }
    }
    
    fun saveSiteDesktopView(site: String, enabled: Boolean?) {
        val prefs = context.getSharedPreferences("LabarrPrefs", Context.MODE_PRIVATE)
        if (enabled == null) {
            prefs.edit().remove("site_desktop_$site").apply()
        } else {
            prefs.edit().putBoolean("site_desktop_$site", enabled).apply()
        }
    }
    
    // Backup and restore functionality
    fun createBackup(): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            
            // Create Labarr/Backups directory in root storage
            val externalStorageDir = Environment.getExternalStorageDirectory()
            val labarrDir = File(externalStorageDir, "Labarr")
            val backupsDir = File(labarrDir, "Backups")
            
            if (!backupsDir.exists()) {
                backupsDir.mkdirs()
            }
            
            val backupFileName = "labarr_backup_$timestamp.zip"
            val backupFile = File(backupsDir, backupFileName)
            
            // Create temporary file for the backup data
            val tempFile = File(context.cacheDir, "temp_backup.zip")
            
            ZipOutputStream(FileOutputStream(tempFile)).use { zipOut ->
                // Backup settings
                if (settingsFile.exists()) {
                    val entry = ZipEntry("settings.json")
                    zipOut.putNextEntry(entry)
                    FileInputStream(settingsFile).use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
                
                // Backup credentials
                if (credentialsFile.exists()) {
                    val entry = ZipEntry("credentials.json")
                    zipOut.putNextEntry(entry)
                    FileInputStream(credentialsFile).use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
                
                // Backup shared preferences
                val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/LabarrPrefs.xml")
                if (prefsFile.exists()) {
                    val entry = ZipEntry("preferences.xml")
                    zipOut.putNextEntry(entry)
                    FileInputStream(prefsFile).use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }
            
            // If PIN is set, encrypt the backup file
            if (secureCredentialManager.isPinSet()) {
                val encryptedData = secureCredentialManager.encryptFile(tempFile)
                FileWriter(backupFile).use { writer ->
                    writer.write(encryptedData)
                }
                tempFile.delete()
            } else {
                // No PIN - just copy the file as-is
                tempFile.copyTo(backupFile, overwrite = true)
                tempFile.delete()
            }
            
            backupFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun restoreBackup(backupFile: File): Boolean {
        return try {
            // Create temporary file for processing
            val tempFile = File(context.cacheDir, "temp_restore.zip")
            
            // If PIN is set, decrypt the backup file
            if (secureCredentialManager.isPinSet()) {
                val encryptedData = FileReader(backupFile).use { it.readText() }
                val decryptedData = secureCredentialManager.decryptFile(encryptedData)
                FileWriter(tempFile).use { writer ->
                    writer.write(decryptedData)
                }
            } else {
                // No PIN - just copy the file as-is
                backupFile.copyTo(tempFile, overwrite = true)
            }
            
            // Extract the backup
            ZipInputStream(FileInputStream(tempFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "settings.json" -> {
                            FileOutputStream(settingsFile).use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                        "credentials.json" -> {
                            FileOutputStream(credentialsFile).use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                        "preferences.xml" -> {
                            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                            if (!prefsDir.exists()) {
                                prefsDir.mkdirs()
                            }
                            val prefsFile = File(prefsDir, "LabarrPrefs.xml")
                            FileOutputStream(prefsFile).use { output ->
                                zipIn.copyTo(output)
                            }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            
            // Clean up temp file
            tempFile.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun getBackupFiles(): List<File> {
        val externalStorageDir = Environment.getExternalStorageDirectory()
        val labarrDir = File(externalStorageDir, "Labarr")
        val backupsDir = File(labarrDir, "Backups")
        
        return if (backupsDir.exists()) {
            backupsDir.listFiles { file ->
                file.name.startsWith("labarr_backup_") && file.name.endsWith(".zip")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    fun deleteBackup(backupFile: File): Boolean {
        return try {
            backupFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
} 