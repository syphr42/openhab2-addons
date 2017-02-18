/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.voice.webtts.internal;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
            return new URLAudioStream(
                    createURL(this.baseUrl, text, voice.getLocale().toLanguageTag(), requestedFormat.getCodec()));
        } catch (AudioException ex) {
            throw new TTSException("Could not create AudioStream: " + ex.getMessage(), ex);
        }
    }

    /**
     * This method will create the URL for the cloud service. The text will be
     * URI encoded as it is part of the URL.
     *
     * It is in package scope to be accessed by tests.
     */
    private String createURL(String baseUrl, String text, String locale, String audioFormat) {
        String encodedMsg;
        try {
            encodedMsg = URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            logger.error("UnsupportedEncodingException for UTF-8 MUST NEVER HAPPEN! Check your JVM configuration!", ex);
            // fall through and use msg un-encoded
            encodedMsg = text;
        }
        return baseUrl + "?lng=" + locale + "&msg=" + encodedMsg;
    }

    /**
     * Initializes this.voices.
     *
     * @return The voices of this instance
     */
    private final HashSet<Voice> initVoices() {
        HashSet<Voice> voices = new HashSet<Voice>();
        Set<Locale> locales = getAvailableLocales();
        for (Locale local : locales) {
            Set<String> voiceLabels = getAvailableVoices(local);
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
        audioFormats.add(AudioFormat.MP3);
        return audioFormats;
    }

    @Override
    public String getId() {
        return "webtts";
    }

    @Override
    public String getLabel(Locale locale) {
        return "WebTTS Text-to-Speech Engine";
    }

    private static Set<Locale> supportedLocales = getSupportedLocales();

    /**
     * Will support only 1 locale for the moment.
     */
    private static Set<Locale> getSupportedLocales() {
        Set<Locale> locales = new HashSet<Locale>();
        locales.add(Locale.forLanguageTag("en-us"));
        locales.add(Locale.forLanguageTag("en-gb"));
        locales.add(Locale.forLanguageTag("de-de"));
        locales.add(Locale.forLanguageTag("es-es"));
        locales.add(Locale.forLanguageTag("fr-fr"));
        locales.add(Locale.forLanguageTag("it-it"));
        return locales;
    }

    public Set<Locale> getAvailableLocales() {
        return supportedLocales;
    }

    public Set<String> getAvailableVoices(Locale locale) {
        Set<String> voices = new HashSet<String>();
        for (Locale voiceLocale : supportedLocales) {
            if (voiceLocale.toLanguageTag().equalsIgnoreCase(locale.toLanguageTag())) {
                voices.add("WebTTS");
                break;
            }
        }
        return voices;
    }
}
