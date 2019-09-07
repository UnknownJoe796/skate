package com.ivieleague.skate

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

fun File.createSymbolicLinkTo(other: File): Boolean {
    return try {
        println("Linking $this <==> $other")
        Files.createSymbolicLink(this.absoluteFile.toPath(), other.absoluteFile.toPath())

        true
    } catch (e: Exception) {
        if (System.getProperty("os.name").contains("win", true)) {
            val batLoc = File(System.getProperty("user.home")).resolve(".temp/make_sym_link.bat").also {
                if (!it.exists()) {
                    it.parentFile.mkdirs()
                    it.writeText("mklink %1 %2")
                }
            }
            ProcessBuilder()
                .command(batLoc.absolutePath, this.absolutePath, other.absolutePath)
//                .inheritIO()
                .start()
                .waitFor(100L, TimeUnit.MILLISECONDS)
            return true
        }

        false
    }
}
