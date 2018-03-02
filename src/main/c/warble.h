#ifndef WARBLE_H
#define WARBLE_H

#ifdef __cplusplus
extern "C" {
#endif
	
#include <stdint.h>

/**
* @struct  Warble
* @breif	Object to encapsulate the parameters for the generation and recognition of pitch sequence.
*/
typedef struct _warble {
	double firstFrequency;          /**< Frequency of word 0 */
	double frequencyMultiplication; /**< Increment factor between each word, 0 if usage of frequencyIncrement */
	int16_t frequencyIncrement;     /**< Increment between each word, 0 if usage of frequencyMultiplication */
	int16_t wordSize;               /**< Number of frequency bands */
	int16_t payloadSize;            /**< Number of payload words */
	int16_t paritySize;             /**< Number of Reed–Solomon error-correcting words*/
	int16_t wordTriggerCount;       /**< Number of constant words that trigger the sequence of words */
	int16_t* wordTriggers;          /**< Word index that trigger the sequence of words */
	int16_t* parsed;                /**< parsed words of length wordTriggerCount+payloadSize+paritySize */
	int64_t triggerSampleIndex;     /**< Sample index of first trigger */
} warble;


/***/
warble* warble_create(double firstFrequency,
	double frequencyMultiplication,
	int16_t frequencyIncrement, int16_t wordSize,
	int16_t payloadSize, int16_t wordTriggerCount, int16_t* wordTriggers);


/**
* Free buffer in object
* @param warble Object to free
*/
void warble_free(warble *warble);

/**
* Analyse the provided spectrum
* @return 1 if the message can be collected using warble_GetPayload
*/
int16_t warble_feed(warble *warble, double* rms, int rmsSize, double sampleRate, int64_t sampleIndex);

/**
* @return payload of size warble->payloadSize
*/
int16_t* warble_GetPayload(warble *warble);

#ifdef __cplusplus
}
#endif

#endif