package com.jayway.rplidarapi.sample

import android.app.Activity

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import com.jayway.rplidarapi.RPLidarService

class MainActivity : Activity() {

    val TAG = "SampleRPLidarActivity"
    lateinit var rpLidarService: RPLidarService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val usbManager = this.getSystemService(Context.USB_SERVICE) as UsbManager
        rpLidarService = RPLidarService(usbManager)

        Log.i(TAG, "Fetching RPLidar device health status")
        val deviceHealthStatus = rpLidarService.getDeviceHealthStatus()
        Log.i(TAG, "$deviceHealthStatus")
        Log.i(TAG, "Fetching RPLidar device info")
        val deviceInfo = rpLidarService.getDeviceInfo()
        Log.i(TAG, "$deviceInfo")

//        scanSingle()
        scanContinuous()
    }


    private fun scanSingle() {
        Log.i(TAG, "Starting RPLidar motor")
        rpLidarService.setMotorSpeed(500)

        Log.i(TAG, "Reading RPLidar scan data")
        val scanData = rpLidarService.getSingleScanData()
        Log.i(TAG, "Stopping RPLidar motor")
        rpLidarService.setMotorSpeed(0)

        scanData.forEach {
            Log.i(TAG, String.format("Angle: %3.2f - Distance: %4.2f", it.angle, it.distance))
        }
        Log.i(TAG, "Received ${scanData.size} scan data nodes")
    }


    private fun scanContinuous() {
        Log.i(TAG, "Starting RPLidar motor")
        rpLidarService.setMotorSpeed(500)

        Log.i(TAG, "Reading RPLidar scan data")
        rpLidarService.startContinuousScan { scanData ->
            Log.i(TAG, "Received ${scanData.size} scan data nodes")
        }

        Thread.sleep(50000)
        Log.i(TAG, "Stopping RPLidar scan")
        rpLidarService.stopScan()

        Log.i(TAG, "Stopping RPLidar motor")
        rpLidarService.setMotorSpeed(0)
    }

    override fun onDestroy() {
        super.onDestroy()
        rpLidarService.close()
    }
}
