package io.bitrise.bitkot.utils.test

import io.bitrise.bitkot.utils.TaskExecutorPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class CoroTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testTaskExecutorPool() = runTest {
        val taskExecutorPool = TaskExecutorPool(4, "test_pool")
        val values = listOf(0 until 12).flatten()
        val tasksInParallel = AtomicInteger(0)
        val maxTasksInParallel = AtomicInteger(0)
        val results = taskExecutorPool.runTasks(values.asSequence()) {
            try {
                val curInParallel = tasksInParallel.incrementAndGet()
                while (true) {
                    val curMaxInParallel = maxTasksInParallel.get()
                    if (curInParallel > curMaxInParallel) {
                        if (maxTasksInParallel.compareAndSet(curMaxInParallel, curInParallel))
                            break
                    } else {
                        break
                    }
                }
                withContext(Dispatchers.Default) { delay(it.toLong()) }
                return@runTasks it
            } finally {
                tasksInParallel.decrementAndGet()
            }
        }.toList()
        assertEquals(results.size, 12)
        results.forEach { assert(it.isSuccess) }
        assertEquals(maxTasksInParallel.get(), 4)
    }

}