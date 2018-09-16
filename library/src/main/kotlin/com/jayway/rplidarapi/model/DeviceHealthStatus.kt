package com.jayway.rplidarapi.model

data class DeviceHealthStatus(
        val status: Int = 0,
        val errorCode: Int = 0
) {
    companion object {
        fun from(data: ByteArray): DeviceHealthStatus {
            if (data.size != 3) return DeviceHealthStatus()

            val status = data[0].toInt()
            val errorCode = (data[2].toInt() shl 8) + data[1]
            return DeviceHealthStatus(status, errorCode)
         }
     }
}
