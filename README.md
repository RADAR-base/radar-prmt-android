# RADAR-AndroidApplication
Application to be run on an Android 4.4 (or later) device with Bluetooth Low Energy (Bluetooth 4.0 or later), to interact with wearable devices. The app is cloned from the [Empatica sample app][2].

Currently only the Empatica E4 is supported. 

## Setup Empatica E4

First, request an Empatica API key for your Empatica Connect account from our [Developer Area][1]. Also download the Empatica Android SDK there.

- Clone / download this repository.
- Copy the empalink-2.0.aar from the Empatica Android SDK package to the `empalink-2.0` directory.
- Edit the `apikey.xml` file and enter your Empatica API key in the apikey xml element.
- Edit the `server.xml` file and add the URLs of the Kafka REST Proxy and the Schema Registry. If the app should not upload any data, leave them blank.
- Move the `apikey.xml` and `server.xml` files to the `app/src/main/res/values/` directory.
- Edit the `app/src/main/res/values/device.xml` file by setting the `group_id` string to a suitable user ID.
- Compile the project with `./gradlew build`.
- Open the project in Android Studio.
- Launch the application. Note that the application only runs on an ARM architecture

[1]: https://www.empatica.com/connect/developer.php
[2]: https://github.com/empatica/empalink-sample-project-android

## Usage

To send some mock data to the `radar-test.thehyve.net` server, run `./gradlew :app:cleanTest :app:test`.
