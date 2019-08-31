package com.ivieleague.skate

import java.io.File
import java.io.PrintStream
import java.util.*

fun main(vararg args: String) {
    if (args.isEmpty()) {
        println("Usage: ")
        println("Run: skate <file> [arguments...]")
        println("Edit: skate -edit <file>")
        println("Refresh Project: skate -project <file>")
        println("Interactive: skate -interactive <file> [-y]")
        println("Single Action: skate -action <file> \"lineOfKotlin()\"")
        println("In addition, you can include -v before the file argument to include verbose information about collecting dependencies.")
        return
    }

    val argsList = args.toList()
    val flags = args.takeWhile { it.startsWith('-') }.map { it.removePrefix("-") }
    val file = argsList.firstOrNull { !it.startsWith('-') }?.let { File(it) } ?: return
    val verboseOutput = if(flags.contains("v")) System.out else PrintStream(NullOutputStream)

    when {
        flags.contains("p") || flags.contains("project") -> {
            if (checkIfFileShouldBeCreated(file, args)) return
            println("Resolving dependencies... (use -v for more info)")
            val fileInfo = Skate.resolve(file, verboseOutput)
            val project = IntelliJ.singleModuleProject(
                sources = fileInfo.sources.distinct().toList(),
                libraries = fileInfo.libraries.distinct().toList(),
                folder = fileInfo.projectFolder,
                mainClass = fileInfo.main
            )
            println("Project refreshed at $project")
        }
        flags.contains("e") || flags.contains("edit") -> {
            if (checkIfFileShouldBeCreated(file, args)) return
            println("Resolving dependencies... (use -v for more info)")
            val fileInfo = Skate.resolve(file, verboseOutput)
            val project = IntelliJ.singleModuleProject(
                sources = fileInfo.sources.distinct().toList(),
                libraries = fileInfo.libraries.distinct().toList(),
                folder = fileInfo.projectFolder,
                mainClass = fileInfo.main
            )
            IntelliJ.launch(project)
            IntelliJ.launch(project.resolve("src/${file.name}"))
        }
        flags.contains("i") || flags.contains("interactive") -> {
            while(true){
                println("Resolving dependencies... (use -v for more info)")
                val skateResult = Skate.getJarsForKt(file, verboseOutput)
                val result = JVM.runInteractive(
                    jars = skateResult.jars,
                    autoImports = skateResult.fileInfo.autoImports
                )
                if(!result) return
            }
        }
        flags.contains("a") || flags.contains("action") -> {
            println("Resolving dependencies... (use -v for more info)")
            val action = args.sliceArray(1..args.lastIndex).joinToString("; ")
            val skateResult = Skate.getJarsForKt(file, verboseOutput)
            JVM.runWithLine(
                jars = skateResult.jars,
                autoImports = skateResult.fileInfo.autoImports,
                line = action
            )
        }
        else -> {
            println("Resolving dependencies... (use -v for more info)")
            val skateResult = Skate.getJarsForKt(file, verboseOutput)
            JVM.runMain(
                jars = skateResult.jars,
                mainClass = skateResult.fileInfo.fileClassName,
                arguments = args.sliceArray(1..args.lastIndex)
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
