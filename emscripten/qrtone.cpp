#include <emscripten/bind.h>

using namespace emscripten;


#include "qrtone.hpp"

QRTone::QRTone(float sample_rate) {
    qrtone = qrtone_new();
    qrtone_init(qrtone, sample_rate);
}

QRTone::~QRTone() {
    qrtone_free(qrtone);
}

int32_t QRTone::getMaximumLength() {
    return qrtone_get_maximum_length(this->qrtone);
}

bool QRTone::pushSamples(float* samples, int32_t samples_length) {
    return qrtone_push_samples(this->qrtone, samples, samples_length);
}

int8_t* QRTone::getPayload() {        
    return qrtone_get_payload(this->qrtone);
}

int32_t QRTone::getPayloadLength() {
    return qrtone_get_payload_length(this->qrtone);
}

int64_t QRTone::getPayloadSampleIndex() {
    return qrtone_get_payload_sample_index(this->qrtone);
}

int32_t QRTone::getFixedErrors() {
    return qrtone_get_fixed_errors(this->qrtone);        
}

int32_t QRTone::setPayload(int8_t* payload, uint8_t payload_length) {
    return qrtone_set_payload(this->qrtone, payload, payload_length);   
}

int32_t QRTone::setPayloadExt(int8_t* payload, uint8_t payload_length, int8_t ecc_level, int8_t add_crc) {
    return qrtone_set_payload_ext(this->qrtone, payload, payload_length, ecc_level, add_crc);
}

void QRTone::getSamples(float* samples, int32_t samples_length, float power) {
    qrtone_get_samples(this->qrtone, samples, samples_length, power);
}



EMSCRIPTEN_BINDINGS(my_module) {
    class_<QRTone>("QRTone")
    .constructor<float>();
}


