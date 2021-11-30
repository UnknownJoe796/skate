package com.ivieleague.skate

import org.jetbrains.kotlin.build.DEFAULT_KOTLIN_SOURCE_FILES_EXTENSIONS
import org.jetbrains.kotlin.build.report.BuildReporter
import org.jetbrains.kotlin.build.report.metrics.BuildMetricsReporterImpl
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.cli.metadata.K2MetadataCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.*
import org.jetbrains.kotlin.incremental.multiproject.EmptyModulesApiHistory
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngine
import org.jetbrains.kotlin.script.jsr223.KotlinJsr223JvmLocalScriptEngineFactory
import org.jetbrains.kotlin.script.jsr223.KotlinStandardJsr223ScriptTemplate
import org.jetbrains.kotlin.script.util.scriptCompilationClasspathFromContextOrStlib
import java.io.File
import javax.script.Bindings
import javax.script.ScriptContext

object Kotlin {

    init {
        setIdeaIoUseFallback()
    }

    data class CompilationMessage(
        val severity: CompilerMessageSeverity,
        val message: String,
        val location: CompilerMessageSourceLocation? = null
    )

    data class Result(
        val messages: List<CompilationMessage>,
        val output: File
    )

    class CompilationMessageCollector : MessageCollector {
        val messages = ArrayList<CompilationMessage>()

        override fun clear() {
            messages.clear()
        }

        override fun hasErrors(): Boolean = messages.any { it.severity.isError }

        override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?
        ) {
            messages.add(CompilationMessage(severity, message, location))
        }
    }

    fun compileJvm(
        moduleName: String = "MyModule",
        source: List<File>,
        withLibraries: List<File>,
        buildFolder: File = File("build"),
        out: File = buildFolder.resolve("$moduleName.jar"),
        arguments: K2JVMCompilerArguments.() -> Unit = {}
    ): Result = suppressStandardOutputAndError {
        val outputFolder = if (out.extension == "jar") buildFolder.resolve("output") else out
        buildFolder.mkdirs()
        val collector = CompilationMessageCollector()
        val code = IncrementalJvmCompilerRunner(
            workingDir = File(buildFolder, "cache"),
            reporter = BuildReporter(EmptyICReporter, BuildMetricsReporterImpl()),
            usePreciseJavaTracking = true,
            outputFiles = emptyList(),
            buildHistoryFile = File(buildFolder, "build-history.bin"),
            modulesApiHistory = EmptyModulesApiHistory,
            classpathChanges = ClasspathChanges.NotAvailable.UnableToCompute,
            kotlinSourceFilesExtensions = DEFAULT_KOTLIN_SOURCE_FILES_EXTENSIONS
        ).compile(
            allSourceFiles = source,
            args = K2JVMCompilerArguments().apply {
                this.moduleName = moduleName
                classpathAsList = withLibraries
                freeArgs = source.map { it.toString() }
                noStdlib = true
                destination = outputFolder.toString()
            }.apply(arguments),
            messageCollector = collector,
            providedChangedFiles = null
        )
        if (code == ExitCode.OK) {
            if (outputFolder.exists() && out != outputFolder) {
                outputFolder.resolve("META-INF").also { it.mkdirs() }.resolve("MANIFEST.MF").takeIf { !it.exists() }
                    ?.outputStream()?.buffered()?.use {
                    Jar.defaultManifest().write(it)
                }
                Jar.create(outputFolder, out)
            }
        }
        return Result(messages = collector.messages, output = out)
    }

    val scriptingEngineFactory by lazy { KotlinJsr223JvmLocalScriptEngineFactory() }
    fun engine(classLoader: ClassLoader): KotlinJsr223JvmLocalScriptEngine {
        return KotlinJsr223JvmLocalScriptEngine(
            scriptingEngineFactory,
            scriptCompilationClasspathFromContextOrStlib(
                "kotlin-script-util.jar",
                wholeClasspath = true,
                classLoader = classLoader
            ),
            KotlinStandardJsr223ScriptTemplate::class.qualifiedName!!,
            { ctx, types ->
                ScriptArgsWithTypes(
                    arrayOf(ctx.getBindings(ScriptContext.ENGINE_SCOPE)),
                    types ?: emptyArray()
                )
            },
            arrayOf(Bindings::class)
        )
    }
}