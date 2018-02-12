// example.cpp : Shows usage of the YinAcf class
//
#include <math.h>
#include "yinacf.h"

YinACF<float> yin;

// The caller is expected to call initYin() during program 
// initialization, or whenever sample rate of minimum frequency
// requirements change.
//
// this implementation uses tmax=1/minFreq seconds

void initYin (float sampleRate, float minFreq) {

    unsigned w, tmax;
    w = (unsigned)ceil(sampleRate/minFreq);
    tmax = w;
    yin.build(w, tmax);
}


// extract frequency estimates from the signal in inSamples, and save 
// in outFrequencies

int getFundamentalFrequency(int n, float* inSamples, float* outFrequencies)
{
    int i;
    for (i = 0; i < n; ++i)
        outFrequencies[i] = yin.tick(inSamples[i]);
        return 0;
}

int main() {
	return 0;
}