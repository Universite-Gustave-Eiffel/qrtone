package org.noise_planet.qrtone.utils;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import java.util.Arrays;

public class ArrayWriteProcessor implements AudioProcessor {
    private float[] data;
    private int length;
    private int minBufferSize;

    public ArrayWriteProcessor(int minBufferSize) {
        this.minBufferSize = minBufferSize;
        data = new float[minBufferSize];
    }

    @Override
    public boolean process(AudioEvent audioEvent) {
        float[] buffer = audioEvent.getFloatBuffer();
        if(buffer.length + length > data.length) {
            data = Arrays.copyOf(data, Math.max(data.length + minBufferSize, length + data.length));
        }
        System.arraycopy(buffer, 0, data, length, buffer.length);
        length += buffer.length;
        return true;
    }

    public float[] getData() {
        return Arrays.copyOf(data, length);
    }

    @Override
    public void processingFinished() {
    }
}
