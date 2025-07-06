package com.labarr

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64
import android.util.Base64 as AndroidBase64
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import android.util.Log

class SecureCredentialManager(private val context: Context) {
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val keyAlias = "Labarr_CREDENTIALS_KEY"
        private val pinKeyAlias = "Labarr_PIN_KEY"
    }
    
    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    
    // Check if PIN is already set
    fun isPinSet(): Boolean {
        return try {
            keyStore.containsAlias(pinKeyAlias)
        } catch (e: Exception) {
            false
        }
    }
    
    // Set PIN for the first time
    fun setPin(pin: String): Boolean {
        return try {
            // Generate a key for PIN storage
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                pinKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            val secretKey = keyGenerator.generateKey()
            
            // Encrypt and store the PIN
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedPin = cipher.doFinal(pin.toByteArray())
            val iv = cipher.iv
            
            // Store encrypted PIN and IV
            val pinData = iv + encryptedPin
            context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("encrypted_pin", AndroidBase64.encodeToString(pinData, AndroidBase64.DEFAULT))
                .apply()
            
            // Generate main encryption key
            generateMainKey()
            
            // Re-encrypt existing credentials with the new key
            reEncryptExistingCredentials()
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // Verify PIN
    fun verifyPin(pin: String): Boolean {
        return try {
            val storedPinData = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
                .getString("encrypted_pin", null) ?: return false
            
            val pinData = AndroidBase64.decode(storedPinData, AndroidBase64.DEFAULT)
            val iv = pinData.sliceArray(0..11)
            val encryptedPin = pinData.sliceArray(12 until pinData.size)
            
            val secretKey = keyStore.getKey(pinKeyAlias, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedPin = String(cipher.doFinal(encryptedPin))
            pin == decryptedPin
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // Generate main encryption key for credentials
    private fun generateMainKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }
    
    // Re-encrypt existing credentials when PIN is set
    private fun reEncryptExistingCredentials() {
        try {
            val credentialsFile = File(context.filesDir, "credentials.json")
            if (credentialsFile.exists()) {
                val fileContent = FileReader(credentialsFile).use { it.readText() }
                
                // Try to parse as unencrypted JSON first (credentials saved before PIN was set)
                try {
                    val jsonArray = org.json.JSONArray(fileContent)
                    val credentials = mutableListOf<JsonDataManager.Credential>()
                    
                    for (i in 0 until jsonArray.length()) {
                        val credentialObject = jsonArray.getJSONObject(i)
                        credentials.add(
                            JsonDataManager.Credential(
                                site = credentialObject.getString("site"),
                                username = credentialObject.getString("username"),
                                password = credentialObject.getString("password"),
                                name = credentialObject.optString("name", "")
                            )
                        )
                    }
                    
                    // Re-encrypt with the new key
                    val encryptedData = encryptCredentials(credentials)
                    FileWriter(credentialsFile).use { writer: FileWriter ->
                        writer.write(encryptedData)
                    }
                    
                    // Delete the old unencrypted file (it's now replaced with encrypted version)
                    // Note: We don't need to delete here since we're overwriting the same file
                } catch (e: Exception) {
                    // If parsing as JSON fails, the data might already be encrypted
                    // In this case, we don't need to do anything
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Decrypt existing credentials when PIN is removed
    private fun decryptExistingCredentials() {
        try {
            val credentialsFile = File(context.filesDir, "credentials.json")
            if (credentialsFile.exists()) {
                val fileContent = FileReader(credentialsFile).use { it.readText() }
                
                // Try to decrypt the data (credentials are currently encrypted)
                try {
                    val credentials = decryptCredentials(fileContent)
                    
                    // Convert back to unencrypted JSON format
                    val jsonArray = org.json.JSONArray()
                    credentials.forEach { credential ->
                        val credentialObject = org.json.JSONObject().apply {
                            put("site", credential.site)
                            put("username", credential.username)
                            put("password", credential.password)
                            put("name", credential.name)
                        }
                        jsonArray.put(credentialObject)
                    }
                    
                    // Write unencrypted JSON back to file
                    FileWriter(credentialsFile).use { writer: FileWriter ->
                        writer.write(jsonArray.toString())
                    }
                } catch (e: Exception) {
                    // If decryption fails, the data might already be unencrypted
                    // In this case, we don't need to do anything
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Encrypt credentials
    fun encryptCredentials(credentials: List<JsonDataManager.Credential>): String {
        return try {
            val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val credentialsJson = credentials.joinToString(",") { credential ->
                """{"site":"${credential.site}","username":"${credential.username}","password":"${credential.password}"}"""
            }
            val jsonString = "[$credentialsJson]"
            
            val encryptedData = cipher.doFinal(jsonString.toByteArray())
            val iv = cipher.iv
            
            val combined = iv + encryptedData
            AndroidBase64.encodeToString(combined, AndroidBase64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    // Decrypt credentials
    fun decryptCredentials(encryptedData: String): List<JsonDataManager.Credential> {
        return try {
            val data = AndroidBase64.decode(encryptedData, AndroidBase64.DEFAULT)
            val iv = data.sliceArray(0..11)
            val encrypted = data.sliceArray(12 until data.size)
            
            val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedJson = String(cipher.doFinal(encrypted))
            
            // Parse JSON back to credentials
            parseCredentialsFromJson(decryptedJson)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    // Parse JSON string back to credentials
    private fun parseCredentialsFromJson(jsonString: String): List<JsonDataManager.Credential> {
        return try {
            val jsonArray = org.json.JSONArray(jsonString)
            val credentials = mutableListOf<JsonDataManager.Credential>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                credentials.add(
                    JsonDataManager.Credential(
                        site = obj.getString("site"),
                        username = obj.getString("username"),
                        password = obj.getString("password")
                    )
                )
            }
            credentials
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    // Clear all encrypted data (for PIN reset)
    fun clearAllEncryptedData() {
        try {
            context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            
            // Remove keys from KeyStore
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
            if (keyStore.containsAlias(pinKeyAlias)) {
                keyStore.deleteEntry(pinKeyAlias)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Remove PIN protection
    fun removePin(): Boolean {
        return try {
            // Decrypt existing credentials before removing PIN
            decryptExistingCredentials()
            
            // Clear all encrypted data (PIN and keys)
            clearAllEncryptedData()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // Encrypt a file using the main encryption key
    fun encryptFile(file: File): String {
        return try {
            val fileContent = file.readBytes()
            val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val encryptedData = cipher.doFinal(fileContent)
            val iv = cipher.iv
            
            val combined = iv + encryptedData
            AndroidBase64.encodeToString(combined, AndroidBase64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    // Decrypt a file using the main encryption key
    fun decryptFile(encryptedData: String): String {
        return try {
            val data = AndroidBase64.decode(encryptedData, AndroidBase64.DEFAULT)
            val iv = data.sliceArray(0..11)
            val encrypted = data.sliceArray(12 until data.size)
            
            val secretKey = keyStore.getKey(keyAlias, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedData = cipher.doFinal(encrypted)
            String(decryptedData)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
} 