package com.ricardorlg.devicefarm.tractor.mavem

import arrow.core.Either
import com.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmTractorLogger
import com.ricardorlg.devicefarm.tractor.factory.DeviceFarmTractorFactory
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
                        onFailure = { throw MojoExecutionException("There was an error test execution", it) },
                        onSuccess = {
                            if (it.result() != ExecutionResult.PASSED)
                                throw MojoFailureException("Tests result was not success - actual result = ${it.result()}")
                        }
                    )
                }
            }
        }


    }
}