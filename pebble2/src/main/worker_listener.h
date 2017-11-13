#include "../common.h"

extern DeviceState device_state;
extern WorkerStatus worker_status;

void worker_listener_init();
void worker_listener_handler(void (*handler)(WorkerKey));
void worker_listener_deinit();