#include "main_window.h"
#include "detail_window.h"
#include "../common.h"
#include "../main/worker_listener.h"
#include <pebble.h>

static Window *s_window;
static Layer *s_layer;
static GRect window_frame;

static void select_click_handler(ClickRecognizerRef recognizer, void *context) {
  AppWorkerMessage msg;
  app_worker_send_message(WORKER_KEY_TOGGLE_LOGGING, &msg);
}

static void down_click_handler(ClickRecognizerRef recognizer, void *context) {
  detail_window_push();
}

static void click_config_provider(void *context) {
  // limit the number of clicks, a Pebble bug seems to be triggered otherwise:
  // https://forums.pebble.com/t/repeating-single-click-handlers-sometimes-continue-to-be-called-after-the-button-is-released/6621/6
  window_single_repeating_click_subscribe(BUTTON_ID_SELECT, 1000, select_click_handler);
  window_single_repeating_click_subscribe(BUTTON_ID_DOWN, 1000, down_click_handler);
}

static void worker_callback(WorkerKey type) {
  if (type == WORKER_KEY_STATUS) {
    layer_mark_dirty(s_layer);
  }
}

static void layer_update_proc(Layer *layer, GContext *ctx) {
  GRect bounds = layer_get_bounds(layer);

  graphics_context_set_fill_color(ctx, GColorWhite);
  graphics_fill_circle(ctx, GPoint(bounds.size.w, bounds.size.h / 2), 5);
  graphics_fill_circle(ctx, GPoint(bounds.size.w, 7 * bounds.size.h / 8), 5);

  GRect text_bounds;
  text_bounds.origin.x = bounds.origin.x;
  text_bounds.size.w = bounds.size.w;
  text_bounds.size.h = 20;

  const char *status_text;
  if (worker_status == WORKER_STATUS_RUNNING) {
    status_text = "collecting...";
  } else {
    status_text = "disabled";
  }

  GFont id_font = fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD);
  GFont title_font = fonts_get_system_font(FONT_KEY_GOTHIC_28);

  graphics_context_set_text_color(ctx, GColorWhite);

  text_bounds.origin.y = 20;
  graphics_draw_text(ctx, "RADAR-CNS", title_font, text_bounds,
                     GTextOverflowModeFill, GTextAlignmentCenter, NULL);

  text_bounds.origin.y = (bounds.size.h / 2) - 13;
  graphics_draw_text(ctx, status_text, id_font, text_bounds,
                     GTextOverflowModeFill, GTextAlignmentCenter, NULL);
}

static void window_load(Window *window) {
  window_set_background_color(window, GColorBlack);
  window_set_click_config_provider(window, click_config_provider);

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
}

static void window_disappear(Window *window) {
  worker_listener_handler(NULL);
}

static void window_unload(Window *window) {
  layer_destroy(s_layer);
  window_destroy(s_window);
  s_window = NULL;
}

void main_window_push() {
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
