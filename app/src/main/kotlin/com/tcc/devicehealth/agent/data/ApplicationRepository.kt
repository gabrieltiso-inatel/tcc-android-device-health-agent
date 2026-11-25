package com.tcc.devicehealth.agent.data

import com.tcc.devicehealth.agent.data.android.PackageManagerDataSource
import com.tcc.devicehealth.agent.data.local.AgentPreferencesDataSource
import com.tcc.devicehealth.agent.data.remote.ControllerApiClient
import com.tcc.devicehealth.agent.model.Action
import com.tcc.devicehealth.agent.model.ApplicationInventory
import com.tcc.devicehealth.agent.model.PendingApplicationRemoval
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ApplicationRepository {
    suspend fun collectApplications(): ApplicationInventory
    suspend fun syncApplications(): Result<Unit>
    suspend fun validateApplicationRemoval(action: Action)
    suspend fun validateApplicationRemoval(approval: PendingApplicationRemoval)
    suspend fun isApplicationRemoved(approval: PendingApplicationRemoval): Boolean
}

internal class AndroidApplicationRepository(
    private val preferences: AgentPreferencesDataSource,
    private val controllerClient: ControllerApiClient,
    private val packageManagerDataSource: PackageManagerDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ApplicationRepository {
    override suspend fun collectApplications(): ApplicationInventory = withContext(ioDispatcher) {
        packageManagerDataSource.collect()
    }

    override suspend fun syncApplications(): Result<Unit> = repositoryOperation(ioDispatcher) {
        val token = checkNotNull(preferences.getToken()) { "Device is not paired" }
        controllerClient.sendAppInventory(preferences.getDeviceId(), collectApplications(), token)
    }

    override suspend fun validateApplicationRemoval(action: Action) = withContext(ioDispatcher) {
        packageManagerDataSource.validateRemoval(action)
    }

    override suspend fun validateApplicationRemoval(approval: PendingApplicationRemoval) = withContext(ioDispatcher) {
        packageManagerDataSource.validateRemoval(approval)
    }

    override suspend fun isApplicationRemoved(approval: PendingApplicationRemoval): Boolean = withContext(ioDispatcher) {
        packageManagerDataSource.isRemoved(approval)
    }
}
