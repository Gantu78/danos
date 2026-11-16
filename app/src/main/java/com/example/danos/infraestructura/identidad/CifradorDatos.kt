package com.example.danos.infraestructura.identidad

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Abstracción de cifrado para que los tokens nunca se guarden en texto plano en DataStore (DT-13). */
interface CifradorDatos {
    fun cifrar(textoPlano: ByteArray): String
    fun descifrar(textoCifrado: String): ByteArray?
}

@Singleton
class CifradorAndroidKeystore @Inject constructor() : CifradorDatos {

    override fun cifrar(textoPlano: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerOCrearLlave())
        val iv = cipher.iv
        val cifrado = cipher.doFinal(textoPlano)
        val combinado = ByteArray(1 + iv.size + cifrado.size)
        combinado[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combinado, 1, iv.size)
        System.arraycopy(cifrado, 0, combinado, 1 + iv.size, cifrado.size)
        return Base64.getEncoder().encodeToString(combinado)
    }

    override fun descifrar(textoCifrado: String): ByteArray? = runCatching {
        val combinado = Base64.getDecoder().decode(textoCifrado)
        if (combinado.size < 2) return null
        val longitudIv = combinado[0].toInt() and 0xFF
        if (longitudIv <= 0 || combinado.size <= 1 + longitudIv) return null
        val iv = combinado.copyOfRange(1, 1 + longitudIv)
        val cuerpo = combinado.copyOfRange(1 + longitudIv, combinado.size)
        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.DECRYPT_MODE, obtenerOCrearLlave(), GCMParameterSpec(BITS_ETIQUETA_GCM, iv))
        cipher.doFinal(cuerpo)
    }.getOrNull()

    @Synchronized
    private fun obtenerOCrearLlave(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVEEDOR_KEYSTORE).apply { load(null) }
        val existente = (keyStore.getEntry(ALIAS_LLAVE, null) as? KeyStore.SecretKeyEntry)?.secretKey
        if (existente != null) return existente

        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR_KEYSTORE)
        val especificacion = KeyGenParameterSpec.Builder(
            ALIAS_LLAVE,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generador.init(especificacion)
        return generador.generateKey()
    }

    private companion object {
        const val PROVEEDOR_KEYSTORE = "AndroidKeyStore"
        const val ALIAS_LLAVE = "co.plataforma.danos.oidc.aes"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val BITS_ETIQUETA_GCM = 128
    }
}
