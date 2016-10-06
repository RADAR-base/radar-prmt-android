# RADAR-AndroidApplication
Application to be run on a Raspberry Pi3 (Anroid 6) to interact with wearable devices. The app is cloned from the [Empatica sample app][2].

Currently only the Empatica E4 is supported. 

## Setup Empatica E4

- Clone / download this repository.
- Clone / download [RADAR-Backend](https://github.com/RADAR-CNS/RADAR-Backend.git)
- Run `./gradlew jar` there, and copy the resulting `build/libs/radarbackend.jar` to the current repository's `app/libs`.
- Make sure you have a valid API key. You can request one for your Empatica Connect account from our [Developer Area][1].
- Edit the `apikey.xml` and enter your API key in the apikey xml element.
- Edit the `server.xml` and add the URLs of the Kafka REST Proxy and the Schema Registry.
- Move the `apikey.xml` and `server.xml` files to the `app/src/main/res/values/` directory.
- Open the sample project in Android Studio.
- Launch the application. Note that the application only runs on an ARM architecture

[1]: https://www.empatica.com/connect/developer.php
[2]: https://github.com/empatica/empalink-sample-project-android
