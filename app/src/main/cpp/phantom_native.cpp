#include <jni.h>

static jstring toJavaString(JNIEnv * env, const char * value) {
    return env->NewStringUTF(value);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeRuntimeStatus(JNIEnv * env, jobject) {
#if defined(PHANTOM_LLAMA_AVAILABLE) && defined(PHANTOM_WHISPER_AVAILABLE)
    return toJavaString(env, "llama.cpp and whisper.cpp linked; JNI inference adapter pending validation.");
#elif defined(PHANTOM_LLAMA_AVAILABLE)
    return toJavaString(env, "llama.cpp linked; Whisper backend unavailable. JNI inference adapter pending validation.");
#elif defined(PHANTOM_WHISPER_AVAILABLE)
    return toJavaString(env, "whisper.cpp linked; Llama backend unavailable. JNI inference adapter pending validation.");
#else
    return toJavaString(env, "Native inference libraries are not linked.");
#endif
}

// Deliberately fail closed until JNI model/context lifecycle and cancellation are implemented
// against the pinned upstream API and exercised on physical ARM hardware.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeLoadLlama(JNIEnv *, jobject, jstring) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeGenerate(JNIEnv * env, jobject, jstring, jint, jfloat, jfloat, jfloat) {
    return toJavaString(env, "Llama JNI generation adapter is not yet enabled in this build.");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeLoadWhisper(JNIEnv *, jobject, jstring) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeTranscribe(JNIEnv * env, jobject, jshortArray) {
    return toJavaString(env, "Whisper JNI transcription adapter is not yet enabled in this build.");
}
