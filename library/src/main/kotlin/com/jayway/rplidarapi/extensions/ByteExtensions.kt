package com.jayway.rplidarapi.extensions

fun ByteArray.toHex() = map { String.format("%02X", it) }.joinToString("")

fun Byte.toHex() = String.format("%02X", this)

fun Byte.toBinaryString() = String.format("%8s", Integer.toBinaryString(toInt() and 0xFF)).replace(" ", "0")

fun Byte.toPositiveInt() = toInt() and 0xFF