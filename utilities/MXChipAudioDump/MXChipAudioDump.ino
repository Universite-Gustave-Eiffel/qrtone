/*
 * MXChip and Arduino native headers.
 */
#include "Arduino.h"
#include "AudioClassV2.h"
#include "RGB_LED.h"

/*
 * QRTone header
 */
#include "qrtone.h"

#define RGB_LED_BRIGHTNESS 32

RGB_LED rgbLed;

/*
 * The audio sample rate used by the microphone.
 */
#define SAMPLE_RATE 16000

#define AUDIO_SAMPLE_SIZE 16

#define MAX_AUDIO_WINDOW_SIZE 16000

// QRTone instance
qrtone_t* qrtone = NULL;

// MXChip audio controler
AudioClass& Audio = AudioClass::getInstance();

/*
 * Buffers containnig the audio to play and record data.
 */
// AUDIO_CHUNK_SIZE is 512 bytes
// A sample is 2 bytes (16 bits)
// raw_audio_buffer contains 128 short left samples and 128 short right samples
// We keep only left samples
static char recordBuffer[AUDIO_CHUNK_SIZE] = {0};

// Audio circular buffer provided to QRTone.
static int64_t scaled_input_buffer_feed_cursor = 0;
static int64_t scaled_input_buffer_consume_cursor = 0;
static float scaled_input_buffer[MAX_AUDIO_WINDOW_SIZE];

static int missed_window = 0;
static int last_color = -1;
/*
 * Audio recording callback called by the Audio Class instance when a new
 * buffer of samples is available with new recorded samples.
 */
void recordCallback(void)
{
    int32_t record_buffer_length = Audio.readFromRecordBuffer(recordBuffer, AUDIO_CHUNK_SIZE);
    char* cur_reader = &recordBuffer[0];
    char* end_reader = &recordBuffer[record_buffer_length];
    const int offset = 4; // short samples size + skip right channel
    int sample_index = 0;
    while(cur_reader < end_reader) {
      int16_t sample = *((int16_t *)cur_reader);
      scaled_input_buffer[(scaled_input_buffer_feed_cursor+sample_index) % MAX_AUDIO_WINDOW_SIZE] = (float)sample / 32768.0f;
      sample_index += 1;
      cur_reader += offset;
    }
    scaled_input_buffer_feed_cursor += sample_index;
    if(scaled_input_buffer_feed_cursor - scaled_input_buffer_consume_cursor > MAX_AUDIO_WINDOW_SIZE) {
      // it overwrites unprocessed samples
      missed_window += 1;
    }
}

void debug_serial(void *ptr, float first_tone_level, float second_tone_level, int32_t triggered) {
  char buf[100];
  sprintf(buf, "%.2f,%.2f,%d\n\r", first_tone_level, second_tone_level, triggered);
  Serial.write(buf);
}

void setup(void)
{  
  Serial.begin(115200);
  // Setup the audio class
  Audio.format(SAMPLE_RATE, AUDIO_SAMPLE_SIZE);

  // Allocate struct
  qrtone = qrtone_new();

  // Init internal state
  qrtone_init(qrtone, SAMPLE_RATE);

  // Init callback method
  qrtone_set_level_callback(qrtone, NULL, debug_serial);

  // Start recording.
  int res = Audio.startRecord(recordCallback); 
}

void loop(void)
{
  // Once the recording buffer is full, we process it.
  if (scaled_input_buffer_feed_cursor > scaled_input_buffer_consume_cursor)
  {
    unsigned long start = millis();
    if(missed_window > 0 && last_color != 1) {      
      rgbLed.setColor(RGB_LED_BRIGHTNESS, 0, 0);
      last_color = 1;
      missed_window = 0;
    } else if(last_color != 2){
      rgbLed.setColor(0, RGB_LED_BRIGHTNESS, 0);
      last_color = 2;
    }
    int sample_to_read = scaled_input_buffer_feed_cursor - scaled_input_buffer_consume_cursor;
    int max_window_length = qrtone_get_maximum_length(qrtone);
    int sample_index = 0;
    while(sample_index < sample_to_read) {
      int32_t position_in_buffer = ((scaled_input_buffer_consume_cursor + sample_index) % MAX_AUDIO_WINDOW_SIZE);
      int32_t window_length = min(max_window_length, min(sample_to_read - sample_index, MAX_AUDIO_WINDOW_SIZE - position_in_buffer % MAX_AUDIO_WINDOW_SIZE));
      if(qrtone_push_samples(qrtone, scaled_input_buffer + position_in_buffer, window_length)) {
        // Got a message
        rgbLed.setColor(0, 0, RGB_LED_BRIGHTNESS);
        Serial.write((const char *) qrtone_get_payload(qrtone), qrtone_get_payload_length(qrtone));
      }
      sample_index += window_length;
      max_window_length = qrtone_get_maximum_length(qrtone);      
    }
    scaled_input_buffer_consume_cursor += sample_index;
  }
}
