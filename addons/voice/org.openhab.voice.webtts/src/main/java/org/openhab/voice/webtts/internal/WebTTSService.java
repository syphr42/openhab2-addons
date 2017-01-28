/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.voice.webtts.internal;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.core.audio.AudioException;
import org.eclipse.smarthome.core.audio.AudioFormat;
import org.eclipse.smarthome.core.audio.AudioStream;
import org.eclipse.smarthome.core.audio.URLAudioStream;
import org.eclipse.smarthome.core.voice.TTSException;
import org.eclipse.smarthome.core.voice.TTSService;
import org.eclipse.smarthome.core.voice.Voice;
import org.openhab.voice.webtts.internal.cloudapi.WebTTSCloudAPI;
import org.openhab.voice.webtts.internal.cloudapi.WebTTSCloudImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a TTS service implementation for using WebTTS service.
 *
 * @author Gregory P. Moyer - Initial contribution
 */
public class WebTTSService implements TTSService {

    private static final String CONFIG_URL = "baseUrl";
    private String baseUrl = null;

    private final Logger logger = LoggerFactory.getLogger(WebTTSService.class);

    private WebTTSCloudAPI webttsImpl;

    /**
     * Set of supported voices
     */
    private HashSet<Voice> voices;

    /**
     * Set of supported audio formats
     */
    private HashSet<AudioFormat> audioFormats;

    /**
     * DS activate, with access to ConfigAdmin
     */
    protected void activate(Map<String, Object> config) {
        try {
            modified(config);
            webttsImpl = initVoiceImplementation();
            voices = initVoices();
            audioFormats = initAudioFormats();
        } catch (Throwable t) {
            logger.error("Failed to activate WebTTS: {}", t.getMessage(), t);
        }
    }

    protected void modified(Map<String, Object> config) {
        if (config != null) {
            this.baseUrl = config.containsKey(CONFIG_URL) ? config.get(CONFIG_URL).toString() : null;
        }
    }

    @Override
    public Set<Voice> getAvailableVoices() {
        return this.voices;
    }

    @Override
    public Set<AudioFormat> getSupportedFormats() {
        return this.audioFormats;
    }

    @Override
    public AudioStream synthesize(String text, Voice voice, AudioFormat requestedFormat) throws TTSException {
        logger.debug("Synthesize '{}' for voice '{}' in format {}", text, voice.getUID(), requestedFormat);
        // Validate known api key
        if (this.baseUrl == null) {
            throw new TTSException("Missing base URL, configure it first before using");
        }
        // Validate arguments
        // trim text
        text = text.trim();
        if ((null == text) || text.isEmpty()) {
            throw new TTSException("The passed text is null or empty");
        }
        if (!this.voices.contains(voice)) {
            throw new TTSException("The passed voice is unsupported");
        }
        boolean isAudioFormatSupported = false;
        for (AudioFormat currentAudioFormat : this.audioFormats) {
            if (currentAudioFormat.isCompatible(requestedFormat)) {
                isAudioFormatSupported = true;
                break;
            }
        }
        if (!isAudioFormatSupported) {
            throw new TTSException("The passed AudioFormat is unsupported");
        }

        // now create the input stream for given text, locale, format. There is
        // only a default voice
        try {
            return new URLAudioStream(webttsImpl.createURL(this.baseUrl, text, voice.getLocale().toLanguageTag(),
                    requestedFormat.getCodec()));
        } catch (AudioException ex) {
            throw new TTSException("Could not create AudioStream: " + ex.getMessage(), ex);
        }
    }

    /**
     * Initializes this.voices.
     *
     * @return The voices of this instance
     */
    private final HashSet<Voice> initVoices() {
        HashSet<Voice> voices = new HashSet<Voice>();
        Set<Locale> locales = webttsImpl.getAvailableLocales();
        for (Locale local : locales) {
            Set<String> voiceLabels = webttsImpl.getAvailableVoices(local);
            for (String voiceLabel : voiceLabels) {
                voices.add(new WebTTSVoice(local, voiceLabel));
            }
        }
        return voices;
    }

    /**
     * Initializes this.audioFormats
     *
     * @return The audio formats of this instance
     */
    private final HashSet<AudioFormat> initAudioFormats() {
        HashSet<AudioFormat> audioFormats = new HashSet<AudioFormat>();
        Set<String> formats = webttsImpl.getAvailableAudioFormats();
        for (String format : formats) {
            audioFormats.add(getAudioFormat(format));
        }
        return audioFormats;
    }

    /**
     * Up to now only MP3 supported.
     */
    private final AudioFormat getAudioFormat(String format) {
        // MP3 format
        if (AudioFormat.CODEC_MP3.equals(format)) {
            // we use by default: MP3, 44khz_16bit_mono
            Boolean bigEndian = null; // not used here
            Integer bitDepth = 16;
            Integer bitRate = null;
            Long frequency = 44000L;

            return new AudioFormat(AudioFormat.CONTAINER_NONE, AudioFormat.CODEC_MP3, bigEndian, bitDepth, bitRate,
                    frequency);
        } else {
            throw new RuntimeException("Audio format " + format + " not yet supported");
        }
    }

    private final WebTTSCloudAPI initVoiceImplementation() {
        return new WebTTSCloudImpl();
    }

    @Override
    public String getId() {
        return "webtts";
    }

    @Override
    public String getLabel(Locale locale) {
        return "WebTTS Text-to-Speech Engine";
    }
}
