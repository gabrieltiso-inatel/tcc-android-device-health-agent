package com.tcc.devicehealth.agent

import android.content.Context
import com.tcc.devicehealth.agent.actions.ActionExecutor
import com.tcc.devicehealth.agent.data.ActionRepository
import com.tcc.devicehealth.agent.data.AndroidActionRepository
import com.tcc.devicehealth.agent.data.AndroidApplicationRepository
import com.tcc.devicehealth.agent.data.AndroidDeviceRepository
import com.tcc.devicehealth.agent.data.AndroidFileRepository
import com.tcc.devicehealth.agent.data.ApplicationRepository
import com.tcc.devicehealth.agent.data.DeviceRepository
import com.tcc.devicehealth.agent.data.FileRepository
import com.tcc.devicehealth.agent.data.android.ActionNotificationDataSource
import com.tcc.devicehealth.agent.data.android.DeviceTelemetryDataSource
import com.tcc.devicehealth.agent.data.android.DocumentTreeDataSource
import com.tcc.devicehealth.agent.data.android.MediaStoreDataSource
import com.tcc.devicehealth.agent.data.android.PackageManagerDataSource
import com.tcc.devicehealth.agent.data.android.StorageDataSource
import com.tcc.devicehealth.agent.data.local.AgentPreferencesDataSource
import com.tcc.devicehealth.agent.data.local.LocalActionDataSource
import com.tcc.devicehealth.agent.data.remote.ControllerApiClient
import kotlinx.coroutines.sync.Mutex

internal class AgentDependencies(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = AgentPreferencesDataSource(appContext)
    private val apiClient = ControllerApiClient { deviceRepository.getControllerAddress() }

    val deviceRepository: DeviceRepository = AndroidDeviceRepository(
        controllerBaseUrl = BuildConfig.CONTROLLER_BASE_URL,
        preferences = preferences,
        controllerClient = apiClient,
        telemetrySource = DeviceTelemetryDataSource(appContext, preferences),
        storageDataSource = StorageDataSource(),
    )

    val applicationRepository: ApplicationRepository = AndroidApplicationRepository(
        preferences, apiClient, PackageManagerDataSource(appContext.packageManager, appContext.packageName),
    )

    val fileRepository: FileRepository = AndroidFileRepository(
        preferences, apiClient, MediaStoreDataSource(appContext), DocumentTreeDataSource(appContext),
    )

    val actionRepository: ActionRepository = AndroidActionRepository(
        preferences = preferences,
        localActions = LocalActionDataSource(appContext),
        controllerClient = apiClient,
        deviceRepository = deviceRepository,
        applicationRepository = applicationRepository,
        fileRepository = fileRepository,
        actionExecutor = ActionExecutor(deviceRepository, applicationRepository, fileRepository),
        approvalNotificationDataSource = ActionNotificationDataSource(appContext),
        lifecycleMutex = actionLifecycleMutex,
    )

    private companion object {
        val actionLifecycleMutex = Mutex()
    }
}
