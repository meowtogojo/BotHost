#include <jni.h>
#include <android/log.h>

// Node.js Mobile's node::Start JNI bridge is intentionally left as the
// integration milestone. See README.md before enabling JavaScript execution.
// Do not call an unbounded native runtime directly from the UI thread.

extern "C" JNIEXPORT jstring JNICALL
Java_com_bothost_app_MainActivity_nodeRuntimeStatus(JNIEnv *env, jobject) {
    return env->NewStringUTF("Node.js Mobile native bridge not configured yet");
}
