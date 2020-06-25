package com.ivieleague.skate

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.collection.CollectResult
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.io.File
import java.io.PrintStream


object Maven {
    private val repositorySystem: RepositorySystem = run {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, FileTransporterFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)

        locator.getService(RepositorySystem::class.java)
    }

    private fun session(): RepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession()

        val localRepo = LocalRepository(File(File(System.getProperty("user.home")), ".maven-cache"))
        session.localRepositoryManager = repositorySystem.newLocalRepositoryManager(session, localRepo)

        return session
    }

    fun DependencyNode.allArtifacts(): Sequence<Artifact> =
        (if (this.artifact != null) sequenceOf(this.artifact) else sequenceOf()) + this.children.asSequence().flatMap { it.allArtifacts() }

    fun libraries(
        repositories: List<RemoteRepository>,
        dependencies: List<Dependency>,
        output: PrintStream = System.out
    ): List<Library> {
        val session = session()
        val dependencyResults: CollectResult = repositorySystem.collectDependencies(
            session,
            CollectRequest(dependencies, null, repositories)
        )

        when (dependencyResults.exceptions.size) {
            0 -> {
            }
            1 -> throw dependencyResults.exceptions.first()
            else -> throw Exception("Several exceptions: ${dependencyResults.exceptions.joinToString("\n") {
                it?.message ?: "?"
            }}")
        }

        return dependencyResults.root.allArtifacts()
            .map {
                output.println("Obtaining ${it.run { "$groupId:$artifactId:$version" }}")
                Library(
                    name = it.run { "$groupId:$artifactId:$version" },
                    default = repositorySystem.resolveArtifact(
                        session,
                        ArtifactRequest(it, repositories, null)
                    ).let { result ->
                        if (result.isResolved)
                            result.artifact.file
                        else
                            throw IllegalStateException("Could not resolve ${it.run { "$groupId:$artifactId:$version" }}: ${result.exceptions.joinToString {
                                it.message ?: ""
                            }}")
                    },
                    javadoc = try {
                        repositorySystem.resolveArtifact(
                            session,
                            ArtifactRequest(it.javadoc(), repositories, null)
                        ).let { result ->
                            if (result.isResolved)
                                result.artifact.file
                            else
                                null
                        }
                    } catch (e: Exception) {
                        null
                    },
                    sources = try {
                        repositorySystem.resolveArtifact(
                            session,
                            ArtifactRequest(it.sources(), repositories, null)
                        ).let { result ->
                            if (result.isResolved)
                                result.artifact.file
                            else
                                null
                        }
                    } catch (e: Exception) {
                        null
                    }
                )
            }
            .toList()
    }

    val central = RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build()
    val jcenter = RemoteRepository.Builder("jcenter", "default", "https://jcenter.bintray.com/").build()
    val google = RemoteRepository.Builder("google", "default", "https://dl.google.com/dl/android/maven2/").build()
    val local = RemoteRepository.Builder(
        "local",
        "default",
        "file://" + File(File(System.getProperty("user.home")), ".m2").invariantSeparatorsPath
    ).build()
    val defaultRepositories = listOf(central, jcenter, google, local)

    const val kotlinStandardLibrary = "org.jetbrains.kotlin:kotlin-stdlib:1.3.50"

    fun compile(stringAddress: String) = Dependency(DefaultArtifact(stringAddress), "compile")
}

fun Artifact.javadoc() = DefaultArtifact(this.groupId, this.artifactId, "javadoc", "jar", this.version)
fun Artifact.sources() = DefaultArtifact(this.groupId, this.artifactId, "sources", "jar", this.version)

fun main() {
    Maven.libraries(
        repositories = Maven.defaultRepositories,
        dependencies = listOf(Maven.compile("org.jetbrains.kotlin:kotlin-compiler-embeddable:jar:1.3.+"))
    ).let {
        println("Dependencies: ")
        println(it.joinToString("\n"))
    }
}
