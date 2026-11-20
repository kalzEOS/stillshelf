package com.stillshelf.app.scrobbling

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import io.mockk.*
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningSettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val koito = ListeningConnection(ListeningProvider.KOITO, "https://koito.invalid", "reader", "saved-token")
    private val lb = ListeningConnection(ListeningProvider.LISTEN_BRAINZ, username = "other", token = "lb-token")
    private val state = MutableStateFlow(ListeningSettings())
    private val busyState = MutableStateFlow(false)
    private val messageState = MutableStateFlow<String?>(null)
    private val saveEvents = MutableStateFlow<Pair<Long, String>?>(null)

    private fun viewModel(settings: ListeningSettings = ListeningSettings(loaded = true, connection = koito,
        connections = listOf(koito), multipleConnections = true, showStatistics = true, statisticsSource = StatisticsSource.EXTERNAL)): ListeningSettingsViewModel {
        state.value = settings
        return mockk(relaxed = true) {
            every { this@mockk.settings } returns state
            every { busy } returns busyState
            every { networkBusy } returns MutableStateFlow(false)
            every { message } returns messageState
            every { authorizationUrl } returns MutableStateFlow(null)
            every { awaitingAuthorization } returns MutableStateFlow(false)
            every { savedConnection } returns saveEvents
            every { setScrobblingEnabled(false) } answers { state.value = state.value.copy(scrobblingEnabled = false) }
            every { enableDirect(any()) } answers {
                val id = firstArg<String>()
                val connection = state.value.savedConnections.first { it.revision == id }.withSubmissionPermission(true)
                state.value = state.value.saveConnection(connection, id).copy(sendingConnectionId = id, scrobblingEnabled = true)
            }
            every { update(any()) } answers { state.value = firstArg<(ListeningSettings) -> ListeningSettings>()(state.value) }
            every { remove(any()) } answers { firstArg<String?>()?.let { state.value = state.value.withoutConnection(it) }; Unit }
            every { saveManaged(any(), any(), any()) } answers {
                val connection = firstArg<ListeningConnection>()
                state.value = state.value.saveConnection(connection, secondArg(), thirdArg())
                val saved = state.value.savedConnections.first { sameListeningAccount(it, connection) }
                saveEvents.value = (saveEvents.value?.first?.plus(1) ?: 1L) to saved.revision
            }
        }
    }

    private fun show(vm: ListeningSettingsViewModel, back: () -> Unit = {}, home: () -> Unit = {}, dark: Boolean = false) {
        // Match the production NavHost's consumed status-bar inset before the shared header.
        compose.setContent { MaterialTheme(colorScheme = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onBackground) {
                Box(Modifier.width(320.dp).fillMaxSize().statusBarsPadding()) {
            ListeningSettingsScreen(onBack = back, onHome = home, viewModel = vm)
        } } } }
        dismissTestAgentWarning()
    }
    private fun dismissTestAgentWarning() {
        // MockK's test-only native agent triggers Android 17's compatibility notice. It steals
        // activity focus and prevents the keyboard from opening, despite semantics actions working.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        val previousFlags = info.flags
        try {
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            android.os.SystemClock.sleep(500)
            val roots = listOfNotNull(automation.rootInActiveWindow) + automation.windows.mapNotNull { it.root }
            roots.flatMap { it.findAccessibilityNodeInfosByText("Don't Show Again") }.firstOrNull()?.let { button ->
                var target: android.view.accessibility.AccessibilityNodeInfo? = button
                while (target != null && !target.isClickable) target = target.parent
                target?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                android.os.SystemClock.sleep(400)
            }
        } finally { info.flags = previousFlags; automation.serviceInfo = info }
    }
    private fun main(text: String) = compose.onNodeWithText(text).performScrollTo()
    private fun toggle(text: String) = compose.onNodeWithContentDescription(text).performScrollTo()
    private fun openManager() { main("Manage Connection").performClick() }
    private fun editUrlWithKeyboard() {
        compose.onNodeWithText("https://koito.invalid").performScrollTo().performClick()
            .performTextReplacement("https://koito.invalid/draft")
        compose.runOnUiThread {
            androidx.core.view.WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
                .show(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(5_000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        android.os.SystemClock.sleep(700) // Check the settled keyboard, not an intermediate animation frame.
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = instrumentation.uiAutomation.rootInActiveWindow
        if (root?.findAccessibilityNodeInfosByText("Android App Compatibility")?.isNotEmpty() == true) {
            root.findAccessibilityNodeInfosByText("OK").firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
        }
        android.os.SystemClock.sleep(350)
        File(instrumentation.targetContext.getExternalFilesDir("ui-verification"), "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun statisticsFirstLayoutHasOnlyOneMainManagementAction() {
        show(viewModel())
        screenshot("statistics-first-initial-320dp")
        val statisticsTop = main("Listening Statistics").fetchSemanticsNode().positionOnScreen.y
        screenshot("statistics-first-top-320dp")
        val directTop = main("Direct Scrobbling (Optional)").fetchSemanticsNode().positionOnScreen.y
        assertTrue(statisticsTop < directTop)
        compose.onNodeWithText("Edit Configuration").assertDoesNotExist()
        compose.onNodeWithText("Verify Connection").assertDoesNotExist()
        compose.onNodeWithTag("method-OFF").assertDoesNotExist()
        screenshot("statistics-first-direct-320dp")
    }

    @Test fun directSwitchNeedsConfirmationAndCancelKeepsStatisticsAndCredentials() {
        show(viewModel())
        toggle("Send scrobbles from StillShelf").performClick()
        compose.onNodeWithText("Avoid Duplicate Scrobbles").assertIsDisplayed()
        screenshot("statistics-first-duplicate-warning")
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(state.value.scrobblingEnabled)
        assertTrue(state.value.showStatistics)
        toggle("Send scrobbles from StillShelf").performClick()
        compose.onNodeWithText("Continue").performClick()
        assertTrue(state.value.canSubmit)
        toggle("Send scrobbles from StillShelf").performClick()
        assertFalse(state.value.canSubmit)
        assertEquals("saved-token", state.value.connection?.token)
    }

    @Test fun multipleConnectionsHaveIndependentStatisticsAndSendingSelectors() {
        show(viewModel(ListeningSettings(loaded = true, connection = lb, connections = listOf(koito, lb), multipleConnections = true,
            sendingConnectionId = koito.revision, showStatistics = true, statisticsSource = StatisticsSource.EXTERNAL)))
        compose.onNodeWithTag("choice-Send to").performScrollTo().performClick()
        compose.onNodeWithTag("option-Koito").performClick()
        toggle("Send scrobbles from StillShelf").performClick()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(koito.revision, state.value.sendingConnection?.revision)
        assertEquals(lb.revision, state.value.connection?.revision)
        compose.onNodeWithTag("choice-Statistics from").performScrollTo().performClick()
        compose.onNodeWithTag("option-Navidrome history").performClick()
        assertTrue(state.value.canSubmit)
        assertEquals(koito.revision, state.value.sendingConnection?.revision)
        screenshot("statistics-first-independent-connections")
    }

    @Test fun noConnectionGuidesSetupWithoutEnablingSubmissions() {
        show(viewModel(ListeningSettings(loaded = true, multipleConnections = true)))
        toggle("Send scrobbles from StillShelf").performClick()
        compose.onNodeWithText("Add Connection").assertIsDisplayed()
        compose.onNodeWithText("Save Configuration").performScrollTo().assertIsDisplayed()
        assertFalse(state.value.canSubmit)
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertFalse(state.value.scrobblingEnabled)
    }

    @Test fun managerShowsSavedAccountUrlAndConfirmedRemovalKeepsOtherConnection() {
        val vm = viewModel(ListeningSettings(loaded = true, connection = koito, connections = listOf(koito, lb),
            multipleConnections = true, sendingConnectionId = lb.revision, scrobblingEnabled = true))
        show(vm)
        openManager()
        compose.onNodeWithText("Account: reader").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("https://koito.invalid").performScrollTo().assertIsDisplayed()
        screenshot("statistics-first-manage-320dp")
        compose.onNodeWithText("Remove Connection").performScrollTo().performClick()
        compose.onNodeWithText("Remove service connection?").assertIsDisplayed()
        screenshot("statistics-first-remove-confirmation")
        compose.onNodeWithText("Cancel").performClick()
        verify(exactly = 0) { vm.remove(any()) }
        compose.onNodeWithText("Remove Connection").performScrollTo().performClick()
        compose.onNodeWithText("Remove").performClick()
        verify(exactly = 1) { vm.remove(koito.revision) }
        assertEquals(lb.revision, state.value.sendingConnection?.revision)
        assertTrue(state.value.canSubmit)
    }

    @Test fun editingCanBeCancelledAndBlankSecretsAreReusedWhenSaving() {
        val vm = viewModel()
        show(vm)
        openManager()
        compose.onNodeWithText("Edit Configuration").performScrollTo().performClick()
        compose.onNodeWithText("https://koito.invalid").performScrollTo().performTextReplacement("https://changed.invalid")
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals("https://koito.invalid", state.value.connection?.serverUrl)
        verify(exactly = 0) { vm.saveManaged(any(), any(), any()) }
        compose.onNodeWithText("Edit Configuration").performScrollTo().performClick()
        compose.onNodeWithText("Save Configuration").performScrollTo().performClick()
        verify { vm.saveManaged(match { it.token == "saved-token" }, koito.revision, false) }
        compose.onNodeWithText("Edit Configuration").performScrollTo().assertIsDisplayed()
    }

    @Test fun serviceMenuIsAnchoredAndProviderFieldsAreSpecific() {
        show(viewModel(ListeningSettings(loaded = true, multipleConnections = true)))
        openManager()
        compose.onNodeWithTag("choice-Service").performScrollTo().performClick()
        listOf("Koito", "Maloja", "Last.fm", "ListenBrainz").forEach { compose.onNodeWithTag("option-$it").assertIsDisplayed() }
        screenshot("statistics-first-service-menu")
        val field = compose.onNodeWithTag("choice-Service").fetchSemanticsNode()
        val menu = compose.onNodeWithTag("menu-Service").fetchSemanticsNode()
        assertTrue(menu.positionOnScreen.x >= field.positionOnScreen.x - 2)
        assertTrue(menu.positionOnScreen.x + menu.size.width <= compose.activity.resources.displayMetrics.widthPixels)
        compose.onNodeWithTag("option-ListenBrainz").performClick()
        compose.onNodeWithText("Username").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Server URL").assertDoesNotExist()
        compose.onNodeWithTag("choice-Service").performScrollTo().performClick()
        compose.onNodeWithTag("option-Maloja").performClick()
        compose.onNodeWithText("Server URL").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("choice-Service").performScrollTo().performClick()
        compose.onNodeWithTag("option-Last.fm").performClick()
        compose.onNodeWithText("API key").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Server URL").assertDoesNotExist()
    }

    @Test fun sourceMenuSupportsSavedProvidersAndStatisticsOnlySetup() {
        show(viewModel())
        compose.onNodeWithTag("choice-Statistics from").performScrollTo().performClick()
        listOf("Navidrome history", "Koito", "Add Connection…").forEach { compose.onNodeWithTag("option-$it").assertIsDisplayed() }
        screenshot("statistics-first-source-menu")
        compose.onNodeWithTag("option-Add Connection…").performClick()
        compose.onNodeWithText("Save Configuration").performScrollTo().assertIsDisplayed()
        assertFalse(state.value.canSubmit)
    }

    @Test fun verifyFeedbackIsVisibleInManagement() {
        val vm = viewModel()
        every { vm.verifyConnection(koito.revision) } answers { messageState.value = "Statistics access verified. No test listen was sent." }
        show(vm)
        openManager()
        compose.onNodeWithText("Verify Connection").performScrollTo().performClick()
        compose.onNodeWithText("Statistics access verified. No test listen was sent.").assertIsDisplayed()
        screenshot("statistics-first-verify-feedback")
    }

    @Test fun visibilityAndNavigationStayIndependentOfDirectSending() {
        val vm = viewModel()
        var back = 0
        var home = 0
        show(vm, { back++ }, { home++ })
        toggle("Show Listening Statistics").performClick()
        assertFalse(state.value.showStatistics)
        assertEquals("saved-token", state.value.connection?.token)
        main("Manage Connection").assertIsDisplayed()
        toggle("Send scrobbles from StillShelf").performClick()
        compose.onNodeWithText("Continue").performClick()
        assertTrue(state.value.canSubmit)
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, home)
        assertEquals(1, back)
    }

    @Test fun directToggleAndBusySaveKeepControlsStableAndHeaderPinned() {
        show(viewModel())
        val before = toggle("Send scrobbles from StillShelf").fetchSemanticsNode().boundsInRoot.top
        toggle("Send scrobbles from StillShelf").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        val after = compose.onNodeWithContentDescription("Send scrobbles from StillShelf").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 2f)
        val homeTop = compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("listening-settings-list").performTouchInput { swipeUp() }
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
        assertEquals(homeTop, compose.onNodeWithContentDescription("Home").fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.runOnIdle { busyState.value = true }
        compose.onNodeWithTag("connection-progress").assertDoesNotExist()
        screenshot("statistics-first-pinned-header")
    }

    @Test fun darkThemeKeepsManagementActionsAndRemovalConfirmationReadable() {
        show(viewModel(), dark = true)
        screenshot("statistics-first-dark-main")
        openManager()
        compose.onNodeWithText("Verify Connection").performScrollTo().assertIsDisplayed()
        screenshot("statistics-first-dark-manager")
        compose.onNodeWithText("Remove Connection").performScrollTo().performClick()
        compose.onNodeWithText("Remove service connection?").assertIsDisplayed()
        screenshot("statistics-first-dark-remove")
    }

    @Test fun editorKeepsSaveAndCancelReachableWithKeyboardOpen() {
        val vm = viewModel()
        show(vm)
        openManager()
        compose.onNodeWithText("Edit Configuration").performScrollTo().performClick()
        editUrlWithKeyboard()
        compose.onNodeWithText("Save Configuration").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val previousFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        android.os.SystemClock.sleep(400) // Allow the accessibility window inventory to update.
        val ime = automation.windows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        assertNotNull("The keyboard must actually be visible for this regression check", ime)
        val keyboardBounds = android.graphics.Rect().also { ime!!.getBoundsInScreen(it) }
        automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
        val save = compose.onNodeWithText("Save Configuration").fetchSemanticsNode()
        screenshot("statistics-first-editor-keyboard")
        assertTrue("Save must remain above keyboard: ${save.positionOnScreen} / $keyboardBounds",
            save.positionOnScreen.y + save.size.height <= keyboardBounds.top + 2)
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.onNodeWithText("Edit Configuration").performScrollTo().assertIsDisplayed()
        assertEquals("https://koito.invalid", state.value.connection?.serverUrl)
        verify(exactly = 0) { vm.saveManaged(any(), any(), any()) }
    }

    @Test fun editorCanSaveWithKeyboardOpenThenBackReturnsToSettings() {
        val vm = viewModel()
        show(vm)
        openManager()
        compose.onNodeWithText("Edit Configuration").performScrollTo().performClick()
        editUrlWithKeyboard()
        compose.onNodeWithText("Save Configuration").performScrollTo().performClick()
        verify(exactly = 1) { vm.saveManaged(match { it.serverUrl == "https://koito.invalid/draft" && it.token == "saved-token" }, koito.revision, false) }
        compose.onNodeWithText("Edit Configuration").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("https://koito.invalid/draft").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("choice-Statistics from").performScrollTo().assertIsDisplayed()
        assertFalse(state.value.canSubmit)
        assertTrue(state.value.showStatistics)
    }
}
