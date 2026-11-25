package com.tcc.devicehealth.agent

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tcc.devicehealth.agent.model.ApplicationInventory
import com.tcc.devicehealth.agent.model.InstalledApp
import com.tcc.devicehealth.agent.model.LatestDeviceSnapshots
import com.tcc.devicehealth.agent.model.PendingFileRemoval
import com.tcc.devicehealth.agent.ui.AgentScreen
import com.tcc.devicehealth.agent.ui.AgentUiState
import com.tcc.devicehealth.agent.ui.DeviceHealthTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AgentScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsSavedAppsAndDoesNotOfferRemovalForTheAgent() {
        showAgent(AgentUiState(isReady = true, isPaired = true, snapshots = LatestDeviceSnapshots(
            apps = ApplicationInventory("2026-10-31T12:00:00Z", listOf(InstalledApp("agent", "Device Health Agent", false, "0.1", 1))),
        )))

        compose.onNodeWithText("Apps", useUnmergedTree = true).performClick()

        compose.onNodeWithText("Device Health Agent").assertIsDisplayed()
        compose.onNodeWithText("Protected").assertIsDisplayed()
        compose.onNodeWithText("Remove", substring = false).assertDoesNotExist()
    }

    @Test
    fun decliningAFileFromThePendingListTargetsThatApproval() {
        var declined: String? = null
        val approval = PendingFileRemoval("file-action", "file", "revision", "Family notes.txt")
        showAgent(AgentUiState(isReady = true, isPaired = true, pendingFileRemovals = listOf(approval)), onDeclineFile = { declined = it.actionId })

        compose.onNodeWithText("Approvals", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Remove Family notes.txt?").assertIsDisplayed()
        compose.onNodeWithText("Decline").performClick()

        compose.runOnIdle { assertEquals("file-action", declined) }
    }

    private fun showAgent(state: AgentUiState, onDeclineFile: (PendingFileRemoval) -> Unit = {}) {
        compose.setContent {
            DeviceHealthTheme {
                AgentScreen(
                    state = state,
                    onPairingCodeChange = {}, onControllerAddressChange = {}, onPair = {}, onSubmitAction = {},
                    onReviewApplicationRemoval = {}, onCancelApplicationRemoval = {},
                    onReviewFileRemoval = {}, onCancelFileRemoval = onDeclineFile,
                    onDismissFileRemoval = {}, onRemoveApplication = {}, onRemoveFile = {},
                    onConfirmFileRemoval = {}, onCollectFileInventory = {}, onSelectFileFolder = {}, onRequestMediaAccess = {},
                )
            }
        }
    }
}
