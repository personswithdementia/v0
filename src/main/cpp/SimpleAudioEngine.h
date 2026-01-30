#pragma once

#include <android/log.h>
#include <jni.h>
#include <atomic>
#include <cmath>
#include <vector>
#include <map>
#include <mutex>
#include <memory>
#include <chrono>
#include <oboe/Oboe.h>

#define LOG_TAG "SimpleAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class SimpleAudioEngine : public oboe::AudioStreamDataCallback {
public:
    SimpleAudioEngine();
    ~SimpleAudioEngine();

    // Initialize audio engine (call this after construction), this breaks flow :(
    void initialize();

    // Monophonic methods (legacy)
    void playNote(int midiNote);
    void stopNote();

    // Polyphonic methods
    void playNotePolyphonic(int midiNote);
    void stopNotePolyphonic(int midiNote);
    void stopAllNotes();

    // Time reference for arranger playback sync
    double getCurrentTime();

private:
    // Polyphonic note management with ADSR envelope
    enum class EnvelopeState {
        ATTACK,
        DECAY,
        SUSTAIN,
        RELEASE,
        DONE
    };

    struct NoteData {
        int midiNote;
        double frequency;
        double phase;
        EnvelopeState state;
        double stateStartTime;  // Time when current state started
        double noteStartTime;   // Time when note was first pressed
        bool isReleasing;       // True when stopNote was called

        NoteData(int note, double freq, double startTime)
            : midiNote(note),
              frequency(freq),
              phase(0.0),
              state(EnvelopeState::ATTACK),
              stateStartTime(startTime),
              noteStartTime(startTime),
              isReleasing(false) {}
    };

    std::map<int, std::shared_ptr<NoteData>> activeNotes;
    std::mutex notesMutex;

    // Oboe audio stream
    std::shared_ptr<oboe::AudioStream> audioStream;

    // Time reference for ADSR envelope calculations
    std::chrono::steady_clock::time_point engineStartTime;

    static constexpr int SAMPLE_RATE = 48000;
    static constexpr double TWO_PI = 2.0 * M_PI;
    static constexpr int MAX_POLYPHONY = 20;  // Increased for more simultaneous notes

    // ADSR envelope parameters (in seconds)
    static constexpr double ATTACK_TIME = 0.01;   // Quick attack (10ms)
    static constexpr double DECAY_TIME = 0.3;     // Decay to sustain (300ms)
    static constexpr double SUSTAIN_LEVEL = 0.6;  // 60% of peak
    static constexpr double RELEASE_TIME = 3.0;   // 3 second fade out

    // Harmonic amplitudes for piano-like tone
    static constexpr double HARMONIC_1_AMP = 1.0;   // Fundamental
    static constexpr double HARMONIC_2_AMP = 0.4;   // Octave
    static constexpr double HARMONIC_3_AMP = 0.2;   // Fifth above octave
    static constexpr double HARMONIC_4_AMP = 0.1;   // Two octaves

    double midiNoteToFrequency(int midiNote);
    double calculateEnvelope(const std::shared_ptr<NoteData>& noteData, double currentTime);

    // Oboe callback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) override;
};
