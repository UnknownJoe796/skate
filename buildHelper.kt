@file:DependsOn("org.kohsuke:github-api:1.95")
@file:DependsOn("org.eclipse.jgit:org.eclipse.jgit:5.4.2.201908231537-r")

import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.kohsuke.github.GHIssueState
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import java.io.File
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit

val github by lazy { GitHub.connect() }
fun git(base: File) = Git(FileRepositoryBuilder().setGitDir(base.resolve(".git")).build())
val githubAuth by lazy {
    val lines = File(System.getProperty("user.home")).resolve(".github").readLines()
    lines.find { it.startsWith("oauth") }?.let { oauthLine ->
        val oauth = oauthLine.substringAfter('=').trim()
        return@lazy UsernamePasswordCredentialsProvider("token", oauth)
    }
    lines.find { it.startsWith("login") }?.let { loginLine ->
        lines.find { it.startsWith("password") }?.let { passwordLine ->
            val login = loginLine.substringAfter('=').trim()
            val password = passwordLine.substringAfter('=').trim()
            return@lazy UsernamePasswordCredentialsProvider(login, password)
        }
    }
}

class GithubTasks(val git: Git, val remote: GHRepository) {
    fun start(issueNumber: Int, basedOnBranch: String = remote.defaultBranch) {
        val issue = remote.getIssue(issueNumber)
        val branchName: String =
            "issue-$issueNumber-${issue.title.map { if (it.isLetterOrDigit()) it.toLowerCase() else '-' }.joinToString("")}"
        issue.assignTo(github.myself)
        issue.comment("I have started working on this issue.  The branch name will be '$branchName'.")
        if (!git.status().call().isClean) {
            git.stashCreate().call()
        }
        git.checkout().setName(basedOnBranch).call()
        git.pull().setRemote("origin").setCredentialsProvider(githubAuth).setRemoteBranchName(basedOnBranch).call()
        git.branchCreate().setName(branchName).call()
        git.checkout().setName(branchName).call()
    }

    fun pause(message: String) {
        //Commit current stuff
        git.commit().setMessage(message).setAuthor("Joseph Ivie", "josephivie@gmail.com").call()

        //Reset to master
        git.checkout().setName(remote.defaultBranch).call()
        git.pull().setRemote("origin").setCredentialsProvider(githubAuth).setRemoteBranchName(remote.defaultBranch)
            .call()
    }

    fun submit(
        title: String,
        message: String = "",
        issueNumber: Int = git.repository.branch.substringAfter("issue-").takeWhile { it.isDigit() }.toInt()
    ) {
        //Push
        git.add().addFilepattern(".").call()
        git.commit().setMessage(message).setAuthor("Joseph Ivie", "josephivie@gmail.com").call()
        git.push().setRemote("origin").setCredentialsProvider(githubAuth).call()
        val pushedBranch = git.repository.branch

        //Reset to master
        git.checkout().setName(remote.defaultBranch).call()
        git.pull().setRemote("origin").setCredentialsProvider(githubAuth).setRemoteBranchName(remote.defaultBranch)
            .call()

        //Create pull request
        val p = remote.createPullRequest(
            title,
            pushedBranch,
            remote.defaultBranch,
            message + "\nThis branch handles issue #$issueNumber.}"
        )

        //Comment on issue
        val issue = remote.getIssue(issueNumber)
        issue.comment("This issue is solved by pull request #${p.number}.")
    }
}

val git = git(File("C:\\Users\\josep\\Projects\\standalone-kotlin-file"))
val repo = github.getRepository("UnknownJoe796/skate")
val tasks = GithubTasks(git, repo)

fun release(versionString: String, updateInfo: String = "") {

    //Reset to Master
    println("Resetting to Master")
    git.checkout().setName(repo.defaultBranch).call()
    git.pull().setRemote("origin").setCredentialsProvider(githubAuth).setRemoteBranchName(repo.defaultBranch).call()

    //Update build number
    println("Updating build number to $versionString")
    File("build.gradle").let {
        it.writeText(it.readLines().map {
            if (it.trim().startsWith("version")) {
                "version '$versionString'"
            } else it
        }.joinToString("\n"))
    }

    //Commit and push
    println("Committing change")
    git.add().addFilepattern(".").call()
    git.commit().setMessage("Version bump to $versionString").setAuthor("Joseph Ivie", "josephivie@gmail.com").call()
    git.push().setRemote("origin").setCredentialsProvider(githubAuth).call()

    //Build
    println("Building")
    val process = ProcessBuilder(".\\gradlew.bat", "distZip").inheritIO().start()
    process.waitFor(60, TimeUnit.SECONDS)
    assert(process.exitValue() == 0)

    //Push release
    println("Pushing release")
    repo.createRelease(versionString)
        .name(versionString)
        .body(updateInfo)
        .create()
        .uploadAsset(File("build/distributions/skate-${versionString}.zip"), "application/zip")
}