package com.stillshelf.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GithubReleaseApkTest {
    @Test
    fun selectsRegularApkRegardlessOfCase() {
        assertTrue(isGithubReleaseApk("StillShelf-v0.9.2.apk", "v0.9.2"))
        assertTrue(isGithubReleaseApk("stillshelf-v0.9.apk", "v0.9"))
    }

    @Test
    fun rejectsFdroidAndUnrelatedApks() {
        assertFalse(isGithubReleaseApk("StillShelf-v0.9.2-fdroid.apk", "v0.9.2"))
        assertFalse(isGithubReleaseApk("StillShelf-v0.9.1.apk", "v0.9.2"))
        assertFalse(isGithubReleaseApk("other.apk", "v0.9.2"))
    }
}
