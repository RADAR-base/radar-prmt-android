#include "pebble.h"
#include "main.h"
#include <stdlib.h>

static Window *s_main_window;
static Layer *s_disc_layer;
static GRect window_frame;
static DataLoggingSessionRef battery_level_session_ref;
static DataLoggingSessionRef heart_rate_session_ref;
static DataLoggingSessionRef heart_rate_filtered_session_ref;
static DataLoggingSessionRef acceleration_session_ref;

static Acceleration *accel_data;

static void accel_data_handler(AccelData *data, uint32_t num_samples) {
  // copy a variable-length acceleration data batch to our own data structure with fixed length
  for (uint32_t i = 0; i < num_samples; i++) {
    if(!data[i].did_vibrate) {
      // Read sample 0's x, y, and z values
      accel_data[i].time = data[i].timestamp;
      accel_data[i].x = data[i].x;
      accel_data[i].y = data[i].y;
      accel_data[i].z = data[i].z;
    } else {
      // do not include acceleration during Pebble vibration notification.
      memset(&accel_data[i], 0, sizeof(Acceleration));
    }
  }
  // set all data not included in a batch to zero
  if (num_samples < ACCELERATION_BATCH) {
    memset(&accel_data[num_samples], 0, sizeof(Acceleration)*(ACCELERATION_BATCH - num_samples));
  }
  // send the data
  data_logging_log(acceleration_session_ref, &accel_data, 1);
}

static void main_window_load(Window *window) {
  Layer *window_layer = window_get_root_layer(window);
  GRect frame = window_frame = layer_get_frame(window_layer);

  s_disc_layer = layer_create(frame);
//   layer_set_update_proc(s_disc_layer, disc_layer_update_callback);
  layer_add_child(window_layer, s_disc_layer);
}

static void main_window_unload(Window *window) {
  layer_destroy(s_disc_layer);
}

static void init(void) {
  accel_data = malloc(sizeof(Acceleration)*ACCELERATION_BATCH);

  battery_level_session_ref = data_logging_create(BATTERY_LEVEL_LOG, DATA_LOGGING_BYTE_ARRAY, sizeof(BatteryLevel), true);
  heart_rate_session_ref = data_logging_create(HEART_RATE_LOG, DATA_LOGGING_BYTE_ARRAY, sizeof(HeartRate), true);
  heart_rate_filtered_session_ref = data_logging_create(HEART_RATE_FILTERED_LOG, DATA_LOGGING_BYTE_ARRAY, sizeof(HeartRate), true);
  // send all acceleration data in a single batch at once
  acceleration_session_ref = data_logging_create(ACCELERATION_LOG, DATA_LOGGING_BYTE_ARRAY, ACCELERATION_BATCH*sizeof(Acceleration), true);

  s_main_window = window_create();
  window_set_background_color(s_main_window, GColorBlack);
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);

  accel_data_service_subscribe(25, accel_data_handler);
}

static void deinit(void) {
  accel_data_service_unsubscribe();

  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
