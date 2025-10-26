package space.ring0.airheadwaves

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import space.ring0.airheadwaves.models.NodeType
import space.ring0.airheadwaves.models.PairingData
import java.util.UUID

/**
 * QR Code utilities for device pairing
 */
object QRCodeUtils {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Generate pairing data for QR code
     */
    fun generatePairingData(
        brokerIp: String,
        brokerPort: Int = 8883,
        nodeId: String,
        nodeType: NodeType,
        expiryMinutes: Int = 5
    ): PairingData {
        val apiKey = UUID.randomUUID().toString()
        val expiryTime = System.currentTimeMillis() + (expiryMinutes * 60 * 1000)

        return PairingData(
            protocol = "mqtt",
            brokerIp = brokerIp,
            brokerPort = brokerPort,
            apiKey = apiKey,
            nodeId = nodeId,
            nodeType = nodeType,
            pairingExpiry = expiryTime
        )
    }

    /**
     * Generate QR code bitmap from pairing data
     */
    fun generateQRCodeBitmap(pairingData: PairingData, size: Int = 512): Bitmap? {
        return try {
            val jsonString = json.encodeToString(pairingData)
            generateQRCodeFromString(jsonString, size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generate QR code bitmap from any string
     */
    fun generateQRCodeFromString(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1
            )

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) {
                        0xFF000000.toInt()  // Black
                    } else {
                        0xFFFFFFFF.toInt()  // White
                    }
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parse pairing data from QR code JSON string
     */
    fun parsePairingData(jsonString: String): PairingData? {
        return try {
            val data = json.decodeFromString<PairingData>(jsonString)

            // Check if pairing has expired
            if (System.currentTimeMillis() > data.pairingExpiry) {
                null  // Expired
            } else {
                data
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Validate pairing data
     */
    fun validatePairingData(pairingData: PairingData): Boolean {
        // Check expiry
        if (System.currentTimeMillis() > pairingData.pairingExpiry) {
            return false
        }

        // Check required fields
        if (pairingData.brokerIp.isBlank() ||
            pairingData.apiKey.isBlank() ||
            pairingData.nodeId.isBlank()) {
            return false
        }

        // Check port range
        if (pairingData.brokerPort !in 1..65535) {
            return false
        }

        return true
    }
}
