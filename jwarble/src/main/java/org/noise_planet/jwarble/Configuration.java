/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2018, Ifsttar
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 *  Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.noise_planet.jwarble;

/**
 * OpenWarble configuration object
 */
public class Configuration {
  public static final double MULT_SEMITONE = 1.0594630943592952646;
  public static final double DEFAULT_WORD_TIME = 0.0872;
  public static final double DEFAULT_AUDIBLE_FIRST_FREQUENCY = 1760;
  public static final double DEFAULT_INAUDIBLE_FIRST_FREQUENCY = 18200;
  public static final int DEFAULT_INAUDIBLE_STEP = 120;
  public static final double DEFAULT_TRIGGER_SNR = 15;
  // Peak ratio, when computing SNR, no other peaks must be found in the provided percentage
  public static final double DEFAULT_DOOR_PEAK_RATIO = 0.8;

  public final int payloadSize;
  public final double sampleRate;
  public final double firstFrequency;
  public final int frequencyIncrement;
  public final double frequencyMulti;
  public final double wordTime;
  public final double triggerSnr;
  public final double convolutionPeakRatio;

  public Configuration(int payloadSize, double sampleRate, double firstFrequency, int frequencyIncrement, double frequencyMulti, double wordTime, double triggerSnr, double convolutionPeakRatio) {
    this.payloadSize = payloadSize;
    this.sampleRate = sampleRate;
    this.firstFrequency = firstFrequency;
    this.frequencyIncrement = frequencyIncrement;
    this.frequencyMulti = frequencyMulti;
    this.wordTime = wordTime;
    this.triggerSnr = triggerSnr;
    this.convolutionPeakRatio = convolutionPeakRatio;
  }

  /**
   * Audible data communication
   * @param payloadSize Payload size in bytes.
   * @param sampleRate Sampling rate in Hz
   * @return Default configuration for this profile
   */
  public static Configuration getAudible(int payloadSize, double sampleRate) {
    return new Configuration(payloadSize, sampleRate, DEFAULT_AUDIBLE_FIRST_FREQUENCY,
            0, MULT_SEMITONE, DEFAULT_WORD_TIME, DEFAULT_TRIGGER_SNR, DEFAULT_DOOR_PEAK_RATIO);
  }


  /**
   * Inaudible data communication (from 18200 Hz to 22040 hz)
   * @param payloadSize Payload size in bytes. Must be greater or equal to 44100 Hz (Nyquist frequency)
   * @param sampleRate Sampling rate in Hz
   * @return Default configuration for this profile
   */
  public static Configuration getInaudible(int payloadSize, double sampleRate) {
    return new Configuration(payloadSize, sampleRate, DEFAULT_INAUDIBLE_FIRST_FREQUENCY,
            DEFAULT_INAUDIBLE_STEP, 0, DEFAULT_WORD_TIME, DEFAULT_TRIGGER_SNR, DEFAULT_DOOR_PEAK_RATIO);
  }
}
