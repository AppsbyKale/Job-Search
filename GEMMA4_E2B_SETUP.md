# Gemma 4 E2B — Complete Working Setup Reference

> Extracted from ChatAI (working app) for comparison against a non-working app
> that fails GPU load and falls back to CPU, then times out.

---

## 1. Dependencies (app/build.gradle.kts)

```kotlin
dependencies {
    // CRITICAL: coroutines 1.11.0 is a HARD PIN.
    // litertlm 0.16.1 bytecode calls SendChannel.close$default as a static
    // on the interface (google-ai-edge/LiteRT-LM#2812). Older coroutines
    // keep it on DefaultImpls → NoSuchMethodError crash after EVERY completed
    // generation. Do NOT downgrade.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // The LiteRT-LM runtime — this is the only inference dependency.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")

    // Everything else is app-specific (Hilt, Room, Compose, etc.)
    // but the two above are the ones that matter for model loading.
}
```

**Toolchain pins that matter:**
- Kotlin 2.2.21
- AGP 8.12.0
- compileSdk / targetSdk 35
- minSdk 29
- JVM target 17

---

## 2. AndroidManifest.xml — GPU Native Library Entries

These two lines are **required** for GPU backend to load. Without them, the
GPU backend silently fails and you fall back to CPU.

```xml
<application ... >
    <!-- These enable GPU backend loading via OpenCL/VNDK support.
         required="false" means the app still installs on devices without
         them, but GPU init will succeed when they ARE present. -->
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>

    <activity ... >
        ...
    </activity>
</application>
```

**If your other app is missing these, that's why GPU fails.**

---

## 3. Engine Initialization (LmChatEngine.kt — complete file)

```kotlin
package com.example.chatai.data.local

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the LiteRT-LM engine lifecycle for on-device inference.
 */
class LmChatEngine(
    private val appContext: Context,
) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    var activeBackend: String? = null
        private set

    /**
     * Initializes the model on the requested backend. GPU failures fall back
     * to CPU automatically so a missing driver never bricks chat.
     */
    @OptIn(ExperimentalApi::class)
    suspend fun initialize(
        model: File,
        useGpu: Boolean,
        enableMtp: Boolean,
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            // MTP = Multi-Token Prediction (speculative decoding).
            // ~2x decode speed, ~3x less RAM. Opt-in via Settings.
            ExperimentalFlags.enableSpeculativeDecoding = enableMtp

            if (useGpu) {
                try {
                    initializeWith(model, Backend.GPU())
                    activeBackend = "GPU"
                    return@runCatching model
                } catch (_: Throwable) {
                    release()  // Clean up failed GPU attempt before CPU retry
                }
            }
            initializeWith(model, Backend.CPU())
            activeBackend = "CPU"
            model
        }.onFailure { release() }
    }

    private fun initializeWith(model: File, backend: Backend) {
        val config = EngineConfig(
            modelPath = model.absolutePath,
            backend = backend,
            cacheDir = appContext.cacheDir.path,
        )
        Engine(config).also { created ->
            created.initialize()
            engine = created
            conversation = created.createConversation(chatConfig(SYSTEM_PROMPT))
        }
    }

    /**
     * Sampling + generation bounds shared by every conversation.
     *
     * Key settings:
     * - temperature 0.7, topK 40, topP 0.95 → diversity that prevents
     *   repetition loops while staying coherent
     * - maxOutputToken 1024 → guarantees streams always terminate
     *   (without this, the model can loop forever)
     */
    private fun chatConfig(
        systemInstruction: String,
        temperature: Double = 0.7,
        topK: Int = 40,
        history: List<Pair<Boolean, String>> = emptyList(),
    ) = ConversationConfig(
        systemInstruction = Contents.of(systemInstruction),
        initialMessages = history.map { (isUser, text) ->
            if (isUser) Message.user(text) else Message.model(text)
        },
        samplerConfig = SamplerConfig(
            topK = topK,
            topP = 0.95,
            temperature = temperature,
        ),
        maxOutputToken = MAX_OUTPUT_TOKENS,  // 1024
    )

    fun sendMessage(text: String): Flow<String> {
        val activeConversation = checkNotNull(conversation) { "Engine not initialized" }
        return activeConversation
            .sendMessageAsync(Contents.of(text))
            .map { message ->
                message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            }
            .flowOn(Dispatchers.Default)
    }

    /**
     * One-shot completion on a throwaway conversation. Used for Brain
     * side-channel tasks (routing, summarization). Cooler sampling for
     * structured output.
     */
    suspend fun completeOnce(systemInstruction: String, prompt: String): Result<String> =
        withContext(Dispatchers.Default) {
            runCatching {
                val activeEngine = checkNotNull(engine) { "Engine not initialized" }
                val scratch = activeEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemInstruction),
                        samplerConfig = SamplerConfig(topK = 20, topP = 0.95, temperature = 0.3),
                        maxOutputToken = MAX_OUTPUT_TOKENS,
                    )
                )
                try {
                    val output = StringBuilder()
                    scratch.sendMessageAsync(prompt)
                        .map { message ->
                            message.contents.contents
                                .filterIsInstance<Content.Text>()
                                .joinToString("") { it.text }
                        }
                        .collect { output.append(it) }
                    output.toString().trim()
                } finally {
                    runCatching { scratch.close() }
                }
            }
        }

    /**
     * Resets the main conversation with optional history prefills.
     * IMPORTANT: must recreate WITH chatConfig — bare createConversation
     * silently drops sampler/token caps.
     */
    suspend fun resetConversation(
        history: List<Pair<Boolean, String>> = emptyList()
    ) = withContext(Dispatchers.Default) {
        runCatching {
            conversation?.close()
            conversation = engine?.createConversation(
                chatConfig(SYSTEM_PROMPT, history = history)
            )
        }
    }

    fun isReady(): Boolean = conversation != null

    fun release() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        activeBackend = null
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a helpful assistant running fully offline on the device. " +
                "Keep answers concise unless asked for detail."

        /** Hard cap on output tokens per generation. Prevents infinite loops. */
        const val MAX_OUTPUT_TOKENS = 1024
    }
}
```

