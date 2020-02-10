package dev.whyoleg.ktd.client

import dev.whyoleg.ktd.*
import dev.whyoleg.ktd.api.*
import kotlinx.coroutines.*

class DeferredTdClient internal constructor(
    private val job: Job,
    api: AnyTdApi,
    runner: SynchronizedRunner,
    private val updatesCallback: TdUpdatesCallback
) : AbstractTdClient(api, runner), Job by job {
    private val requestsJob = SupervisorJob(job)

    override fun cancel(cause: CancellationException?) {
        close()
        job.cancel(cause)
    }

    override fun onClose() {
        job.cancel()
    }

    override fun onUpdate(update: TdUpdate): Unit = updatesCallback(update)

    fun send(request: TdApiRequest) {
        sendCallback(request)
    }

    suspend fun <R : TdResponse> exec(request: TdRequest<R>): R = Dispatchers.Default {
        CompletableDeferred<R>(requestsJob).also { sendCallback(request, it::complete, it::completeExceptionally) }.await()
    }
}


//TODO ext on [AnyTdApi]
@Suppress("FunctionName")
fun DeferredTdClient(
    api: AnyTdApi,
    parentJob: Job? = null,
    runner: SynchronizedRunner = DefaultSynchronizedRunner(),
    updatesCallback: TdUpdatesCallback = {}
): DeferredTdClient = DeferredTdClient(Job(parentJob), api, runner, updatesCallback)
