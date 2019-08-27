package com.ivieleague.standalonekotlin

import lk.kotlin.okhttp.lambdaString
import okhttp3.Request
import org.eclipse.aether.repository.RemoteRepository
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import java.io.File
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue

object Skate {

    data class JarsResult(
        val fileInfo: Result,
        val jars: List<File>
    )

    val rootFolder = File(System.getProperty("user.home")).resolve(".skate")
    val buildFolder = rootFolder.resolve("build")
    val projectFolder = rootFolder.resolve("project")
    val cacheFolder = rootFolder.resolve("cache")

    init {
        rootFolder.mkdirs()
        buildFolder.mkdir()
        projectFolder.mkdir()
        cacheFolder.mkdir()
    }

    const val CACHE_TIME = 1000L * 60L * 60L //One hour

    fun annotationStubsFile(forPackage: String?): File {
        val file = rootFolder.resolve("FileAnnotations_${forPackage?.replace('.', '_') ?: "default"}.kt")
        if (!file.exists()) {
            val packageLine = forPackage?.let { "package $it" } ?: ""
            file.writeText(
                """
$packageLine

@Target(AnnotationTarget.FILE)
@Repeatable
annotation class Repository(val url: String)

@Target(AnnotationTarget.FILE)
@Repeatable
annotation class DependsOn(val maven: String)

@Target(AnnotationTarget.FILE)
@Repeatable
annotation class Import(val file: String)
"""
            )
        }
        return file
    }

    fun resolveRemoteFile(url: String): File {
        if (!url.startsWith("https"))
            throw SecurityException("For safety purposes, you cannot include a source file from an insecure URL.")
        val now = System.currentTimeMillis()
        val file =
            Skate.cacheFolder.resolve(url.substringAfterLast('/').filter { it.isJavaIdentifierPart() }.removeSuffix("kt").plus(
                ".kt"
            )
            )
        val timePrefix = "//System.currentTimeMillis() == "
        if (file.exists()) {
            val lastLoaded = file.useLines { sequence ->
                sequence
                    .firstOrNull { it.startsWith(timePrefix) }
                    ?.removePrefix(timePrefix)
                    ?.toLongOrNull()
            } ?: 0L
            if (now < lastLoaded + CACHE_TIME) {
                println("Using cached $url")
                return file
            }
        }
        println("Obtaining $url")
        val rawTextResult = Request.Builder().get().url(url).lambdaString().invoke()
        if (rawTextResult.isSuccessful()) {
            val fullText = "//Imported by Skate at ${DateFormat.getDateTimeInstance().format(Date(now))}\n" +
                    "$timePrefix${now}\n" +
                    rawTextResult.result!!
            file.writeText(fullText)
            return file
        } else if (file.exists()) {
            println("WARNING: Could not reload '$url', got code ${rawTextResult.code}")
            return file
        } else {
            throw IllegalStateException("Could not load '$url', got code ${rawTextResult.code}.")
        }

    }

    fun getJarsForKt(file: File): JarsResult {
        val fileInfo = getKtInfo(file)
        val libraries = fileInfo.libraries
        val compiled = Kotlin.compileJvm(
            source = fileInfo.sources,
            withLibraries = libraries.map { it.default },
            buildFolder = fileInfo.buildFolder,
            out = fileInfo.buildFolder.resolve("output")
        )
        println(compiled.messages.filter { it.severity >= CompilerMessageSeverity.STRONG_WARNING }.joinToString("\n") { it.message + "\n at " + it.location })
        return JarsResult(jars = libraries.map { it.default } + compiled.output, fileInfo = fileInfo)
    }

    data class Result(
        val file: File,
        val packageName: String?,
        val repositories: List<String>,
        val dependsOn: List<String>,
        val includes: List<String>
    ) {
        val buildFolder
            get() = Skate.buildFolder.resolve(
                "${file.nameWithoutExtension}-${file.absolutePath.hashCode().absoluteValue.toString(
                    32
                )}"
            )
        val projectFolder
            get() = Skate.projectFolder.resolve(
                "${file.nameWithoutExtension}-${file.absolutePath.hashCode().absoluteValue.toString(
                    32
                )}"
            )
        val fileClassName: String
            get() = (packageName?.let { it + "." } ?: "") + file.nameWithoutExtension.capitalize() + "Kt"
        val sources: List<File>
            get() {
                println("Collecting sources...")
                return listOf(file) + includes.map {
                    if (it.startsWith("http")) {
                        resolveRemoteFile(it)
                    } else {
                        file.resolve(it)
                    }
                } + annotationStubsFile(packageName)
            }
        val libraries: List<Library>
            get() {
                return Maven.libraries(
                    repositories = repositories.map {
                        RemoteRepository.Builder(it.replace(Regex("[:/.]+"), "_"), "default", it).build()
                    } + listOf(Maven.central, Maven.jcenter, Maven.google, Maven.local),
                    dependencies = listOf(Maven.compile(Maven.kotlinStandardLibrary)) + dependsOn.map { Maven.compile(it) }
                )
            }
    }

    fun getKtInfo(file: File): Result {
        val repStart = "@file:Repository(\""
        val depStart = "@file:DependsOn(\""
        val incStart = "@file:Import(\""
        var packageName: String? = null
        val repositories = ArrayList<String>()
        val dependsOn = ArrayList<String>()
        val includes = ArrayList<String>()
        file.useLines { lines ->
            lines
                .map { it.trim() }
                .forEach {
                    when {
                        it.startsWith(repStart) -> {
                            repositories.add(it.substringAfter(repStart).substringBeforeLast('"'))
                        }
                        it.startsWith(depStart) -> {
                            dependsOn.add(it.substringAfter(depStart).substringBeforeLast('"'))
                        }
                        it.startsWith(incStart) -> {
                            includes.add(it.substringAfter(incStart).substringBeforeLast('"'))
                        }
                        it.startsWith("package") -> {
                            packageName = it.substringAfter("package ")
                        }
                    }
                }
        }
        return Result(file, packageName, repositories, dependsOn, includes)
    }
}