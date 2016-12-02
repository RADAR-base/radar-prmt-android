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

static void prv_on_health_data(HealthEventType type, void *context) {
  // If the update was from the Heart Rate Monitor, query it
  if (type == HealthEventHeartRateUpdate) {
    HeartRate heartRate;

    time_t t_s;
    uint16_t t_ms;
    time_ms(&t_s, &t_ms);
    heartRate.time = 1000L * t_s + t_ms; // milliseconds

    heartRate.heartRate = health_service_peek_current_value(HealthMetricHeartRateBPM);
    data_logging_log(heart_rate_filtered_session_ref, &heartRate, 1);

    heartRate.heartRate = health_service_peek_current_value(HealthMetricHeartRateRawBPM);
    data_logging_log(heart_rate_session_ref, &heartRate, 1);
  }
}

static void handle_battery(BatteryChargeState charge_state) {
  BatteryLevel level;
  time_t t_s;
  uint16_t t_ms;
  time_ms(&t_s, &t_ms);
  level.time = 1000L * t_s + t_ms; // milliseconds

  level.batteryLevel = charge_state.charge_percent;
  level.isCharging = charge_state.is_charging;
  level.isPlugged = charge_state.is_plugged;
  data_logging_log(battery_level_session_ref, &level, 1);
}

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
