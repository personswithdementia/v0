/*
 * kwada (C) 2026
 * Author: phedwin
 *
 * JNI bindings for native audio engine
 */

#include <android/log.h>
#include <jni.h>
#include "SimpleAudioEngine.h"

#define LOG_TAG "JNIBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static SimpleAudioEngine *g_engine = nullptr;

extern "C" {

JNIEXPORT void JNICALL Java_com_ongoma_AudioEngine_nativeInit(JNIEnv *env,
							      jobject thiz) {
	LOGI("═══ nativeInit CALLED ═══");
	if (g_engine == nullptr) {
		LOGI("Creating new SimpleAudioEngine instance...");
		g_engine = new SimpleAudioEngine();
		LOGI("Calling initialize()...");
		g_engine->initialize();
		LOGI("SimpleAudioEngine initialized successfully");
	} else {
		LOGI(
		    "SimpleAudioEngine already exists, skipping "
		    "initialization");
	}
}

JNIEXPORT void JNICALL
Java_com_ongoma_AudioEngine_nativeShutdown(JNIEnv *env, jobject thiz) {
	if (g_engine != nullptr) {
		delete g_engine;
		g_engine = nullptr;
	}
}

JNIEXPORT void JNICALL Java_com_ongoma_AudioEngine_nativePlayNote(
    JNIEnv *env, jobject thiz, jint midiNote) {
	if (g_engine != nullptr) {
		g_engine->playNote(static_cast<int>(midiNote));
	}
}

JNIEXPORT void JNICALL
Java_com_ongoma_AudioEngine_nativeStopNote(JNIEnv *env, jobject thiz) {
	if (g_engine != nullptr) {
		g_engine->stopNote();
	}
}

JNIEXPORT void JNICALL Java_com_ongoma_AudioEngine_nativePlayNotePolyphonic(
    JNIEnv *env, jobject thiz, jint midiNote) {
	LOGI("nativePlayNotePolyphonic called with MIDI=%d", midiNote);
	if (g_engine != nullptr) {
		LOGI("Engine exists, calling playNotePolyphonic(%d)", midiNote);
		g_engine->playNotePolyphonic(static_cast<int>(midiNote));
	} else {
		LOGE(
		    "⚠️ CRITICAL: g_engine is NULL - audio engine not "
		    "initialized!");
	}
}

JNIEXPORT void JNICALL Java_com_ongoma_AudioEngine_nativeStopNotePolyphonic(
    JNIEnv *env, jobject thiz, jint midiNote) {
	if (g_engine != nullptr) {
		g_engine->stopNotePolyphonic(static_cast<int>(midiNote));
	}
}

JNIEXPORT void JNICALL
Java_com_ongoma_AudioEngine_nativeStopAllNotes(JNIEnv *env, jobject thiz) {
	if (g_engine != nullptr) {
		g_engine->stopAllNotes();
	}
}

JNIEXPORT jdouble JNICALL
Java_com_ongoma_AudioEngine_nativeGetCurrentTime(JNIEnv *env, jobject thiz) {
	if (g_engine != nullptr) {
		return static_cast<jdouble>(g_engine->getCurrentTime());
	}
	return 0.0;
}

}
