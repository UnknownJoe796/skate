package com.ivieleague.skate

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
        val file = rootFolder.resolve("FileAnnotations2_${forPackage?.replace('.', '_') ?: "default"}.kt")
        if (!file.exists()) {
            val packageLine = forPackage?.let { "package $it" } ?: ""
            file.writeText(
                """
$packageLine

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FILE)
@Repeatable
annotation class Repository(val url: String)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FILE)
@Repeatable
annotation class DependsOn(val maven: String)

@Retention(AnnotationRetention.SOURCE)
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
            Skate.cacheFolder.resolve(
                url.substringAfterLast('/').filter { it.isJavaIdentifierPart() }.removeSuffix("kt").plus(
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

    data class JarsResult(
        val fileInfo: FullResult,
        val jars: List<File>
    )

    fun getJarsForKt(file: File): JarsResult {
        val fileInfo = resolve(file)
        val libraries = fileInfo.libraries.map { it.default }.toList()
        val compiled = Kotlin.compileJvm(
            source = fileInfo.sources.distinct().toList(),
            withLibraries = libraries,
            buildFolder = fileInfo.buildFolder,
            out = fileInfo.buildFolder.resolve("output")
        )
        println(compiled.messages.filter { it.severity <= CompilerMessageSeverity.ERROR }.joinToString("\n") { it.message + "\n at " + it.location })
        return JarsResult(jars = libraries + compiled.output, fileInfo = fileInfo)
    }

    data class FullResult(
        val file: File,
        val hasMain: Boolean,
        val packageName: String?,
        val dependsOn: List<Library>,
        var includes: List<FullResult> = listOf(),
        val imports: List<String>
    ) {
        val sources: Sequence<File> get() = sequenceOf(file, annotationStubsFile(packageName)) + includes.asSequence().flatMap { it.sources }
        val libraries: Sequence<Library> get() = dependsOn.asSequence() + includes.asSequence().flatMap { it.libraries }

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
        val main: String?
            get() = if (hasMain) fileClassName else null
        val autoImports: List<String> get() = imports + listOf(packageName + ".*")
    }

    fun resolve(file: File): FullResult {
        val repStart = "@file:Repository(\""
        val depStart = "@file:DependsOn(\""
        val incStart = "@file:Import(\""
        var packageName: String? = null
        var hasMain: Boolean = false
        val repositories = ArrayList<String>()
        val dependsOn = ArrayList<String>()
        val includes = ArrayList<String>()
        val imports = ArrayList<String>()
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
                        it.startsWith("import ") -> {
                            imports.add(it.substringAfter("import "))
                        }
                        it.startsWith("fun main(") -> {
                            hasMain = true
                        }
                    }
                }
        }
        val includedFiles = includes.map {
            if (it.startsWith("http")) {
                resolveRemoteFile(it)
            } else {
                file.parentFile.resolve(it)
            }
        }
        return FullResult(
            file = file,
            hasMain = hasMain,
            packageName = packageName,
            dependsOn = Maven.libraries(
                repositories = repositories.map {
                    RemoteRepository.Builder(it.replace(Regex("[:/.]+"), "_"), "default", it).build()
                } + listOf(Maven.central, Maven.jcenter, Maven.google, Maven.local),
                dependencies = listOf(Maven.compile(Maven.kotlinStandardLibrary)) + dependsOn.map { Maven.compile(it) }
            ),
            includes = includedFiles.map { resolve(it) },
            imports = imports
        )
    }
}