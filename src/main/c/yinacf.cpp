// example.cpp : Shows usage of the YinAcf class
//
#include <math.h>
#include "yinacf.h"

typedef YinACF<float> YinFloat;

extern "C" YinFloat* create() {
	return new YinFloat();
}


extern "C" bool build (YinFloat* that, int windowSize, int tmax) {
	return that->build(windowSize, tmax);
}

extern "C" void reset(YinFloat* that) {
	that->reset();
}


extern "C" float tick(YinFloat* that, float s) {
	return that->tick(s);
}



extern "C" int getLatency(YinFloat* that) {
	return that->getLatency();
}


extern "C" int getWindowSize(YinFloat* that) {
	return that->getWindowSize();
}


extern "C" const float* getDiff(YinFloat* that, int offset = 0) {
	return that->getDiff(offset);
}



extern "C" const float* getCMNDiff(YinFloat* that, int offset = 0) {
	return that->getCMNDiff(offset);
}



extern "C" const float getFrequency(YinFloat* that, int offset = 0) {
	return that->getFrequency(offset);
}

extern "C" float getThreshold(YinFloat* that)  {
	return that->getThreshold();
}

extern "C" void setThreshold(YinFloat* that, float threshold) {
	that->setThreshold(threshold);
}


extern "C" int getMaxPeriod(YinFloat* that) {
	return that->getMaxPeriod();
}