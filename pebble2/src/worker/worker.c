#include "worker.h"
#include "data_handler.h"
#include "../common.h"
#include <pebble_worker.h>

static void start() {
  data_handler_start();
  AppWorkerMessage msg = {
    .data0 = WORKER_STATUS_RUNNING,
  };
  app_worker_send_message(WORKER_KEY_STATUS, &msg);
}

static void stop() {
  data_handler_stop();
  AppWorkerMessage msg = {
    .data0 = WORKER_STATUS_DISABLED,
  };
  app_worker_send_message(WORKER_KEY_STATUS, &msg);
}

static void worker_message_handler(uint16_t type, AppWorkerMessage *data) {
  AppWorkerMessage msg, b_msg, hr_msg;
  DeviceState state;

  switch (type) {
    case WORKER_KEY_START_LOGGING:
      if (!data_handler_is_running()) {
        persist_write_bool(PERSIST_KEY_IS_LOGGING, true);
        start();
      }
      break;
    case WORKER_KEY_TOGGLE_LOGGING:
      if (!data_handler_is_running()) {
        persist_write_bool(PERSIST_KEY_IS_LOGGING, true);
        start();
      } else {
        persist_write_bool(PERSIST_KEY_IS_LOGGING, false);
        stop();
      }
      break;
    case WORKER_KEY_DEVICE_STATE:
      data_handler_state(&state);
      msg.data0 = state.x;
      msg.data1 = state.y;
      msg.data2 = state.z;
      app_worker_send_message(WORKER_KEY_DEVICE_STATE_ACCEL, &msg);
      hr_msg.data0 = state.heartRate;
      hr_msg.data1 = state.heartRateFiltered;
      app_worker_send_message(WORKER_KEY_DEVICE_STATE_HEART_RATE, &hr_msg);
      b_msg.data0 = state.battery_level;
      b_msg.data1 = state.battery_charging;
      b_msg.data2 = state.battery_plugged;
      app_worker_send_message(WORKER_KEY_DEVICE_STATE_BATTERY, &b_msg);
      break;
    case WORKER_KEY_STOP_LOGGING:
      if (data_handler_is_running()) {
        persist_write_bool(PERSIST_KEY_IS_LOGGING, false);
        stop();
      }
      break;
    case WORKER_KEY_STATUS:
      msg.data0 = data_handler_is_running() ? WORKER_STATUS_RUNNING : WORKER_STATUS_DISABLED;
      app_worker_send_message(WORKER_KEY_STATUS, &msg);
      break;
  }
}


static void init(void) {
  app_worker_message_subscribe(worker_message_handler);

  if (persist_read_bool(PERSIST_KEY_IS_LOGGING)) {
    start();
  }
}

static void deinit(void) {
  if (data_handler_is_running()) {
    stop();
  }
}

int main(void) {
  init();
  worker_event_loop();
  deinit();
}
