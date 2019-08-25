package com.ivieleague.standalonekotlin

import java.io.OutputStream
import java.io.PrintStream

inline fun <T> suppressStandardError(action: () -> T): T {
    val realOut = System.err
    System.setErr(PrintStream(OutputStream.nullOutputStream()))
    val result = action()
    System.setErr(realOut)
    return result
}

inline fun <T> suppressStandardOutput(action: () -> T): T {
    val realOut = System.out
    System.setOut(PrintStream(OutputStream.nullOutputStream()))
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