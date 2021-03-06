package com.ivieleague.skate

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository
import java.io.File
import java.io.PrintStream
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

    fun runMain(jars: List<File>, mainClass: String, arguments: Array<*>) {
        val jarLoader = JarFileLoader()
        for (jar in jars) {
            jarLoader.addFile(jar)
        }
        jarLoader.loadClass(mainClass).let {
            it.getDeclaredMethodOrNull("main", Array<String>::class.java)
                ?.apply { invoke(jarLoader, arguments) }
                ?: it.methods.filter { it.name == "main" }
                    .filter { it.parameters.size == arguments.size }
                    .firstOrNull()
                    ?.also {
                        val params = it.parameters.mapIndexed { index, parameter ->
                            val raw = arguments[index]
                            if (raw is String) {
                                when (parameter.type) {
                                    Byte::class.java -> raw.toByteOrNull()
                                    Short::class.java -> raw.toShortOrNull()
                                    Int::class.java -> raw.toIntOrNull()
                                    Long::class.java -> raw.toLongOrNull()
                                    Float::class.java -> raw.toFloatOrNull()
                                    Double::class.java -> raw.toDoubleOrNull()
                                    Boolean::class.java -> raw.toBoolean()
                                    Char::class.java -> raw.firstOrNull()
                                    String::class.java -> raw
                                    File::class.java -> File(raw)
                                    else -> raw
                                }
                            } else raw
                        }
                        it.invoke(jarLoader, *params.toTypedArray())
                    }
                ?: println("Could not find main function.")
        }
    }

    fun runWithLine(jars: List<File>, autoImports: List<String> = listOf(), line: String) {
        val jarLoader = JarFileLoader()
        for (jar in jars) {
            jarLoader.addFile(jar)
        }

        val engine = loadEngine(jarLoader, autoImports)
        try {
            println(engine.eval(line))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun runInteractive(
        jars: List<File>,
        autoImports: List<String> = listOf(),
        repositories: List<RemoteRepository> = Maven.defaultRepositories,
        originalFile: File = File("")
    ): Boolean {
        val jarLoader = JarFileLoader()
        for (jar in jars) {
            jarLoader.addFile(jar)
        }

        var engine = loadEngine(jarLoader, autoImports)

        println("Ready.  Type your commands below or type 'exit' to quit.")
        System.out.flush()

        val scanner = Scanner(System.`in`)
        loop@ while (true) {
            println()
            print("> ")
            System.out.flush()
            val line = scanner.nextLine()
            when (line.substringBefore(' ').substringBefore('(')) {
                "exit", "exit()", ":q", ":quit" -> return false
                ":dependsOn", "@file:DependsOn", "@DependsOn" -> {
                    val arg = getDirectiveArgument(line)
                    try {
                        val loaded =
                            Maven.libraries(repositories, listOf(Dependency(DefaultArtifact(arg), "compile", false)))
                        for (lib in loaded) {
                            jarLoader.addFile(lib.default)
                        }
                        println("Successfully loaded $arg.")
                    } catch (e: Exception) {
                        println("Error: ${e.message}")
                    }
                    engine = loadEngine(jarLoader, autoImports)
                }
                ":import", "@file:Import", "@Import" -> {
                    val arg = getDirectiveArgument(line)
                    try {
                        val file = if (arg.startsWith("http")) {
                            Skate.resolveRemoteFile(arg, PrintStream(System.out))
                        } else {
                            originalFile.resolve(arg)
                        }
                        Skate.getJarsForKt(file, PrintStream(System.out)).jars.forEach {
                            jarLoader.addFile(it)
                        }
                    } catch (e: Exception) {
                        println("Error: ${e.message}")
                    }
                    engine = loadEngine(jarLoader, autoImports)
                }
                ":reload" -> {
                    return true
                }
                else -> {
                    try {
                        val result = engine.eval(line)
                        when (result) {
                            is Unit -> {
                            }
                            null -> println("null")
                            else -> println(result.toString())
                        }
                    } catch (e: ScriptException) {
                        println("Syntax Error: ${e.message}")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun loadEngine(jarLoader: JarFileLoader, autoImports: List<String>) = suppressStandardOutputAndError {
        val e = Kotlin.engine(jarLoader)

        for (imp in autoImports) {
            try {
                e.eval("import $imp")
            } catch (e: Exception) {

            }
            println("import $imp")
        }

        e
    }

    fun getDirectiveArgument(string: String): String = when {
        string.startsWith('@') -> string.substringAfter('"').substringBeforeLast('"')
        string.startsWith(':') -> string.substringAfter(' ').trim()
        else -> ""
    }

    fun Class<*>.getDeclaredMethodOrNull(name: String, vararg parameterTypes: Class<*>): Method? = try {
        getDeclaredMethod(name, *parameterTypes)
    } catch (e: NoSuchMethodException) {
        null
    }
}