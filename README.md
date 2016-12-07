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

## Setup Pebble 2

To run this app with a Pebble 2 device, the RADAR-CNS app must be installed on the Pebble 2. For now, [install the Pebble SDK](https://developer.pebble.com/sdk/install/) on your machine. Go to the `pebble2/` directory. There we can use the [Pebble command line tools](https://developer.pebble.com/guides/tools-and-resources/pebble-tool/). First, build the app with
```shell
pebble build
```
Then run the following sequence:

1. Pair the Pebble 2 with the app on the endpoint.
2. Disable Bluetooth on the endpoint.
3. Enable Bluetooth on your phone.
4. Pair the Pebble 2 with the Pebble app on your phone.
5. Open the developer menu on the Pebble app on your phone and enable developer mode.
6. Install the app with `pebble install --phone 1.2.3.4` with the IP address stated in the Pebble app on your phone.
7. Disable Bluetooth on your phone. If desired, remove the pairing with your phone and the Pebble 2 device.
8. Enable Bluetooth on the endpoint.

The RADAR-CNS Pebble app will now send data to the endpoint.

## Usage

To send some mock data to a Confluent Kafka set up on localhost, run `./gradlew :app:cleanTest :app:test`. If the Confluent Kafka setup is running elsewhere, edit `app/src/test/resources/org/radarcns/kafka/kafka.properties` accordingly.

## Contributing

To add additional device types to this application, make the following steps (see the `org.radarcns.pebble2` package as an example):

- In the `avro/src/main/resources/avro` directory, create the Avro schemas for your record values. Your record keys should be `org.radarcns.key.MeasurementKey`. Be sure to set the `namespace` property to `org.radarcns.mydevicetype` so that generated classes will be put in the right package. All values should have `time` and `timeReceived` fields, with type `double`. These represent the time in seconds since the Unix Epoch (1 January 1970, 00:00:00 UTC). Subsecond precision is possible by using floating point decimals.
- Create a new package `org.radarcns.mydevicetype`. In that package, create classes that:
  - implement `org.radarcns.android.DeviceManager` to connect to a device and collect its data.
  - implement `org.radarcns.android.DeviceState` to keep the current state of the device.
  - subclass `org.radarcns.android.DeviceService` to run the device manager in.
  - implement a singleton `org.radarcns.android.DeviceTopics` that contains all Kafka topics that the wearable will generate.
- Finally, modify `org.radarcns.android.empaticaE4.MainActivity` to run a service for showing the device data and for controlling the recording.

Make a pull request once the code is working.
