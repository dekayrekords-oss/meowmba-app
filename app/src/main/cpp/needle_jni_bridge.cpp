#include <jni.h>
#include <string>
#include <vector>

extern "C" {
int needle_load(const unsigned char* cact, unsigned long long n);
int needle_init(const char* system_prompt, const char* tools_json, const char* tool_index_path);
int needle_complete(const char* input, int max_new_tokens, char* out, int out_capacity);
void needle_reset(void);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_meowmba_core_agent_runtime_NeedleJniNative_load(
    JNIEnv* env, jobject, jbyteArray bytes) {
    const jsize size = env->GetArrayLength(bytes);
    jbyte* data = env->GetByteArrayElements(bytes, nullptr);
    const int result = needle_load(reinterpret_cast<unsigned char*>(data), size);
    env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_meowmba_core_agent_runtime_NeedleJniNative_initialize(
    JNIEnv* env, jobject, jstring system, jstring tools) {
    const char* system_chars = env->GetStringUTFChars(system, nullptr);
    const char* tools_chars = env->GetStringUTFChars(tools, nullptr);
    const int result = needle_init(system_chars, tools_chars, nullptr);
    env->ReleaseStringUTFChars(system, system_chars);
    env->ReleaseStringUTFChars(tools, tools_chars);
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_meowmba_core_agent_runtime_NeedleJniNative_complete(
    JNIEnv* env, jobject, jstring input, jint max_tokens) {
    const char* input_chars = env->GetStringUTFChars(input, nullptr);
    std::vector<char> output(65536, '\0');
    const int result = needle_complete(input_chars, max_tokens, output.data(), output.size());
    env->ReleaseStringUTFChars(input, input_chars);
    if (result < 0) return nullptr;
    return env->NewStringUTF(output.data());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_meowmba_core_agent_runtime_NeedleJniNative_reset(JNIEnv*, jobject) {
    needle_reset();
}
