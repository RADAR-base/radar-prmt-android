#include "pebble.h"
#include "main.h"
#include "serialization.h"
#include <stdlib.h>

static Window *s_main_window;
static Layer *s_disc_layer;
static GRect window_frame;
static DataLoggingSessionRef battery_level_session_ref;
static DataLoggingSessionRef heart_rate_session_ref;
static DataLoggingSessionRef heart_rate_filtered_session_ref;
static DataLoggingSessionRef acceleration_session_ref;

static unsigned char * const accel_data;
static const unsigned char * const accel_end_ptr;

static uint64_t timestamp() {
    time_t t_s;
    uint16_t t_ms;
    time_ms(&t_s, &t_ms);
    return 1000L * t_s + t_ms; // milliseconds
}

/** Upload health data */
static void prv_on_health_data(HealthEventType type, void *context) {
  // If the update was from the Heart Rate Monitor, query it
  if (type == HealthEventHeartRateUpdate) {
    unsigned char data[HEART_RATE_SIZE], data_raw[HEART_RATE_SIZE];
    unsigned char* data_ptr;

    // filtered heart rate
    uint64_t t = timestamp();
    data_ptr = serialize_uint64(data, t);
    serialize_int32(data_ptr, health_service_peek_current_value(HealthMetricHeartRateBPM));
    data_logging_log(heart_rate_filtered_session_ref, data, 1);

    // raw heart rate
    data_ptr = serialize_uint64(data_raw, t);
    serialize_int32(data_ptr, health_service_peek_current_value(HealthMetricHeartRateRawBPM));
    data_logging_log(heart_rate_session_ref, data_raw, 1);
  }
}

static void handle_battery(BatteryChargeState charge_state) {
  unsigned char data[BATTERY_LEVEL_SIZE];
  unsigned char* data_ptr;
  uint64_t t = timestamp();
  data_ptr = serialize_uint64(data, t);
  data_ptr = serialize_char(data_ptr, charge_state.charge_percent);
  data_ptr = serialize_char(data_ptr, charge_state.is_charging);
  serialize_char(data_ptr, charge_state.is_plugged);
  data_logging_log(battery_level_session_ref, data, 1);
}

static void accel_data_handler(AccelData *data, uint32_t num_samples) {
  data_ptr = accel_data;

  // copy a variable-length acceleration data batch to our own data structure with fixed length
  for (uint32_t i = 0; i < num_samples; i++) {
    // do not include acceleration during Pebble vibration notification.
    if(!data[i].did_vibrate) {
      // Read sample 0's x, y, and z values
      data_ptr = serialize_uint64(data_ptr, data[i].timestamp);
      data_ptr = serialize_int16(data_ptr, data[i].x);
      data_ptr = serialize_int16(data_ptr, data[i].y);
      data_ptr = serialize_int16(data_ptr, data[i].z);
    }
  }
  // set all data not included in a batch to zero
  if (data_ptr < accel_end_ptr) {
    memset(data_ptr, 0, accel_end_ptr - data_ptr);
  }
  // send the data
  data_logging_log(acceleration_session_ref, accel_data, 1);
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
  const int accel_size = ACCELERATION_SIZE * ACCELERATION_BATCH;
  accel_data = malloc(accel_size);
  accel_end_ptr = accel_data + accel_size;

  battery_level_session_ref = data_logging_create(BATTERY_LEVEL_LOG, DATA_LOGGING_BYTE_ARRAY, BATTERY_LEVEL_SIZE, true);
  heart_rate_session_ref = data_logging_create(HEART_RATE_LOG, DATA_LOGGING_BYTE_ARRAY, HEART_RATE_SIZE, true);
  heart_rate_filtered_session_ref = data_logging_create(HEART_RATE_FILTERED_LOG, DATA_LOGGING_BYTE_ARRAY, HEART_RATE_SIZE, true);
  // send all acceleration data in a single batch at once
  acceleration_session_ref = data_logging_create(ACCELERATION_LOG, DATA_LOGGING_BYTE_ARRAY, accel_size, true);

  s_main_window = window_create();
  window_set_background_color(s_main_window, GColorBlack);
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);

  accel_data_service_subscribe(ACCELERATION_BATCH, accel_data_handler);
  health_service_events_subscribe(prv_on_health_data, NULL);
  handle_battery(battery_state_service_peek());
  battery_state_service_subscribe(handle_battery);
}

static void deinit(void) {
  accel_data_service_unsubscribe();
  health_service_events_unsubscribe();
  battery_state_service_unsubscribe();
  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
