package com.jayway.rplidarapi

import android.hardware.usb.UsbManager
import com.jayway.rplidarapi.model.DeviceHealthStatus
import com.jayway.rplidarapi.model.DeviceInfo
import com.jayway.rplidarapi.model.ScanData
import java.util.concurrent.atomic.AtomicBoolean


private const val MAX_MOTOR_PWM = 1023

class RPLidarService constructor(usbManager: UsbManager) {

    private val connector = RPLidarConnector(usbManager)
    private val isScanning = AtomicBoolean(false)


    fun getDeviceInfo(): DeviceInfo {
        return connector.getDeviceInfo()
    }

    fun getDeviceHealthStatus(): DeviceHealthStatus {
        return connector.getDeviceHealthStatus()
    }

    fun setMotorSpeed(speed: Int) {
        if (speed > MAX_MOTOR_PWM || speed < 0) {
            throw IllegalArgumentException("Invalid motor speed: $speed. Please specify a value between 0 and $MAX_MOTOR_PWM")
        }
        connector.setMotorSpeed(speed)
    }

    fun stopScan() {
        isScanning.set(false)
        connector.stop()
    }

    fun getSingleScanData(): List<ScanData> {
        connector.startScan()

        val scan = mutableListOf<ScanData>()

        loop@ while (scan.size < 8192) {
            val data = connector.receiveScanData()
            for (i in 0 until data.size) {
                if (scan.isEmpty() && !data[i].startBitSet) continue
                if (!scan.isEmpty() && data[i].startBitSet) break@loop
                scan.add(data[i])
            }
        }
        connector.stop()
        return scan.sortedBy { it.angle }
    }

    fun startContinuousScan(responseHandler: (scanData: List<ScanData>) -> Unit) {
        if (isScanning.get()) return
        connector.startScan()

        Thread {
            isScanning.set(true)
            var scan = mutableListOf<ScanData>()
            while (isScanning.get()) {
                val data = connector.receiveScanData()
                for (i in 0 until data.size) {
                    if (scan.isEmpty() && !data[i].startBitSet) continue
                    if (!scan.isEmpty() && data[i].startBitSet) {
                        responseHandler.invoke(scan.sortedBy { it.angle })
                        scan = mutableListOf()
                    }
                    scan.add(data[i])
                    if (scan.size > 8192) scan = mutableListOf()
                }
            }
        }.start()
    }

    fun close() {
        isScanning.set(false)
        connector.close()
    }

}