package kontinuum

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.*
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.NoSuchProviderException
import java.security.SecureRandom
import java.security.Security
import java.util.*

object PGPUtils {

    private val BUFFER_SIZE = 1 shl 16 // should always be power of 2
    private val KEY_FLAGS = 27
    private val MASTER_KEY_CERTIFICATION_TYPES = intArrayOf(PGPSignature.POSITIVE_CERTIFICATION, PGPSignature.CASUAL_CERTIFICATION, PGPSignature.NO_CERTIFICATION, PGPSignature.DEFAULT_CERTIFICATION)

    @Throws(IOException::class, PGPException::class)
    fun readPublicKey(`in`: InputStream): PGPPublicKey {
        val keyRingCollection = PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(`in`), BcKeyFingerprintCalculator())
        //
        // we just loop through the collection till we find a key suitable for encryption, in the real
        // world you would probably want to be a bit smarter about this.
        //
        var publicKey: PGPPublicKey? = null

        //
        // iterate through the key rings.
        //
        val rIt = keyRingCollection.keyRings

        while (publicKey == null && rIt.hasNext()) {
            val kRing = rIt.next()
            val kIt = kRing.publicKeys
            while (publicKey == null && kIt.hasNext()) {
                val key = kIt.next()
                if (key.isEncryptionKey) {
                    publicKey = key
                }
            }
        }

        if (publicKey == null) {
            throw IllegalArgumentException("Can't find public key in the key ring.")
        }
        if (!isForEncryption(publicKey)) {
            throw IllegalArgumentException("KeyID " + publicKey.keyID + " not flagged for encryption.")
        }

        return publicKey
    }

    /**
     * From LockBox Lobs PGP Encryption tools.
     * http://www.lockboxlabs.org/content/downloads
     *
     * I didn't think it was worth having to import a 4meg lib for three methods
     * @param key
     * @return
     */
    fun isForEncryption(key: PGPPublicKey): Boolean {
        return if (key.algorithm == PublicKeyAlgorithmTags.RSA_SIGN
                || key.algorithm == PublicKeyAlgorithmTags.DSA
                || key.algorithm == PublicKeyAlgorithmTags.EC
                || key.algorithm == PublicKeyAlgorithmTags.ECDSA) {
            false
        } else hasKeyFlags(key, KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE)

    }

    /**
     * From LockBox Lobs PGP Encryption tools.
     * http://www.lockboxlabs.org/content/downloads
     *
     * I didn't think it was worth having to import a 4meg lib for three methods
     * @return
     */
    private fun hasKeyFlags(encKey: PGPPublicKey, keyUsage: Int): Boolean {
        if (encKey.isMasterKey) {
            for (i in PGPUtils.MASTER_KEY_CERTIFICATION_TYPES.indices) {
                val eIt = encKey.getSignaturesOfType(PGPUtils.MASTER_KEY_CERTIFICATION_TYPES[i])
                while (eIt.hasNext()) {
                    val sig = eIt.next()
                    if (!isMatchingUsage(sig as PGPSignature, keyUsage)) {
                        return false
                    }
                }
            }
        } else {
            val eIt = encKey.getSignaturesOfType(PGPSignature.SUBKEY_BINDING)
            while (eIt.hasNext()) {
                val sig = eIt.next()
                if (!isMatchingUsage(sig as PGPSignature, keyUsage)) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * From LockBox Lobs PGP Encryption tools.
     * http://www.lockboxlabs.org/content/downloads
     *
     * I didn't think it was worth having to import a 4meg lib for three methods
     * @return
     */
    private fun isMatchingUsage(sig: PGPSignature, keyUsage: Int): Boolean {
        if (sig.hasSubpackets()) {
            val sv = sig.hashedSubPackets
            if (sv.hasSubpacket(PGPUtils.KEY_FLAGS)) {
                // code fix suggested by kzt (see comments)
                if (sv.keyFlags == 0 && keyUsage == 0) {
                    return false
                }
            }
        }
        return true
    }

    fun writeBytesToLiteralData(out: OutputStream,
                                 name: String, bytes: ByteArray,fileType: Char = PGPLiteralDataGenerator.TEXT) {
        val lData = PGPLiteralDataGenerator();
        val pOut = lData.open(out, fileType, name, bytes.size.toLong(), Date())
        pOut.write(bytes);
    }

    @Throws(IOException::class, NoSuchProviderException::class, PGPException::class)
    fun encryptFile(
            out: OutputStream,
            string: String,
            encKey: PGPPublicKey,
            armor: Boolean,
            withIntegrityCheck: Boolean) {
        var out = out
        Security.addProvider(BouncyCastleProvider())

        if (armor) {
            out = ArmoredOutputStream(out)
        }

        val bOut = ByteArrayOutputStream()
        val comData = PGPCompressedDataGenerator(PGPCompressedData.ZIP)

        writeBytesToLiteralData(
                bOut,
                "fileName",
                string.toByteArray())

        comData.close()

        val dataEncryptor = BcPGPDataEncryptorBuilder(PGPEncryptedData.TRIPLE_DES)
        dataEncryptor.setWithIntegrityPacket(withIntegrityCheck)
        dataEncryptor.secureRandom = SecureRandom()

        val encryptedDataGenerator = PGPEncryptedDataGenerator(dataEncryptor)
        encryptedDataGenerator.addMethod(BcPublicKeyKeyEncryptionMethodGenerator(encKey))

        val bytes = bOut.toByteArray()
        val cOut = encryptedDataGenerator.open(out, bytes.size.toLong())
        cOut.write(bytes)
        cOut.close()
        out.close()
    }

}
