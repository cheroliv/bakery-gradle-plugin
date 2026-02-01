package com.cheroliv.bakery

import com.cheroliv.bakery.FileSystemManager.copyBakedFilesToRepo
import com.cheroliv.bakery.FileSystemManager.createRepoDir
import com.cheroliv.bakery.GitService.FileOperationResult.Failure
import com.cheroliv.bakery.GitService.FileOperationResult.Success
import com.cheroliv.bakery.RepositoryConfiguration.Companion.ORIGIN
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.slf4j.Logger
import java.io.File

object GitService {
    const val GIT_ATTRIBUTES_CONTENT = """
                                    #
                                    # https://help.github.com/articles/dealing-with-line-endings/
                                    #
                                    # Linux start script should use lf
                                    /gradlew        text eol=lf

                                    # These are Windows script files and should use crlf
                                    *.bat           text eol=crlf

                                    # Binary files should be left untouched
                                    *.jar           binary
                                """

    sealed class FileOperationResult {
        sealed class GitOperationResult {
            data class Success(
                val commit: RevCommit, val pushResults: MutableIterable<PushResult>?
            ) : GitOperationResult()

            data class Failure(val error: String) : GitOperationResult()
        }

        object Success : FileOperationResult()
        data class Failure(val error: String) : FileOperationResult()
    }

    fun pushPages(
        destPath: () -> String,
        pathTo: () -> String,
        git: GitPushConfiguration,
        logger: Logger
    ) {
        val repoDir: File = createRepoDir(pathTo(), logger)
        try {
            when (val copyResult = copyBakedFilesToRepo(destPath(), repoDir, logger)) {
                is Success -> {
                    logger.info("Successfully copied files to publication repository.")
                    performGitPush(repoDir, git, logger)
                }

                is Failure -> {
                    logger.error("Failed to copy baked files: ${copyResult.error}")
                    throw Exception("Publication failed during file copy: ${copyResult.error}")
                }
            }
        } finally {
            cleanupPublicationArtifacts(repoDir, destPath(), logger)
        }
    }

    private fun performGitPush(
        repoDir: File,
        git: GitPushConfiguration,
        logger: Logger
    ) {
        logger.info("Starting Git operations.")
        initAddCommit(repoDir, git, logger)
        executePushCommand(
            openRepository(repoDir, logger),
            git,
            logger
        )?.forEach { pushResult ->
            val resultString = pushResult.toString()
            logger.info(resultString)
            println(resultString)
        }
        logger.info("Git push completed.")
    }

    private fun cleanupPublicationArtifacts(
        repoDir: File,
        destPath: String,
        logger: Logger
    ) {
        logger.info("Cleaning up publication artifacts.")
        try {
            if (repoDir.exists()) {
                repoDir.deleteRecursively()
                logger.info("Deleted temporary repository directory: $repoDir")
            }
            val destDir = File(destPath)
            if (destDir.exists()) {
                destDir.deleteRecursively()
                logger.info("Deleted baked output directory: $destDir")
            }
        } catch (e: Exception) {
            logger.error("Error during cleanup: ${e.message}", e)
        }
    }

    private fun openRepository(repoDir: File, logger: Logger): Git {
        logger.info("Opening repository at: ${repoDir.absolutePath}")
        val repository = FileRepositoryBuilder().setGitDir(File(repoDir, ".git"))
            .readEnvironment()
            .findGitDir()
            .setMustExist(true)
            .build()

        if (repository.isBare) {
            val errorMessage = "$repository must not be bare."
            logger.error(errorMessage)
            throw IllegalStateException(errorMessage)
        }
        logger.info("Repository opened successfully.")
        return Git(repository)
    }

    private fun executePushCommand(
        git: Git,
        gitConfig: GitPushConfiguration,
        logger: Logger
    ): MutableIterable<PushResult>? {
        logger.info("Preparing to push to remote '$ORIGIN' on branch '${gitConfig.branch}'")
        val credentialsProvider = UsernamePasswordCredentialsProvider(
            gitConfig.repo.credentials.username,
            gitConfig.repo.credentials.password
        )

        return git.push().apply {
            setCredentialsProvider(credentialsProvider)
            remote = ORIGIN
            isForce = true
        }.call()
    }

    fun initAddCommit(
        repoDir: File,
        git: GitPushConfiguration,
        logger: Logger
    ): RevCommit = initRepository(repoDir, git.branch, logger)
        .addRemote(git.repo.repository, logger)
        .addAllFiles(logger)
        .commitChanges(git.message, logger)

    private fun initRepository(
        repoDir: File,
        branch: String,
        logger: Logger
    ): Git {
        logger.info("Initializing repository in $repoDir on branch $branch")
        val git = Git.init()
            .setInitialBranch(branch)
            .setDirectory(repoDir)
            .call()
        if (git.repository.isBare)
            throw Exception("Repository must not be bare")
        if (!git.repository.directory.isDirectory)
            throw Exception("Repository path must be a directory")
        logger.info("Repository initialized successfully.")
        return git
    }

    private fun Git.addRemote(
        remoteUri: String,
        logger: Logger
    ): Git {
        logger.info("Adding remote '$ORIGIN' with URI '$remoteUri'")
        remoteAdd()
            .setName(ORIGIN)
            .setUri(URIish(remoteUri))
            .call()
        logger.info("Remote added successfully.")
        return this
    }

    private fun Git.addAllFiles(logger: Logger): Git {
        logger.info("Adding all files to the index")
        add()
            .addFilepattern(".")
            .call()
        logger.info("All files added.")
        return this
    }

    private fun Git.commitChanges(
        message: String,
        logger: Logger
    ): RevCommit {
        logger.info("Committing changes with message: \"$message\"")
        val revCommit = commit()
            .setMessage(message)
            .call()
        logger.info("Changes committed: ${revCommit.id.name}")
        return revCommit
    }
}