/*
 * kwada (C) 2026
 * Author: phedwin
 *
 * Audio engine header with ADSR envelope definitions
 */

#pragma once

#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cmath>
#include <jni.h>
#include <map>
#include <memory>
#include <mutex>
#include <oboe/Oboe.h>
#include <vector>

#define LOG_TAG "OngomaAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class SimpleAudioEngine : public oboe::AudioStreamDataCallback {
public:
  SimpleAudioEngine();
  ~SimpleAudioEngine();

  void initialize();

  void playNote(int midiNote);
  void stopNote();

  void playNotePolyphonic(int midiNote);
  void stopNotePolyphonic(int midiNote);
  void stopAllNotes();

  double getCurrentTime();

  static constexpr int SAMPLE_RATE = 48000;
  static constexpr double TWO_PI = 2.0 * M_PI;
  static constexpr int MAX_POLYPHONY = 24;

  static constexpr double ATTACK_TIME = 0.008;
  static constexpr double DECAY_TIME = 0.15;
  static constexpr double SUSTAIN_LEVEL = 0.6;
  static constexpr double RELEASE_TIME = 2.0;

  static constexpr double HARMONIC_1_AMP = 1.0;
  static constexpr double HARMONIC_2_AMP = 0.4;
  static constexpr double HARMONIC_3_AMP = 0.2;
  static constexpr double HARMONIC_4_AMP = 0.1;

  static constexpr int WAVE_TABLE_SIZE = 4096;
  static constexpr int WAVE_TABLE_MASK = WAVE_TABLE_SIZE - 1;
  static constexpr double WAVE_TABLE_SCALE =
      static_cast<double>(WAVE_TABLE_SIZE) / TWO_PI;
  static float waveTable[WAVE_TABLE_SIZE];

  void initWaveTable();

private:

  enum class EnvelopeState { ATTACK, DECAY, SUSTAIN, RELEASE, DONE };

  struct NoteData {
    int midiNote;
    double frequency;
    double phase;
    EnvelopeState state;
    double stateStartTime;
    double noteStartTime;
    bool isReleasing;
    uint64_t noteId;

    NoteData(int note, double freq, double startTime, uint64_t id)
        : midiNote(note), frequency(freq), phase(0.0),
          state(EnvelopeState::ATTACK), stateStartTime(startTime),
          noteStartTime(startTime), isReleasing(false), noteId(id) {}
  };

  std::map<int, std::shared_ptr<NoteData>> activeNotes;
  std::mutex notesMutex;
  uint64_t nextNoteId = 0;

  std::shared_ptr<oboe::AudioStream> audioStream;

  std::chrono::steady_clock::time_point engineStartTime;

  double midiNoteToFrequency(int midiNote);
  double calculateEnvelope(const std::shared_ptr<NoteData> &noteData,
                           double currentTime);

  oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream,
                                        void *audioData,
                                        int32_t numFrames) override;
};
