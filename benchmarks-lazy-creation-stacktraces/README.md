# Benchmarks of DebugProbes dump coroutines

### Ah?

When `DebugProbes` **enabled**, `DebugProbes.dumpCoroutinesInfo()` catches info about all living coroutines.
Suppose that `DebugProbes.enableCreationStackTraces == true`, then each coroutine creation 
triggers stacktrace extracting from `Exception`. This operation is so *hard*, 
therefore we can optimize it for coroutines dumping usecase by 
storing Exception on coroutine creation and **lazily** extraction of stacktrace
on `CoroutineInfo.creationStackTrace` request. We can achieve it using `DebugProbes.lazyCreationStackTraces` mode.

---

#### ATTENTION

**When `DebugProbes.lazyCreationStackTraces` enabled, creation stacktraces 
won't be passed to `CoroutineOwner` and *debugger*, but only for `CoroutineInfo`**.

---

### What's measured?

1. `BenchmarkCoroutineCreation` measures coroutines creation (including `kotlinx.coroutines.debug.internal#probeCoroutineCreated`) time on different modes of `DebugProbes` enabled
2. 'BenchmarkOnDumpCoroutines' measures execution time of `DebugProbes.dumpCoroutinesInfo()` and/or getting access to `CoroutineInfo.creationStackTrace`

### Measurement profiles:

1. `originallib` is default measurement profile based on official `1.6.0` coroutines 
2. `patchedlib` is a profile based on optimized (via lazy mode) computation of coroutines creation stacktraces with `DebugProbes`


### Measurement modes:

`getCreationStackTrace` is for `OnDump`-benchmarks used for call creationStackTrace after receiving `CoroutineInfo` via *dump*.

* `NO_PROBES` - mode with disabled DebugProbes
* `DEFAULT` - mode with enabled only DebugProbes
* `CREATION_ST (C)` - mode with enabled DebugProbes and enabled creation stack traces
* `SANITIZE_ST (S)` - mode with enabled DebugProbes and enabled sanitizing stack traces
* `C_S` = `CREATION_ST` + `SANITIZE_ST`
---
Modes, specific for `patchedlib` profile:
* `LAZY_CREATION_ST (L)` - mode with enabled DebugProbes and enabled lazy creation stack traces. **Only for patched lib!**
* `C_L` = `CREATION_ST` + `LAZY_CREATION_ST`
* `S_L` = `SANITIZE_ST` + `LAZY_CREATION_ST`
* `C_S_L` = `CREATION_ST` + `SANITIZE_ST` + `LAZY_CREATION_ST`

---

### How to run?

* `patchedlib` profile: `gradle :benchmarks-lazy-creation-stacktraces:jmhRun -Ppatchedlib`
* `originallib` profile: `gradle :benchmarks-lazy-creation-stacktraces:jmhRun`

---

### Benchmark results

* On CPU: Intel® Core™ i7-4790 × 8
* On GPU: Mesa Intel® HD Graphics 4600
* Under Arch x64, GNOME

