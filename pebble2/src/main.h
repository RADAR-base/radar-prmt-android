#pragma once
#include <inttypes.h>
#define ACCELERATION_LOG 1
#define HEART_RATE_LOG 2
#define HEART_RATE_FILTERED_LOG 3
#define BATTERY_LEVEL_LOG 4

#define ACCELERATION_BATCH 25



typedef struct Acceleration {
  uint64_t time;
  int16_t x;
  int16_t y;
  int16_t z;
} Acceleration;

typedef struct BatteryLevel {
  uint64_t time;
  uint8_t batteryLevel;
  int8_t isCharging;
  int8_t isPlugged;
} BatteryLevel;

typedef struct HeartRate {
  uint64_t time;
  uint32_t heartRate;
} HeartRate;
