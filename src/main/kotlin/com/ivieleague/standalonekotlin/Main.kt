package com.ivieleague.standalonekotlin

import java.io.File
import java.util.*

fun main(vararg args: String) {
    if (args.isEmpty()) {
        println("Usage: ")
        println("Run: skate <file> [arguments...]")
        println("Edit: skate -edit <file>")
        println("Refresh Project: skate -project <file>")
        println("Interactive: skate -interactive <file> [-y]")
        println("Single Action: skate -action <file> \"lineOfKotlin()\"")
        return
    }

    val flag = args[0].toLowerCase().takeIf { it.startsWith('-') }?.removePrefix("-")

    when (flag) {
        null -> {
            val file = File(args[0])
            val skateResult = Skate.getJarsForKt(file)
            JVM.runMain(
                jars = skateResult.jars,
                mainClass = skateResult.fileInfo.fileClassName,
                arguments = args.sliceArray(1..args.lastIndex)
            )
        }
        "p", "project" -> {
            val file = File(args[1])
            if (checkIfFileShouldBeCreated(file, args)) return
            val fileInfo = Skate.getKtInfo(file)
            val project = IntelliJ.singleModuleProject(
                sources = fileInfo.sources,
                libraries = fileInfo.libraries,
                folder = fileInfo.projectFolder,
                mainClass = fileInfo.main
            )
            println("Project refreshed at $project")
        }
        "e", "edit" -> {
            val file = File(args[1])
            if (checkIfFileShouldBeCreated(file, args)) return
            val fileInfo = Skate.getKtInfo(file)
            val project = IntelliJ.singleModuleProject(
                sources = fileInfo.sources,
                libraries = fileInfo.libraries,
                folder = fileInfo.projectFolder,
                mainClass = fileInfo.main
            )
            IntelliJ.launch(project)
            IntelliJ.launch(project.resolve("src/${file.name}"))
        }
        "i", "interactive" -> {
            val file = File(args[1])
            val skateResult = Skate.getJarsForKt(file)
            JVM.runInteractive(
                jars = skateResult.jars,
                autoImport = skateResult.fileInfo.packageName
            )
        }
        "a", "action" -> {
            val file = File(args[1])
            val action = args.sliceArray(1..args.lastIndex).joinToString("; ")
            val skateResult = Skate.getJarsForKt(file)
            JVM.runWithLine(
                jars = skateResult.jars,
                autoImport = skateResult.fileInfo.packageName,
                line = action
            )
        }
    }
}

private fun checkIfFileShouldBeCreated(file: File, args: Array<out String>): Boolean {
    if (!file.exists()) {
        if (args.contains("-y") || args.contains("-Y")) {
            file.createNewFile()
        } else {
            val scanner = Scanner(System.`in`)
            println("The file '$file' does not exist.  Do you wish to create it? [y/n]")
            label@ while (true) {
                when (scanner.nextLine()) {
                    "y", "Y", "yes", "YES" -> {
                        file.createNewFile()
                        break@label
                    }
                    "n", "N", "no", "NO" -> {
                        return true
                    }
                    else -> {
                        //Repeat
                    }
                }
            }
        }
    }
    return false
}
