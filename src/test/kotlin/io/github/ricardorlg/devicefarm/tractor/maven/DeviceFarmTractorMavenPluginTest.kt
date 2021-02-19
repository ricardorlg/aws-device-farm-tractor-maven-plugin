package io.github.ricardorlg.devicefarm.tractor.maven

import arrow.core.right
import io.github.ricardorlg.devicefarm.tractor.factory.DeviceFarmTractorFactory
import io.github.ricardorlg.devicefarm.tractor.runner.DeviceFarmTractorRunner
import io.mockk.*
import kotlinx.coroutines.runBlocking
import mu.KLogger
import mu.KotlinLogging
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugin.testing.AbstractMojoTestCase
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.services.devicefarm.model.DeviceFarmException
import software.amazon.awssdk.services.devicefarm.model.ExecutionResult
import software.amazon.awssdk.services.devicefarm.model.Run

class DeviceFarmTractorMavenPluginTest : AbstractMojoTestCase() {

    private val projectName = "test project"
    private val appPath = "user/app.apk"
    private val testsProjectPath = "src/project.zip"
    private val testSpecFilePath = "src/specfile.yaml"

    @Before
    fun s() {
        super.setUp()
    }

    @Test
    fun testPluginIsAdded() {
        val pluginPom = getBasedir() + "/src/test/resources/plugin-pom.xml"
        val deviceFarmPlugin = lookupMojo("runAwsTests", pluginPom) as DeviceFarmTractorMavenPlugin
        Assertions.assertThat(deviceFarmPlugin).isNotNull
    }

    @Test
    fun testPluginFailsWhenProjectNameIsNotConfigured() {
        val pluginPom = getBasedir() + "/src/test/resources/project-name-empty/plugin-pom.xml"
        val deviceFarmPlugin = lookupMojo("runAwsTests", pluginPom) as DeviceFarmTractorMavenPlugin
        Assertions.assertThatExceptionOfType(MojoExecutionException::class.java)
            .isThrownBy(deviceFarmPlugin::execute)
            .withMessage("There was an error in the test execution")
            .withRootCauseInstanceOf(UninitializedPropertyAccessException::class.java)
            .havingCause()
            .withMessage("lateinit property projectName has not been initialized")
    }

    @Test
    fun testPluginFailsWhenAppPathIsNotConfigured() {
        val pluginPom = getBasedir() + "/src/test/resources/app-path-empty/plugin-pom.xml"
        val deviceFarmPlugin = lookupMojo("runAwsTests", pluginPom) as DeviceFarmTractorMavenPlugin
        Assertions.assertThatExceptionOfType(MojoExecutionException::class.java)
            .isThrownBy(deviceFarmPlugin::execute)
            .withMessage("There was an error in the test execution")
            .withRootCauseInstanceOf(UninitializedPropertyAccessException::class.java)
            .havingCause()
            .withMessage("lateinit property appPath has not been initialized")
    }

    @Test
    fun testPluginFailsWhenTestProjectPathIsNotConfigured() {
        val pluginPom = getBasedir() + "/src/test/resources/test-project-path-empty/plugin-pom.xml"
        val deviceFarmPlugin = lookupMojo("runAwsTests", pluginPom) as DeviceFarmTractorMavenPlugin
        Assertions.assertThatExceptionOfType(MojoExecutionException::class.java)
            .isThrownBy(deviceFarmPlugin::execute)
            .withMessage("There was an error in the test execution")
            .withRootCauseInstanceOf(UninitializedPropertyAccessException::class.java)
            .havingCause()
            .withMessage("lateinit property testsProjectPath has not been initialized")
    }

    @Test
    fun testPluginFailsWhenTestSpecPathIsNotConfigured() {
        val pluginPom = getBasedir() + "/src/test/resources/test-spec-path-empty/plugin-pom.xml"
        val deviceFarmPlugin = lookupMojo("runAwsTests", pluginPom) as DeviceFarmTractorMavenPlugin
        Assertions.assertThatExceptionOfType(MojoExecutionException::class.java)
            .isThrownBy(deviceFarmPlugin::execute)
            .withMessage("There was an error in the test execution")
            .withRootCauseInstanceOf(UninitializedPropertyAccessException::class.java)
            .havingCause()
            .withMessage("lateinit property testSpecFilePath has not been initialized")
    }

    @Test
    fun testPluginFailsWhenStrictRunIsUsedAndIsConfiguredWithMandatoryParameters() {
        val pluginPom = getBasedir() + "/src/test/resources/mandatory-params-configured-strict-run/plugin-pom.xml"
        val deviceFarmPlugin = lookupMojo("runAwsTests", pluginPom) as DeviceFarmTractorMavenPlugin
        Assertions.assertThatExceptionOfType(MojoExecutionException::class.java)
            .isThrownBy(deviceFarmPlugin::execute)
            .withMessage("There was an error fetching projects from AWS")
            .withRootCauseInstanceOf(DeviceFarmException::class.java)
    }

