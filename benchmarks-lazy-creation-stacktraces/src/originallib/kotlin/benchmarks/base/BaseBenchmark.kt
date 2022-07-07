/*
 * Copyright 2016-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package benchmarks.base

import kotlinx.coroutines.debug.DebugProbes

public abstract class BaseBenchmark : Base() {

    override fun setup() {
        super.setup()

        if (mode.installedProbes) {
            DebugProbes.install()
        }

        DebugProbes.enableCreationStackTraces = mode.creationStackTrace
        DebugProbes.sanitizeStackTraces = mode.sanitizeStackTraces
    }

    override fun tearDown() {
        super.tearDown()

        if (mode.installedProbes) {
            DebugProbes.uninstall()
        }
    }
}
