/*
 * kwada (C) 2026
 * Author: phedwin
 *
 * JUCE audio engine header
 */

#pragma once

#include <android/log.h>
#include <jni.h>
#include <vector>
#include <map>
#include <memory>
#include <mutex>
#include <cmath>

#include <juce_core/juce_core.h>
#include <juce_events/juce_events.h>
#include <juce_audio_basics/juce_audio_basics.h>
#include <juce_audio_devices/juce_audio_devices.h>

#define LOG_TAG "JUCEAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

class JUCEAudioEngine : public juce::AudioIODeviceCallback {
public:
    JUCEAudioEngine();
    ~JUCEAudioEngine();

    void initialize();
    void shutdown();

    void playNotePolyphonic(int midiNote);
    void stopNotePolyphonic(int midiNote);
    void stopAllNotes();

private:

    struct Voice {
        int midiNote;
        double frequency;
        double phase;
        double amplitude;
        bool active;

        enum EnvelopePhase { Attack, Decay, Sustain, Release, Idle };
        EnvelopePhase envelopePhase;
        double envelopeValue;

        Voice()
            : midiNote(-1), frequency(0.0), phase(0.0),
              amplitude(0.0), active(false),
              envelopePhase(Idle), envelopeValue(0.0) {}
    };

    static constexpr int MAX_VOICES = 16;
    static constexpr double SAMPLE_RATE = 48000.0;
    static constexpr double TWO_PI = 2.0 * M_PI;

    static constexpr double ATTACK_TIME = 0.005;
    static constexpr double DECAY_TIME = 0.2;
    static constexpr double SUSTAIN_LEVEL = 0.6;
    static constexpr double RELEASE_TIME = 2.5;

    std::vector<Voice> voices;
    std::mutex voicesMutex;

    std::unique_ptr<juce::AudioDeviceManager> deviceManager;
    double sampleRate;

    double midiNoteToFrequency(int midiNote);
    Voice* findFreeVoice();
    Voice* findVoiceForNote(int midiNote);
    void updateEnvelope(Voice& voice, int numSamples);

    void audioDeviceIOCallbackWithContext(
        const float* const* inputChannelData,
        int numInputChannels,
        float* const* outputChannelData,
        int numOutputChannels,
        int numSamples,
        const juce::AudioIODeviceCallbackContext& context) override;

    void audioDeviceAboutToStart(juce::AudioIODevice* device) override;
    void audioDeviceStopped() override;
};
