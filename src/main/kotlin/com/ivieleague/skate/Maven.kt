package com.ivieleague.skate

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import java.io.File


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

    fun resolveVersions(
        repositories: List<RemoteRepository>,
        dependencies: List<Dependency>,
        session: RepositorySystemSession = session()
    ): List<Dependency> {
        return dependencies.map {
            var versionString = it.artifact.version
            if (versionString.contains('+')) {
                val beforeParts = versionString.substringBefore('+').split('.').mapNotNull { it.toIntOrNull() }
                val before = beforeParts.joinToString(".")
                val afterParts = beforeParts.dropLast(1) + (beforeParts.last() + 1)
                val after = afterParts.joinToString(".")
                versionString = "[$before, $after)"
            }
            if (versionString.contains('(') || versionString.contains('[')) {
                val withVersionString = it.artifact.setVersion(versionString)
                val result =
                    repositorySystem.resolveVersionRange(
                        session,
                        VersionRangeRequest(withVersionString, repositories, null)
                    )
                Dependency(
                    it.artifact.setVersion(result.highestVersion.toString()),
                    it.scope,
                    it.optional,
                    it.exclusions
                )
            } else it
        }
    }

    fun collect(
        repositories: List<RemoteRepository>,
        dependencies: List<Dependency>,
        session: RepositorySystemSession = session()
    ): List<Artifact> {
        println("Collecting dependencies...")
        val deps = repositorySystem.resolveDependencies(
            session,
            DependencyRequest(
                repositorySystem.collectDependencies(
                    session,
                    CollectRequest(dependencies, null, repositories)
                ).root,
                null
            )
        )
        return deps.artifactResults.map {
            if (it.exceptions.isNotEmpty()) {
                if (it.exceptions.size == 1) {
                    throw it.exceptions.first()
                } else {
                    throw Exception("Several exceptions: ${it.exceptions.joinToString("\n") { it.message ?: "?" }}")
                }
            } else it.artifact!!
        }
    }

    fun libraries(repositories: List<RemoteRepository>, dependencies: List<Dependency>): List<Library> {
        val session = session()
        val versionedDependencies = resolveVersions(repositories, dependencies, session)
        val source = collect(repositories, versionedDependencies, session)
        val secondaryResults = source.asSequence().flatMap {
            it.additionalArtifacts().map { ArtifactRequest(it, repositories, null) }
        }.toList().map {
            println("Obtaining ${it.artifact.run { "$groupId:$artifactId:$version" }}")
            repositorySystem.resolveArtifact(
                session,
                it
            )
        }
        return source.map { s ->
            Library(
                name = s.groupId + ":" + s.artifactId + ":" + s.version,
                default = s.file,
                javadoc = secondaryResults.find { it.artifact.groupId == s.groupId && it.artifact.artifactId == s.artifactId && it.artifact.classifier == "javadoc" }?.artifact?.file,
                sources = secondaryResults.find { it.artifact.groupId == s.groupId && it.artifact.artifactId == s.artifactId && it.artifact.classifier == "sources" }?.artifact?.file
            )
        }
    }

    val central = RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build()
    val jcenter = RemoteRepository.Builder("jcenter", "default", "http://jcenter.bintray.com/").build()
    val google = RemoteRepository.Builder("google", "default", "https://dl.google.com/dl/android/maven2/").build()
    val local = RemoteRepository.Builder(
        "local",
        "default",
        "file://" + File(File(System.getProperty("user.home")), ".m2").invariantSeparatorsPath
    ).build()
    val defaultRepositories = listOf(central, jcenter, google, local)

    const val kotlinStandardLibrary = "org.jetbrains.kotlin:kotlin-stdlib:1.3.+"

    fun compile(stringAddress: String) = Dependency(DefaultArtifact(stringAddress), "compile")
}

fun Artifact.additionalArtifacts(): Sequence<Artifact> = sequenceOf(
    DefaultArtifact(this.groupId, this.artifactId, "javadoc", "jar", this.version),
    DefaultArtifact(this.groupId, this.artifactId, "sources", "jar", this.version)
)

fun main() {
    Maven.libraries(
        repositories = Maven.defaultRepositories,
        dependencies = listOf(Maven.compile("org.jetbrains.kotlin:kotlin-compiler-embeddable:jar:1.3.+"))
    ).let {
        println("Dependencies: ")
        println(it.joinToString("\n"))
    }
}