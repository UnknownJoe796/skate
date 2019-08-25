package com.ivieleague.standalonekotlin

import java.io.File

fun main(vararg args: String) {
    //skate test.skt
    //skate -edit test.skt
    //skate test.skt arg0 arg1 arg2 ...
    //skate -interactive test.skt
    //skate -action test.skt "build()"
    if (args.size == 0) {
        println("Usage: ")
        println("Run: skate [file] [arguments...]")
        println("Edit: skate -edit [file]")
        println("Refresh Project: skate -project [file]")
        println("Interactive: skate -interactive [file]")
        println("Single Action: skate -action [file] \"lineOfKotlin()\"")
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
            val fileInfo = Skate.getKtInfo(file)
            val project = IntelliJ.singleModuleProject(
                sources = fileInfo.sources,
                libraries = fileInfo.libraries,
                folder = fileInfo.projectFolder
            )
            println("Project refreshed at $project")
        }
        "e", "edit" -> {
            val file = File(args[1])
            val fileInfo = Skate.getKtInfo(file)
            val project = IntelliJ.singleModuleProject(
                sources = fileInfo.sources,
                libraries = fileInfo.libraries,
                folder = fileInfo.projectFolder
            )
            IntelliJ.launch(project)
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