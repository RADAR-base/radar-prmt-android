#pragma once

#define ACCELERATION_LOG 1
#define ACCELERATION_SIZE 14
#define ACCELERATION_BATCH 25
#define HEART_RATE_LOG 2
#define HEART_RATE_SIZE 12
#define HEART_RATE_FILTERED_LOG 3
#define BATTERY_LEVEL_LOG 4
#define BATTERY_LEVEL_SIZE 11

void data_handler_start();
void data_handler_stop();
int data_handler_is_running();
