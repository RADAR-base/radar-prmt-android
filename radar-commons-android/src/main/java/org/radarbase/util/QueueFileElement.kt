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

/**
 * A pointer to an element. It contains a starting position and length of the data that the element
 * contains.
 * @param position within file
 * @param length of data
 */
class QueueFileElement(position: Long = 0L, length: Int = 0) {
    /** Start position.  */
    var position: Long = position
        set(value) {
            require(value >= 0) { "position < 0" }
            field = value
        }

    /**
     * Get the length in bytes, excluding the header. If this is 0, the element is not valid
     * for reading or writing.
     */
    var length: Int = length
        set(value) {
            require(value >= 0) { "length < 0" }
            field = value
        }

    /** Single byte checksum of the length.  */
    val crc: Byte
        get() = crc(length)

    /** The element does not have valid data.  */
    val isEmpty: Boolean
        get() = length == 0

    /** Position that the data begins.  */
    val dataPosition: Long
        get() {
            check(!isEmpty) { "Cannot get data position of empty element" }
            return position + ELEMENT_HEADER_LENGTH
        }

    /** Position of the next element.  */
    val nextPosition: Long
        get() = if (isEmpty) {
            QueueFileHeader.QUEUE_HEADER_LENGTH.toLong()
        } else {
            position + ELEMENT_HEADER_LENGTH + length
        }

    /** Update element values to the given element.  */
    fun update(element: QueueFileElement) {
        this.position = element.position
        this.length = element.length
    }

    /**
     * Update the current position if it was moved. This is deduced from the previous element's
     * position and length and from the file length.
     */
    fun updateIfMoved(previousElement: QueueFileElement, header: QueueFileHeader) {
        if (position < previousElement.position && previousElement.nextPosition < header.length) {
            position = previousElement.nextPosition
        }
    }

    /** Sets the element to empty.  */
    fun reset() {
        position = 0
        length = 0
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        val otherElement = other as QueueFileElement
        return position == otherElement.position && length == otherElement.length
    }

    override fun hashCode(): Int {
        return 31 * (position shr 32 xor position).toInt() + length
    }

    override fun toString(): String {
        return "QueueFileElement[position=$position, length=$length]"
    }

    companion object {
        /** Length of element header in bytes.  */
        const val ELEMENT_HEADER_LENGTH = 5

        /** Single byte checksum of given value.  */
        fun crc(value: Int): Byte {
            var result: Byte = 17
            result = (31 * result + (value shr 24) and 0xFF).toByte()
            result = (31 * result + (value shr 16 and 0xFF)).toByte()
            result = (31 * result + (value shr 8 and 0xFF)).toByte()
            result = (31 * result + (value and 0xFF)).toByte()
            return result
        }
    }
}
