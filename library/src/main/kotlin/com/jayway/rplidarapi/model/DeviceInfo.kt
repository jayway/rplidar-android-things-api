package com.jayway.rplidarapi.model

import com.jayway.rplidarapi.extensions.toHex

data class DeviceInfo(
        val model: String = "",
        val firmwareVersion: String = "",
        val hardware: String = "",
        val serialNumber: String = ""
) {
    companion object {

        fun from(data: ByteArray): DeviceInfo {
            if (data.size != 20) return DeviceInfo()

            val model = data[0].toHex()
            val firmwareVersion = "${data[2].toInt()}.${data[1].toInt()}"
            val hardware = data[3].toInt().toString()
            val serialNumber = data.copyOfRange(4, data.size).toHex()
            return DeviceInfo(model, firmwareVersion, hardware, serialNumber)
        }
    }
}
