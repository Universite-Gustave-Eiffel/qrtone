#include <PDM.h>

/*
 * QRTone header
 */
#include "qrtone.h"

// QRTone instance
qrtone_t* qrtone = NULL;

#define SAMPLE_RATE 16000

// buffer to read samples into, each sample is 16-bits
short sampleBuffer[256];

static float scaled_input_buffer[256];

// number of samples read
volatile int samplesRead;

void setup() {
  Serial.begin(9600);
  while (!Serial);

  // Allocate struct
  //qrtone = qrtone_new();

  // Init internal state
  //qrtone_init(qrtone, SAMPLE_RATE);

  // configure the data receive callback
  PDM.onReceive(onPDMdata);

  // optionally set the gain, defaults to 20
  // PDM.setGain(30);

  // initialize PDM with:
  // - one channel (mono mode)
  // - a 16 kHz sample rate
  if (!PDM.begin(1, SAMPLE_RATE)) {
    Serial.println("Failed to start PDM!");
    while (1);
  }
}

void loop() {
  if(samplesRead > 0) {
    Serial.println(samplesRead);
    samplesRead = 0;
  }
}

void onPDMdata() {
  // query the number of bytes available
  int bytesAvailable = PDM.available();


  // read into the sample buffer
  PDM.read(sampleBuffer, bytesAvailable);

  // 16-bit, 2 bytes per sample
  samplesRead = bytesAvailable / 2;
}