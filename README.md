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

## Contributing

To add additional device types to this application, make the following steps:

- Create a new package `org.radarcns.mydevicetype`
- In that package, create classes that
  - implement `org.radarcns.android.DeviceManager`
  - implement `org.radarcns.android.DeviceState`
  - subclass `android.os.Service` with a binder that implements `org.radarcns.android.DeviceServiceBinder`, and sends its data using a TableDataHandler.
  - implement a singleton like `org.radarcns.empaticaE4.E4Topics` that contains all Kafka topics that the wearable will generate.
- In the `avro/src/main/resources/avro` directory, create the Avro schemas for your record values. Your record keys should be `org.radarcns.key.MeasurementKey`. Be sure to set the `namespace` property to `org.radarcns.mydevicetype` so that generated classes will be put in the right package. All values should have `time` and `timeReceived` fields, with type `double`. These represent the time in seconds since the Unix Epoch (1 January 1970, 00:00:00 UTC). Subsecond precision is possible by using floating point decimals.
- Finally, extend the main activity to run a service for showing the device data and for controlling the recording.

Make a pull request once the code is working.
