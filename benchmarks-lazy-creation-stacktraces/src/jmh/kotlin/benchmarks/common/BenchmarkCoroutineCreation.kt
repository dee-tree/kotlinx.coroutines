/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package benchmarks.common

import benchmarks.base.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.infra.Blackhole

open class BenchmarkRunBlocking : BaseBenchmark() {

    override fun run(blackhole: Blackhole) {
        runBlocking {
            blackhole.consume(null)
        }
    }
}

open class BenchmarkRunBlockingLaunch : BaseBenchmark() {

    override fun run(blackhole: Blackhole) {
        runBlocking {
            launch {
                blackhole.consume(null)
            }
        }
    }
}

open class BenchmarkLaunch : BaseBenchmark() {

    override fun run(blackhole: Blackhole) {
        val job = GlobalScope.launch {
            blackhole.consume(null)

        }
    }
}
