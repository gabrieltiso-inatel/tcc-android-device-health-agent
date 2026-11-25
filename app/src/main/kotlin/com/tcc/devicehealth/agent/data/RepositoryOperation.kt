package com.tcc.devicehealth.agent.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal suspend fun <T> repositoryOperation(dispatcher: CoroutineDispatcher, operation: suspend () -> T): Result<T> =
    withContext(dispatcher) {
        try {
            Result.success(operation())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
