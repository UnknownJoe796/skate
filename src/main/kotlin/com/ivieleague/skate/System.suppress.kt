package com.ivieleague.skate

import java.io.OutputStream
import java.io.PrintStream

class NullOutputStream() : OutputStream() {
    override fun write(b: Int) {
    }

    override fun write(b: ByteArray) {
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
    }

    override fun flush() {
    }

    override fun close() {
    }
}

inline fun <T> suppressStandardError(action: () -> T): T {
    val realOut = System.err
    System.setErr(PrintStream(NullOutputStream()))
    val result = action()
    System.setErr(realOut)
    return result
}

inline fun <T> suppressStandardOutput(action: () -> T): T {
    val realOut = System.out
    System.setOut(PrintStream(NullOutputStream()))
    val result = action()
    System.setOut(realOut)
    return result
}

inline fun <T> suppressStandardOutputAndError(action: () -> T): T {
    val realErr = System.err
    val realOut = System.out
//    System.setOut(PrintStream(OutputStream.nullOutputStream()))
//    System.setErr(PrintStream(OutputStream.nullOutputStream()))
    val result = action()
//    System.setErr(realErr)
//    System.setOut(realOut)
    return result
}
