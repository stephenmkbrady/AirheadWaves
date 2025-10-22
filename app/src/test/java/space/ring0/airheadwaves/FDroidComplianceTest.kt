package space.ring0.airheadwaves

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * F-Droid compliance tests
 *
 * These tests verify that the build configuration meets F-Droid requirements:
 * 1. Reproducible builds (PNG crunching disabled, vector drawables support)
 * 2. No Google dependency info blocks
 * 3. Correct package naming and versioning
 */
class FDroidComplianceTest {

    private val buildGradleFile = File("build.gradle.kts").let { file ->
        // Try current directory first (CI environment)
        if (file.exists()) file
        // Fall back to app/ subdirectory (local environment)
        else File("app/build.gradle.kts")
    }

    @Test
    fun testBuildGradleFileExists() {
        assertTrue(
            "build.gradle.kts should exist",
            buildGradleFile.exists()
        )
    }

    @Test
    fun testDependencyInfoBlockDisabled() {
        val content = buildGradleFile.readText()

        assertTrue(
            "DependencyInfoBlock should be disabled for F-Droid (includeInApk = false)",
            content.contains("includeInApk = false")
        )

        assertTrue(
            "DependencyInfoBlock should be disabled for F-Droid (includeInBundle = false)",
            content.contains("includeInBundle = false")
        )

        assertTrue(
            "Should have comment explaining F-Droid requirement",
            content.contains("F-Droid") && content.contains("DependencyInfoBlock")
        )
    }

    @Test
    fun testPngCrunchingDisabled() {
        val content = buildGradleFile.readText()

        assertTrue(
            "PNG crunching should be disabled in release build for reproducibility",
            content.contains("isCrunchPngs = false")
        )

        // Verify it's in both debug and release configs
        val crunchCount = content.split("isCrunchPngs = false").size - 1
        assertTrue(
            "PNG crunching should be disabled in both debug and release builds",
            crunchCount >= 2
        )
    }

    @Test
    fun testVectorDrawablesSupportLibrary() {
        val content = buildGradleFile.readText()

        assertTrue(
            "Vector drawables should use support library for reproducibility",
            content.contains("vectorDrawables.useSupportLibrary = true")
        )
    }

    @Test
    fun testMetaInfExclusion() {
        val content = buildGradleFile.readText()

        assertTrue(
            "Should exclude META-INF license files to avoid conflicts",
            content.contains("excludes += \"/META-INF/{AL2.0,LGPL2.1}\"")
        )
    }

    @Test
    fun testPackageName() {
        val content = buildGradleFile.readText()

        assertTrue(
            "Package should be space.ring0.airheadwaves",
            content.contains("applicationId = \"space.ring0.airheadwaves\"")
        )

        assertTrue(
            "Namespace should match applicationId",
            content.contains("namespace = \"space.ring0.airheadwaves\"")
        )
    }

    @Test
    fun testVersioningExists() {
        val content = buildGradleFile.readText()

        assertTrue(
            "versionCode must be defined",
            content.contains("versionCode = ")
        )

        assertTrue(
            "versionName must be defined",
            content.contains("versionName = ")
        )
    }

    @Test
    fun testVersionCodeIsPositive() {
        val content = buildGradleFile.readText()
        val versionCodeMatch = Regex("versionCode = (\\d+)").find(content)

        assertNotNull("versionCode should be found", versionCodeMatch)

        val versionCode = versionCodeMatch?.groupValues?.get(1)?.toInt()
        assertNotNull("versionCode should be a number", versionCode)
        assertTrue("versionCode should be positive", versionCode!! > 0)
    }

    @Test
    fun testVersionNameFormat() {
        val content = buildGradleFile.readText()
        val versionNameMatch = Regex("versionName = \"([^\"]+)\"").find(content)

        assertNotNull("versionName should be found", versionNameMatch)

        val versionName = versionNameMatch?.groupValues?.get(1)
        assertNotNull("versionName should exist", versionName)
        assertTrue("versionName should not be empty", versionName!!.isNotEmpty())

        // Semantic versioning pattern (major.minor or major.minor.patch)
        val semverPattern = Regex("^\\d+\\.\\d+(\\.\\d+)?$")
        assertTrue(
            "versionName should follow semantic versioning (e.g., 1.0 or 1.0.0)",
            semverPattern.matches(versionName)
        )
    }

    @Test
    fun testMinifyDisabled() {
        val content = buildGradleFile.readText()

        // F-Droid prefers non-minified builds for transparency
        assertTrue(
            "Minification should be disabled for F-Droid transparency",
            content.contains("isMinifyEnabled = false")
        )
    }

    @Test
    fun testNoProprietaryDependencies() {
        val content = buildGradleFile.readText()

        // Check for common proprietary dependencies
        assertFalse(
            "Should not use Google Play Services",
            content.contains("com.google.android.gms:play-services")
        )

        assertFalse(
            "Should not use Firebase",
            content.contains("com.google.firebase")
        )

        assertFalse(
            "Should not use proprietary analytics",
            content.contains("analytics") && !content.contains("// analytics")
        )
    }

    @Test
    fun testReproducibleBuildComment() {
        val content = buildGradleFile.readText()

        // Verify comments explain reproducible build requirements
        val reproducibleCommentCount = content.split("Reproducible builds").size - 1
        assertTrue(
            "Should have comments explaining reproducible builds",
            reproducibleCommentCount > 0
        )
    }
}
