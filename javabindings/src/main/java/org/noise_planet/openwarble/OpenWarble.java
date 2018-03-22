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

package org.noise_planet.openwarble;

import org.renjin.gcc.runtime.BytePtr;
import org.renjin.gcc.runtime.DoublePtr;
import org.renjin.gcc.runtime.Ptr;
import org.renjin.gcc.runtime.IntPtr;

import java.util.Arrays;
import java.util.List;


public class OpenWarble {
  private final Ptr cfg;
  private MessageCallback callback = null;
  private long sampleIndex = 0;
  private double[] cache = null;
  private int windowLength;
  private int messageSampleLength;

  public OpenWarble(Configuration c) {
    cfg = warble.warble_create();
    warble.warble_init(cfg, c.sampleRate, c.firstFrequency, c.frequencyMulti, c.frequencyIncrement, c.wordTime,
            c.payloadSize, new IntPtr(c.triggerFrequencies), c.triggerFrequencies.length);
    windowLength = warble.warble_cfg_get_window_length(cfg);
    messageSampleLength = warble.warble_generate_window_size(cfg);
  }

  /**
   * @return Window length of internal program for sample analyzing.
   */
  public int getDefaultWindowLength() {
    return windowLength;
  }

  public double getMessageTimeLength() {
    return messageSampleLength / getSampleRate();
  }

  public double getSampleRate() {
    return warble.warble_cfg_get_sampleRate(cfg);
  }

  /**
   * Generate a sound signal using the provided payload
   * @param payload Array of bytes
   * @param powerPeak maximum power of signal
   * @return Signal using sample rate configuration.
   */
  public double[] generateSignal(byte[] payload, double powerPeak) {
    double[] signal = new double[messageSampleLength];
    warble.warble_generate_signal(cfg, powerPeak, new BytePtr(payload), new DoublePtr(signal));
    return signal;
  }

  private void pushFixedSamples(double[] samples) {
    int res = warble.warble_feed(cfg, new DoublePtr(samples), sampleIndex);
    if(res != 0 && callback != null) {
      if(res == -1) {
        callback.onError(sampleIndex);
      } else if(res == 1) {
        byte[] decodedPayload = new byte[warble.warble_cfg_get_payloadSize(cfg)];
        BytePtr dest = new BytePtr(decodedPayload);
        warble.warble_reed_decode_solomon(cfg, warble.warble_cfg_get_parsed(cfg), dest);
        callback.onNewMessage(decodedPayload, sampleIndex - messageSampleLength);
      } else if(res == 2) {
        callback.onPitch(sampleIndex);
      }
    }
    sampleIndex += samples.length;
  }

  /**
   * @return The number of pushed samples
   */
  public long getPushedSamples() {
    return sampleIndex;
  }

  /**
   * @param samples Audio samples of any size. Must be the same sampling rate than set in configuration
   *               and no samples must be skipped between each call of this function.
   */
  public void pushSamples(double[] samples) {
    if(samples == null || samples.length == 0) {
      return;
    }
    double[] buffer = new double[windowLength];
    if(cache!=null) {
      if(cache.length + samples.length >= windowLength) {
        // Push cache
        System.arraycopy(cache, 0, buffer, 0, cache.length);
        System.arraycopy(samples, 0, buffer, cache.length, windowLength - cache.length);
        pushFixedSamples(buffer);
        // Process the remaining samples
        samples = Arrays.copyOfRange(samples, windowLength - cache.length, samples.length);
        cache = null;
      } else {
        double[] newCache = new double[cache.length + samples.length];
        System.arraycopy(cache, 0, newCache, 0, cache.length);
        System.arraycopy(samples, 0, newCache, cache.length, samples.length);
        cache = newCache;
        return;
      }
    }
    int cursor = 0;
    while(cursor + windowLength <= samples.length) {
      pushFixedSamples(Arrays.copyOfRange(samples, cursor, cursor+windowLength));
      cursor+=windowLength;
    }
    int remaining = samples.length % windowLength;
    if(remaining != 0) {
      cache = Arrays.copyOfRange(samples, samples.length - remaining, samples.length);
    }

  }

  public MessageCallback getCallback() {
    return callback;
  }

  public void setCallback(MessageCallback callback) {
    this.callback = callback;
  }
}
