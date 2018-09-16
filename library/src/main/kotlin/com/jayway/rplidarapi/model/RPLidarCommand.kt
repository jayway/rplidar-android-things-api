package com.jayway.rplidarapi.model

enum class RPLidarCommand(val byte: Byte) {
    SCAN(0x20),
    EXPRESS_SCAN(0x82.toByte()),
    STOP(0x25),
    GET_INFO(0x50),
    GET_HEALTH(0x52),
    START_MOTOR(0xF0.toByte())
}