package com.jayway.rplidarapi.model

import com.jayway.rplidarapi.extensions.toPositiveInt
import kotlin.experimental.and

data class ScanData(
        val quality: Int = 0,
        val startBitSet: Boolean = false,
        val angle: Double = 0.0,
        val distance: Double = 0.0
) {

    companion object {
        fun from(b0: Byte, b1: Byte, b2: Byte, b3: Byte, b4: Byte): ScanData {
            val quality: Int = b0.toPositiveInt() shr 2
            val startBitSet: Boolean = (b0 and 0x1.toByte()) == 0x1.toByte()
            val angle: Double = (((b1.toPositiveInt() shr 1) + (b2.toPositiveInt() shl 7)) / 64.0)
            val distance: Double = ((b3 + (b4.toPositiveInt() shl 8)) / 4.0)
            return ScanData(quality, startBitSet, angle, distance)
        }

        fun from(data: ByteArray): ScanData {
            if (data.size != 5) return ScanData()
            return from(data[0], data[1], data[2], data[3], data[4])
        }
    }
}
