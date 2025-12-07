package com.bilidownloader.app.util

import android.util.Log
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
    
    private val waitingQueue = ConcurrentLinkedQueue<DownloadTask>()
    private val runningTasks = mutableMapOf<String, DownloadTask>()
    
    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()
    
    data class QueueState(
        val running: Int = 0,
        val waiting: Int = 0,
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
    
    suspend fun enqueue(task: DownloadTask) {
        waitingQueue.offer(task)
        updateQueueState()
        Log.d(TAG, "Task enqueued: ${task.title}, Queue size: ${waitingQueue.size}")
        
        executeNext()
    }
    
    private suspend fun executeNext() {
        val task = waitingQueue.poll() ?: return
        
        semaphore.acquire()
        
        runningTasks[task.id] = task
        updateQueueState()
        
        Log.d(TAG, "Executing task: ${task.title}")
        
        try {
            task.onExecute()
        } catch (e: Exception) {
            Log.e(TAG, "Task execution failed: ${task.title}", e)
        } finally {
            runningTasks.remove(task.id)
            semaphore.release()
            updateQueueState()
            
            if (waitingQueue.isNotEmpty()) {
                executeNext()
            }
        }
    }
    
    private fun updateQueueState() {
        val runningList = runningTasks.values.map { it.title }
        val waitingList = waitingQueue.map { it.title }
        
        _queueState.value = QueueState(
            running = runningTasks.size,
            waiting = waitingQueue.size,
            runningTaskNames = runningList,
            waitingTaskNames = waitingList
        )
    }
    
    fun clear() {
        waitingQueue.clear()
        updateQueueState()
        Log.d(TAG, "Queue cleared")
    }
}