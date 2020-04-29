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
#define SAMPLE_RATE 44100

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
 * Display message on Oled Screen
 * Message use a special format specified by the demo android app:
 * [field type int8_t][field_length int8_t][field_content int8_t array]
 * There is currently 2 type of field username(0) and message(1)à
 */
void process_message(void) {
  char* user_name = NULL;
  int user_name_length = 0;
  char* message = NULL;
  int message_length = 0;
  int8_t* data = qrtone_get_payload(qrtone);
  int32_t data_length = qrtone_get_payload_length(qrtone);
  int c = 0;
  while(c < data_length) {
    if(data[c] == 0) {
      // username
      user_name_length = (int32_t)data[c+1];
      user_name = (char*)&(data[c+2]);
    } else if(data[c]==1) {
      message_length = (int32_t)data[c+1];
      message = (char*)&(data[c+2]);
    }
  }
  if(user_name != NULL && message != NULL) {
    Screen.clean();
    // Print username
    char buf[256];
    memset(buf, 0, 256);
    memcpy(buf, user_name, user_name_length);
    Screen.print(0, buf, false);
    // Print message
    memset(buf, 0, 256);
    memcpy(buf, message, message_length);
    Screen.print(1, buf, true);
  }
}

void debug_serial(void *ptr, float first_tone_level, float second_tone_level, int32_t triggered) {
  char buf[100];
  sprintf(buf, "%.2f,%.2f,%d\n\r", first_tone_level, second_tone_level, triggered);
  Serial.write(buf);
}

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
  int max_window_length = qrtone_get_maximum_length(qrtone);
  while(cur_reader < end_reader)
  {
    int16_t sample = *((int16_t *)cur_reader);
    scaled_input_buffer[sample_index++] = (float)sample / 32768.0f;
    if(sample_index == max_window_length) {
      // End of max window length
      if(qrtone_push_samples(qrtone, scaled_input_buffer, sample_index)) {
        // Got a message
        process_message();
        sample_index = 0;
      }
      max_window_length = qrtone_get_maximum_length(qrtone);
    }
    cur_reader += offset;
  }
  // Push remaining samples
  if(sample_index > 0) {
    if(qrtone_push_samples(qrtone, scaled_input_buffer, sample_index)) {
      // Got a message
      process_message();
    }
  }
}

void setup(void)
{
  Serial.begin(115200);
  Screen.init();
  Audio.format(SAMPLE_RATE, SAMPLE_SIZE);
  // disable automatic level control
  // Audio.setPGAGain(0x3F);

  // Allocate struct
  qrtone = qrtone_new();

  // Init internal state
  qrtone_init(qrtone, SAMPLE_RATE);

  // Init callback method
  qrtone_set_level_callback(qrtone, NULL, debug_serial);

  delay(100);
  
  // Start to record audio data
  Audio.startRecord(recordCallback);
  
  printIdleMessage();
}

void loop(void)
{
  delay(100);
}

void printIdleMessage()
{
  Screen.clean();
  Screen.print(0, "Awaiting message");
}
