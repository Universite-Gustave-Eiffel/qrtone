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

#define AUDIO_SAMPLE_SIZE 16
#define SHORT_BUFFER_SIZE (AUDIO_CHUNK_SIZE / 2)

// MXChip audio controler
AudioClass& Audio = AudioClass::getInstance();

/*
 * To keep track of audio buffer states.
 */
typedef enum {
  BUFFER_STATE_NONE,
  BUFFER_STATE_EMPTY,
  BUFFER_STATE_FULL,
} bufferState;

bufferState recordBufferState = BUFFER_STATE_EMPTY;

/*
 * Buffers containnig the audio to play and record data.
 */
static int16_t shortRecordBuffer[SHORT_BUFFER_SIZE] = {0};

/*
 * Audio recording callback called by the Audio Class instance when a new
 * buffer of samples is available with new recorded samples.
 */
void recordCallback(void)
{
    Audio.readFromRecordBuffer((char *) shortRecordBuffer, SHORT_BUFFER_SIZE * 2);
    recordBufferState = BUFFER_STATE_FULL;
}

void setup(void)
{  
  Serial.begin(2000000);
  delay(1000);
  Screen.init();
  
  Screen.clean();
  Screen.print(0, "Listening ...");
  // Setup the audio class and start recording.
  Audio.setPGAGain(0x00);
  Audio.format(SAMPLE_RATE, AUDIO_SAMPLE_SIZE);
  int res = Audio.startRecord(recordCallback);  
}

void loop(void)
{
  // Once the recording buffer is full, we process it.
  if (recordBufferState == BUFFER_STATE_FULL)
  {
    Serial.write((char*)shortRecordBuffer, SHORT_BUFFER_SIZE * 2);
    recordBufferState = BUFFER_STATE_EMPTY;
  }
}

