package com.tcc.devicehealth.agent

import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkerCompatibilityTest {
    @Test
    fun persistedWorkerClassNameRemainsAvailable() {
        val workerClass = Class.forName("com.tcc.devicehealth.agent.ActionPollingWorker")
            .asSubclass(ListenableWorker::class.java)

        assertEquals(ActionPollingWorker::class.java, workerClass)
    }
}
