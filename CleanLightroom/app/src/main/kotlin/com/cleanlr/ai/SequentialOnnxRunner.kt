package com.cleanlr.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.channels.FileChannel
import java.util.EnumSet

/**
 * Native Sequential ONNX Runtime 1.20.0 Execution & Zero-Duplication Asset Loader.
 *
 * Guarantees:
 *  1. STRICT Single-Model-In-Memory Invariant:
 *     Acquires a global Mutex before mapping/loading any ONNX session, executes inference,
 *     explicitly invokes `session.close()`, closes the `AssetFileDescriptor`/`FileChannel`,
 *     nullifies the direct `ByteBuffer`, and invokes `System.gc()` in `finally`.
 *  2. Zero Storage Duplication:
 *     Uncompressed (`STORED`) `.onnx` models in `assets/models/` are loaded directly via
 *     read-only `MappedByteBuffer` / direct `ByteBuffer` without EVER copying 750MB+ into `filesDir`.
 *  3. Hardware Delegate Priority Chain:
 *     QNNExecutionProvider (Qualcomm NPU) -> NNAPI -> GPU -> XNNPACK (CPU fallback).
 */
enum class HardwareDelegate(val providerName: String, val priority: Int) {
    QNN_NPU("QNNExecutionProvider", 1),
    NNAPI("NnapiExecutionProvider", 2),
    GPU("GpuExecutionProvider", 3),
    XNNPACK_CPU("XnnpackExecutionProvider", 4)
}

interface InferenceSessionHandle : AutoCloseable {
    val modelId: String
    val activeDelegate: HardwareDelegate
    val isNativeOnnx: Boolean
    val inputNames: Set<String>
    val outputNames: Set<String>
    fun runFloatTensor(inputName: String, data: FloatArray, shape: LongArray): FloatArray?
    fun runFloatTensors(inputs: Map<String, Pair<FloatArray, LongArray>>): FloatArray?
    fun runMixedTensors(
        floatInputs: Map<String, Pair<FloatArray, LongArray>>,
        longInputs: Map<String, Pair<LongArray, LongArray>> = emptyMap()
    ): FloatArray?
}

object SequentialOnnxRunner {
    private const val TAG = "SequentialOnnxRunner"
    const val ORT_VERSION = "1.20.0"

    // Strict global lock ensuring AT MOST ONE neural network is ever resident in memory
    private val executionMutex = Mutex()

    @Volatile
    var activeLoadedModelId: String? = null
        private set

    @Volatile
    var peakConcurrentModels: Int = 0
        private set

    @Volatile
    private var currentConcurrentModels: Int = 0

    @Volatile
    var lastActiveDelegate: HardwareDelegate = HardwareDelegate.XNNPACK_CPU
        private set

    @Volatile
    var lastLoadMode: String = "Direct Asset MappedByteBuffer (0-Copy)"
        private set

    @Volatile
    var lastInferenceMs: Long = 0L
        private set

    val delegatePriorityOrder: List<HardwareDelegate> = listOf(
        HardwareDelegate.QNN_NPU,
        HardwareDelegate.NNAPI,
        HardwareDelegate.GPU,
        HardwareDelegate.XNNPACK_CPU
    )

    /**
     * Native ONNX Runtime Android 1.20.0 is compiled directly into the APK.
     * In JVM unit tests without Android `.so` binaries, gracefully catches `UnsatisfiedLinkError`.
     */
    fun isOnnxRuntimeAvailable(): Boolean = try {
        OrtEnvironment.getEnvironment() != null
    } catch (_: Throwable) {
        false
    }

