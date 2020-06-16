package org.radarbase.android.util;/*
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

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * Test that all hashes are unique for different values and the same for the same values.
 * Also test that all hashes are unique for different keys but with the same values.
 */
@RunWith(AndroidJUnit4.class)
public class HashGeneratorTest {

    private SharedPreferences getPrefs(String name) {
        return ApplicationProvider.getApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    @Test
    public void createHash() {
        HashGenerator hasher1 = new HashGenerator(getPrefs("createHash1"));
        HashGenerator hasher2 = new HashGenerator(getPrefs("createHash2"));
        Random random = new Random();

        byte[] previousB = null;
        int previousV = 0;
        for (int i = 0; i < 10; i++) {
            int v = random.nextInt();
            byte[] b1 = hasher1.createHash(v);
            byte[] b2 = hasher1.createHash(v);
            byte[] b3 = hasher2.createHash(v);
            Assert.assertNotNull(b1);
            Assert.assertTrue(Arrays.equals(b2, b1));
            Assert.assertFalse(Arrays.equals(b3, b1));
            if (i > 0 && previousV != v) {
                Assert.assertFalse(Arrays.equals(previousB, b1));
            }
            previousV = v;
            previousB = b1;
        }
    }

    @Test
    public void createHash1() {
        HashGenerator hasher1 = new HashGenerator(getPrefs("createHashString1"));
        HashGenerator hasher2 = new HashGenerator(getPrefs("createHashString2"));

        byte[] previousB = null;
        String previousV = null;
        for (int i = 0; i < 10; i++) {
            String v = UUID.randomUUID().toString();
            byte[] b1 = hasher1.createHash(v);
            byte[] b2 = hasher1.createHash(v);
            byte[] b3 = hasher2.createHash(v);
            Assert.assertTrue(Arrays.equals(b2, b1));
            Assert.assertFalse(Arrays.equals(b3, b1));
            if (i > 0 && !v.equals(previousV)) {
                Assert.assertFalse(Arrays.equals(previousB, b1));
            }
            previousV = v;
            previousB = b1;
        }
    }

    @Test
    public void createHashByteBuffer() {
        HashGenerator hasher1 = new HashGenerator(getPrefs("createHashByteBuffer1"));
        HashGenerator hasher2 = new HashGenerator(getPrefs("createHashByteBuffer2"));
        Random random = new Random();

        ByteBuffer previousB = null;
        int previousV = 0;
        for (int i = 0; i < 10; i++) {
            int v = random.nextInt();
            ByteBuffer b1 = hasher1.createHashByteBuffer(v);
            ByteBuffer b2 = hasher1.createHashByteBuffer(v);
            ByteBuffer b3 = hasher2.createHashByteBuffer(v);
            Assert.assertEquals(b2, b1);
            Assert.assertNotEquals(b3, b1);
            if (i > 0 && v != previousV) {
                Assert.assertNotEquals(previousB, b1);
            }
            previousV = v;
            previousB = b1;
        }
    }

    @Test
    public void createHashByteBuffer1() {
        HashGenerator hasher1 = new HashGenerator(getPrefs("createHashByteBufferString1"));
        HashGenerator hasher2 = new HashGenerator(getPrefs("createHashByteBufferString2"));

        ByteBuffer previousB = null;
        String previousV = null;
        for (int i = 0; i < 10; i++) {
            String v = UUID.randomUUID().toString();
            ByteBuffer b1 = hasher1.createHashByteBuffer(v);
            ByteBuffer b2 = hasher1.createHashByteBuffer(v);
            ByteBuffer b3 = hasher2.createHashByteBuffer(v);
            Assert.assertEquals(b2, b1);
            Assert.assertNotEquals(b3, b1);
            if (i > 0 && !v.equals(previousV)) {
                Assert.assertNotEquals(previousB, b1);
            }
            previousV = v;
            previousB = b1;
        }
    }

}
