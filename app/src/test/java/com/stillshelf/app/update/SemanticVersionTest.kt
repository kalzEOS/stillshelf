package com.stillshelf.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {

    @Test
    fun parse_acceptsMinorOnlyVersionsAsZeroPatch() {
        val version = SemanticVersion.parse("0.2")

        assertNotNull(version)
        assertEquals(0, version?.major)
        assertEquals(2, version?.minor)
        assertEquals(0, version?.patch)
        assertEquals("0.2", version?.raw)
    }

    @Test
    fun parse_acceptsTaggedMinorOnlyVersions() {
        val version = SemanticVersion.parse("v0.2")

        assertNotNull(version)
        assertEquals(0, version?.major)
        assertEquals(2, version?.minor)
        assertEquals(0, version?.patch)
        assertEquals("0.2", version?.raw)
    }

    @Test
    fun compare_treatsMinorOnlyAndExplicitPatchVersionsAsEqual() {
        val left = SemanticVersion.parse("0.2")
        val right = SemanticVersion.parse("0.2.0")

        requireNotNull(left)
        requireNotNull(right)

        assertEquals(0, left.compareTo(right))
        assertEquals(0, right.compareTo(left))
    }

    @Test
    fun compare_recognizesTwoZeroAsNewerThanZeroOneNine() {
        val current = SemanticVersion.parse("0.1.9")
        val latest = SemanticVersion.parse("v0.2")

        requireNotNull(current)
        requireNotNull(latest)

        assertTrue(latest > current)
    }

    @Test
    fun compare_keepsPrereleasesBelowFinalRelease() {
        val prerelease = SemanticVersion.parse("v0.2.0-beta.1")
        val stable = SemanticVersion.parse("v0.2.0")

        requireNotNull(prerelease)
        requireNotNull(stable)

        assertTrue(stable > prerelease)
    }
}
