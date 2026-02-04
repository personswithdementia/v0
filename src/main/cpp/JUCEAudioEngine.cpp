/*
 * kwada (C) 2026
 * Author: phedwin
 *
 * JUCE-based audio engine implementation
 */

#include "JUCEAudioEngine.h"

JUCEAudioEngine::JUCEAudioEngine() : sampleRate(SAMPLE_RATE) {
	LOGI("JUCEAudioEngine constructor called");

	voices.resize(MAX_VOICES);
	for (auto &voice : voices) {
		voice = Voice();
	}
}

void JUCEAudioEngine::initialize() {
	LOGI("JUCEAudioEngine initializing with JUCE framework");

	deviceManager = std::make_unique<juce::AudioDeviceManager>();

	juce::String error =
	    deviceManager->initialise(0,
				      2,
				      nullptr,
				      true
	    );

	if (error.isNotEmpty()) {
		LOGI("ERROR: Failed to initialize audio device: %s",
		     error.toRawUTF8());
		return;
	}

	deviceManager->addAudioCallback(this);

	if (auto *device = deviceManager->getCurrentAudioDevice()) {
		sampleRate = device->getCurrentSampleRate();
		LOGI("Audio device initialized: sampleRate=%.0f, bufferSize=%d",
		     sampleRate, device->getCurrentBufferSizeSamples());
	}

	LOGI("JUCE audio engine started successfully");
}

JUCEAudioEngine::~JUCEAudioEngine() {
	shutdown();
}

void JUCEAudioEngine::shutdown() {
	LOGI("Shutting down JUCEAudioEngine");

	stopAllNotes();

	if (deviceManager) {
		deviceManager->removeAudioCallback(this);
		deviceManager->closeAudioDevice();
		deviceManager.reset();
	}

	LOGI("JUCEAudioEngine destroyed");
}

double JUCEAudioEngine::midiNoteToFrequency(int midiNote) {
	return 440.0 * std::pow(2.0, (midiNote - 69) / 12.0);
}

JUCEAudioEngine::Voice *JUCEAudioEngine::findFreeVoice() {
	for (auto &voice : voices) {
		if (!voice.active && voice.envelopePhase == Voice::Idle) {
			return &voice;
		}
	}

	for (auto &voice : voices) {
		if (voice.envelopePhase == Voice::Release) {
			return &voice;
		}
	}

	return &voices[0];
}

JUCEAudioEngine::Voice *JUCEAudioEngine::findVoiceForNote(int midiNote) {
	for (auto &voice : voices) {
		if (voice.active && voice.midiNote == midiNote) {
			return &voice;
		}
	}
	return nullptr;
}

void JUCEAudioEngine::playNotePolyphonic(int midiNote) {
	std::lock_guard<std::mutex> lock(voicesMutex);

	if (findVoiceForNote(midiNote) != nullptr) {
		LOGI("Note %d already playing, ignoring", midiNote);
		return;
	}

	Voice *voice = findFreeVoice();
	if (!voice) {
		LOGI("No free voices available");
		return;
	}

	voice->midiNote = midiNote;
	voice->frequency = midiNoteToFrequency(midiNote);
	voice->phase = 0.0;
	voice->active = true;
	voice->envelopePhase = Voice::Attack;
	voice->envelopeValue = 0.0;
	voice->amplitude = 0.3;

	LOGI("Playing note: %d (%.2f Hz) on voice", midiNote, voice->frequency);
}

void JUCEAudioEngine::stopNotePolyphonic(int midiNote) {
	std::lock_guard<std::mutex> lock(voicesMutex);

	Voice *voice = findVoiceForNote(midiNote);
	if (voice) {
		voice->envelopePhase = Voice::Release;
		LOGI("Stopped note: %d (entering release)", midiNote);
	}
}

void JUCEAudioEngine::stopAllNotes() {
	std::lock_guard<std::mutex> lock(voicesMutex);

	for (auto &voice : voices) {
		if (voice.active) {
			voice.envelopePhase = Voice::Release;
		}
	}

	LOGI("All notes entering release phase");
}

void JUCEAudioEngine::updateEnvelope(Voice &voice, int numSamples) {
	double timeStep = 1.0 / sampleRate;

	switch (voice.envelopePhase) {
		case Voice::Attack:
			voice.envelopeValue += timeStep / ATTACK_TIME;
			if (voice.envelopeValue >= 1.0) {
				voice.envelopeValue = 1.0;
				voice.envelopePhase = Voice::Decay;
			}
			break;

		case Voice::Decay:
			voice.envelopeValue -=
			    (1.0 - SUSTAIN_LEVEL) * timeStep / DECAY_TIME;
			if (voice.envelopeValue <= SUSTAIN_LEVEL) {
				voice.envelopeValue = SUSTAIN_LEVEL;
				voice.envelopePhase = Voice::Sustain;
			}
			break;

		case Voice::Sustain:
			voice.envelopeValue = SUSTAIN_LEVEL;
			break;

		case Voice::Release:
			voice.envelopeValue -=
			    SUSTAIN_LEVEL * timeStep / RELEASE_TIME;
			if (voice.envelopeValue <= 0.0) {
				voice.envelopeValue = 0.0;
				voice.envelopePhase = Voice::Idle;
				voice.active = false;
			}
			break;

		case Voice::Idle:
			voice.envelopeValue = 0.0;
			voice.active = false;
			break;
	}
}

void JUCEAudioEngine::audioDeviceIOCallbackWithContext(
    const float *const *inputChannelData,
    int numInputChannels,
    float *const *outputChannelData,
    int numOutputChannels,
    int numSamples,
    const juce::AudioIODeviceCallbackContext &context) {

	for (int channel = 0; channel < numOutputChannels; ++channel) {
		if (outputChannelData[channel] != nullptr) {
			std::fill_n(outputChannelData[channel], numSamples,
				    0.0f);
		}
	}

	std::lock_guard<std::mutex> lock(voicesMutex);

	for (auto &voice : voices) {
		if (!voice.active && voice.envelopePhase == Voice::Idle) {
			continue;
		}

		double phaseIncrement = TWO_PI * voice.frequency / sampleRate;

		for (int i = 0; i < numSamples; ++i) {
			updateEnvelope(voice, 1);
			float sample = 0.0f;
			sample += std::sin(voice.phase) * 1.0f;
			sample += std::sin(voice.phase * 2.0) * 0.5f;
			sample += std::sin(voice.phase * 3.0) * 0.25f;
			sample += std::sin(voice.phase * 4.0) * 0.125f;
			sample += std::sin(voice.phase * 5.0) * 0.08f;

			sample *= static_cast<float>(
			    voice.amplitude * voice.envelopeValue * 0.5f);

			for (int channel = 0; channel < numOutputChannels;
			     ++channel) {
				if (outputChannelData[channel] != nullptr) {
					outputChannelData[channel][i] += sample;
				}
			}
			voice.phase += phaseIncrement;
			if (voice.phase >= TWO_PI) {
				voice.phase -= TWO_PI;
			}
		}
	}
}

void JUCEAudioEngine::audioDeviceAboutToStart(juce::AudioIODevice *device) {
	sampleRate = device->getCurrentSampleRate();
	LOGI("Audio device about to start: sampleRate=%.0f", sampleRate);
}

void JUCEAudioEngine::audioDeviceStopped() {
	LOGI("Audio device stopped");
}
