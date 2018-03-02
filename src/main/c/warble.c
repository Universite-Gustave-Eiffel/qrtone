

#include "warble.h"
#include "math.h"


warble* warble_create(double firstFrequency,
 double frequencyMultiplication,
  int16_t frequencyIncrement,int16_t wordSize,
  int16_t payloadSize,int16_t wordTriggerCount, int16_t* wordTriggers)  {
    warble* this = (warble*) malloc(sizeof(warble));
    this->firstFrequency = firstFrequency;
	this->frequencyIncrement = frequencyIncrement;
	this->wordSize = wordSize;
	this->payloadSize = payloadSize;
	this->wordTriggerCount = wordTriggerCount;
	this->wordTriggers = wordTriggers;
	this->triggerSampleIndex = -1;
	this->paritySize =  wordSize - 1 - payloadSize / 2;
	this->parsed = malloc(sizeof(int16_t) * wordTriggerCount + payloadSize + this->paritySize);
    return this;
}


/**
 * Free buffer in object
 * @param warble Object to free
 */
void warble_free(warble *warble) {
    free(warble->wordTriggers);
    free(warble->parsed);
    free(warble);
}

/**
 * Analyse the provided spectrum
 * @return 1 if the message can be collected using warble_GetPayload
 */
int16_t warble_feed(warble *warble, double* rms, int rmsSize, double sampleRate, int64_t sampleIndex);

/**
 * @return payload of size warble->payloadSize
 */
int16_t* warble_GetPayload(warble *warble);