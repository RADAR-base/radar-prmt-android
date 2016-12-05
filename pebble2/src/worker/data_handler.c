#include "data_handler.h"
#include "serialization.h"
#include <pebble_worker.h>
#include <stdlib.h>
#include <errno.h>

static unsigned char *buffer;
static const unsigned char *accel_end_ptr;

static DataLoggingSessionRef battery_level_session_ref;
static DataLoggingSessionRef heart_rate_session_ref;
static DataLoggingSessionRef heart_rate_filtered_session_ref;
static DataLoggingSessionRef acceleration_session_ref;

static uint64_t timestamp() {
    time_t t_s;
    uint16_t t_ms;
    time_ms(&t_s, &t_ms);
    return 1000L * t_s + t_ms; // milliseconds
}

static void data_handler_accel(AccelData *data, uint32_t num_samples) {
  unsigned char *data_ptr = buffer;

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
  DataLoggingResult res = data_logging_log(acceleration_session_ref, buffer, 1);
  if (res != DATA_LOGGING_SUCCESS) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "failed to add acceleration data to the logging session: %d", (int) res);
  }
}

static void data_handler_battery(BatteryChargeState charge_state) {
  unsigned char *data_ptr;
  data_ptr = serialize_uint64(buffer, timestamp());
  data_ptr = serialize_char(data_ptr, charge_state.charge_percent);
  data_ptr = serialize_char(data_ptr, charge_state.is_charging);
  serialize_char(data_ptr, charge_state.is_plugged);
  DataLoggingResult res = data_logging_log(battery_level_session_ref, buffer, 1);
  if (res != DATA_LOGGING_SUCCESS) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "failed to add battery level data to the logging session: %d", (int) res);
  }
}

/** Upload health data */
static void data_handler_health(HealthEventType type, void *context) {
  // If the update was from the Heart Rate Monitor, query it
  if (type == HealthEventHeartRateUpdate) {
    unsigned char* data_ptr;

    // filtered heart rate
    data_ptr = serialize_uint64(buffer, timestamp());
    serialize_int32(data_ptr, health_service_peek_current_value(HealthMetricHeartRateBPM));
    DataLoggingResult res = data_logging_log(heart_rate_filtered_session_ref, buffer, 1);
    if (res != DATA_LOGGING_SUCCESS) {
      APP_LOG(APP_LOG_LEVEL_ERROR, "failed to add raw heart rate data to the logging session: %d", (int) res);
    }

    // raw heart rate
    serialize_int32(data_ptr, health_service_peek_current_value(HealthMetricHeartRateRawBPM));
    res = data_logging_log(heart_rate_session_ref, buffer, 1);
    if (res != DATA_LOGGING_SUCCESS) {
      APP_LOG(APP_LOG_LEVEL_ERROR, "failed to add filtered heart rate data to the logging session: %d", (int) res);
    }
  }
}

void data_handler_start(void) {
  const int accel_size = ACCELERATION_SIZE * ACCELERATION_BATCH;
  buffer = malloc(accel_size);
  if (buffer == NULL) {
    APP_LOG(APP_LOG_LEVEL_ERROR, "failed to initialize data logging buffer: %s (%d)", strerror(errno), errno);
  }
  accel_end_ptr = buffer + accel_size;

  // send all acceleration data in a single batch at once
  acceleration_session_ref = data_logging_create(ACCELERATION_LOG, DATA_LOGGING_BYTE_ARRAY, accel_size, true);

  battery_level_session_ref = data_logging_create(BATTERY_LEVEL_LOG, DATA_LOGGING_BYTE_ARRAY, BATTERY_LEVEL_SIZE, true);
  heart_rate_session_ref = data_logging_create(HEART_RATE_LOG, DATA_LOGGING_BYTE_ARRAY, HEART_RATE_SIZE, true);
  heart_rate_filtered_session_ref = data_logging_create(HEART_RATE_FILTERED_LOG, DATA_LOGGING_BYTE_ARRAY, HEART_RATE_SIZE, true);

  accel_data_service_subscribe(ACCELERATION_BATCH, data_handler_accel);
  data_handler_battery(battery_state_service_peek());
  battery_state_service_subscribe(data_handler_battery);
  data_handler_health(HealthEventHeartRateUpdate, NULL);
  health_service_events_subscribe(data_handler_health, NULL);
}

void data_handler_stop(void) {
  accel_data_service_unsubscribe();
  battery_state_service_unsubscribe();
  health_service_events_unsubscribe();

  data_logging_finish(acceleration_session_ref);
  acceleration_session_ref = NULL;
  data_logging_finish(battery_level_session_ref);
  battery_level_session_ref = NULL;
  data_logging_finish(heart_rate_session_ref);
  heart_rate_session_ref = NULL;
  data_logging_finish(heart_rate_filtered_session_ref);
  heart_rate_filtered_session_ref = NULL;

  free(buffer);
  buffer = NULL;
}

int data_handler_is_running(void) {
  return buffer != NULL;
}