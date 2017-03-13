# RADAR-pRMT

Application to be run on an Android 4.4 (or later) device with Bluetooth Low Energy (Bluetooth 4.0 or later), to interact with wearable devices. The app is cloned from the [Empatica sample app][2].

Currently the Empatica E4 and Pebble 2 are supported. Also note that the application only runs on an ARM architecture.

![Screenshot](/man/screen20161215_edited.png?raw=True "Screenshot 2016-12-15")

To clone this respository, use the command

```shell
git clone --recursive https://github.com/RADAR-CNS/RADAR-AndroidApplication.git
```

If the repository is already cloned, go to the source directory and run

```shell
git submodule update --init --recursive
```

## Setup Empatica E4

First, request an Empatica API key for your Empatica Connect account from their [Developer Area][1]. Also download the Empatica Android SDK there.

1. Copy the empalink-2.0.aar from the Empatica Android SDK package to the `empalink-2.0` directory.
2. Edit the `app/src/main/res/xml/remote_config_defaults_TEMPLATE.xml` file
	- Set your Empatica API key in the `empatica_api_key` xml element.
 	- Set the `kafka_rest_proxy_url` and the `schema_registry_url`. If the app should not upload any data, leave them blank.
	- Set the `device_group_id` string to a suitable user ID.
3. Rename the file to `remote_config_defaults.xml` to use the file with Firebase. For the full Firebase setup, see below.

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

## Setup Firebase Remote Configuration
Firebase can be used to remotely configure some device and system parameters, e.g. the E4 API key, kafka server address and upload rate. The default parameters are also stored locally in `app/src/main/res/xml/remote_config_defaults.xml`, which will be used if the remote parameters cannot be accessed.

1. [Install the Firebase SDK](https://firebase.google.com/docs/android/setup) in Android Studio.
2. Login to a Google account.
3. In the [Firebase console](https://console.firebase.google.com/), add the app (`org.radarcns.android.android`) to a new Firebase project.
4. Download the `google-services.json` from the Firebase console (under Project Settings) and move the file to the `app/` folder. 
5. [Optional] Set the parameter values on the server. The avaiable parameters can be found in `app/src/main/res/xml/remote_config_defaults_TEMPLATE.xml`.

[Full Firebase guide](https://firebase.google.com/docs/remote-config/use-config-android)

## Usage

To send some mock data to a Confluent Kafka set up on localhost, run `./gradlew :app:cleanTest :app:test`. If the Confluent Kafka setup is running elsewhere, edit `app/src/test/resources/org/radarcns/kafka/kafka.properties` accordingly.

## Contributing

To add additional device types to this application, make the following steps (see the `org.radarcns.pebble2` package as an example):

- Add the schemas of the data you intend to send to the [RADAR-CNS Schemas repository](https://github.com/RADAR-CNS/RADAR-Schemas). Your record keys should be `org.radarcns.key.MeasurementKey`. Be sure to set the `namespace` property to `org.radarcns.mydevicetype` so that generated classes will be put in the right package. All values should have `time` and `timeReceived` fields, with type `double`. These represent the time in seconds since the Unix Epoch (1 January 1970, 00:00:00 UTC). Subsecond precision is possible by using floating point decimals.
- Create a new package `org.radarcns.mydevicetype`. In that package, create classes that:
  - implement `org.radarcns.android.device.DeviceManager` to connect to a device and collect its data.
  - implement `org.radarcns.android.DeviceState` to keep the current state of the device.
  - subclass `org.radarcns.android.device.DeviceService` to run the device manager in.
  - subclass a singleton `org.radarcns.android.device.DeviceTopics` that contains all Kafka topics that the wearable will generate.
  - subclass a `org.radarcns.android.device.DeviceServiceProvider` that exposes the new service.
- Add a new service element to `AndroidManifest.xml`, referencing the newly created device service.
- Add the `DeviceServiceProvider` you just created to the `device_services_to_connect` property in `app/src/main/res/xml/remote_config_defaults.xml`.

Make a pull request once the code is working.
