#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <new>
#include <string>
#include <thread>
#include <vector>
#if defined(PHANTOM_LLAMA_AVAILABLE)
#include "llama.h"
#endif
#if defined(PHANTOM_WHISPER_AVAILABLE)
#include "whisper.h"
#endif

#define LOG_TAG "PhantomNative"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
std::mutex llamaMutex, whisperMutex;
std::atomic<bool> cancelGeneration{false};
#if defined(PHANTOM_LLAMA_AVAILABLE)
llama_model *model = nullptr;
llama_context *context = nullptr;
bool backendReady = false;
#endif
#if defined(PHANTOM_WHISPER_AVAILABLE)
whisper_context *whisper = nullptr;
#endif
jstring javaString(JNIEnv *env, const char *s) { return env->NewStringUTF(s ? s : ""); }
void throwJava(JNIEnv *env, const char *type, const char *message) {
    jclass cls = env->FindClass(type);
    if (cls) env->ThrowNew(cls, message);
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeRuntimeStatus(JNIEnv *env, jobject) {
#if defined(PHANTOM_LLAMA_AVAILABLE) && defined(PHANTOM_WHISPER_AVAILABLE)
    return javaString(env, "llama.cpp and whisper.cpp JNI adapters enabled");
#elif defined(PHANTOM_LLAMA_AVAILABLE)
    return javaString(env, "llama.cpp enabled; Whisper unavailable");
#elif defined(PHANTOM_WHISPER_AVAILABLE)
    return javaString(env, "whisper.cpp enabled; Llama unavailable");
#else
    return javaString(env, "Native libraries unavailable");
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeLoadLlama(JNIEnv *env, jobject, jstring path) {
#if defined(PHANTOM_LLAMA_AVAILABLE)
    if (!path) return JNI_FALSE;
    const char *chars = env->GetStringUTFChars(path, nullptr);
    if (!chars) return JNI_FALSE;
    const std::string modelPath(chars);
    env->ReleaseStringUTFChars(path, chars);
    std::lock_guard<std::mutex> guard(llamaMutex);
    try {
        if (!backendReady) { llama_backend_init(); backendReady = true; }
        if (context) { llama_free(context); context = nullptr; }
        if (model) { llama_model_free(model); model = nullptr; }
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        model = llama_model_load_from_file(modelPath.c_str(), mp);
        if (!model) {
            throwJava(env, "java/lang/IllegalStateException", "llama.cpp could not load this GGUF model.");
            return JNI_FALSE;
        }
        const int threads = std::max(2, std::min(4, static_cast<int>(std::thread::hardware_concurrency())));
        const uint32_t sizes[] = {2048, 1024, 512};
        for (uint32_t nctx : sizes) {
            auto cp = llama_context_default_params();
            cp.n_ctx = nctx;
            cp.n_batch = std::min<uint32_t>(512, nctx);
            cp.n_ubatch = std::min<uint32_t>(128, nctx);
            cp.n_threads = threads;
            cp.n_threads_batch = threads;
            try { context = llama_init_from_model(model, cp); }
            catch (const std::bad_alloc &) { context = nullptr; }
            if (context) break;
            LOGE("Context allocation failed at %u tokens; trying smaller context", nctx);
        }
        if (!context) {
            llama_model_free(model); model = nullptr;
            throwJava(env, "java/lang/OutOfMemoryError", "Unable to allocate Llama context at 2048, 1024, or 512 tokens.");
            return JNI_FALSE;
        }
        return JNI_TRUE;
    } catch (const std::bad_alloc &) {
        if (context) { llama_free(context); context = nullptr; }
        if (model) { llama_model_free(model); model = nullptr; }
        throwJava(env, "java/lang/OutOfMemoryError", "Native allocation failed while loading the Llama model.");
        return JNI_FALSE;
    } catch (...) {
        throwJava(env, "java/lang/IllegalStateException", "Unexpected native error while loading Llama.");
        return JNI_FALSE;
    }
#else
    (void)env; (void)path; return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeGenerate(
        JNIEnv *env, jobject receiver, jstring prompt, jint maxTokens,
        jfloat temperature, jfloat topP, jfloat repeatPenalty) {
#if defined(PHANTOM_LLAMA_AVAILABLE)
    if (!prompt || maxTokens < 1 || maxTokens > 512) return JNI_FALSE;
    const char *chars = env->GetStringUTFChars(prompt, nullptr);
    if (!chars) return JNI_FALSE;
    const std::string promptText(chars);
    env->ReleaseStringUTFChars(prompt, chars);
    std::lock_guard<std::mutex> guard(llamaMutex);
    cancelGeneration.store(false);
    jclass cls = env->GetObjectClass(receiver);
    jmethodID callback = cls ? env->GetMethodID(cls, "onTokenGenerated", "(Ljava/lang/String;)V") : nullptr;
    if (!callback) {
        throwJava(env, "java/lang/IllegalStateException", "Kotlin token callback is missing.");
        return JNI_FALSE;
    }
    try {
        if (!model || !context) {
            throwJava(env, "java/lang/IllegalStateException", "Load a GGUF model before generation.");
            return JNI_FALSE;
        }
        const llama_vocab *vocab = llama_model_get_vocab(model);
        int32_t needed = llama_tokenize(vocab, promptText.c_str(), static_cast<int32_t>(promptText.size()), nullptr, 0, true, true);
        if (needed >= 0) needed = -needed;
        std::vector<llama_token> tokens(static_cast<size_t>(-needed) + 8);
        const int32_t nTokens = llama_tokenize(vocab, promptText.c_str(), static_cast<int32_t>(promptText.size()),
                                               tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
        if (nTokens <= 0) {
            throwJava(env, "java/lang/IllegalArgumentException", "Prompt tokenization failed.");
            return JNI_FALSE;
        }
        tokens.resize(static_cast<size_t>(nTokens));
        const size_t reserve = static_cast<size_t>(maxTokens) + 1;
        const size_t limit = llama_n_ctx(context);
        if (tokens.size() + reserve > limit) {
            const size_t keep = limit > reserve ? limit - reserve : 1;
            tokens.erase(tokens.begin(), tokens.end() - static_cast<std::ptrdiff_t>(keep));
        }
        llama_memory_clear(llama_get_memory(context), true);
        const size_t batchLimit = std::max<uint32_t>(1, llama_n_batch(context));
        for (size_t offset = 0; offset < tokens.size();) {
            if (cancelGeneration.load()) return JNI_FALSE;
            const int32_t count = static_cast<int32_t>(std::min(batchLimit, tokens.size() - offset));
            auto batch = llama_batch_get_one(tokens.data() + offset, count);
            if (llama_decode(context, batch) < 0) {
                throwJava(env, "java/lang/IllegalStateException", "llama_decode failed while evaluating the prompt.");
                return JNI_FALSE;
            }
            offset += static_cast<size_t>(count);
        }

        auto sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        if (!sampler) throw std::bad_alloc();
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
                llama_vocab_n_tokens(vocab), 64, std::max(0.8f, repeatPenalty), 0.0f, 0.0f));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(std::clamp(topP, 0.05f, 1.0f), 1));
        if (temperature <= 0.001f) {
            llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
        } else {
            llama_sampler_chain_add(sampler, llama_sampler_init_temp(std::clamp(temperature, 0.05f, 2.0f)));
            llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        }

        std::vector<char> piece(4096);
        for (jint i = 0; i < maxTokens && !cancelGeneration.load(); ++i) {
            const llama_token token = llama_sampler_sample(sampler, context, -1);
            if (llama_vocab_is_eog(vocab, token)) break;
            llama_sampler_accept(sampler, token);
            int32_t len = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
            if (len < 0) {
                piece.resize(static_cast<size_t>(-len) + 1);
                len = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, true);
            }
            if (len > 0) {
                std::string bytes(piece.data(), static_cast<size_t>(len));
                jstring value = env->NewStringUTF(bytes.c_str());
                if (value) {
                    env->CallVoidMethod(receiver, callback, value);
                    env->DeleteLocalRef(value);
                }
                if (env->ExceptionCheck()) { llama_sampler_free(sampler); return JNI_FALSE; }
            }
            llama_token next = token;
            auto batch = llama_batch_get_one(&next, 1);
            if (llama_decode(context, batch) < 0) {
                llama_sampler_free(sampler);
                throwJava(env, "java/lang/IllegalStateException", "llama_decode failed during generation.");
                return JNI_FALSE;
            }
        }
        llama_sampler_free(sampler);
        return cancelGeneration.load() ? JNI_FALSE : JNI_TRUE;
    } catch (const std::bad_alloc &) {
        throwJava(env, "java/lang/OutOfMemoryError", "Native generation ran out of memory; use a smaller model or context.");
        return JNI_FALSE;
    } catch (...) {
        throwJava(env, "java/lang/IllegalStateException", "Unexpected native generation error.");
        return JNI_FALSE;
    }
#else
    (void)env; (void)receiver; (void)prompt; (void)maxTokens; (void)temperature; (void)topP; (void)repeatPenalty;
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeCancelGeneration(JNIEnv *, jobject) {
    cancelGeneration.store(true);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeLoadWhisper(JNIEnv *env, jobject, jstring path) {
#if defined(PHANTOM_WHISPER_AVAILABLE)
    if (!path) return JNI_FALSE;
    const char *chars = env->GetStringUTFChars(path, nullptr);
    if (!chars) return JNI_FALSE;
    const std::string modelPath(chars);
    env->ReleaseStringUTFChars(path, chars);
    std::lock_guard<std::mutex> guard(whisperMutex);
    if (whisper) { whisper_free(whisper); whisper = nullptr; }
    auto params = whisper_context_default_params();
    params.use_gpu = false;
    whisper = whisper_init_from_file_with_params(modelPath.c_str(), params);
    if (!whisper) {
        throwJava(env, "java/lang/IllegalStateException", "whisper.cpp could not load this model.");
        return JNI_FALSE;
    }
    return JNI_TRUE;
#else
    (void)env; (void)path; return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeTranscribe(JNIEnv *env, jobject, jshortArray pcm) {
#if defined(PHANTOM_WHISPER_AVAILABLE)
    if (!pcm) return javaString(env, "");
    std::lock_guard<std::mutex> guard(whisperMutex);
    if (!whisper) {
        throwJava(env, "java/lang/IllegalStateException", "Load a Whisper model before transcription.");
        return nullptr;
    }
    const jsize count = env->GetArrayLength(pcm);
    if (count <= 0) return javaString(env, "");
    jshort *raw = env->GetShortArrayElements(pcm, nullptr);
    if (!raw) return nullptr;
    std::vector<float> samples(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) samples[static_cast<size_t>(i)] = static_cast<float>(raw[i]) / 32768.0f;
    env->ReleaseShortArrayElements(pcm, raw, JNI_ABORT);
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = 2;
    params.no_context = true;
    params.no_timestamps = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.language = "auto";
    if (whisper_full(whisper, params, samples.data(), static_cast<int>(samples.size())) != 0) {
        throwJava(env, "java/lang/IllegalStateException", "whisper_full failed; input must be mono 16 kHz PCM16.");
        return nullptr;
    }
    std::string result;
    for (int i = 0; i < whisper_full_n_segments(whisper); ++i) {
        const char *segment = whisper_full_get_segment_text(whisper, i);
        if (segment) result += segment;
    }
    return env->NewStringUTF(result.c_str());
#else
    (void)env; (void)pcm; return javaString(env, "Whisper backend unavailable.");
#endif
}
