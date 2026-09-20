#include <jni.h>
#include <sys/auxv.h>

// Compiled for baseline ARMv8-A; no optional vendor runtime is linked here.
extern "C" JNIEXPORT jlong JNICALL
Java_com_example_meowmba_core_agent_runtime_NativeCpuCapabilities_arm64Hwcap(
    JNIEnv*, jobject) {
    return static_cast<jlong>(getauxval(AT_HWCAP));
}
