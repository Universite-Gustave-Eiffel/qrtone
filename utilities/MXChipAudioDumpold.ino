// output mono audio samples on serial
/*
 * MXChip and Arduino native headers.
 */
#include "Arduino.h"
#include "AudioClassV2.h"

/*
 * The audio sample rate used by the microphone.
 */
#define SAMPLE_RATE 16000

#define SAMPLE_SIZE 16

// AUDIO_CHUNK_SIZE is 512 bytes
// A sample is 2 bytes (16 bits)
// raw_audio_buffer contains 128 short left samples and 128 short right samples
// We keep only left samples
#define MAX_WINDOW_SIZE 128

// MXChip audio controler
AudioClass& Audio = AudioClass::getInstance();

// Audio buffer used by MXChip audio controler
static char raw_audio_buffer[AUDIO_CHUNK_SIZE];

// left Audio buffer
static int16_t scaled_input_buffer[MAX_WINDOW_SIZE];

/**
 * Called by AudioClass when the audio buffer is full
 */
void recordCallback(void)
{
  int length = Audio.readFromRecordBuffer(raw_audio_buffer, AUDIO_CHUNK_SIZE);
  // Convert Stereo short samples to mono samples
  char* cur_reader = &raw_audio_buffer[0];
  char* end_reader = &raw_audio_buffer[length];
  const int offset = 4; // short samples size + skip right channel
  int sample_index = 0;
  while(cur_reader < end_reader)
  {
    int16_t sample = *((int16_t *)cur_reader);
    scaled_input_buffer[sample_index++] = sample;
    cur_reader += offset;
  }
  // Push samples
  Serial.write((uint8_t*)scaled_input_buffer, sample_index * 2);
}

void setup(void)
{  
  Serial.begin(2000000);
  Screen.init();
  Audio.format(SAMPLE_RATE, SAMPLE_SIZE);
  // disable automatic level control
  // Audio.setPGAGain(0x00);
  
  // Start to record audio data
  Audio.startRecord(recordCallback);
  
}

void loop(void)
{
  
}

