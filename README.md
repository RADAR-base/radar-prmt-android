# RADAR-AndroidApplication
Application to be run on a Raspberry Pi3 (Anroid 6) to interact with wearable devices. The app is cloned from the [Empatica sample app][2].


Currently only the Empatica E4 is supported. 
## Setup Empatica E4

- Clone / download this repository
- Make sure you have a valid API key. You can request one for your Empatica Connect account from the [Empatica Developer Area][1]
- Edit the apikey.xml file: enter your API key in the apikey xml element
- Open the sample project in Android Studio
- Move the apikey.xml file to res/values/apikey.xml
- Launch the application. Note that the application only runs on an ARM architecture

[1]: https://www.empatica.com/connect/developer.php
[2]: https://github.com/empatica/empalink-sample-project-android
