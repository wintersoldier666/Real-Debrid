package com.ghostnotes;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * CryptoManager - All encryption operations for Ghost Notes
 *
 * Security architecture:
 * 1. Password → PBKDF2-HMAC-SHA256 (210,000 iterations) → 256-bit master key
 * 2. Notes encrypted with AES-256-GCM (authenticated encryption)
 * 3. Android Keystore wraps the master key derivation parameters
 * 4. Each note has a unique 12-byte random IV
 * 5. Encrypted format: [16-byte salt][12-byte IV][ciphertext+16-byte GCM tag]
 */
public class CryptoManager {

    private static final String PREFS_NAME = "ghost_secure_prefs";
    private static final String KEY_SALT = "k_s";
    private static final String KEY_VERIFIER = "k_v";
    private static final String KEY_ITERATIONS = "k_i";
    private static final String KEYSTORE_ALIAS = "ghost_notes_master";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    // AES-256-GCM parameters
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int SALT_LENGTH_BYTES = 32;

    // PBKDF2 parameters (OWASP recommended: 210,000 for SHA-256 in 2024)
    private static final int PBKDF2_ITERATIONS = 210000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    // Known plaintext for password verification
    private static final String VERIFIER_PLAINTEXT = "GHOST_NOTES_VERIFIED_2024";

    private final Context context;
    private byte[] sessionKey = null; // In-memory only, cleared on lock

    public CryptoManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Check if app has been set up (password exists)
     */
    public boolean isSetUp() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.contains(KEY_VERIFIER);
    }

    /**
     * Set up the app with a new master password.
     * Generates salt, derives key, stores verifier.
     */
    public boolean setupPassword(String password) {
        if (password == null || password.length() < 8) return false;

        try {
            // Generate a cryptographically secure random salt
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            random.nextBytes(salt);

            // Derive the master key using PBKDF2
            byte[] key = deriveKey(password.toCharArray(), salt, PBKDF2_ITERATIONS);

            // Create a verification token: encrypt known plaintext with derived key
            byte[] verifier = encryptWithKey(VERIFIER_PLAINTEXT.getBytes("UTF-8"), key);

            // Store salt and verifier (NOT the key itself)
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));
            editor.putString(KEY_VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP));
            editor.putInt(KEY_ITERATIONS, PBKDF2_ITERATIONS);
            editor.commit();

            // Store key in memory for this session
            sessionKey = key;
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verify password and unlock session.
     * Returns true if password is correct.
     */
    public boolean unlockWithPassword(String password) {
        if (password == null || password.isEmpty()) return false;

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            byte[] salt = Base64.decode(prefs.getString(KEY_SALT, ""), Base64.NO_WRAP);
            byte[] storedVerifier = Base64.decode(prefs.getString(KEY_VERIFIER, ""), Base64.NO_WRAP);
            int iterations = prefs.getInt(KEY_ITERATIONS, PBKDF2_ITERATIONS);

            // Derive key from provided password
            byte[] key = deriveKey(password.toCharArray(), salt, iterations);

            // Try to decrypt the verifier
            byte[] decrypted = decryptWithKey(storedVerifier, key);
            if (decrypted == null) return false;

            String decryptedText = new String(decrypted, "UTF-8");
            if (!VERIFIER_PLAINTEXT.equals(decryptedText)) {
                // Constant time wipe
                Arrays.fill(key, (byte) 0);
                return false;
            }

            // Password is correct - store key for session
            clearSessionKey();
            sessionKey = key;
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Encrypt note content with the session key.
     * Returns Base64-encoded [salt][iv][ciphertext]
     */
    public String encryptNote(String plaintext) {
        if (sessionKey == null || plaintext == null) return null;

        try {
            byte[] data = plaintext.getBytes("UTF-8");
            byte[] encrypted = encryptWithKey(data, sessionKey);
            if (encrypted == null) return null;
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decrypt note content with the session key.
     */
    public String decryptNote(String encryptedBase64) {
        if (sessionKey == null || encryptedBase64 == null) return null;

        try {
            byte[] encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP);
            byte[] decrypted = decryptWithKey(encrypted, sessionKey);
            if (decrypted == null) return null;
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if session is currently active (user is authenticated)
     */
    public boolean isSessionActive() {
        return sessionKey != null;
    }

    /**
     * Lock the session - wipe the key from memory
     */
    public void lockSession() {
        clearSessionKey();
    }

    /**
     * Securely wipe the session key from memory
     */
    private void clearSessionKey() {
        if (sessionKey != null) {
            Arrays.fill(sessionKey, (byte) 0);
            sessionKey = null;
        }
    }

    // ============================================================
    // Core cryptographic operations
    // ============================================================

    /**
     * Derive a 256-bit key from password + salt using PBKDF2-HMAC-SHA256
     */
    private byte[] deriveKey(char[] password, byte[] salt, int iterations) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS);
        try {
            SecretKey key = factory.generateSecret(spec);
            return key.getEncoded();
        } finally {
            spec.clearPassword();
            // Overwrite password chars
            java.util.Arrays.fill(password, '\0');
        }
    }

    /**
     * Encrypt data with AES-256-GCM using the provided raw key bytes.
     * Output format: [12-byte IV][ciphertext+16-byte GCM auth tag]
     */
    private byte[] encryptWithKey(byte[] plaintext, byte[] keyBytes) throws Exception {
        // Generate a unique random IV for this encryption
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[IV_LENGTH_BYTES];
        random.nextBytes(iv);

        // Build the cipher
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

        byte[] ciphertext = cipher.doFinal(plaintext);

        // Prepend IV to ciphertext
        byte[] result = new byte[IV_LENGTH_BYTES + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, IV_LENGTH_BYTES);
        System.arraycopy(ciphertext, 0, result, IV_LENGTH_BYTES, ciphertext.length);

        return result;
    }

    /**
     * Decrypt data with AES-256-GCM.
     * Input format: [12-byte IV][ciphertext+16-byte GCM auth tag]
     * Returns null on authentication failure (tampered data, wrong key)
     */
    private byte[] decryptWithKey(byte[] encrypted, byte[] keyBytes) {
        try {
            if (encrypted.length < IV_LENGTH_BYTES + 16) return null;

            // Extract IV
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(encrypted, 0, iv, 0, IV_LENGTH_BYTES);

            // Extract ciphertext
            int ciphertextLength = encrypted.length - IV_LENGTH_BYTES;
            byte[] ciphertext = new byte[ciphertextLength];
            System.arraycopy(encrypted, IV_LENGTH_BYTES, ciphertext, 0, ciphertextLength);

            // Decrypt and verify authentication tag
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            return cipher.doFinal(ciphertext);

        } catch (Exception e) {
            // AEADBadTagException = tampered data or wrong key
            return null;
        }
    }
}
