package com.example.danos.infraestructura.dispositivo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.example.danos.dominio.FotoEnCurso
import com.example.danos.dominio.FotoProcesada
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

class ProcesadorFotos @Inject constructor(
    @ApplicationContext private val contexto: Context,
) {

    fun preparar(): FotoEnCurso {
        val carpeta = File(contexto.filesDir, CARPETA).apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val archivo = File(carpeta, "$id.jpg")
        val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.fileprovider", archivo)
        return FotoEnCurso(id = id, rutaArchivo = archivo.absolutePath, uri = uri.toString())
    }

    /** Devuelve null si la imagen no se pudo leer. Hash y tamaño son los del archivo final. */
    suspend fun procesar(foto: FotoEnCurso): FotoProcesada? = withContext(Dispatchers.IO) {
        val archivo = File(foto.rutaArchivo)
        if (!archivo.isFile || archivo.length() == 0L) return@withContext null
        val mapa = decodificarEnderezada(archivo) ?: return@withContext null

        val temporal = File(archivo.parentFile, "${archivo.name}.tmp")
        FileOutputStream(temporal).use { salida ->
            mapa.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)
            salida.fd.sync()
        }
        mapa.recycle()
        if (!temporal.renameTo(archivo)) {
            temporal.delete()
            return@withContext null
        }
        FotoProcesada(sha256 = sha256(archivo), tamanoBytes = archivo.length())
    }

    fun descartar(ruta: String) {
        File(ruta).delete()
    }

    private fun decodificarEnderezada(archivo: File): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.path, limites)
        val ladoMayor = max(limites.outWidth, limites.outHeight)
        if (ladoMayor <= 0) return null

        var muestreo = 1
        while (ladoMayor / (muestreo * 2) >= LADO_MAXIMO) muestreo *= 2
        val decodificado = BitmapFactory.decodeFile(
            archivo.path,
            BitmapFactory.Options().apply { inSampleSize = muestreo },
        ) ?: return null

        val matriz = matrizOrientacion(ExifInterface(archivo.path))
        val mayor = max(decodificado.width, decodificado.height)
        if (mayor > LADO_MAXIMO) {
            val escala = LADO_MAXIMO.toFloat() / mayor
            matriz.postScale(escala, escala)
        }
        if (matriz.isIdentity) return decodificado
        val resultado = Bitmap.createBitmap(
            decodificado, 0, 0, decodificado.width, decodificado.height, matriz, true,
        )
        if (resultado !== decodificado) decodificado.recycle()
        return resultado
    }

    private fun matrizOrientacion(exif: ExifInterface): Matrix = Matrix().apply {
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(270f)
                postScale(-1f, 1f)
            }
        }
    }

    private fun sha256(archivo: File): String {
        val resumen = MessageDigest.getInstance("SHA-256")
        archivo.inputStream().use { entrada ->
            val bufer = ByteArray(TAMANO_BUFER)
            while (true) {
                val leidos = entrada.read(bufer)
                if (leidos < 0) break
                resumen.update(bufer, 0, leidos)
            }
        }
        return resumen.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val CARPETA = "evidencias"
        const val LADO_MAXIMO = 1600
        const val CALIDAD_JPEG = 80
        const val TAMANO_BUFER = 8 * 1024
    }
}