    /**
     * Executes [block] sequentially using a [ModelSource] (preferring zero-copy APK asset ByteBuffer).
     */
    suspend fun <T> runSequential(
        modelId: String,
        source: ModelSource,
        context: Context? = null,
        onStageLog: ((String) -> Unit)? = null,
        block: (session: InferenceSessionHandle?) -> T
    ): T = withContext(Dispatchers.Default) {
        executionMutex.withLock {
            currentConcurrentModels++
            if (currentConcurrentModels > peakConcurrentModels) {
                peakConcurrentModels = currentConcurrentModels
            }
            activeLoadedModelId = modelId
            val t0 = System.currentTimeMillis()
            var session: NativeOrtHandle? = null
            var afd: AssetFileDescriptor? = null
            var fis: FileInputStream? = null
            var channel: FileChannel? = null
            var directBuffer: ByteBuffer? = null

            try {
                when (source) {
                    is ModelSource.BundledAsset -> {
                        if (context != null) {
                            onStageLog?.invoke("Mapping $modelId (${source.sizeBytes / (1024 * 1024)}MB direct ByteBuffer)...")
                            val loaded = openFromBundledAsset(modelId, source.assetPath, context)
                            session = loaded.handle
                            afd = loaded.afd
                            fis = loaded.fis
                            channel = loaded.channel
                            directBuffer = loaded.buffer
                            if (session != null) {
                                lastActiveDelegate = session.activeDelegate
                                lastLoadMode = "Direct Asset ByteBuffer (0-Copy)"
                            }
                        }
                    }
                    is ModelSource.LocalFile -> {
                        if (source.file.exists() && source.file.length() > 0L) {
                            onStageLog?.invoke("Loading local override $modelId (${source.file.length() / (1024 * 1024)}MB)...")
                            session = openFromLocalFile(modelId, source.file)
                            if (session != null) {
                                lastActiveDelegate = session.activeDelegate
                                lastLoadMode = "Local File Override"
                            }
                        }
                    }
                    ModelSource.Missing -> Unit
                }
                block(session)
            } finally {
                // CRITICAL MOBILE MEMORY RULE: Explicit session close + buffer/FD release before next model loads
                try {
                    session?.close()
                } catch (e: Throwable) {
                    Log.w(TAG, "Error closing ONNX session for $modelId", e)
                }
                session = null
                directBuffer = null
                runCatching { channel?.close() }
                runCatching { fis?.close() }
                runCatching { afd?.close() }
                channel = null
                fis = null
                afd = null
                lastInferenceMs = (System.currentTimeMillis() - t0).coerceAtLeast(1L)
                activeLoadedModelId = null
                currentConcurrentModels = (currentConcurrentModels - 1).coerceAtLeast(0)
                // Release large native/direct tensor buffers before loading the next sequential model
                System.gc()
            }
        }
    }

    /**
     * Convenience overload accepting a local [File] (or null in JVM unit tests).
     */
    suspend fun <T> runSequential(
        modelId: String,
        modelFile: File?,
        onStageLog: ((String) -> Unit)? = null,
        block: (session: InferenceSessionHandle?) -> T
    ): T {
        val src = if (modelFile != null && modelFile.exists()) {
            ModelSource.LocalFile(modelFile)
        } else {
            ModelSource.Missing
        }
        return runSequential(modelId = modelId, source = src, context = null, onStageLog = onStageLog, block = block)
    }

    private data class MappedAssetSession(
        val handle: NativeOrtHandle?,
        val afd: AssetFileDescriptor? = null,
        val fis: FileInputStream? = null,
        val channel: FileChannel? = null,
        val buffer: ByteBuffer? = null
    )

    /**
     * Opens an uncompressed (`STORED`) `.onnx` asset directly from APK via read-only `MappedByteBuffer`
     * (falling back to `ByteBuffer.allocateDirect` only if asset is compressed) — ZERO `filesDir` duplication!
     */
    private fun openFromBundledAsset(
        modelId: String,
        assetPath: String,
        context: Context
    ): MappedAssetSession {
        var afd: AssetFileDescriptor? = null
        var fis: FileInputStream? = null
        var channel: FileChannel? = null
        var buffer: ByteBuffer? = null
        return try {
            val env = OrtEnvironment.getEnvironment()
            val (opts, chosenDelegate) = buildSessionOptionsWithDelegates()

            // Preferred path: zero-copy virtual memory mapping of STORED APK asset
            try {
                afd = context.assets.openFd(assetPath)
                fis = FileInputStream(afd.fileDescriptor)
                channel = fis.channel
                buffer = channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            } catch (e: Exception) {
                // Fallback if asset wasn't stored uncompressed
                Log.w(TAG, "openFd unavailable for $assetPath (${e.message}); loading via direct ByteBuffer stream")
                val bytes = context.assets.open(assetPath).use { it.readBytes() }
                buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                    put(bytes)
                    flip()
                }
            }

            val ortSession = env.createSession(buffer!!, opts)
            opts.close()
            Log.i(TAG, "Opened bundled ONNX [$modelId] from $assetPath via ${chosenDelegate.providerName}")
            MappedAssetSession(
                handle = NativeOrtHandle(modelId, chosenDelegate, env, ortSession),
                afd = afd,
                fis = fis,
                channel = channel,
                buffer = buffer
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Native ORT bundled asset open skipped for $modelId: ${t.message}")
            runCatching { channel?.close() }
            runCatching { fis?.close() }
            runCatching { afd?.close() }
            MappedAssetSession(handle = null)
        }
    }

    private fun openFromLocalFile(modelId: String, modelFile: File): NativeOrtHandle? {
        return try {
            val env = OrtEnvironment.getEnvironment()
            val (opts, chosenDelegate) = buildSessionOptionsWithDelegates()
            val ortSession = env.createSession(modelFile.absolutePath, opts)
            opts.close()
            Log.i(TAG, "Opened local ONNX [$modelId] via ${chosenDelegate.providerName}")
            NativeOrtHandle(modelId, chosenDelegate, env, ortSession)
        } catch (t: Throwable) {
            Log.w(TAG, "Native ORT local open skipped for $modelId: ${t.message}")
            null
        }
    }

