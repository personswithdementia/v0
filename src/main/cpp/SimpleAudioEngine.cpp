#include "SimpleAudioEngine.h"
#include <algorithm>

SimpleAudioEngine::SimpleAudioEngine()
    : engineStartTime(std::chrono::steady_clock::now()) {
    LOGI("SimpleAudioEngine constructor called");
}

double SimpleAudioEngine::getCurrentTime() {
    // Return elapsed time in seconds since engine initialization
    // This provides a stable, monotonic time reference for ADSR envelopes
    auto now = std::chrono::steady_clock::now();
    auto elapsed = now - engineStartTime;
    return std::chrono::duration<double>(elapsed).count();
}

void SimpleAudioEngine::initialize() {
    LOGI("SimpleAudioEngine initializing with Oboe");

    // Create Oboe audio stream
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

    // Start the audio stream
    result = audioStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start audio stream: %s", oboe::convertToText(result));
        return;
    }

    LOGI("Audio stream started successfully");
}
/* - kill audio - */
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

// Legacy monophonic methods
void SimpleAudioEngine::playNote(int midiNote) {
    playNotePolyphonic(midiNote);
}

void SimpleAudioEngine::stopNote() {
    stopAllNotes();
}

// Polyphonic methods
void SimpleAudioEngine::playNotePolyphonic(int midiNote) {
    // Safety check: ensure audio stream exists
    if (!audioStream) {
        LOGE("Cannot play note - audio stream not initialized");
        return;
    }

    std::lock_guard<std::mutex> lock(notesMutex);

    // Check if note is already playing
    if (activeNotes.find(midiNote) != activeNotes.end()) {
        return;
    }

    // Check polyphony limit
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
        // Trigger RELEASE phase instead of immediate removal
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
    // A4 (MIDI 69) = 440 Hz
    // Ensure proper floating-point division: ((double)midiNote - 69.0) / 12.0
    return 440.0 * std::pow(2.0, ((double)midiNote - 69.0) / 12.0);
}

double SimpleAudioEngine::calculateEnvelope(const std::shared_ptr<NoteData>& noteData, double currentTime) {
    double timeInState = currentTime - noteData->stateStartTime;

    switch (noteData->state) {
        case EnvelopeState::ATTACK: {
            // Linear ramp from 0 to 1 over ATTACK_TIME
            if (timeInState >= ATTACK_TIME) {
                return 1.0;  // Attack complete
            }
            return timeInState / ATTACK_TIME;
        }

        case EnvelopeState::DECAY: {
            // Linear ramp from 1 to SUSTAIN_LEVEL over DECAY_TIME
            if (timeInState >= DECAY_TIME) {
                return SUSTAIN_LEVEL;  // Decay complete
            }
            double progress = timeInState / DECAY_TIME;
            return 1.0 - (1.0 - SUSTAIN_LEVEL) * progress;
        }

        case EnvelopeState::SUSTAIN:
            // Hold at sustain level
            return SUSTAIN_LEVEL;

        case EnvelopeState::RELEASE: {
            // Exponential fade from current level to 0 over RELEASE_TIME
            if (timeInState >= RELEASE_TIME) {
                return 0.0;  // Release complete
            }
            double progress = timeInState / RELEASE_TIME;
            // Exponential decay: e^(-4*progress) gives smooth fade
            return SUSTAIN_LEVEL * std::exp(-4.0 * progress);
        }

        case EnvelopeState::DONE:
            return 0.0;
    }

    return 0.0;
}

// Oboe audio callback - THIS IS WHERE SOUND IS ACTUALLY GENERATED
oboe::DataCallbackResult SimpleAudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames) {

    float *outputBuffer = static_cast<float *>(audioData);

    // Clear buffer first
    std::fill_n(outputBuffer, numFrames, 0.0f);

    std::lock_guard<std::mutex> lock(notesMutex);

    if (activeNotes.empty()) {
        return oboe::DataCallbackResult::Continue;
    }

    double currentTime = getCurrentTime();

    // Calculate volume per note to avoid clipping
    const double volumePerNote = 0.25 / std::max(1.0, static_cast<double>(activeNotes.size()));

    // Track notes to remove (those that finished RELEASE)
    std::vector<int> notesToRemove;

    // Mix all active notes into the output buffer
    for (auto& pair : activeNotes) {
        auto& noteData = pair.second;

        // Update envelope state transitions
        double timeInState = currentTime - noteData->stateStartTime;

        if (noteData->state == EnvelopeState::ATTACK && timeInState >= ATTACK_TIME) {
            noteData->state = EnvelopeState::DECAY;
            noteData->stateStartTime = currentTime;
        } else if (noteData->state == EnvelopeState::DECAY && timeInState >= DECAY_TIME) {
            noteData->state = EnvelopeState::SUSTAIN;
            noteData->stateStartTime = currentTime;
        } else if (noteData->state == EnvelopeState::RELEASE && timeInState >= RELEASE_TIME) {
            // Mark for removal after RELEASE completes
            notesToRemove.push_back(noteData->midiNote);
            continue;
        }

        // Calculate envelope value
        double envelope = calculateEnvelope(noteData, currentTime);

        if (envelope <= 0.0) {
            notesToRemove.push_back(noteData->midiNote);
            continue;
        }

        // Phase increments for harmonics
        double phaseIncrement = TWO_PI * noteData->frequency / SAMPLE_RATE;
        double phaseIncrement2 = phaseIncrement * 2.0;  // Octave
        double phaseIncrement3 = phaseIncrement * 3.0;  // Fifth above octave
        double phaseIncrement4 = phaseIncrement * 4.0;  // Two octaves

        for (int32_t i = 0; i < numFrames; ++i) {
            // Generate piano-like tone with harmonics
            double sample =
                HARMONIC_1_AMP * std::sin(noteData->phase) +
                HARMONIC_2_AMP * std::sin(noteData->phase * 2.0) +
                HARMONIC_3_AMP * std::sin(noteData->phase * 3.0) +
                HARMONIC_4_AMP * std::sin(noteData->phase * 4.0);

            // Normalize harmonics (sum of amplitudes = 1.7)
            sample /= (HARMONIC_1_AMP + HARMONIC_2_AMP + HARMONIC_3_AMP + HARMONIC_4_AMP);

            // Apply envelope and volume
            outputBuffer[i] += static_cast<float>(sample * envelope * volumePerNote);

            // Update phase
            noteData->phase += phaseIncrement;
            if (noteData->phase >= TWO_PI) {
                noteData->phase -= TWO_PI;
            }
        }
    }

    // Remove notes that finished RELEASE
    for (int midiNote : notesToRemove) {
        activeNotes.erase(midiNote);
    }

    return oboe::DataCallbackResult::Continue;
}
