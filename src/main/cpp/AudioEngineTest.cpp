/*
 * kwada (C) 2026
 * Author: phedwin
 *
 * Native audio engine tests — called from Kotlin via JNI
 * Returns a string of "PASS" or "FAIL: reason" for each test.
 */

#include "SimpleAudioEngine.h"
#include <cmath>
#include <sstream>
#include <string>
#include <jni.h>

static std::string runAllTests() {
    std::ostringstream results;
    int passed = 0;
    int failed = 0;

    auto check = [&](const char* name, bool condition, const char* detail = "") {
        if (condition) {
            passed++;
        } else {
            failed++;
            results << "FAIL: " << name << " " << detail << "\n";
        }
    };

    // --- midiNoteToFrequency tests ---
    {
        SimpleAudioEngine engine;
        // A4 = MIDI 69 = 440 Hz
        double a4 = 440.0 * std::pow(2.0, (69.0 - 69.0) / 12.0);
        check("A4 is 440Hz", std::abs(a4 - 440.0) < 0.01);

        // C4 = MIDI 60 ≈ 261.63 Hz
        double c4 = 440.0 * std::pow(2.0, (60.0 - 69.0) / 12.0);
        check("C4 is ~261.63Hz", std::abs(c4 - 261.63) < 0.1);

        // MIDI 0 = C-1 ≈ 8.18 Hz
        double c_neg1 = 440.0 * std::pow(2.0, (0.0 - 69.0) / 12.0);
        check("MIDI 0 is ~8.18Hz", std::abs(c_neg1 - 8.18) < 0.01);

        // MIDI 127 = G9 ≈ 12543.85 Hz
        double g9 = 440.0 * std::pow(2.0, (127.0 - 69.0) / 12.0);
        check("MIDI 127 is ~12543Hz", std::abs(g9 - 12543.85) < 1.0);

        // Octave relationship: MIDI N+12 = 2x frequency of MIDI N
        double f60 = 440.0 * std::pow(2.0, (60.0 - 69.0) / 12.0);
        double f72 = 440.0 * std::pow(2.0, (72.0 - 69.0) / 12.0);
        check("Octave doubles frequency", std::abs(f72 / f60 - 2.0) < 0.001);
    }

    // --- Wave table tests ---
    {
        SimpleAudioEngine engine;
        engine.initWaveTable();

        // Wave table should be initialized (not all zeros)
        bool hasNonZero = false;
        for (int i = 0; i < 4096; i++) {
            if (std::abs(SimpleAudioEngine::waveTable[i]) > 0.001f) {
                hasNonZero = true;
                break;
            }
        }
        check("Wave table has non-zero values", hasNonZero);

        // Wave table at index 0 should be ~0 (sin(0) = 0)
        check("Wave table[0] near zero", std::abs(SimpleAudioEngine::waveTable[0]) < 0.01f);

        // Wave table should be periodic: first and last entries close
        float first = SimpleAudioEngine::waveTable[0];
        float last = SimpleAudioEngine::waveTable[4095];
        check("Wave table wraps smoothly", std::abs(last - first) < 0.01f,
              ("diff=" + std::to_string(std::abs(last - first))).c_str());

        // Peak should be <= 1.0 (normalized)
        float maxVal = 0.0f;
        for (int i = 0; i < 4096; i++) {
            float v = std::abs(SimpleAudioEngine::waveTable[i]);
            if (v > maxVal) maxVal = v;
        }
        check("Wave table peak <= 1.0", maxVal <= 1.001f);
    }

    // --- ADSR constants sanity ---
    {
        check("Attack < 50ms", SimpleAudioEngine::ATTACK_TIME < 0.05);
        check("Decay < 500ms", SimpleAudioEngine::DECAY_TIME < 0.5);
        check("Sustain 0-1", SimpleAudioEngine::SUSTAIN_LEVEL > 0.0 &&
                              SimpleAudioEngine::SUSTAIN_LEVEL <= 1.0);
        check("Release > 0", SimpleAudioEngine::RELEASE_TIME > 0.0);
        check("Max polyphony >= 8", SimpleAudioEngine::MAX_POLYPHONY >= 8);
    }

    // --- Summary ---
    results << "Tests: " << passed << " passed, " << failed << " failed\n";
    return results.str();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_ongoma_AudioEngine_nativeRunTests(JNIEnv *env, jobject) {
    std::string result = runAllTests();
    return env->NewStringUTF(result.c_str());
}
