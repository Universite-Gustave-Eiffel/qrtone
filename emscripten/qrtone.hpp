#include <stdio.h>
#include "qrtone.h"

class QRTone {
    private:    
      qrtone_t* qrtone;
  
    public:
    QRTone(float sample_rate);

    ~QRTone();

    /**
    * Compute the maximum samples_length to feed with the method `pushSamples`
    * @return The maximum samples_length to feed with the method `pushSamples`
    */
    int32_t getMaximumLength();

    /**
    * Process audio samples in order to find payload in tones.
    * @param samples Audio samples array in float. All tests have been done with values between -1 and 1.
    * @param samples_length Size Audio samples array. The size should be inferior or equal to `qrtone_get_maximum_length`.
    * @return 1 if a payload has been received, 0 otherwise.
    */
    bool pushSamples(float* samples, int32_t samples_length);

    /**
    * Fetch stored payload. Call this function only when `qrtone_push_samples` return 1.
    * @return int8_t array of the size provided by `qrtone_get_payload_length`. QRTone is responsible for freeing this array.
    */
    int8_t* getPayload();

    /**
    * Get stored payload length. Call this function only when `pushSamples` return 1.
    * @return int8_t stored payload length.
    */
    int32_t getPayloadLength();

    /**
    * Gives the exact index of the audio sample corresponding to the beginning of the last received message.
    * This information can be used for synchronization purposes.
    * @return int64_t Audio sample index
    */
    int64_t getPayloadSampleIndex();

    /**
    * When there is not enough signal/noise ratio, some bytes could be error corrected by Reed-Solomon code.
    * @return Number of fixed
    */

    int32_t getFixedErrors();

    ///////////////////////////
    // Send payload
    ///////////////////////////

    /**
    * Set the message to send. With QRTONE_ECC_Q ECC level and with a CRC code.
    * @param payload Byte array to send.
    * @param payload Byte array length. Should be less than 255 bytes.
    * @return The number of audio samples to send.
    */
    int32_t setPayload(int8_t* payload, uint8_t payload_length);

    /**
    * Set the message to send, with additional parameters.
    * @param payload Byte array to send.
    * @param payload Byte array length. Should be less than 255 bytes.
    * @param ecc_level Error correction level `QRTONE_ECC_LEVEL`. Error correction level add robustness at the cost of tone length.
    * @param add_crc If 1 ,add a crc16 code in order to check if the message has not been altered on the receiver side.
    * @return The number of audio samples to send.
    */
    int32_t setPayloadExt(int8_t* payload, uint8_t payload_length, int8_t ecc_level, int8_t add_crc);

    /**
    * Populate the provided array with audio samples. You must call qrtone_set_payload function before.
    * @param qrtone A pointer to the initialized qrtone structure.
    * @param samples Pre-allocated array of samples_length length
    * @param samples_length array length. samples_length + offset should be equal or inferior than the total number of audio samples.
    * @param power Amplitude of the audio tones.
    */
    void getSamples(float* samples, int32_t samples_length, float power);


};
