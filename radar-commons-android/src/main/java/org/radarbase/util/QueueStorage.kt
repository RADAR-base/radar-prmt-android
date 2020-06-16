/*
 * Copyright 2017 The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.radarbase.util

import java.io.Closeable
import java.io.Flushable
import java.io.IOException

/**
 * Storage for a queue. Data in the queue must be written contiguously starting at position 0. The
 * storage uses wraparound, so data written that would exceed the end of the storage, will be
 * written to the beginning.
 */

interface QueueStorage : Closeable, Flushable {

    /** Minimum size of the storage in bytes.  */
    val minimumLength: Long

    /**
     * Maximum size of the storage in bytes.
     * @throws IllegalArgumentException if the length is larger than the storage medium allows.
     */
    var maximumLength: Long

    /** Whether the close function was called.  */
    val isClosed: Boolean

    /**
     * Write data to storage medium. The position will wrap around.
     * @param position position to write to
     * @param buffer buffer to write
     * @param offset offset in buffer to write
     * @param count number of bytes to write
     * @throws IndexOutOfBoundsException if `position < 0`,
     * `offset < 0`, `count < 0`,
     * `offset + count > buffer.length`, or
     * `count > file size - QueueFileHeader.ELEMENT_HEADER_LENGTH`
     * @throws IOException if the storage is full or cannot be written to
     * @return wrapped position after the write)
     */
    @Throws(IOException::class)
    fun write(position: Long, buffer: ByteArray, offset: Int, count: Int): Long

    /**
     * Read data from storage medium. The position will wrap around.
     * @param position position read from
     * @param buffer buffer to read data into
     * @param offset offset in buffer read data to
     * @param count number of bytes to read
     * @throws IndexOutOfBoundsException if `position < QueueFileHeader.ELEMENT_HEADER_LENGTH`,
     * `offset < 0`, `count < 0`, or
     * `offset + count > buffer.length`
     * @throws IOException if the storage cannot be read.
     * @return wrapped position after the read
     */
    @Throws(IOException::class)
    fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Long

    /**
     * Move part of the storage to another location, overwriting any data on the previous location.
     *
     * @throws IllegalArgumentException if `srcPosition < QueueFileHeader.ELEMENT_HEADER_LENGTH`,
     * `dstPosition < QueueFileHeader.ELEMENT_HEADER_LENGTH`,
     * `count <= 0`, `srcPosition + count > size`
     * or `dstPosition + count > size`
     */
    @Throws(IOException::class)
    fun move(srcPosition: Long, dstPosition: Long, count: Long)

    /**
     * Resize the storage. If the size is made smaller, after the given size is discarded. If the
     * size is made larger, the new part is not yet used. To use a new part of the storage, write to
     * it contiguously from previously written data.
     *
     * @param size new size in bytes.
     * @throws IllegalArgumentException if `size < QueueFileHeader.ELEMENT_HEADER_LENGTH` or
     * if the size is increased and `size > #getMaximumSize()`.
     * @throws IOException if the storage could not be resized
     */
    @Throws(IOException::class)
    fun resize(size: Long)

    /**
     * Current size of the storage.
     * @return size in bytes
     */
    val length: Long

    /** Whether underlying file existed when the current object was created.  */
    val isPreExisting: Boolean
}
