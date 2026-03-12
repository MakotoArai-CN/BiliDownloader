package com.bilidownloader.app.util

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentLinkedQueue

data class DownloadTask(
    val id: String,
    val videoId: String,
    val cid: Long,
    val title: String,
    val quality: Int,
    val onExecute: suspend () -> Unit
)

object DownloadQueueManager {
    private const val TAG = "DownloadQueueManager"
    private const val DEFAULT_MAX_CONCURRENT = 2

    private var maxConcurrent = DEFAULT_MAX_CONCURRENT
    private lateinit var semaphore: Semaphore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val waitingQueue = ConcurrentLinkedQueue<DownloadTask>()
    private val runningTasks = mutableMapOf<String, DownloadTask>()
    private val completedTasks = mutableSetOf<String>()
    private val failedTasks = mutableMapOf<String, Exception>()

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    data class QueueState(
        val running: Int = 0,
        val waiting: Int = 0,
        val completed: Int = 0,
        val failed: Int = 0,
        val runningTaskNames: List<String> = emptyList(),
        val waitingTaskNames: List<String> = emptyList()
    )

    init {
        initSemaphore(DEFAULT_MAX_CONCURRENT)
    }

    private fun initSemaphore(permits: Int) {
        maxConcurrent = permits
        semaphore = Semaphore(permits)
    }

    fun setMaxConcurrent(max: Int) {
        if (max in 1..10) {
            initSemaphore(max)
            Log.d(TAG, "Max concurrent downloads set to: $max")
        }
    }

    /**
     * Enqueue a task and launch it asynchronously.
     * Returns immediately - does not block until the task completes.
     * Each page (video+audio+merge) is ONE task.
     */
    fun enqueue(task: DownloadTask) {
        waitingQueue.offer(task)
        updateQueueState()
        Log.d(TAG, "Task enqueued: ${task.title}, Queue size: ${waitingQueue.size}")

        scope.launch {
            executeNext()
        }
    }

    /**
     * Enqueue all tasks and suspend until every one of them completes.
     * Throws the first encountered exception if any task fails.
     */
    suspend fun enqueueAllAndAwait(tasks: List<DownloadTask>) {
        if (tasks.isEmpty()) return

        completedTasks.clear()
        failedTasks.clear()

        val jobs = tasks.map { task ->
            waitingQueue.offer(task)
            updateQueueState()
            Log.d(TAG, "Task enqueued: ${task.title}, Queue size: ${waitingQueue.size}")

            scope.launch {
                executeNext()
            }
        }

        jobs.joinAll()

        val firstError = failedTasks.values.firstOrNull()
        if (firstError != null) {
            throw firstError
        }
    }

    private suspend fun executeNext() {
        val task = waitingQueue.poll() ?: return

        semaphore.acquire()

        synchronized(runningTasks) {
            runningTasks[task.id] = task
        }
        updateQueueState()

        Log.d(TAG, "Executing task: ${task.title}")

        try {
            task.onExecute()
            synchronized(completedTasks) {
                completedTasks.add(task.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed: ${task.title}", e)
            synchronized(failedTasks) {
                failedTasks[task.id] = e
            }
        } finally {
            synchronized(runningTasks) {
                runningTasks.remove(task.id)
            }
            semaphore.release()
            updateQueueState()

            // Process next waiting task
            if (waitingQueue.isNotEmpty()) {
                executeNext()
            }
        }
    }

    private fun updateQueueState() {
        val runningList = synchronized(runningTasks) { runningTasks.values.map { it.title } }
        val waitingList = waitingQueue.map { it.title }

        _queueState.value = QueueState(
            running = runningList.size,
            waiting = waitingList.size,
            completed = synchronized(completedTasks) { completedTasks.size },
            failed = synchronized(failedTasks) { failedTasks.size },
            runningTaskNames = runningList,
            waitingTaskNames = waitingList
        )
    }

    fun clear() {
        waitingQueue.clear()
        synchronized(completedTasks) { completedTasks.clear() }
        synchronized(failedTasks) { failedTasks.clear() }
        updateQueueState()
        Log.d(TAG, "Queue cleared")
    }
}
