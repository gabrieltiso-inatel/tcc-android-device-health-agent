package com.tcc.devicehealth.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.tcc.devicehealth.agent.data.android.ApplicationNotAllowedException
import com.tcc.devicehealth.agent.data.android.PackageManagerDataSource
import com.tcc.devicehealth.agent.model.Action
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PackageManagerDataSourceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val source = PackageManagerDataSource(context.packageManager, context.packageName)

    @Test
    fun validatingRemovalKeepsTheApplicationInstalled() {
        val target = InstrumentationRegistry.getInstrumentation().context.packageName

        source.validateRemoval(removalAction(target))

        assertNotNull(context.packageManager.getApplicationInfo(target, 0))
    }

    @Test
    fun validatingRemovalRejectsTheAgentItself() {
        assertThrows(ApplicationNotAllowedException::class.java) {
            source.validateRemoval(removalAction(context.packageName))
        }
    }

    private fun removalAction(applicationId: String) = Action(
        id = "application-approval-test",
        type = "removeApplication",
        applicationId = applicationId,
        origin = "device",
        status = "delivered",
        requestedAt = "2026-11-18T12:00:00Z",
    )
}