---

## 4. Token Limits

| Constant | Value | Where | Purpose |
|---|---|---|---|
| `MAX_OUTPUT_TOKENS` | 1024 | LmChatEngine | Hard cap per generation — prevents infinite repetition loops |
| `REPLY_CHAR_LIMIT` | 8000 chars | ChatViewModel | Backstop: stops collection if streamed text exceeds this |

**Per-response token count for E2B: 1024 tokens max.**

Without this cap, the model can fall into repetition attractors and stream
the same phrase forever. The 1024 cap guarantees every stream terminates.

---

## 5. GPU Fallback Logic

```
initialize(model, useGpu=true, enableMtp=false)
  │
  ├── Try GPU backend
  │   ├── Engine(EngineConfig(modelPath, Backend.GPU(), cacheDir))
  │   ├── engine.initialize()
  │   └── Success → activeBackend = "GPU" ✓
  │
  ├── GPU fails (catch Throwable)
  │   ├── release() ← clean up the failed engine completely
  │   └── Fall through to CPU
  │
  └── CPU backend
      ├── Engine(EngineConfig(modelPath, Backend.CPU(), cacheDir))
      ├── engine.initialize()
      └── Success → activeBackend = "CPU" (degraded but functional)
```

**Critical: `release()` is called between GPU failure and CPU retry.**
If you don't clean up the failed GPU engine, the CPU init can conflict.

---

## 6. Common Failure Modes (Your Other App)

### GPU load fails → CPU fallback → timeout

**Most likely causes:**

1. **Missing `uses-native-library` entries in AndroidManifest.xml.**
   Without `libOpenCL.so` and `libvndksupport.so` declared, the GPU backend
   can't find the OpenCL libraries it needs. Add:
   ```xml
   <uses-native-library android:name="libvndksupport.so" android:required="false"/>
   <uses-native-library android:name="libOpenCL.so" android:required="false"/>
   ```

2. **Wrong coroutines version.** If you're using coroutines < 1.11.0, every
   completed generation crashes with `NoSuchMethodError: close$default`.
   Pin to 1.11.0:
   ```kotlin
   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
   ```

3. **Missing `cacheDir` in EngineConfig.** The engine needs a cache directory
   for compiled GPU kernels. Pass `context.cacheDir.path`.

4. **No `maxOutputToken` set.** Without a token cap, the model can loop
   forever. Always set `maxOutputToken = 1024` in ConversationConfig.

5. **MTP (Multi-Token Prediction) incompatibility.** If
   `ExperimentalFlags.enableSpeculativeDecoding = true` and the model doesn't
   support it, initialization may fail. Try with MTP off first.

6. **GPU driver issues on specific devices.** Some Mali GPUs (common in
   Samsung/Exynos) have known issues with tool-call argument corruption.
   If GPU init succeeds but outputs are garbled, try CPU backend.

### Timeout specifically

If CPU works but is slow enough to timeout:

- E2B on CPU: expect 5-15 seconds for first token on a mid-range phone
- E2B on GPU: expect 1-3 seconds for first token
- If CPU is timing out, your timeout may be too low. Try 30+ seconds.
- Also check: is `completeOnce()` being called with a very long prompt?
  The model processes the entire prompt before emitting any tokens.

---

## 7. Minimal Working Example (No Hilt)

If you just want to get E2B running in a simple app:

```kotlin
// 1. Dependencies in build.gradle.kts
// implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
// implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")

// 2. AndroidManifest.xml — add inside <application>:
// <uses-native-library android:name="libvndksupport.so" android:required="false"/>
// <uses-native-library android:name="libOpenCL.so" android:required="false"/>

// 3. Initialize
class MyEngine(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    @OptIn(ExperimentalApi::class)
    suspend fun init(modelFile: File) = withContext(Dispatchers.Default) {
        // Try GPU first, fall back to CPU
        try {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.GPU(),
                cacheDir = context.cacheDir.path,
            )
            engine = Engine(config).also { it.initialize() }
        } catch (_: Throwable) {
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.path,
            )
            engine = Engine(config).also { it.initialize() }
        }

        conversation = engine?.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of("You are a helpful assistant."),
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
                maxOutputToken = 1024,
            )
        )
    }

    fun send(text: String): Flow<String> =
        conversation!!.sendMessageAsync(Contents.of(text))
            .map { msg ->
                msg.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
            }

    fun release() {
        conversation?.close()
        engine?.close()
    }
}
```

---

## 8. Checklist for Your Other App

- [ ] `litertlm-android:0.16.1` in dependencies
- [ ] `kotlinx-coroutines-android:1.11.0` in dependencies (HARD PIN)
- [ ] `<uses-native-library android:name="libvndksupport.so" android:required="false"/>` in AndroidManifest.xml
- [ ] `<uses-native-library android:name="libOpenCL.so" android:required="false"/>` in AndroidManifest.xml
- [ ] `EngineConfig` includes `cacheDir = context.cacheDir.path`
- [ ] `ConversationConfig` includes `maxOutputToken = 1024`
- [ ] GPU failure path calls `release()` before CPU retry
- [ ] Model file exists and is a valid `.litertlm` file
- [ ] Model file is readable (check with `file.canRead()`)
- [ ] `ExperimentalFlags.enableSpeculativeDecoding` set before init (not after)
- [ ] Timeout is generous enough (30+ seconds for cold init on CPU)
