package com.jayway.rplidarapi

import android.hardware.usb.UsbManager
import com.jayway.rplidarapi.model.ScanData
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_MOTOR_PWM = 1023

class RPLidarService constructor(private val usbManager: UsbManager) {

    private var connector: RPLidarConnector? = null
    private val isScanning = AtomicBoolean(false)
    private var scanningThread: Thread? = null

    fun connect() {
        if (connector == null) connector = RPLidarConnector(usbManager)
    }
    
    fun getDeviceInfo() = connector?.getDeviceInfo()
    fun getDeviceHealthStatus() = connector?.getDeviceHealthStatus()

    fun setMotorSpeed(speed: Int) {
        if (speed > MAX_MOTOR_PWM || speed < 0) {
            throw IllegalArgumentException("Invalid motor speed: $speed. Please specify a value between 0 and $MAX_MOTOR_PWM")
        }
        connector?.setMotorSpeed(speed)
    }

    fun stopScan() {
        isScanning.set(false)
        connector?.stop()
    }

    fun getSingleScanData(): List<ScanData> {
        connector?.startScan()

        val scan = mutableListOf<ScanData>()

        loop@ while (scan.size < 8192) {
            val data = connector?.receiveScanData() ?: emptyList()
            for (i in 0 until data.size) {
                if (scan.isEmpty() && !data[i].startBitSet) continue
                if (!scan.isEmpty() && data[i].startBitSet) break@loop
                scan.add(data[i])
            }
        }
        connector?.stop()
        return scan.sortedBy { it.angle }
    }

    fun startContinuousScan(responseHandler: (scanData: List<ScanData>) -> Unit) {
        if (isScanning.get()) return
        connector?.startScan()

        scanningThread = Thread {
            isScanning.set(true)
            while (isScanning.get()) {
                val scan = mutableListOf<ScanData>()
                // Fetch multiple sets of scan data in case some of it is corrupted
                scan.addAll(receiveScanData())
                scan.addAll(receiveScanData())
                scan.addAll(receiveScanData())
                scan.addAll(receiveScanData())
                responseHandler.invoke(scan.sortedBy { it.angle })
            }
        }
        scanningThread?.start()
    }

    private fun receiveScanData(): List<ScanData> {
        return if (isScanning.get()) connector?.receiveScanData() ?: emptyList()
        else emptyList()
    }

    fun close() {
        isScanning.set(false)
        scanningThread?.join()
        connector?.setMotorSpeed(0)
        connector?.close()
        connector = null
    }

}