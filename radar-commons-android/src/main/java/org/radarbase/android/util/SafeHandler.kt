package org.radarbase.android.util

import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.Keep
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference
import java.util.concurrent.ExecutionException
import java.util.concurrent.SynchronousQueue

class SafeHandler(val name: String, private val priority: Int) {
    private var handlerThread: HandlerThread? = null

    @get:Synchronized
    val isStarted: Boolean
        get() = handler != null

    @get:Synchronized
    var handler: Handler? = null
        private set

    @Synchronized
    fun start() {
        if (isStarted) {
            logger.warn("Tried to start SafeHandler multiple times.")
            return
        }

        handlerThread = HandlerThread(name, priority).also {
            it.start()
            handler = Handler(it.looper)
        }
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    fun await(runnable: Runnable) = compute { runnable.run() }

    @Throws(InterruptedException::class, ExecutionException::class)
    fun await(runnable: () -> Unit) = compute(runnable)

    @Throws(InterruptedException::class, ExecutionException::class)
    fun <T> compute(method: () -> T): T {
        if (Thread.currentThread() == handlerThread) {
            try {
                return method()
            } catch (ex: Exception) {
                throw ExecutionException(ex)
            }
        } else {
            val queue = SynchronousQueue<Any>()
            execute {
                try {
                    queue.put(method() ?: nullMarker)
                } catch (ex: Exception) {
                    queue.put(ExecutionException(ex))
                }
            }
            val result = queue.take()
            @Suppress("UNCHECKED_CAST")
            return when {
                result === nullMarker -> null
                result is ExecutionException -> throw result
                else -> result
            } as T
        }
    }

    private fun <T> tryRunOrNull(callable: () -> T): T? {
        return try {
            callable()
        } catch (ex: Exception) {
            logger.error("Failed to run posted runnable", ex)
            null
        }
    }

    fun execute(runnable: Runnable) = execute(false, runnable::run)

    fun execute(runnable: () -> Unit) = execute(false, runnable)

    fun executeReentrant(runnable: () -> Unit) {
        if (Thread.currentThread() == handlerThread) {
            tryRunOrNull(runnable)
        } else {
            execute(runnable)
        }
    }

    fun executeReentrant(runnable: Runnable) = executeReentrant(runnable::run)

    fun execute(defaultToCurrentThread: Boolean, runnable: Runnable) = execute(defaultToCurrentThread, runnable::run)

    fun execute(defaultToCurrentThread: Boolean, runnable: () -> Unit) {
        val didRun = synchronized(this) {
            handler?.post { tryRunOrNull(runnable) }
        } ?: false

        if (!didRun && defaultToCurrentThread) {
            tryRunOrNull(runnable)
        }
    }

    fun delay(delay: Long, runnable: Runnable): HandlerFuture? = delay(delay, runnable::run)

    @Synchronized
    fun delay(delay: Long, runnable: () -> Unit): HandlerFuture? {
        return handler?.let {
            val r = Runnable {
                tryRunOrNull(runnable)
            }
            it.postDelayed(r, delay)
            HandlerFutureRef(r)
        }
    }

    fun repeatWhile(delay: Long, runnable: RepeatableRunnable): HandlerFuture? = repeatWhile(delay, runnable::runAndRepeat)

    fun repeatWhile(delay: Long, runnable: () -> Boolean): HandlerFuture? {
        return this.delay(delay) {
            if (tryRunOrNull(runnable) == true) repeatWhile(delay, runnable)
        }
    }

    fun repeat(delay: Long, runnable: () -> Unit): HandlerFuture? {
        return this.delay(delay) {
            tryRunOrNull(runnable)
            repeat(delay, runnable)
        }
    }

    fun stop(finalization: Runnable) = stop(finalization::run)

    @Synchronized
    fun stop(finalization: (() -> Unit)? = null) {
        handlerThread?.let { thread ->
            val oldHandler = handler
            handler = null
            finalization?.let {
                oldHandler?.post { tryRunOrNull(it) } ?: tryRunOrNull(it)
            }
            thread.quitSafely()
            handlerThread = null
        } ?: logger.warn("Tried to stop SafeHandler multiple times.")
    }

    interface RepeatableRunnable {
        fun runAndRepeat(): Boolean
    }

    @Keep
    interface HandlerFuture {
        @Throws(InterruptedException::class, ExecutionException::class)
        fun awaitNow()
        fun runNow()
        fun cancel()
    }

    private inner class HandlerFutureRef(val runnable: Runnable): HandlerFuture {
        override fun awaitNow() {
            synchronized(this@SafeHandler) {
                handler?.apply {
                    removeCallbacks(runnable)
                }
                await(runnable)
            }
        }
        override fun runNow() {
            synchronized(this@SafeHandler) {
                handler?.apply {
                    removeCallbacks(runnable)
                }
                executeReentrant(runnable)
            }
        }
        override fun cancel() {
            synchronized(this@SafeHandler) {
                handler?.removeCallbacks(runnable)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SafeHandler::class.java)
        private val nullMarker = object : Any() {}
        private val map: MutableMap<String, WeakReference<SafeHandler>> = HashMap()

        @Synchronized
        fun getInstance(name: String, priority: Int): SafeHandler {
            val handlerRef = map[name]?.get()
            return if (handlerRef != null) {
                handlerRef
            } else {
                val handler = SafeHandler(name, priority)
                map[name] = WeakReference(handler)
                handler
            }
        }
    }
}
