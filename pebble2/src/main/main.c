#include <pebble.h>
#include <stdio.h>
#include "main.h"
#include "worker_listener.h"
#include "../common.h"
#include "../windows/main_window.h"

static void init(void) {
  app_worker_launch();
  worker_listener_init();
  main_window_push();
}

static void deinit(void) {
  worker_listener_deinit();
}

int main(void) {
  init();
  app_event_loop();
  deinit();
}
