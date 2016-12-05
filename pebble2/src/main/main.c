#include <pebble.h>
#include "main.h"

static Window *s_main_window;
static Layer *s_disc_layer;
static GRect window_frame;
static AppTimer *timer;

static void prv_delay_timer_callback(void *data) {
  app_timer_reschedule(timer, REDRAW_INTERVAL_MS);
}

static void prv_app_message_handler(uint16_t type, AppWorkerMessage *data) {

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
  timer = app_timer_register(0, prv_delay_timer_callback, NULL);
  app_worker_launch();
  app_worker_message_subscribe(prv_app_message_handler);
  s_main_window = window_create();
  window_set_background_color(s_main_window, GColorBlack);
  window_set_window_handlers(s_main_window, (WindowHandlers) {
    .load = main_window_load,
    .unload = main_window_unload
  });
  window_stack_push(s_main_window, true);
}

static void deinit(void) {
  app_timer_cancel(timer);
  app_worker_message_unsubscribe();
  window_destroy(s_main_window);
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
