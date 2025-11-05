package app.grapheneos.camera.util

import android.util.Base64
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider
import org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec
import java.nio.ByteBuffer
import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Post-Quantum Encryption Utility using CRYSTALS-Kyber KEM + AES-256-GCM
 *
 * This implements hybrid encryption:
 * 1. Generate random AES-256 key
 * 2. Encrypt data with AES-256-GCM
 * 3. Encapsulate AES key using Kyber-1024 public key
 * 4. Output format: [version][kyber_ct_len][kyber_ct][nonce][tag][encrypted_data]
 *
 * File format (version 1):
 * - Byte 0: Version (0x01)
 * - Bytes 1-4: Kyber ciphertext length (big-endian uint32)
 * - Bytes 5-(5+kyber_ct_len): Kyber encapsulated key
 * - Next 12 bytes: AES-GCM nonce
 * - Next 16 bytes: AES-GCM authentication tag
 * - Remaining bytes: AES-GCM encrypted data
 */
object PQEncryption {

    private const val VERSION: Byte = 0x01
    private const val AES_KEY_SIZE = 256
    private const val GCM_NONCE_SIZE = 12
    private const val GCM_TAG_SIZE = 128 // bits

    init {
        // Register BouncyCastle providers
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.removeProvider(BouncyCastlePQCProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        Security.insertProviderAt(BouncyCastlePQCProvider(), 2)
    }

    /**
     * Data class to hold a Kyber key pair
     */
    data class KeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray
    ) {
        fun publicKeyBase64(): String = Base64.encodeToString(publicKey, Base64.NO_WRAP)
        fun privateKeyBase64(): String = Base64.encodeToString(privateKey, Base64.NO_WRAP)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as KeyPair
            if (!publicKey.contentEquals(other.publicKey)) return false
            if (!privateKey.contentEquals(other.privateKey)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = publicKey.contentHashCode()
            result = 31 * result + privateKey.contentHashCode()
            return result
        }

        companion object {
            fun fromBase64(publicKeyB64: String, privateKeyB64: String): KeyPair {
                return KeyPair(
                    Base64.decode(publicKeyB64, Base64.NO_WRAP),
                    Base64.decode(privateKeyB64, Base64.NO_WRAP)
                )
            }
        }
    }

    /**
     * Generate a new Kyber-1024 key pair (highest security level)
     */
    fun generateKeyPair(): KeyPair {
        val keyPairGen = KeyPairGenerator.getInstance("Kyber", BouncyCastlePQCProvider.PROVIDER_NAME)
        keyPairGen.initialize(KyberParameterSpec.kyber1024, SecureRandom())
        val keyPair = keyPairGen.generateKeyPair()

        return KeyPair(
            publicKey = keyPair.public.encoded,
            privateKey = keyPair.private.encoded
        )
    }

    /**
     * Encrypt data using hybrid post-quantum encryption
     *
     * @param data The plaintext data to encrypt
     * @param publicKeyBytes The Kyber public key (raw bytes)
     * @return Encrypted data in the format described above
     */
    fun encrypt(data: ByteArray, publicKeyBytes: ByteArray): ByteArray {
        // 1. Generate random AES-256 key
        val aesKeyGen = KeyGenerator.getInstance("AES")
        aesKeyGen.init(AES_KEY_SIZE, SecureRandom())
        val aesKey = aesKeyGen.generateKey()

        // 2. Encrypt data with AES-256-GCM
        val nonce = ByteArray(GCM_NONCE_SIZE)
        SecureRandom().nextBytes(nonce)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)

        val encryptedData = cipher.doFinal(data)
        // Note: encryptedData contains ciphertext + tag at the end

        // 3. Encapsulate AES key using Kyber public key
        val keyFactory = KeyFactory.getInstance("Kyber", BouncyCastlePQCProvider.PROVIDER_NAME)
        val publicKeySpec = java.security.spec.X509EncodedKeySpec(publicKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        val keyAgreement = KeyAgreement.getInstance("Kyber", BouncyCastlePQCProvider.PROVIDER_NAME)
        keyAgreement.init(publicKey)

        // Generate the shared secret and encapsulated key
        // In Kyber KEM, we need to use the encapsulation mechanism
        // BouncyCastle's KeyAgreement for Kyber performs encapsulation
        val kemCipher = Cipher.getInstance("Kyber", BouncyCastlePQCProvider.PROVIDER_NAME)
        kemCipher.init(Cipher.WRAP_MODE, publicKey, SecureRandom())
        val kyberCiphertext = kemCipher.wrap(aesKey)

        // 4. Build output format
        val output = ByteBuffer.allocate(
            1 + // version
            4 + // kyber ciphertext length
            kyberCiphertext.size + // kyber ciphertext
            GCM_NONCE_SIZE + // GCM nonce
            encryptedData.size // AES-GCM ciphertext + tag
        )

        output.put(VERSION)
        output.putInt(kyberCiphertext.size)
        output.put(kyberCiphertext)
        output.put(nonce)
        output.put(encryptedData)

        return output.array()
    }

    /**
     * Decrypt data using hybrid post-quantum encryption
     *
     * @param encryptedData The encrypted data in our format
     * @param privateKeyBytes The Kyber private key (raw bytes)
     * @return Decrypted plaintext data
     */
    fun decrypt(encryptedData: ByteArray, privateKeyBytes: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(encryptedData)

        // 1. Parse format
        val version = buffer.get()
        if (version != VERSION) {
            throw IllegalArgumentException("Unsupported encryption version: $version")
        }

        val kyberCtLen = buffer.int
        val kyberCiphertext = ByteArray(kyberCtLen)
        buffer.get(kyberCiphertext)

        val nonce = ByteArray(GCM_NONCE_SIZE)
        buffer.get(nonce)

        val aesCiphertext = ByteArray(buffer.remaining())
        buffer.get(aesCiphertext)

        // 2. Decapsulate AES key using Kyber private key
        val keyFactory = KeyFactory.getInstance("Kyber", BouncyCastlePQCProvider.PROVIDER_NAME)
        val privateKeySpec = java.security.spec.PKCS8EncodedKeySpec(privateKeyBytes)
        val privateKey = keyFactory.generatePrivate(privateKeySpec)

        val kemCipher = Cipher.getInstance("Kyber", BouncyCastlePQCProvider.PROVIDER_NAME)
        kemCipher.init(Cipher.UNWRAP_MODE, privateKey)
        val aesKey = kemCipher.unwrap(kyberCiphertext, "AES", Cipher.SECRET_KEY) as SecretKey

        // 3. Decrypt data with AES-256-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)

        return cipher.doFinal(aesCiphertext)
    }

    /**
     * Get encrypted file extension
     */
    fun getEncryptedFileExtension(): String = ".pqenc"

    /**
     * Check if a filename has encrypted extension
     */
    fun isEncryptedFile(filename: String): Boolean = filename.endsWith(getEncryptedFileExtension())
}
