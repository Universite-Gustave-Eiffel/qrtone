/*
 * MXChip and Arduino native headers.
 */
#include "Arduino.h"
#include "AudioClassV2.h"
#include "RGB_LED.h"


#define RGB_LED_BRIGHTNESS 32

RGB_LED rgbLed;

/*
 * The audio sample rate used by the microphone.
 */
#define SAMPLE_RATE 16000

#define AUDIO_SAMPLE_SIZE 16

// MXChip audio controler
AudioClass& Audio = AudioClass::getInstance();

/*
 * Buffers containnig the audio to play and record data.
 */
static char recordBuffer[AUDIO_CHUNK_SIZE] = {0};
static int to_process = 0;
/*
 * Audio recording callback called by the Audio Class instance when a new
 * buffer of samples is available with new recorded samples.
 */
void recordCallback(void)
{
    if(to_process > 0) {
      rgbLed.setColor(RGB_LED_BRIGHTNESS, 0, 0);
    } else {
      rgbLed.setColor(0, RGB_LED_BRIGHTNESS, 0);
    }
    to_process = Audio.readFromRecordBuffer(recordBuffer, AUDIO_CHUNK_SIZE);
}

void setup(void)
{  
  Serial.begin(2000000);
  delay(1000);

  // Setup the audio class and start recording.
  Audio.format(SAMPLE_RATE, AUDIO_SAMPLE_SIZE);
  int res = Audio.startRecord(recordCallback);  
}

void loop(void)
{
  // Once the recording buffer is full, we process it.
  if (to_process > 0)
  {
    Serial.write(recordBuffer, to_process);
    to_process = 0;
  }
}
