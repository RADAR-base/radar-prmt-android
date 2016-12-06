#pragma once

#include "../common.h"

#define ACCELERATION_LOG 11
#define HEART_RATE_LOG 12
#define HEART_RATE_FILTERED_LOG 13
#define BATTERY_LEVEL_LOG 14

// Pebble2 data logging is apparently limited to 256 bytes. That means we can pack 18 acceleration
// messages in a single batch: 14*18 = 252
#define ACCELERATION_SIZE 14
#define ACCELERATION_BATCH 18
#define BATTERY_LEVEL_SIZE 11
#define HEART_RATE_SIZE 12

extern DeviceState device_state;

void data_handler_start();
void data_handler_stop();
