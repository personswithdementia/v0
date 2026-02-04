/*
 * kwada (C) 2026
 * Author: phedwin
 *
 * Oboe-based polyphonic audio synthesis engine
 */

#include "SimpleAudioEngine.h"
#include <algorithm>

SimpleAudioEngine::SimpleAudioEngine()
    : engineStartTime(std::chrono::steady_clock::now()) {
    LOGI("AudioEngine constructor called");
}

double SimpleAudioEngine::getCurrentTime() {
    auto now = std::chrono::steady_clock::now();
    auto elapsed = now - engineStartTime;
    return std::chrono::duration<double>(elapsed).count() ;
}

void SimpleAudioEngine::initialize() {
    LOGI("SimpleAudioEngine initializing with Oboe");

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Shared);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(oboe::ChannelCount::Mono);
    builder.setSampleRate(SAMPLE_RATE);
    builder.setDataCallback(this);

    oboe::Result result = builder.openStream(audioStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to create audio stream: %s", oboe::convertToText(result));
        return;
    }

    LOGI("Audio stream created: %dHz, %d frames",
         audioStream->getSampleRate(), audioStream->getBufferSizeInFrames());

    result = audioStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start audio stream: %s", oboe::convertToText(result));
        return;
    }

    LOGI("Audio stream started successfully");
}

SimpleAudioEngine::~SimpleAudioEngine() {
    LOGI("Shutting down SimpleAudioEngine");
    stopAllNotes();

    if (audioStream) {
        audioStream->requestStop();
        audioStream->close();
        audioStream.reset();
    }

    LOGI("SimpleAudioEngine destroyed");
}

void SimpleAudioEngine::playNote(int midiNote) {
    playNotePolyphonic(midiNote);
}

void SimpleAudioEngine::stopNote() {
    stopAllNotes();
}

void SimpleAudioEngine::playNotePolyphonic(int midiNote) {

    if (!audioStream) {
        LOGE("Cannot play note - audio stream not initialized");
        return;
    }

    std::lock_guard<std::mutex> lock(notesMutex);

    if (activeNotes.find(midiNote) != activeNotes.end()) {
        return;
    }

    if (activeNotes.size() >= MAX_POLYPHONY) {
        auto oldestNote = activeNotes.begin();
        activeNotes.erase(oldestNote);
    }

    double frequency = midiNoteToFrequency(midiNote);
    double currentTime = getCurrentTime();
    auto noteData = std::make_shared<NoteData>(midiNote, frequency, currentTime);
    activeNotes[midiNote] = noteData;
}

void SimpleAudioEngine::stopNotePolyphonic(int midiNote) {
    std::lock_guard<std::mutex> lock(notesMutex);

    auto it = activeNotes.find(midiNote);
    if (it != activeNotes.end()) {

        auto& noteData = it->second;
        noteData->state = EnvelopeState::RELEASE;
        noteData->stateStartTime = getCurrentTime();
        noteData->isReleasing = true;
    }
}

void SimpleAudioEngine::stopAllNotes() {
    std::lock_guard<std::mutex> lock(notesMutex);
    activeNotes.clear();
}

double SimpleAudioEngine::midiNoteToFrequency(int midiNote) {

    return 440.0 * std::pow(2.0, ((double)midiNote - 69.0) / 12.0);
}

double SimpleAudioEngine::calculateEnvelope(const std::shared_ptr<NoteData>& noteData, double currentTime) {
    double timeInState = currentTime - noteData->stateStartTime;

    switch (noteData->state) {
        case EnvelopeState::ATTACK: {

            if (timeInState >= ATTACK_TIME) {
                return 1.0;
            }
            return timeInState / ATTACK_TIME;
        }

        case EnvelopeState::DECAY: {

            if (timeInState >= DECAY_TIME) {
                return SUSTAIN_LEVEL;
            }
            double progress = timeInState / DECAY_TIME;
            return 1.0 - (1.0 - SUSTAIN_LEVEL) * progress;
        }

        case EnvelopeState::SUSTAIN:

            return SUSTAIN_LEVEL;

        case EnvelopeState::RELEASE: {

            if (timeInState >= RELEASE_TIME) {
                return 0.0;
            }
            double progress = timeInState / RELEASE_TIME;

            return SUSTAIN_LEVEL * std::exp(-4.0 * progress);
        }

        case EnvelopeState::DONE:
            return 0.0;
    }

    return 0.0;
}

oboe::DataCallbackResult SimpleAudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames) {

    float *outputBuffer = static_cast<float *>(audioData);

    std::fill_n(outputBuffer, numFrames, 0.0f);

    std::lock_guard<std::mutex> lock(notesMutex);

    if (activeNotes.empty()) {
        return oboe::DataCallbackResult::Continue;
    }

    double currentTime = getCurrentTime();

    const double volumePerNote = 0.25 / std::max(1.0, static_cast<double>(activeNotes.size()));

    std::vector<int> notesToRemove;

    for (auto& pair : activeNotes) {
        auto& noteData = pair.second;

        double timeInState = currentTime - noteData->stateStartTime;

        if (noteData->state == EnvelopeState::ATTACK && timeInState >= ATTACK_TIME) {
            noteData->state = EnvelopeState::DECAY;
            noteData->stateStartTime = currentTime;
        } else if (noteData->state == EnvelopeState::DECAY && timeInState >= DECAY_TIME) {
            noteData->state = EnvelopeState::SUSTAIN;
            noteData->stateStartTime = currentTime;
        } else if (noteData->state == EnvelopeState::RELEASE && timeInState >= RELEASE_TIME) {

            notesToRemove.push_back(noteData->midiNote);
            continue;
        }

        double envelope = calculateEnvelope(noteData, currentTime);

        if (envelope <= 0.0) {
            notesToRemove.push_back(noteData->midiNote);
            continue;
        }

        double phaseIncrement = TWO_PI * noteData->frequency / SAMPLE_RATE;
        double phaseIncrement2 = phaseIncrement * 2.0;
        double phaseIncrement3 = phaseIncrement * 3.0;
        double phaseIncrement4 = phaseIncrement * 4.0;

        for (int32_t i = 0; i < numFrames; ++i) {

            double sample =
                HARMONIC_1_AMP * std::sin(noteData->phase) +
                HARMONIC_2_AMP * std::sin(noteData->phase * 2.0) +
                HARMONIC_3_AMP * std::sin(noteData->phase * 3.0) +
                HARMONIC_4_AMP * std::sin(noteData->phase * 4.0);

            sample /= (HARMONIC_1_AMP + HARMONIC_2_AMP + HARMONIC_3_AMP + HARMONIC_4_AMP);

            outputBuffer[i] += static_cast<float>(sample * envelope * volumePerNote);

            noteData->phase += phaseIncrement;
            if (noteData->phase >= TWO_PI) {
                noteData->phase -= TWO_PI;
            }
        }
    }

    for (int midiNote : notesToRemove) {
        activeNotes.erase(midiNote);
    }

    return oboe::DataCallbackResult::Continue;
}
