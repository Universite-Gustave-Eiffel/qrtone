/**------------------------------------------------------------------------------
 *
 *  Receive and display chat messages on the Microsoft MXChip IoT DevKit.
 *
 *  @file MXChipChat.ino
 *
 *  @brief You can use the QRTone Android demo application to send audio message to the MXChip.
 * 
 * BSD 3-Clause License 
 *
 * Copyright (c) Unité Mixte de Recherche en Acoustique Environnementale (univ-gustave-eiffel)
 * All rights reserved.
 *
 *----------------------------------------------------------------------------*/

/*
 * MXChip and Arduino native headers.
 */
#include "Arduino.h"
#include "OledDisplay.h"
#include "AudioClassV2.h"

/*
 * QRTone header
 */
#include "qrtone.h"

/*
 * The audio sample rate used by the microphone. 
 * As tones go from 1700 Hz to 8000 Hz, a sample rate of 16 KHz is the minimum.
 */
#define SAMPLE_RATE 16000

#define SAMPLE_SIZE 16

// AUDIO_CHUNK_SIZE is 512 bytes
// A sample is 2 bytes (16 bits)
// raw_audio_buffer contains 128 short left samples and 128 short right samples
// We keep only left samples
#define MAX_WINDOW_SIZE 128

// QRTone instance
qrtone_t* qrtone = NULL;

// MXChip audio controler
AudioClass& Audio = AudioClass::getInstance();

// Audio buffer used by MXChip audio controler
static char raw_audio_buffer[AUDIO_CHUNK_SIZE];

// Audio buffer provided to QRTone. Samples are
static float scaled_input_buffer[MAX_WINDOW_SIZE];

/**
 * Called by AudioClass when the audio buffer is full
 */
void recordCallback(void)
{
  int length = Audio.readFromRecordBuffer(raw_audio_buffer, AUDIO_CHUNK_SIZE);
  // Convert Stereo short samples to mono float samples
  char* cur_reader = &raw_audio_buffer[0];
  char* end_reader = &raw_audio_buffer[length];
  const int offset = 4; // short samples size + skip right channel
  int sample_index = 0;
  while(cur_reader < end_reader)
  {
    int16_t sample = *((int16_t *)cur_reader);
    scaled_input_buffer[sample_index++] = (float)sample / 32768.0f;
    cur_reader += offset;
  }
  if(qrtone_push_samples(qrtone, scaled_input_buffer, sample_index)) {
    // Got a message
    
  }
}

void setup(void)
{
  Screen.init();
  Audio.format(SAMPLE_RATE, SAMPLE_SIZE);

  // Allocate struct
  qrtone = qrtone_new();
  
  // Init internal state
  qrtone_init(qrtone, SAMPLE_RATE);
}

void loop(void)
{
  printIdleMessage();
}

void printIdleMessage()
{
  Screen.clean();
  Screen.print(0, "Awaiting message");
}
