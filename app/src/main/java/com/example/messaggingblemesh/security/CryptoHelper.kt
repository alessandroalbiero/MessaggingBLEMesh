package com.example.messaggingblemesh.security

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private const val EC_ALGORITHM = "EC"
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val NONCE_LENGHT_BYTE = 12

    fun generateMyKeyPair(): KeyPair{
        val keyPairGenerator = KeyPairGenerator.getInstance(EC_ALGORITHM)
        keyPairGenerator.initialize(256)
        return keyPairGenerator.generateKeyPair()
    }

    fun keyBase64(key: java.security.Key): String{
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    fun getPublicKeyFromBase64(keyConverted: String): PublicKey{
        val keyBytes = Base64.decode(keyConverted, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance(EC_ALGORITHM).generatePublic(spec)
    }

    fun getPrivateKeyFromBase64(keyConverted: String): PrivateKey{
        val keyBytes = Base64.decode(keyConverted, Base64.NO_WRAP)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance(EC_ALGORITHM).generatePrivate(spec)
    }

    fun deriveSharedSessionKey(personalPrivateKey: PrivateKey, contactPublicKeyBase64: String): String{
        val contactPublicKey = getPublicKeyFromBase64(contactPublicKeyBase64)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(personalPrivateKey)
        keyAgreement.doPhase(contactPublicKey, true)

        val sharedSecret = keyAgreement.generateSecret()

        val digest = MessageDigest.getInstance("SHA-256")
        val asymmetricKeyBytes = digest.digest(sharedSecret)
        return Base64.encodeToString(asymmetricKeyBytes, Base64.NO_WRAP)
    }

    fun encrypt(plainText: String, sharedSessionKey: String): Pair<String, String>{
        val keyBytes = Base64.decode(sharedSessionKey, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val nonce = ByteArray(NONCE_LENGHT_BYTE)
        SecureRandom().nextBytes(nonce)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Pair(Base64.encodeToString(encryptedBytes, Base64.NO_WRAP), Base64.encodeToString(nonce, Base64.NO_WRAP))
    }

    fun decrypt(encryptedTextBase64: String, nonceBase64: String, sharedSessionKey: String): String {
        val keyBytes = Base64.decode(sharedSessionKey, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val nonce = Base64.decode(nonceBase64, Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(encryptedTextBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        return String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
    }
}