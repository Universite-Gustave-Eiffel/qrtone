#include <PDM.h>
#include <string.h>

/*
 * QRTone header
 */
#include "qrtone.h"


// QRTone instance
qrtone_t* qrtone = NULL;

#define SAMPLE_RATE 41667

// audio circular buffer size
#define MAX_AUDIO_WINDOW_SIZE 512

// buffer to read samples into, each sample is 16-bits
short sampleBuffer[256];

// Audio circular buffer provided to QRTone.
static int64_t scaled_input_buffer_feed_cursor = 0;
static int64_t scaled_input_buffer_consume_cursor = 0;
static float scaled_input_buffer[MAX_AUDIO_WINDOW_SIZE];

// number of samples read
volatile int samplesRead;

void setup() {
  Serial.begin(9600);

  // Uncomment to wait for serial comm to begin setup
  // while (!Serial);

  // initialize digital pin LED_BUILTIN as an output.
  pinMode(LED_BUILTIN, OUTPUT);

  // Allocate struct
  qrtone = qrtone_new();

  // Init internal state
  qrtone_init(qrtone, SAMPLE_RATE);

  // Init callback method for displaying noise level for gate frequencies
  // qrtone_set_level_callback(qrtone, NULL, debug_serial);

  // configure the data receive callback
  PDM.onReceive(onPDMdata);

  // optionally set the gain, defaults to 20
  PDM.setGain(30);

  // initialize PDM with:
  // - one channel (mono mode)
  // - a 16 kHz sample rate
  if (!PDM.begin(1, SAMPLE_RATE)) {
    Serial.println("Failed to start PDM!");
    while (1);
  }
}

/**
 * Display message on Oled Screen
 * Message use a special format specified by the demo android app:
 * [field type int8_t][field_length int8_t][field_content int8_t array]
 * There is currently 2 type of field username(0) and message(1)à
 */
void process_message(void) {
  int32_t user_name = 0;
  int user_name_length = 0;
  int32_t message = 0;
  int message_length = 0;
  int8_t* data = qrtone_get_payload(qrtone);
  int32_t data_length = qrtone_get_payload_length(qrtone);
  int c = 0;
  while(c < data_length) {
    int8_t field_type = data[c++];
    if(field_type == 0) {
      // username
      user_name_length = (int32_t)data[c++];
      user_name = c;
      c += user_name_length;
    } else if(field_type == 1) {
      message_length = (int32_t)data[c++];
      message = c;
      c += message_length;
    }
  }
  if(user_name > 0 && message > 0) {
    // Print username
    char buf[256];
    memset(buf, 0, 256);
    memcpy(buf, data + user_name, user_name_length);
    Serial.println(buf);
    // Print message
    memset(buf, 0, 256);
    memcpy(buf, data + message, message_length);
    Serial.println(buf);
    // Special commands
    if(strcmp( buf, "off" ) == 0) {
      digitalWrite(LED_BUILTIN, LOW);
    } else if(strcmp( buf, "on" ) == 0) {
      digitalWrite(LED_BUILTIN, HIGH);
    }
  }
}

void debug_serial(void *ptr, int64_t location, float first_tone_level, float second_tone_level, int32_t triggered) {
  char buf[100];
  sprintf(buf, "%.2f,%.2f,%ld\n\r", first_tone_level, second_tone_level, triggered * -10);
  Serial.write(buf);
}

void loop() {
  //Once the recording buffer is full, we process it.
  if (scaled_input_buffer_feed_cursor > scaled_input_buffer_consume_cursor)
  {
    if(scaled_input_buffer_feed_cursor - scaled_input_buffer_consume_cursor > MAX_AUDIO_WINDOW_SIZE) {
      // overflow
      Serial.println("Buffer overflow");
    }
    int sample_to_read = scaled_input_buffer_feed_cursor - scaled_input_buffer_consume_cursor;
    int max_window_length = qrtone_get_maximum_length(qrtone);
    int sample_index = 0;
    while(sample_index < sample_to_read) {
      int32_t position_in_buffer = ((scaled_input_buffer_consume_cursor + sample_index) % MAX_AUDIO_WINDOW_SIZE);
      int32_t window_length = min(max_window_length, min(sample_to_read - sample_index, MAX_AUDIO_WINDOW_SIZE - position_in_buffer % MAX_AUDIO_WINDOW_SIZE));
      if(qrtone_push_samples(qrtone, scaled_input_buffer + position_in_buffer, window_length)) {
        // Got a message
        process_message();
      }
      sample_index += window_length;
      max_window_length = qrtone_get_maximum_length(qrtone);      
    }
    scaled_input_buffer_consume_cursor += sample_index;
  }  
}

void onPDMdata() {
  // query the number of bytes available
  int bytesAvailable = PDM.available();

  // read into the sample buffer
  PDM.read(sampleBuffer, bytesAvailable);

  // 16-bit, 2 bytes per sample
  samplesRead = bytesAvailable / 2;

  for(int sample_id = 0; sample_id < samplesRead; sample_id++) {
    scaled_input_buffer[(scaled_input_buffer_feed_cursor+sample_id) % MAX_AUDIO_WINDOW_SIZE] = (float)sampleBuffer[sample_id] / 32768.0f;
  }
  scaled_input_buffer_feed_cursor += samplesRead;
}
