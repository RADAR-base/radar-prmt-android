# RADAR-pRMT

Application to be run on an Android 5 (or later) device. If Bluetooth devices are to be used, the Android device should support Bluetooth Low Energy (Bluetooth 4.0 or later).

![Screenshot](/man/screen20161215_edited.png?raw=True "Screenshot 2016-12-15")

To clone this respository, use the command

```shell
git clone https://github.com/RADAR-base/radar-prmt-android.git
```

## Configuration

Parameters are described in the README of [radar-commons-android](https://github.com/RADAR-base/radar-commons-android) and those from the plugins below. Modify `app/src/main/res/xml/remote_config_defaults.xml` to change their defaults.

### Plugins

This application depends on plugins to collect information. Project-supported plugins are listed in the `plugins` directory of [radar-commons-android](https://github.com/radar-base/radar-commons-android). A plugin can be added to this app in three steps

1. Add it as a dependency in `app/build.gradle`
2. Add the respective `SourceProvider` to the `plugins` value in `app/src/main/java/org/radarbase/passive/app/RadarServiceImpl.kt`
3. In the `plugins` variable in Firebase or `app/src/main/res/xml/remote_config_defaults.xml`, add the plugin name.

See the plugin documentation on what link to each plugin for its configuration options and data collection settings.

### Setup Firebase Remote Configuration

Firebase can be used to remotely configure some device and system parameters, e.g. the E4 API key, kafka server address and upload rate. The default parameters are also stored locally in `app/src/main/res/xml/remote_config_defaults.xml`, which will be used if the remote parameters cannot be accessed.

1. [Install the Firebase SDK](https://firebase.google.com/docs/android/setup) in Android Studio.
2. Login to a Google account.
3. In the [Firebase console](https://console.firebase.google.com/), add the app (`org.radarcns.android`) to a new Firebase project.
4. Download the `google-services.json` from the Firebase console (under Project Settings) and move the file to the `app/src/release/` folder for release config or `app/src/debug/` folder for debug configuration.
5. [Optional] Set the parameter values on the server. The avaiable parameters can be found in `app/src/main/res/xml/remote_config_defaults.xml`. 
Note - Set the `unsafe_kafka_connection` parameter to `true` if the server with kafka and schema-registry is using a self-signed certificate over SSL. If the certificate is issued by a valid CA then leave it to `false`. In production, do NOT set this value to `true`.

[Full Firebase guide](https://firebase.google.com/docs/remote-config/use-config-android)

### Build and Deploy RADAR-pRMT on target device 

1. Set up firebase remote configuration as mentioned above.
2. In project level **build.gradle** file add mavenLocal() in repositories.
3. Remove empatica plugin from app level **build.gradle** also remove empatica plugin in ``app/src/main/java/org/radarbase/passive/app/RadarServiceImpl.kt``. Process    of adding a plugin is mentioned above, reverse it to delete a plugin. 
4. Change the package name of google-services.json it should be same as local package name of the app.
5. Add the following property and value either in remote config or the xml values file ``oauth2_client_secret=saturday$SHARE$scale``.
6. Ask maintainers to add you to test management portal, where you can generate QR code.

## Android Installation

The guide for installing Android on Raspberry Pi3 and UDOO boards is available [here](https://github.com/RADAR-base/RADAR-AndroidApplication/wiki)
