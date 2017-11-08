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

## Building

Copy the `src/main/res/xml/remote_config_defaults_TEMPLATE.xml` from the [RADAR-Commons repository](https://github.com/RADAR/RADAR-Commons.git) to `app/src/main/res/xml/remote_config_defaults.xml`. These are the configuration defaults for the app.

- Set the `kafka_rest_proxy_url` and the `schema_registry_url`. If the app should not upload any data, leave them blank.
- Set the `device_group_id` string to a suitable user ID.

### Plugins

This application depends on plugins to collect information. The application currently supports plugins the following plugins:

- [Application status](https://github.com/RADAR-CNS/RADAR-Android-Application-Status.git)
- [Android Phone telemetry](https://github.com/RADAR-CNS/RADAR-Android-Phone.git)
- [Audio](https://github.com/RADAR-CNS/RADAR-Android-Audio.git)
- [Empatica E4](https://github.com/RADAR-CNS/RADAR-Android-Empatica.git)
- [Pebble](https://github.com/RADAR-CNS/RADAR-Android-Pebble.git)
- [Biovotion](https://github.com/RADAR-CNS/RADAR-Android-Biovotion.git)

See the link to each plugin for its installation instructions. In general, a dependency needs to be added in build.gradle, and a service needs to be aded in the `device_services_to_connect` property in `app/src/main/res/xml/remote_config_defaults.xml`.

### Setup Firebase Remote Configuration

Firebase can be used to remotely configure some device and system parameters, e.g. the E4 API key, kafka server address and upload rate. The default parameters are also stored locally in `app/src/main/res/xml/remote_config_defaults.xml`, which will be used if the remote parameters cannot be accessed.

1. [Install the Firebase SDK](https://firebase.google.com/docs/android/setup) in Android Studio.
2. Login to a Google account.
3. In the [Firebase console](https://console.firebase.google.com/), add the app (`org.radarcns.android`) to a new Firebase project.
4. Download the `google-services.json` from the Firebase console (under Project Settings) and move the file to the `common-android/` folder. 
5. [Optional] Set the parameter values on the server. The avaiable parameters can be found in `app/src/main/res/xml/remote_config_defaults_TEMPLATE.xml`. 
Note - Set the `unsafe_kafka_connection` parameter to `true` if the server with kafka and schema-registry is using a self-signed certificate over SSL. If the certificate is issued by a valid CA then leave it to `false`. In production, do NOT set this value to `true`.

[Full Firebase guide](https://firebase.google.com/docs/remote-config/use-config-android)

## Contributing

To add additional plugins to this application, make the following steps (see the [Pebble plugin](https://github.com/RADAR/RADAR-Android-Pebble.git) as an example):

- Add the schemas of the data you intend to send to the [RADAR-CNS Schemas repository](https://github.com/RADAR-CNS/RADAR-Schemas). Your record keys should be `org.radarcns.key.MeasurementKey`. Be sure to set the `namespace` property to `org.radarcns.mydevicetype` so that generated classes will be put in the right package. All values should have `time` and `timeReceived` fields, with type `double`. These represent the time in seconds since the Unix Epoch (1 January 1970, 00:00:00 UTC). Subsecond precision is possible by using floating point decimals.
- Create a new package `org.radarcns.mydevicetype`. In that package, create classes that:
  - implement `org.radarcns.android.device.DeviceManager` to connect to a device and collect its data.
  - implement `org.radarcns.android.DeviceState` to keep the current state of the device.
  - subclass `org.radarcns.android.device.DeviceService` to run the device manager in.
  - subclass a singleton `org.radarcns.android.device.DeviceTopics` that contains all Kafka topics that the wearable will generate.
  - subclass a `org.radarcns.android.device.DeviceServiceProvider` that exposes the new service.
- Add a new service element to `AndroidManifest.xml`, referencing the newly created device service.
- Add the `DeviceServiceProvider` you just created to the `device_services_to_connect` property in `app/src/main/res/xml/remote_config_defaults.xml`.

This plugin can remain a separate github repository, but it should be published to Bintray for easy integration.

## Android Installation

The guide for installing Android on Raspberry Pi3 and UDOO boards is available [here](https://github.com/RADAR-CNS/RADAR-AndroidApplication/wiki)
