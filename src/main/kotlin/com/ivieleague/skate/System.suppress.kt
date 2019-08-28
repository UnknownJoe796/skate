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

inline fun <T> suppressStandardOutputAndError(action: () -> T): T {
    val realErr = System.err
    val realOut = System.out
    val result = try {
        System.setOut(PrintStream(OutputStream.nullOutputStream()))
        System.setErr(PrintStream(OutputStream.nullOutputStream()))
        action()
    } finally {
        System.setErr(realErr)
        System.setOut(realOut)
    }
    return result
}
