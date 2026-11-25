#include <jni.h>
#include <string>

static jstring message(JNIEnv* env, const char* value) { return env->NewStringUTF(value); }

extern "C" JNIEXPORT jstring JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeRuntimeStatus(JNIEnv* env, jobject) {
#ifdef PHANTOM_WITH_LLAMA
    return message(env, "llama.cpp enabled; Whisper status depends on build flags.");
#else
    return message(env, "JNI loaded; llama.cpp and whisper.cpp sources are not initialized. See docs/native-build.md.");
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeLoadLlama(JNIEnv* env, jobject, jstring) { return JNI_FALSE; }
extern "C" JNIEXPORT jstring JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeGenerate(JNIEnv* env, jobject, jstring, jint, jfloat, jfloat, jfloat) {
    return message(env, "Llama backend is not linked in this build.");
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeLoadWhisper(JNIEnv* env, jobject, jstring) { return JNI_FALSE; }
extern "C" JNIEXPORT jstring JNICALL
Java_com_phantom_ghost_ai_NativeBridge_nativeTranscribe(JNIEnv* env, jobject, jshortArray) {
    return message(env, "Whisper backend is not linked in this build.");
}
