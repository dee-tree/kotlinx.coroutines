/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package benchmarks.base

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@State(Scope.Thread)
public abstract class Base {

    @Param("NO_PROBES", "DEFAULT", "CREATION_ST", "SANITIZE_ST", "C_S")
    public open lateinit var mode: Modes

    @Setup(Level.Iteration)
    public open fun setup(): Unit = Unit

    @TearDown(Level.Iteration)
    public open fun tearDown(): Unit = Unit

    public abstract fun run(blackhole: Blackhole)

    @Benchmark
    @Fork(1)
    public fun runBenchmark(blackhole: Blackhole) {
        run(blackhole)
    }

}

/**
 * NO_PROBES - mode with disabled DebugProbes
 * DEFAULT - mode with enabled DebugProbes without creation stack traces
 * CREATION_ST (C) - mode with enabled DebugProbes and with creation stack traces
 * SANITIZE_ST (S) - mode with enabled DebugProbes and with sanitizing stack traces
 *                   (makes sense only together with CREATION_ST or LAZY_CREATION_ST)
 * LAZY_CREATION_ST (L)` - mode with enabled DebugProbes and with lazy creation stack traces
 * C_S = CREATION_ST + SANITIZE_ST
 * C_L = CREATION_ST + LAZY_CREATION_ST
 * S_L = SANITIZE_ST + LAZY_CREATION_ST
 * C_S_L = CREATION_ST + SANITIZE_ST + LAZY_CREATION_ST
 */
public enum class Modes(public val value: Int) {
    NO_PROBES(0),
    DEFAULT(1),
    SANITIZE_ST(2 or DEFAULT.value),
    CREATION_ST(4 or DEFAULT.value),
    LAZY_CREATION_ST(8 or DEFAULT.value),
    C_S(CREATION_ST.value or SANITIZE_ST.value),
    C_L(CREATION_ST.value or LAZY_CREATION_ST.value),
    S_L(SANITIZE_ST.value or LAZY_CREATION_ST.value),
    C_S_L(CREATION_ST.value or SANITIZE_ST.value or LAZY_CREATION_ST.value);

    public val installedProbes: Boolean
        get() = value.isSet(0)

    public val sanitizeStackTraces: Boolean
        get() = value.isSet(1)

    public val creationStackTrace: Boolean
        get() = value.isSet(2)

    public val lazyCreationStackTraces: Boolean
        get() = value.isSet(3)
}

internal fun Int.isSet(bit: Int): Boolean = ((this shr bit) and 1) == 1
