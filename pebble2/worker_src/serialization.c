#include "serialization.h"

unsigned char* serialize_uint64(unsigned char* data, uint64_t value) {
    data[0] = (value >> 56) & 0xFF;
    data[1] = (value >> 48) & 0xFF;
    data[2] = (value >> 40) & 0xFF;
    data[3] = (value >> 32) & 0xFF;
    data[4] = (value >> 24) & 0xFF;
    data[5] = (value >> 16) & 0xFF;
    data[6] = (value >> 8) & 0xFF;
    data[7] = value & 0xFF;
    return data + 8;
}

unsigned char* serialize_int16(unsigned char* data, int16_t value) {
    data[0] = (value >> 8) & 0xFF;
    data[1] = value & 0xFF;
    return data + 2;
}
unsigned char* serialize_int32(unsigned char* data, int32_t value) {
    data[0] = (value >> 24) & 0xFF;
    data[1] = (value >> 16) & 0xFF;
    data[2] = (value >> 8) & 0xFF;
    data[3] = value & 0xFF;
    return data + 4;
}
unsigned char* serialize_char(unsigned char* data, char value) {
    data[0] = value & 0xFF;
    return data + 1;
}