    @Test
    fun testPluginShouldNotFailsWhenNoStrictRunIsEnabledAndSomeErrorHappens() {
        val pluginPom = getBasedir() + "/src/test/resources/mandatory-params-configured-no-strict-run/plugin-pom.xml"
        val deviceFarmPlugin = lookupMojo("runAwsTests", pluginPom) as DeviceFarmTractorMavenPlugin
        Assertions.assertThatCode { deviceFarmPlugin.execute() }
            .doesNotThrowAnyException()

    }

    @Test
    fun testWhenTestExecutionIsNotSuccessAndStrictRunIsEnablePluginShouldFail() {
        runBlocking {
            val result = Run.builder().result(ExecutionResult.FAILED).build()
            val runner = mockk<DeviceFarmTractorRunner>()
            coEvery { runner.getDeviceResultsTable(any()) } returns ""
            coEvery {
                runner.runTests(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns result
            mockkObject(DeviceFarmTractorFactory)
            coEvery {
                DeviceFarmTractorFactory.createRunner(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns runner.right()
            val pluginPom =
                getBasedir() + "/src/test/resources/mandatory-params-configured-strict-run/plugin-pom.xml"
            val deviceFarmPlugin = lookupMojo("runAwsTests", pluginPom) as DeviceFarmTractorMavenPlugin

            Assertions.assertThatExceptionOfType(MojoFailureException::class.java)
                .isThrownBy(deviceFarmPlugin::execute)
                .withMessage("Tests result was not success - actual result = FAILED")

            coVerify {
                runner.runTests(
                    projectName = projectName,
                    "",
                    appPath = appPath,
                    testProjectPath = testsProjectPath,
                    testSpecPath = testSpecFilePath
                )
                runner.getDeviceResultsTable(result)
            }
            confirmVerified(runner)
            clearMocks(runner)
            unmockkObject(DeviceFarmTractorFactory)
        }
    }

    @Test
    fun testWhenTestExecutionIsNotSuccessAndNoStrictRunIsEnablePluginShouldNotFail() {
        runBlocking {
            val result = Run.builder().result(ExecutionResult.FAILED).build()
            val runner = mockk<DeviceFarmTractorRunner>()
            coEvery { runner.getDeviceResultsTable(any()) } returns ""
            coEvery {
                runner.runTests(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns result
            mockkObject(DeviceFarmTractorFactory)
            coEvery {
                DeviceFarmTractorFactory.createRunner(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns runner.right()
            val pluginPom =
                getBasedir() + "/src/test/resources/mandatory-params-configured-no-strict-run/plugin-pom.xml"
            val deviceFarmPlugin = lookupMojo("runAwsTests", pluginPom) as DeviceFarmTractorMavenPlugin

            Assertions.assertThatCode { deviceFarmPlugin.execute() }
                .doesNotThrowAnyException()

            coVerify {
                runner.runTests(
                    projectName = projectName,
                    "",
                    appPath = appPath,
                    testProjectPath = testsProjectPath,
                    testSpecPath = testSpecFilePath
                )
                runner.getDeviceResultsTable(result)
            }
            confirmVerified(runner)
            clearMocks(runner)
            unmockkObject(DeviceFarmTractorFactory)
        }
    }

    @Test
    fun testWhenTestExecutionIsSuccessAndStrictRunIsEnablePluginShouldNotFail() {
        runBlocking {
            val mockLogger = mockk<KLogger>()
            val slot = slot<String>()
            val result = Run.builder().result(ExecutionResult.PASSED).build()
            val runner = mockk<DeviceFarmTractorRunner>()
            every { mockLogger.info(capture(slot)) } just runs
            coEvery { runner.getDeviceResultsTable(any()) } returns ""
            coEvery {
                runner.runTests(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns result
            mockkObject(DeviceFarmTractorFactory, KotlinLogging)
            every {
                KotlinLogging.logger(any<String>())
            } returns mockLogger
            coEvery {
                DeviceFarmTractorFactory.createRunner(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns runner.right()
            val pluginPom =
                getBasedir() + "/src/test/resources/mandatory-params-configured-strict-run/plugin-pom.xml"
            val deviceFarmPlugin = lookupMojo("runAwsTests", pluginPom) as DeviceFarmTractorMavenPlugin

            Assertions.assertThatCode { deviceFarmPlugin.execute() }
                .doesNotThrowAnyException()

            Assertions.assertThat(slot.captured).isEqualTo("Test execution has successfully finished")

            coVerify {
                runner.runTests(
                    projectName = projectName,
                    "",
                    appPath = appPath,
                    testProjectPath = testsProjectPath,
                    testSpecPath = testSpecFilePath
                )
                runner.getDeviceResultsTable(result)
            }
            verify {
                mockLogger.info(any<String>()) //banner log
                mockLogger.info(any<String>()) // execution finish log
                mockLogger.info(slot.captured) // execution result log
            }
            confirmVerified(runner, mockLogger)
            clearMocks(runner, mockLogger)
            unmockkObject(DeviceFarmTractorFactory, KotlinLogging)
        }
    }
}