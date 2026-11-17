#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cstdint>
#include <atomic>
#include <thread>
#include <new>
#include <cstdio>
#include <cstring>
#include <string>

#undef LOG_TAG
#define LOG_TAG "NativeCarver"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM* g_vm = nullptr;
static std::atomic<bool> g_cancelFlag{false};
static std::atomic<bool> g_scanRunning{false};
static std::string g_outputDir;
static int g_fileCounter = 0;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

static bool attachIfNeeded(JNIEnv** outEnv, bool* didAttach) {
    *didAttach = false;
    jint rc = g_vm->GetEnv(reinterpret_cast<void**>(outEnv), JNI_VERSION_1_6);
    if (rc == JNI_OK) return true;
    if (rc != JNI_EDETACHED) return false;
    if (g_vm->AttachCurrentThread(outEnv, nullptr) != JNI_OK) return false;
    *didAttach = true;
    return true;
}

struct ScanContext {
    int      fd;
    jlong    totalSize;
    jobject  listenerGlobalRef;
    jmethodID reportProgressId;
    jmethodID onFileRecoveredId; // New callback
    jmethodID onScanCompleteId;
};

// Helper to generate a new filename
void getNextFilename(const char* ext, char* outPath, size_t maxLen) {
    snprintf(outPath, maxLen, "%s/carved_%d.%s", g_outputDir.c_str(), g_fileCounter++, ext);
}

static void scanWorker(ScanContext* ctx) {
    JNIEnv* env = nullptr;
    bool didAttach = false;
    if (!attachIfNeeded(&env, &didAttach)) return;

    constexpr size_t CHUNK_SIZE = 4u * 1024u * 1024u; // 4 MB
    auto* buffer = new (std::nothrow) uint8_t[CHUNK_SIZE];
    if (!buffer) return;

    jlong offset = 0;
    int lastPct = -1;

    FILE* outFile = nullptr;
    enum FileType { NONE, JPEG, PNG, PDF } currentType = NONE;
    char currentPath[512];

    while (!g_cancelFlag.load(std::memory_order_relaxed)) {
        ssize_t n = pread(ctx->fd, buffer, CHUNK_SIZE, offset);
        if (n <= 0) break;

        for (ssize_t i = 0; i < n; ++i) {
            // 1. Detect JPEG Start (FF D8 FF)
            if (currentType == NONE && i + 2 < n && buffer[i] == 0xFF && buffer[i+1] == 0xD8 && buffer[i+2] == 0xFF) {
                currentType = JPEG;
                getNextFilename("jpg", currentPath, sizeof(currentPath));
                outFile = fopen(currentPath, "wb");
            }
            // 2. Detect PNG Start (89 50 4E 47 0D 0A 1A 0A)
            else if (currentType == NONE && i + 7 < n && buffer[i] == 0x89 && buffer[i+1] == 0x50 && buffer[i+2] == 0x4E && buffer[i+3] == 0x47) {
                currentType = PNG;
                getNextFilename("png", currentPath, sizeof(currentPath));
                outFile = fopen(currentPath, "wb");
            }
            // 3. Detect PDF Start (%PDF-)
            else if (currentType == NONE && i + 4 < n && buffer[i] == '%' && buffer[i+1] == 'P' && buffer[i+2] == 'D' && buffer[i+3] == 'F') {
                currentType = PDF;
                getNextFilename("pdf", currentPath, sizeof(currentPath));
                outFile = fopen(currentPath, "wb");
            }

            if (currentType != NONE && outFile != nullptr) {
                fputc(buffer[i], outFile);

                // Check for footers
                if (currentType == JPEG && i >= 1 && buffer[i-1] == 0xFF && buffer[i] == 0xD9) {
                    fclose(outFile); outFile = nullptr;
                    env->CallVoidMethod(ctx->listenerGlobalRef, ctx->onFileRecoveredId, env->NewStringUTF(currentPath));
                    currentType = NONE;
                }
                else if (currentType == PNG && i >= 7 && buffer[i-7] == 0x49 && buffer[i-6] == 0x45 && buffer[i-5] == 0x4E && buffer[i-4] == 0x44 && buffer[i-3] == 0xAE && buffer[i-2] == 0x42 && buffer[i-1] == 0x60 && buffer[i] == 0x82) {
                    fclose(outFile); outFile = nullptr;
                    env->CallVoidMethod(ctx->listenerGlobalRef, ctx->onFileRecoveredId, env->NewStringUTF(currentPath));
                    currentType = NONE;
                }
                else if (currentType == PDF && i >= 4 && buffer[i-4] == '%' && buffer[i-3] == '%' && buffer[i-2] == 'E' && buffer[i-1] == 'O' && buffer[i] == 'F') {
                    fclose(outFile); outFile = nullptr;
                    env->CallVoidMethod(ctx->listenerGlobalRef, ctx->onFileRecoveredId, env->NewStringUTF(currentPath));
                    currentType = NONE;
                }
            }
        }

        offset += n;
        int pct = (int)((offset * 100LL) / ctx->totalSize);
        if (pct != lastPct) {
            env->CallVoidMethod(ctx->listenerGlobalRef, ctx->reportProgressId, pct);
            if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
            lastPct = pct;
        }
    }

    if (outFile) fclose(outFile);
    jstring jErr = nullptr;
    env->CallVoidMethod(ctx->listenerGlobalRef, ctx->onScanCompleteId, (jboolean)true, jErr);

    delete[] buffer;
    close(ctx->fd);
    env->DeleteGlobalRef(ctx->listenerGlobalRef);
    if (didAttach) g_vm->DetachCurrentThread();
    delete ctx;
    g_scanRunning.store(false, std::memory_order_release);
}

extern "C" JNIEXPORT void JNICALL
Java_com_androidrecoveryapp_recovery_NativeCarver_startScan(
        JNIEnv* env, jobject, jstring jDevicePath, jlong jTotalSize, jstring jOutputDir, jobject jListener) {

    if (g_scanRunning.exchange(true)) return;
    g_cancelFlag.store(false);

    const char* path = env->GetStringUTFChars(jDevicePath, nullptr);
    const char* outDir = env->GetStringUTFChars(jOutputDir, nullptr);
    g_outputDir = outDir;
    g_fileCounter = 0;
    
    int fd = open(path, O_RDONLY);
    env->ReleaseStringUTFChars(jDevicePath, path);
    env->ReleaseStringUTFChars(jOutputDir, outDir);

    if (fd < 0) { g_scanRunning.store(false); return; }

    jobject globalRef = env->NewGlobalRef(jListener);
    jclass cls = env->GetObjectClass(globalRef);
    jmethodID reportId = env->GetMethodID(cls, "reportProgress", "(I)V");
    jmethodID fileRecoveredId = env->GetMethodID(cls, "onFileRecovered", "(Ljava/lang/String;)V"); // New
    jmethodID finishId = env->GetMethodID(cls, "onScanComplete", "(ZLjava/lang/String;)V");
    env->DeleteLocalRef(cls);

    auto* ctx = new ScanContext{fd, jTotalSize, globalRef, reportId, fileRecoveredId, finishId};
    std::thread(scanWorker, ctx).detach();
}

extern "C" JNIEXPORT void JNICALL
Java_com_androidrecoveryapp_recovery_NativeCarver_requestStop(JNIEnv*, jobject) {
    g_cancelFlag.store(true);
}