```
Benchmark                                                                             (getCreationStackTrace)            (mode)    Mode     Cnt      Score      Error  Units
b.common.BenchmarkLaunch.runBenchmark                                                                     N/A         NO_PROBES  sample  380995      1.675 ±    0.016  us/op
b.common.BenchmarkLaunch.runBenchmark                                                                     N/A           DEFAULT  sample  329757      1.914 ±    0.027  us/op
b.common.BenchmarkLaunch.runBenchmark                                                                     N/A  LAZY_CREATION_ST  sample  341473      1.856 ±    0.034  us/op

b.common.BenchmarkLaunch.runBenchmark                                                                     N/A       CREATION_ST  sample  222073     11.264 ±    0.082  us/op
b.common.BenchmarkLaunch.runBenchmark                                                                     N/A               C_L  sample  343342      3.674 ±    0.062  us/op

b.common.BenchmarkLaunch.runBenchmark                                                                     N/A       SANITIZE_ST  sample  336404      1.874 ±    0.022  us/op
b.common.BenchmarkLaunch.runBenchmark                                                                     N/A               S_L  sample  337188      1.876 ±    0.033  us/op

b.common.BenchmarkLaunch.runBenchmark                                                                     N/A               C_S  sample  207532     12.025 ±    0.083  us/op
b.common.BenchmarkLaunch.runBenchmark                                                                     N/A             C_S_L  sample  333241      3.753 ±    0.043  us/op
----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
b.common.BenchmarkRunBlocking.runBenchmark                                                                N/A         NO_PROBES  sample  337925      0.252 ±    0.002  us/op
b.common.BenchmarkRunBlocking.runBenchmark                                                                N/A           DEFAULT  sample  313752      0.560 ±    0.014  us/op
b.common.BenchmarkRunBlocking.runBenchmark                                                                N/A  LAZY_CREATION_ST  sample  309866      0.569 ±    0.017  us/op

b.common.BenchmarkRunBlocking.runBenchmark                                                                N/A       CREATION_ST  sample  268614      9.271 ±    0.080  us/op
b.common.BenchmarkRunBlocking.runBenchmark                                                                N/A               C_L  sample  342111      1.830 ±    0.016  us/op

b.common.BenchmarkRunBlocking.runBenchmark                                                                N/A       SANITIZE_ST  sample  307692      0.519 ±    0.004  us/op
b.common.BenchmarkRunBlocking.runBenchmark                                                                N/A               S_L  sample  310961      0.511 ±    0.008  us/op

b.common.BenchmarkRunBlocking.runBenchmark                                                                N/A               C_S  sample  267820      9.304 ±    0.078  us/op
b.common.BenchmarkRunBlocking.runBenchmark                                                                N/A             C_S_L  sample  346259      1.850 ±    0.013  us/op
----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
b.common.BenchmarkRunBlockingLaunch.runBenchmark                                                          N/A         NO_PROBES  sample  235479      0.704 ±    0.019  us/op
b.common.BenchmarkRunBlockingLaunch.runBenchmark                                                          N/A           DEFAULT  sample  227838      1.422 ±    0.036  us/op
b.common.BenchmarkRunBlockingLaunch.runBenchmark                                                          N/A  LAZY_CREATION_ST  sample  229064      1.406 ±    0.019  us/op

b.common.BenchmarkRunBlockingLaunch.runBenchmark                                                          N/A       CREATION_ST  sample  230005     21.710 ±    0.104  us/op
b.common.BenchmarkRunBlockingLaunch.runBenchmark                                                          N/A               C_L  sample  267427      4.672 ±    0.048  us/op

b.common.BenchmarkRunBlockingLaunch.runBenchmark                                                          N/A       SANITIZE_ST  sample  229349      1.406 ±    0.022  us/op
b.common.BenchmarkRunBlockingLaunch.runBenchmark                                                          N/A               S_L  sample  220046      1.463 ±    0.022  us/op

b.common.BenchmarkRunBlockingLaunch.runBenchmark                                                          N/A               C_S  sample  226679     22.028 ±    0.106  us/op
b.common.BenchmarkRunBlockingLaunch.runBenchmark                                                          N/A             C_S_L  sample  270738      4.609 ±    0.013  us/op
----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                               false         NO_PROBES  sample  232842      0.031 ±    0.001  us/op
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                               false           DEFAULT  sample  345640      1.796 ±    0.033  us/op
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                               false  LAZY_CREATION_ST  sample  180991      1.747 ±    0.063  us/op

b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                               false       CREATION_ST  sample  119969     27.145 ±    0.096  us/op
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                               false               C_L  sample  296458      0.750 ±    0.033  us/op

b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                               false       SANITIZE_ST  sample  329982      1.933 ±    0.043  us/op
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                               false               S_L  sample  178604      1.777 ±    0.018  us/op

b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                               false               C_S  sample  122156     25.645 ±    0.184  us/op
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                               false             C_S_L  sample  297792      0.821 ±    0.050  us/op

b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                                true         NO_PROBES  sample  236415      0.029 ±    0.001  us/op
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                                true           DEFAULT  sample  348956      1.804 ±    0.037  us/op
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                                true  LAZY_CREATION_ST  sample  333080      1.878 ±    0.030  us/op

b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                                true       CREATION_ST  sample  150611     50.388 ±    0.105  us/op
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                                true               C_L  sample  153847     25.222 ±    0.079  us/op

b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                                true       SANITIZE_ST  sample  343465      1.756 ±    0.041  us/op
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                                true               S_L  sample  175242      1.853 ±    0.086  us/op

b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                                true               C_S  sample  157855     47.008 ±    0.093  us/op
b.dumping.BenchmarkLongDelayOnDumpCoroutines.runBenchmark                                                true             C_S_L  sample  168358     22.593 ±    0.060  us/op
----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                         false         NO_PROBES  sample  245174      0.032 ±    0.001  us/op
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                         false           DEFAULT  sample   40826    223.966 ±    3.438  us/op
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                         false  LAZY_CREATION_ST  sample   39151    230.600 ±    3.753  us/op

b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                         false       CREATION_ST  sample     589  16941.878 ± 2480.057  us/op
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                         false               C_L  sample   22889    415.108 ±    9.249  us/op

b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                         false       SANITIZE_ST  sample   37266    242.036 ±    4.021  us/op
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                         false               S_L  sample   39052    235.809 ±    3.760  us/op

b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                         false               C_S  sample     660  15089.013 ± 2138.017  us/op
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                         false             C_S_L  sample   22269    427.761 ±    9.607  us/op

b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                          true         NO_PROBES  sample  247501      0.034 ±    0.002  us/op
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                          true           DEFAULT  sample   37583    236.445 ±    3.949  us/op
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                          true  LAZY_CREATION_ST  sample   34993    248.202 ±    4.597  us/op

b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                          true       CREATION_ST  sample    2671   3538.949 ±  211.481  us/op
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                          true               C_L  sample    5153   1318.085 ±   67.605  us/op

b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                          true       SANITIZE_ST  sample   38298    235.181 ±    4.049  us/op
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                          true               S_L  sample   38883    229.202 ±    3.705  us/op

b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                          true               C_S  sample    2854   3311.271 ±  184.425  us/op
b.dumping.BenchmarkLotOfCoroutinesOnDumpCoroutines.runBenchmark                                          true             C_S_L  sample    5757   1187.322 ±   55.459  us/op
----------------------------------------------------------------------------------------------------------------------------------------------------------------------------
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                             false         NO_PROBES  sample  234109      0.030 ±    0.001  us/op
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                             false           DEFAULT  sample  333071      1.623 ±    0.072  us/op
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                             false  LAZY_CREATION_ST  sample  329918      1.627 ±    0.050  us/op

b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                             false       CREATION_ST  sample  116029     27.991 ±    0.108  us/op
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                             false               C_L  sample  340621      0.708 ±    0.058  us/op

b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                             false       SANITIZE_ST  sample  345250      1.517 ±    0.014  us/op
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                             false               S_L  sample  336154      1.599 ±    0.026  us/op

b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                             false               C_S  sample  125140     25.419 ±    0.097  us/op
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                             false             C_S_L  sample  184286      0.615 ±    0.014  us/op

b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                              true         NO_PROBES  sample  230369      0.030 ±    0.002  us/op
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                              true           DEFAULT  sample  346128      1.472 ±    0.040  us/op
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                              true  LAZY_CREATION_ST  sample  331747      1.621 ±    0.041  us/op

b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                              true       CREATION_ST  sample  144443     52.535 ±    0.108  us/op
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                              true               C_L  sample  145300     26.550 ±    0.078  us/op

b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                              true       SANITIZE_ST  sample  331192      1.680 ±    0.045  us/op
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                              true               S_L  sample  322544      1.774 ±    0.028  us/op

b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                              true               C_S  sample  151536     49.141 ±    0.085  us/op
b.dumping.BenchmarkLotOfDelaysOnDumpCoroutines.runBenchmark                                              true             C_S_L  sample  158956     24.008 ±    0.070  us/op
```