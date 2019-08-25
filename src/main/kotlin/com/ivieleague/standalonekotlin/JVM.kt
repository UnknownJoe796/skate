package com.ivieleague.standalonekotlin

import java.io.File
import java.lang.reflect.Method
import java.net.URLClassLoader
import java.util.*
import javax.script.ScriptException

object JVM {
    class JarFileLoader() : URLClassLoader(arrayOf()) {
        fun addFile(file: File) {
            addURL(file.toURI().toURL())
        }
    }

    val jarLoader = JarFileLoader()

    fun runMain(jars: List<File>, mainClass: String, arguments: Array<*>) {
        for (jar in jars) {
            jarLoader.addFile(jar)
        }
        jarLoader.loadClass(mainClass).let {
            it.getDeclaredMethodOrNull("main", Array<Any?>::class.java)
                ?.apply { invoke(jarLoader, *arguments) } ?: it.getDeclaredMethodOrNull("main")
                ?.apply { invoke(jarLoader) }
            ?: println("Could not find main function.")
        }
    }

    fun runWithLine(jars: List<File>, autoImport: String? = null, line: String) {
        for (jar in jars) {
            jarLoader.addFile(jar)
        }

        val engine = run {
            val e = Kotlin.engine(jarLoader)

            autoImport?.let {
                try {
                    println("import $it.*")
                    e.eval("import $it.*")
                } catch (e: Exception) {

                }
            }

            e
        }
        try {
            println(engine.eval(line))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun runInteractive(jars: List<File>, autoImport: String? = null) {
        for (jar in jars) {
            jarLoader.addFile(jar)
        }

        val engine = run {
            val e = Kotlin.engine(jarLoader)

            autoImport?.let {
                try {
                    println("import $it.*")
                    e.eval("import $it.*")
                } catch (e: Exception) {

                }
            }

            e
        }

        println("Ready.  Type your commands below or type 'exit' to quit.")
        println()

        val scanner = Scanner(System.`in`)
        while (true) {
            print("> ")
            System.out.flush()
            val line = scanner.nextLine()
            if (line == "exit" || line == "exit()") break
            try {
                val result = engine.eval(line)
                when (result) {
                    is Unit -> {
                    }
                    null -> println("null")
                    else -> println(result.toString())
                }
                println()
            } catch (e: ScriptException) {
                println("Syntax Error: ${e.message}")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun Class<*>.getDeclaredMethodOrNull(name: String, vararg parameterTypes: Class<*>): Method? = try {
        getDeclaredMethod(name, *parameterTypes)
    } catch (e: NoSuchMethodException) {
        null
    }
}