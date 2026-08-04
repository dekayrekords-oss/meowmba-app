#include <android/log.h>
#include <android/bitmap.h>
#include <jni.h>
#include <cstring>
#include "stable-diffusion.h"

#define LOG_TAG "SDCppBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_meowmba_core_media_SDCppNative_nativeCreateContext(
    JNIEnv* env, jobject /* this */, jstring jModelPath, jint nThreads) {
    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);

    sd_ctx_params_t params;
    sd_ctx_params_init(&params);
    params.model_path = modelPath;
    params.n_threads = nThreads;
    params.wtype = SD_TYPE_Q8_0;  // matches the GGUF quant the model catalog downloads

    sd_ctx_t* ctx = new_sd_ctx(&params);
    env->ReleaseStringUTFChars(jModelPath, modelPath);

    if (ctx == nullptr) {
        LOGE("new_sd_ctx returned null — model failed to load");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

// Returns a jbyteArray of raw RGB bytes (width*height*channel), or null on failure.
// Caller (Kotlin) receives width/height/channel via the int[] out-parameter.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_meowmba_core_media_SDCppNative_nativeGenerate(
    JNIEnv* env, jobject /* this */, jlong ctxPtr, jstring jPrompt,
    jint width, jint height, jint steps, jlong seed, jintArray outDims) {
    if (ctxPtr == 0) {
        LOGE("nativeGenerate called with null context");
        return nullptr;
    }
    sd_ctx_t* ctx = reinterpret_cast<sd_ctx_t*>(ctxPtr);
    const char* prompt = env->GetStringUTFChars(jPrompt, nullptr);

    sd_sample_params_t sampleParams;
    sd_sample_params_init(&sampleParams);
    sampleParams.sample_steps = steps;

    sd_img_gen_params_t genParams;
    sd_img_gen_params_init(&genParams);
    genParams.prompt = prompt;
    genParams.width = width;
    genParams.height = height;
    genParams.sample_params = sampleParams;
    genParams.seed = static_cast<int64_t>(seed);
    genParams.batch_count = 1;

    sd_image_t* images = nullptr;
    int numImages = 0;
    bool ok = generate_image(ctx, &genParams, &images, &numImages);
    env->ReleaseStringUTFChars(jPrompt, prompt);

    if (!ok || images == nullptr || numImages < 1) {
        LOGE("generate_image failed or returned no images");
        return nullptr;
    }

    sd_image_t img = images[0];
    size_t byteCount = static_cast<size_t>(img.width) * img.height * img.channel;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(byteCount));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(byteCount),
                             reinterpret_cast<jbyte*>(img.data));

    jint dims[3] = {static_cast<jint>(img.width), static_cast<jint>(img.height),
                     static_cast<jint>(img.channel)};
    env->SetIntArrayRegion(outDims, 0, 3, dims);

    free_sd_images(images, numImages);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_meowmba_core_media_SDCppNative_nativeFreeContext(
    JNIEnv* env, jobject /* this */, jlong ctxPtr) {
    if (ctxPtr != 0) {
        free_sd_ctx(reinterpret_cast<sd_ctx_t*>(ctxPtr));
    }
}
