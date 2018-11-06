package com.jayway.rplidarapi

import android.hardware.usb.UsbManager
import android.util.Log
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.jayway.rplidarapi.extensions.toHex
import com.jayway.rplidarapi.model.DeviceHealthStatus
import com.jayway.rplidarapi.model.DeviceInfo
import com.jayway.rplidarapi.model.RPLidarCommand
import com.jayway.rplidarapi.model.ScanData
import kotlin.experimental.and
import kotlin.experimental.xor

private val TAG = RPLidarConnector::class.java.simpleName
private const val SYNC_BYTE: Byte = 0xA5.toByte()
private const val RESPONSE_HEADER_SIZE: Int = 7
private const val DEVICE_INFO_DATA_SIZE: Int = 20
private const val DEVICE_HEALTH_STATUS_DATA_SIZE: Int = 3
private const val SCAN_DATA_SIZE_BYTES = 5

class RPLidarConnector constructor(
        usbManager: UsbManager
) {

    private val serialConnection = createSerialConnection(usbManager)

    init {
        startSyncSerialConnection()
    }

    private fun createSerialConnection(usbManager: UsbManager): UsbSerialDevice {
        val device = usbManager.deviceList.values.find { it.vendorId == 0x10c4 && it.productId == 0xea60 }!!
        Log.d(TAG, "Device found: $device")
        val connection = usbManager.openDevice(device)
        return UsbSerialDevice.createUsbSerialDevice(device, connection)!!
    }

    private fun startSyncSerialConnection() {
        if (serialConnection.syncOpen()) {
            serialConnection.setBaudRate(115200)
            serialConnection.setDataBits(UsbSerialInterface.DATA_BITS_8)
            serialConnection.setStopBits(UsbSerialInterface.STOP_BITS_1)
            serialConnection.setParity(UsbSerialInterface.PARITY_NONE)
            serialConnection.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
            serialConnection.setDTR(false)
        }
        stop()
        Thread.sleep(100)
        cleanJunkData()
    }

    fun getDeviceInfo(): DeviceInfo {
        sendCommand(RPLidarCommand.GET_INFO)
        Thread.sleep(100)
        return receiveDeviceInfo()
    }

    fun getDeviceHealthStatus(): DeviceHealthStatus {
        sendCommand(RPLidarCommand.GET_HEALTH)
        Thread.sleep(100)
        return receiveDeviceHealthStatus()
    }

    fun setMotorSpeed(speed: Int) {
        val payload = ByteArray(2)

        //load payload little Endian
        payload[0] = speed.toByte()
        payload[1] = (speed shr 8).toByte()
        sendCommand(RPLidarCommand.START_MOTOR, payload)
        Thread.sleep(500)
    }

    fun startScan() {
        Log.d(TAG, "Starting scan")
        stop()
        sendCommand(RPLidarCommand.SCAN)
        receiveResponseHeader()

        // Throw away first set of data as it may be incomplete
        val data = ByteArray(128 * SCAN_DATA_SIZE_BYTES)
        serialConnection.syncRead(data, 100)
    }

    fun close() {
        stop()
        serialConnection.close()
    }

    fun stop() {
        sendCommand(RPLidarCommand.STOP)
        Thread.sleep(5) // No response is expected. Api recommends waiting for >1 ms
    }

    fun receiveScanData(size: Int = 256): List<ScanData> {
        val localBuffer = mutableListOf<ScanData>()
        val data = ByteArray(size * SCAN_DATA_SIZE_BYTES)
        var readBytes = 0

        while (readBytes == 0) {
            readBytes = serialConnection.syncRead(data, 200)
        }
        var pos = 0
        val bytes = ByteArray(5)

        for (i in 0 until readBytes) {
            if (pos == 0 && startBitAndStartCheckBitAreNotInverse(data[i])) {
                continue
            }
            if (pos == 1 && checkBitIsNotOne(data[i])) {
                pos = 0
                continue
            }
            bytes[pos++] = data[i]

            if (pos == 5) {
                pos = 0
                localBuffer.add(ScanData.from(bytes))
            }
        }
        return localBuffer
    }

    private fun cleanJunkData() {
        Log.d(TAG, "Trying to clean data stream")
        val data = ByteArray(128 * SCAN_DATA_SIZE_BYTES)
        val bytes = serialConnection.syncRead(data, 100)
        Log.d(TAG, "Cleanup result: $bytes bytes")
    }

    private fun startBitAndStartCheckBitAreNotInverse(byte: Byte) = ((byte and 2).toInt() shr 1) == (byte and 1).toInt()
    private fun checkBitIsNotOne(byte: Byte) = (byte and 0x1 != 0x1.toByte())

    private fun sendCommand(command: RPLidarCommand) {
        sendHeader(command)
    }

    private fun sendCommand(command: RPLidarCommand, payload: ByteArray) {
        sendHeader(command)
        sendPayloadSize(payload)
        sendPayload(payload)
        sendPayloadChecksum(command, payload)
    }

    private fun sendHeader(command: RPLidarCommand) {
        val header = byteArrayOf(SYNC_BYTE, command.byte)
        serialConnection.syncWrite(header, 100)
    }

    private fun sendPayloadSize(payload: ByteArray) {
        val payloadSize = payload.size.toByte()
        serialConnection.syncWrite(byteArrayOf(payloadSize), 100)
    }

    private fun sendPayload(payload: ByteArray) {
        serialConnection.syncWrite(payload, 100)
    }

    private fun sendPayloadChecksum(command: RPLidarCommand, payload: ByteArray) {
        var checksum: Byte = 0
        checksum = checksum xor SYNC_BYTE
        checksum = checksum xor command.byte
        checksum = checksum xor (payload.size.toByte() and 0xFF.toByte())
        payload.forEach {
            checksum = checksum xor it
        }

        serialConnection.syncWrite(byteArrayOf(checksum), 100)
    }

    private fun receiveDeviceInfo(): DeviceInfo {
        val data = ByteArray(RESPONSE_HEADER_SIZE + DEVICE_INFO_DATA_SIZE)
        serialConnection.syncRead(data, 100)
        Log.d(TAG, "Received response: ${data.joinToString("") { it.toHex() }}")
        return DeviceInfo.from(data.copyOfRange(RESPONSE_HEADER_SIZE, data.size))
    }

    private fun receiveDeviceHealthStatus(): DeviceHealthStatus {
        val data = ByteArray(RESPONSE_HEADER_SIZE + DEVICE_HEALTH_STATUS_DATA_SIZE)
        serialConnection.syncRead(data, 100)
        Log.d(TAG, "Received response: ${data.joinToString("") { it.toHex() }}")
        return DeviceHealthStatus.from(data.copyOfRange(RESPONSE_HEADER_SIZE, data.size))
    }

    private fun receiveResponseHeader() {
        val data = ByteArray(RESPONSE_HEADER_SIZE)
        serialConnection.syncRead(data, 100)
        Log.d(TAG, "Received response: ${data.joinToString("") { it.toHex() }}")
    }
}