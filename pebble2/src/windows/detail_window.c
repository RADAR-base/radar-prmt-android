#include "detail_window.h"
#include "../common.h"
#include "../main/worker_listener.h"
#include <pebble.h>

static Window *s_window;
static Layer *s_layer;
static GRect window_frame;
static AppTimer *timer;

typedef struct decimal_type {
  int value;
  int decimal;
  const char *sign;
} DecimalType;

DecimalType to_decimal(int value, int div, int decimal_max) {
  DecimalType d = {value / div, (value % div) / (div / decimal_max), NULL};
  if (value >= 0) {
    d.sign = "";
  } else {
    d.value = -d.value;
    d.decimal = -d.decimal;
    d.sign = "-";
  }
  return d;
}

static void prv_delay_timer_callback(void *data) {
  AppWorkerMessage msg;
  app_worker_send_message(WORKER_KEY_DEVICE_STATE, &msg);
  if (worker_status == WORKER_STATUS_RUNNING) {
    timer = app_timer_register(REDRAW_INTERVAL_MS, prv_delay_timer_callback, NULL);
  } else {
    timer = NULL;
  }
}

static void worker_callback(WorkerKey type) {
  if (type == WORKER_KEY_STATUS && worker_status == WORKER_STATUS_RUNNING && timer == NULL) {
    timer = app_timer_register(0, prv_delay_timer_callback, NULL);
  }
  layer_mark_dirty(s_layer);
}

static void layer_update_proc(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);

  GRect text_bounds;
  text_bounds.origin.x = bounds.origin.x;
  text_bounds.size.w = bounds.size.w;
  text_bounds.size.h = 20;

  const char *status_text;
  char other_text[30];
  if (worker_status == WORKER_STATUS_RUNNING) {
    status_text = "collecting...";
  } else {
    status_text = "disabled";
  }

  GFont id_font = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
  GFont data_font = fonts_get_system_font(FONT_KEY_GOTHIC_18);

  graphics_context_set_text_color(ctx, GColorWhite);

  text_bounds.origin.y = 20;
  graphics_draw_text(ctx, status_text, id_font, text_bounds,
                     GTextOverflowModeFill, GTextAlignmentCenter, NULL);

  snprintf(other_text, 30, "hr raw: %d, hr: %d", (int)device_state.heartRate, (int)device_state.heartRateFiltered);
  text_bounds.origin.y = 60;
  graphics_draw_text(ctx, other_text, data_font, text_bounds,
                     GTextOverflowModeFill, GTextAlignmentCenter, NULL);

  DecimalType x = to_decimal(device_state.x, 1000, 100);
  DecimalType y = to_decimal(device_state.y, 1000, 100);
  DecimalType z = to_decimal(device_state.z, 1000, 100);
  snprintf(other_text, sizeof(other_text), "x: %s%d.%02d, y: %s%d.%02d, z: %s%d.%02d", x.sign, x.value, x.decimal, y.sign, y.value, y.decimal, z.sign, z.value, z.decimal);
  text_bounds.origin.y = 80;
  graphics_draw_text(ctx, other_text, data_font, text_bounds,
                     GTextOverflowModeFill, GTextAlignmentCenter, NULL);

  snprintf(other_text, 30, "battery: %d (c %d, p %d)", (int)device_state.battery_level, (int)device_state.battery_charging, (int)device_state.battery_plugged);
  text_bounds.origin.y = 100;
  graphics_draw_text(ctx, other_text, data_font, text_bounds,
                     GTextOverflowModeFill, GTextAlignmentCenter, NULL);
}

static void window_load(Window *window) {
  window_set_background_color(window, GColorBlack);

  Layer *window_layer = window_get_root_layer(window);
  GRect frame = window_frame = layer_get_frame(window_layer);
  s_layer = layer_create(frame);
  layer_set_update_proc(s_layer, layer_update_proc);
  layer_add_child(window_layer, s_layer);
}

static void window_appear(Window *window) {
  worker_listener_handler(worker_callback);

  AppWorkerMessage msg;
  app_worker_send_message(WORKER_KEY_STATUS, &msg);

  timer = app_timer_register(0, prv_delay_timer_callback, NULL);
}

static void window_disappear(Window *window) {
  if (timer) {
    app_timer_cancel(timer);
    timer = NULL;
  }
  worker_listener_handler(NULL);
}

static void window_unload(Window *window) {
  layer_destroy(s_layer);
  window_destroy(s_window);
  s_window = NULL;
}

void detail_window_push() {
  if(!s_window) {
    s_window = window_create();
    window_set_window_handlers(s_window, (WindowHandlers) {
      .load = window_load,
      .appear = window_appear,
      .disappear = window_disappear,
      .unload = window_unload,
    });
  }
  window_stack_push(s_window, true);
}
