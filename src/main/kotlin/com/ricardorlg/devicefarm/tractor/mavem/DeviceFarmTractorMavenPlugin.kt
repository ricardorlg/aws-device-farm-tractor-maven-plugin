package com.ricardorlg.devicefarm.tractor.mavem

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmTractorLogger
import com.ricardorlg.devicefarm.tractor.factory.DeviceFarmTractorFactory
import com.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import kotlinx.coroutines.runBlocking
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Execute
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import software.amazon.awssdk.services.devicefarm.model.ExecutionResult

@Mojo(name = "runAwsTests", requiresDirectInvocation = true)
@Execute(phase = LifecyclePhase.PACKAGE)
class DeviceFarmTractorMavenPlugin : AbstractMojo() {

    @Parameter(property = "aws.accessKeyId", required = false)
    private val accessKeyId = ""

    @Parameter(property = "aws.secretAccessKey", required = false)
    private val secretAccessKey = ""

    @Parameter(property = "aws.sessionToken", required = false)
    private val sessionToken = ""

    @Parameter(property = "aws.region", required = false)
    private val region = ""

    @Parameter(property = "aws.project.name", required = true)
    private lateinit var projectName: String

    @Parameter(property = "aws.device.pool", required = false)
    private val devicePool: String = ""

    @Parameter(property = "aws.app.path", required = true)
    private lateinit var appPath: String

    @Parameter(property = "aws.app.type", required = true)
    private lateinit var appType: TestProjectTypes

    @Parameter(property = "aws.tests.path", required = true)
    private lateinit var testsProjectPath: String

    @Parameter(property = "aws.test.spec.path", required = true)
    private lateinit var testSpecFilePath: String

    @Parameter(property = "aws.capture.video", required = false)
    private val captureVideo = true

    @Parameter(property = "aws.run.name", required = false)
    private val testRunName = ""

    @Parameter(property = "aws.reports.base.dir", required = false)
    private val testReportsBaseDirectory: String = ""

    @Parameter(property = "aws.download.reports", required = false)
    private val downloadReports = true

    @Parameter(property = "aws.clean.uploads", required = false)
    private val cleanState = true

    @Parameter(property = "aws.strict", required = false)
    private val strictRun = true

    private val banner = """
 _______           _______  _          ______  _________ _______ __________________ _______  _          ______   _______          _________ _______  _______    _______  _______  _______  _______ 
(  ___  )|\     /|(  ___  )( \        (  __  \ \__   __/(  ____ \\__   __/\__   __/(  ___  )( \        (  __  \ (  ____ \|\     /|\__   __/(  ____ \(  ____ \  (  ____ \(  ___  )(  ____ )(       )
| (   ) || )   ( || (   ) || (        | (  \  )   ) (   | (    \/   ) (      ) (   | (   ) || (        | (  \  )| (    \/| )   ( |   ) (   | (    \/| (    \/  | (    \/| (   ) || (    )|| () () |
| (___) || |   | || (___) || |        | |   ) |   | |   | |         | |      | |   | (___) || |        | |   ) || (__    | |   | |   | |   | |      | (__      | (__    | (___) || (____)|| || || |
|  ___  |( (   ) )|  ___  || |        | |   | |   | |   | | ____    | |      | |   |  ___  || |        | |   | ||  __)   ( (   ) )   | |   | |      |  __)     |  __)   |  ___  ||     __)| |(_)| |
| (   ) | \ \_/ / | (   ) || |        | |   ) |   | |   | | \_  )   | |      | |   | (   ) || |        | |   ) || (       \ \_/ /    | |   | |      | (        | (      | (   ) || (\ (   | |   | |
| )   ( |  \   /  | )   ( || (____/\  | (__/  )___) (___| (___) |___) (___   | |   | )   ( || (____/\  | (__/  )| (____/\  \   /  ___) (___| (____/\| (____/\  | )      | )   ( || ) \ \__| )   ( |
|/     \|   \_/   |/     \|(_______/  (______/ \_______/(_______)\_______/   )_(   |/     \|(_______/  (______/ (_______/   \_/   \_______/(_______/(_______/  |/       |/     \||/   \__/|/     \|

With love from ricardorlg
---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
"""

    override fun execute() {
        runBlocking {
            val logger = DefaultDeviceFarmTractorLogger("Device Farm Tractor")
            val runner = DeviceFarmTractorFactory.createRunner(
                logger = logger,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                sessionToken = sessionToken,
                region = region
            )
            logger.logStatus("\r\n" + banner)
            when (runner) {
                is Either.Left -> throw MojoExecutionException("There was an error loading the test runner", runner.a)
                is Either.Right -> {
                    kotlin.runCatching {
                        runner
                            .b
                            .runTests(
                                projectName = projectName,
                                devicePoolName = devicePool,
                                appPath = appPath,
                                appUploadType = appType.toAwsUploadType(),
                                testProjectPath = testsProjectPath,
                                testSpecPath = testSpecFilePath,
                                captureVideo = captureVideo,
                                runName = testRunName,
                                testReportsBaseDirectory = testReportsBaseDirectory,
                                downloadReports = downloadReports,
                                cleanStateAfterRun = cleanState
                            )
                    }.fold(
                        onFailure = {
                            when {
                                strictRun -> when (it) {
                                    is DeviceFarmTractorError -> throw MojoExecutionException(it.message, it.cause)
                                    else -> throw MojoExecutionException("There was an error in the test execution", it)
                                }
                                it is DeviceFarmTractorError -> logger.logError(it.cause, it.message)
                                else -> logger.logError(it, it.message ?: "There was an error in the text execution")
                            }
                        },
                        onSuccess = {
                            logger.logStatus("Test execution has been completed")
                            val devicesResultsTable = runner.b.getDeviceResultsTable(it)
                            if (devicesResultsTable.isNotEmpty())
                                logger.logStatus("\r\n" + devicesResultsTable)
                            if (it.result() != ExecutionResult.PASSED) {
                                if (strictRun)
                                    throw MojoFailureException("Tests result was not success - actual result = ${it.result()}")
                                else
                                    logger.logError(msg = "Tests result was not success - actual result = ${it.result()}")
                            } else {
                                logger.logStatus("Test execution has successfully finished")
                            }
                        }
                    )
                }
            }
        }
    }
}