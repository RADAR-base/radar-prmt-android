#pragma once

#include "../common.h"

#define ACCELERATION_LOG 11
#define HEART_RATE_LOG 12
#define HEART_RATE_FILTERED_LOG 13
#define BATTERY_LEVEL_LOG 14

#define ACCELERATION_SIZE 14
#define ACCELERATION_BATCH 18
#define BATTERY_LEVEL_SIZE 11
#define HEART_RATE_SIZE 12

void data_handler_start();
void data_handler_stop();
int data_handler_is_running();
void data_handler_state(DeviceState *state);