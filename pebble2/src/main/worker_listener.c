#include <pebble.h>
#include "worker_listener.h"

DeviceState device_state;
WorkerStatus worker_status;
static void (*handler)(WorkerKey);

static void worker_message_handler(uint16_t type, AppWorkerMessage *data) {
  switch (type) {
    case WORKER_KEY_STATUS:
      worker_status = data->data0;
      break;
    case WORKER_KEY_DEVICE_STATE_ACCEL:
      device_state.x = data->data0;
      device_state.y = data->data1;
      device_state.z = data->data2;
      break;
    case WORKER_KEY_DEVICE_STATE_BATTERY:
      device_state.battery_level = data->data0;
      device_state.battery_charging = data->data1;
      device_state.battery_plugged = data->data2;
      break;
    case WORKER_KEY_DEVICE_STATE_HEART_RATE:
      device_state.heartRate = data->data0;
      device_state.heartRateFiltered = data->data1;
      break;
  }
  if (handler) handler((WorkerKey)type);
}

void worker_listener_init() {
  app_worker_message_subscribe(worker_message_handler);
  handler = NULL;
}

void worker_listener_handler(void (*callback)(WorkerKey)) {
  handler = callback;
}

void worker_listener_deinit() {
  app_worker_message_unsubscribe();
  handler = NULL;
}
