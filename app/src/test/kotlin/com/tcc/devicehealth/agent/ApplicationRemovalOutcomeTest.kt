package com.tcc.devicehealth.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApplicationRemovalOutcomeTest {
    @Test
    fun reviewRequiresConfirmationWhenTheApplicationIsStillValid() {
        val result = applicationRemovalReviewResult(isRemoved = false, validationFailure = null)

        assertNull(result)
    }

    @Test
    fun reviewCompletesWhenTheApplicationIsAlreadyAbsent() {
        val result = applicationRemovalReviewResult(isRemoved = true, validationFailure = null)

        assertEquals("completed", result?.status)
        assertEquals("already_absent", result?.errorCode)
        assertEquals(true, result?.succeeded)
    }

    @Test
    fun reviewFailsWhenTheApplicationChangedSinceItWasSelected() {
        val result = applicationRemovalReviewResult(isRemoved = false, validationFailure = ApplicationChangedException())

        assertEquals("failed", result?.status)
        assertEquals("application_changed", result?.errorCode)
    }

    @Test
    fun declinedRemovalIsCancelledEvenWhenTheApplicationIsAbsent() {
        val result = applicationRemovalDecisionResult(ApplicationRemovalDecision.Declined, isRemoved = true)

        assertEquals("cancelled", result.status)
        assertEquals("approval_declined", result.errorCode)
    }

    @Test
    fun acceptedConfirmationCompletesWhenTheApplicationWasRemoved() {
        val result = applicationRemovalDecisionResult(ApplicationRemovalDecision.ConfirmationAccepted, isRemoved = true)

        assertEquals("completed", result.status)
        assertEquals("application_removed", result.errorCode)
        assertEquals(true, result.succeeded)
    }

    @Test
    fun dismissedConfirmationCompletesWhenTheApplicationWasRemoved() {
        val result = applicationRemovalDecisionResult(ApplicationRemovalDecision.ConfirmationDismissed, isRemoved = true)

        assertEquals("completed", result.status)
        assertEquals("application_removed", result.errorCode)
    }

    @Test
    fun acceptedConfirmationFailsWhenTheApplicationIsStillInstalled() {
        val result = applicationRemovalDecisionResult(ApplicationRemovalDecision.ConfirmationAccepted, isRemoved = false)

        assertEquals("failed", result.status)
        assertEquals("execution_failed", result.errorCode)
    }

    @Test
    fun dismissedConfirmationIsCancelledWhenTheApplicationIsStillInstalled() {
        val result = applicationRemovalDecisionResult(ApplicationRemovalDecision.ConfirmationDismissed, isRemoved = false)

        assertEquals("cancelled", result.status)
        assertEquals("approval_declined", result.errorCode)
    }
}
