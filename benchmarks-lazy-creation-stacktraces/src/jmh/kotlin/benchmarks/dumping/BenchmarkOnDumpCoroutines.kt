/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package benchmarks.dumping

import benchmarks.base.*
import kotlinx.coroutines.*
import kotlinx.coroutines.debug.DebugProbes
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole

abstract class BenchmarkOnDumpCoroutines : BaseBenchmark() {

    abstract fun runJob(): Job

    private lateinit var job: Job

    @Setup(Level.Invocation)
    fun onInvocationSetup() {
        job = runJob()
    }

    @TearDown(Level.Invocation)
    fun onInvocationTearDown() {
        if (job.isCompleted) throw IllegalStateException("Job cancelled before invocation tear down!")
        job.cancel("benchmark invocation finished")
    }

    @Param("false", "true")
    var getCreationStackTrace: Boolean = false

    override fun run(blackhole: Blackhole) {
        if (this.mode == Modes.NO_PROBES) return

        val dump = DebugProbes.dumpCoroutinesInfo()

        if (getCreationStackTrace) {
            dump.forEach {
                blackhole.consume(it.creationStackTrace)
            }
        } else {
            blackhole.consume(dump)
        }
    }
}


open class BenchmarkLongDelayOnDumpCoroutines : BenchmarkOnDumpCoroutines() {
    override fun runJob(): Job = GlobalScope.launch {
        delay(Long.MAX_VALUE)
    }
}

open class BenchmarkLotOfDelaysOnDumpCoroutines : BenchmarkOnDumpCoroutines() {
    override fun runJob(): Job = GlobalScope.launch {
        repeat(10_000) {
            delay(10)
        }
    }
}

open class BenchmarkLotOfCoroutinesOnDumpCoroutines : BenchmarkOnDumpCoroutines() {
    override fun runJob(): Job = GlobalScope.launch {
        repeat(1000) {
            launch {
                delay(10)
                delay(10)
                delay(Long.MAX_VALUE)
            }
        }
    }
}