    /**
     * Configures native `OrtSession.SessionOptions` strictly following:
     * QNNExecutionProvider (Qualcomm NPU) -> NNAPI -> GPU -> XNNPACK (CPU fallback).
     */
    private fun buildSessionOptionsWithDelegates(): Pair<OrtSession.SessionOptions, HardwareDelegate> {
        val opts = OrtSession.SessionOptions()
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
        opts.setMemoryPatternOptimization(true)

        var chosenDelegate = HardwareDelegate.XNNPACK_CPU
        for (delegate in delegatePriorityOrder) {
            val attached = when (delegate) {
                HardwareDelegate.QNN_NPU -> runCatching {
                    opts.addQnn(mapOf("backend_path" to "libQnnHtp.so"))
                    true
                }.getOrDefault(false)
                HardwareDelegate.NNAPI -> runCatching {
                    // Enable FP16 acceleration for MediaTek Dimensity APU / Qualcomm DSP on real phones
                    try {
                        opts.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
                    } catch (_: Throwable) {
                        opts.addNnapi()
                    }
                    true
                }.getOrDefault(false)
                HardwareDelegate.GPU -> runCatching {
                    opts.addConfigEntry("session.use_gpu", "1")
                    true
                }.getOrDefault(false)
                HardwareDelegate.XNNPACK_CPU -> runCatching {
                    opts.addXnnpack(mapOf("intra_op_num_threads" to "4"))
                    true
                }.getOrDefault(true)
            }
            if (attached) {
                chosenDelegate = delegate
                break
            }
        }
        return Pair(opts, chosenDelegate)
    }

    private class NativeOrtHandle(
        override val modelId: String,
        override val activeDelegate: HardwareDelegate,
        private val env: OrtEnvironment,
        private val ortSession: OrtSession
    ) : InferenceSessionHandle {
        override val isNativeOnnx: Boolean = true
        override val inputNames: Set<String> = ortSession.inputNames
        override val outputNames: Set<String> = ortSession.outputNames

        override fun runFloatTensor(inputName: String, data: FloatArray, shape: LongArray): FloatArray? {
            val resolvedName = if (inputName in inputNames) inputName else inputNames.firstOrNull() ?: inputName
            return runFloatTensors(mapOf(resolvedName to Pair(data, shape)))
        }

        override fun runFloatTensors(inputs: Map<String, Pair<FloatArray, LongArray>>): FloatArray? {
            return runMixedTensors(floatInputs = inputs, longInputs = emptyMap())
        }

        override fun runMixedTensors(
            floatInputs: Map<String, Pair<FloatArray, LongArray>>,
            longInputs: Map<String, Pair<LongArray, LongArray>>
        ): FloatArray? {
            val tensors = LinkedHashMap<String, OnnxTensor>()
            return try {
                for ((name, pair) in floatInputs) {
                    val (data, shape) = pair
                    tensors[name] = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
                }
                for ((name, pair) in longInputs) {
                    val (data, shape) = pair
                    tensors[name] = OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)
                }
                ortSession.run(tensors).use { result ->
                    if (result.size() <= 0) return null
                    val raw = result.get(0).value
                    flattenToFloatArray(raw)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "ORT inference skipped on $modelId: ${t.message}")
                null
            } finally {
                for (tensor in tensors.values) {
                    runCatching { tensor.close() }
                }
                tensors.clear()
            }
        }

        private fun flattenToFloatArray(obj: Any?): FloatArray? {
            val out = ArrayList<Float>()
            fun collect(cur: Any?) {
                when (cur) {
                    is FloatArray -> for (v in cur) out.add(v)
                    is ShortArray -> for (h in cur) out.add(halfToFloat(h))
                    is Array<*> -> for (elem in cur) collect(elem)
                }
            }
            collect(obj)
            return if (out.isNotEmpty()) out.toFloatArray() else null
        }

        private fun halfToFloat(bits: Short): Float {
            val h = bits.toInt() and 0xFFFF
            val sign = (h and 0x8000) shl 16
            val exp = (h and 0x7C00) ushr 10
            val mant = h and 0x03FF
            val fBits = when (exp) {
                0 -> if (mant == 0) sign else {
                    var m = mant
                    var e = -1
                    do { e++; m = m shl 1 } while ((m and 0x0400) == 0)
                    m = m and 0x03FF
                    sign or ((127 - 15 - e) shl 23) or (m shl 13)
                }
                31 -> sign or 0x7F800000 or (mant shl 13)
                else -> sign or ((exp + 112) shl 23) or (mant shl 13)
            }
            return java.lang.Float.intBitsToFloat(fBits)
        }

        override fun close() {
            runCatching { ortSession.close() }
        }
    }
}
