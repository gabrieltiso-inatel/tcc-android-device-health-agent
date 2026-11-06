package com.tcc.devicehealth.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AgentScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsSavedAppsAndDoesNotOfferRemovalForTheAgent() {
        showAgent(AgentUiState(isReady = true, isPaired = true, snapshots = DeviceSnapshots(
            apps = AppInventory("2026-10-31T12:00:00Z", listOf(InstalledApp("agent", "Device Health Agent", false, "0.1", 1))),
        )))

        compose.onNodeWithText("Apps", useUnmergedTree = true).performClick()

        compose.onNodeWithText("Device Health Agent").assertIsDisplayed()
        compose.onNodeWithText("Protected").assertIsDisplayed()
        compose.onNodeWithText("Remove", substring = false).assertDoesNotExist()
    }

    @Test
    fun decliningAFileFromThePendingListTargetsThatApproval() {
        var declined: String? = null
        val approval = PendingFileApproval("file-action", "file", "revision", "Family notes.txt")
        showAgent(AgentUiState(isReady = true, isPaired = true, pendingFileApprovals = listOf(approval)), onDeclineFile = { declined = it.actionId })

        compose.onNodeWithText("Approvals", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Remove Family notes.txt?").assertIsDisplayed()
        compose.onNodeWithText("Decline").performClick()

        compose.runOnIdle { assertEquals("file-action", declined) }
    }

    private fun showAgent(state: AgentUiState, onDeclineFile: (PendingFileApproval) -> Unit = {}) {
        compose.setContent {
            DeviceHealthTheme {
                AgentScreen(
                    state = state,
                    onPairingCodeChange = {}, onControllerAddressChange = {}, onPair = {}, onExecuteAction = {},
                    onRequestApplicationRemoval = {}, onCancelApplicationRemoval = {},
                    onRequestFileRemoval = {}, onCancelFileRemoval = onDeclineFile,
                    onDismissFileRemoval = {}, onRemoveApplication = {}, onRemoveFile = {},
                    onConfirmFileRemoval = {}, onCollectFileInventory = {}, onSelectFileFolder = {}, onRequestMediaAccess = {},
                )
            }
        }
    }
}
