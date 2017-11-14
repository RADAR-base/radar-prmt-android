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

package org.radarcns.detail;

import org.radarcns.android.RadarService;

import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.RECEIVE_BOOT_COMPLETED;

public class DetailRadarService extends RadarService {
    @Override
    protected List<String> getServicePermissions() {
        List<String> superPermissions = super.getServicePermissions();
        List<String> result = new ArrayList<>(superPermissions.size() + 1);
        result.addAll(superPermissions);
        result.add(RECEIVE_BOOT_COMPLETED);
        return result;
    }

    @Override
    protected void configure() {
        super.configure();
        configureRunAtBoot(MainActivityBootStarter.class);
    }
}

