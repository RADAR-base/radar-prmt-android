# OAuth 2.0 authentication for RADAR-pRMT

Plugin that contains a `LoginManager` that can handle OAuth 2.0 authentication using the Authorization Code Grant flow. See the source in `src/main/java/org/radarbase/android/auth/oauth2/OAuth2LoginManager` for more information.

## Installation

To add the plugin code to your app, add the following snippet to your app's `build.gradle` file.

```gradle
dependencies {
    implementation "org.radarbase:radar-android-login-oauth2:$radarCommonsAndroidVersion"
}
```

## Configuration

The following Firebase parameters are available:

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `oauth2_authorize_url` | string | `<none>` | OAuth 2.0 authorization code URL |
| `oauth2_token_url` | string | `<none>` | OAuth 2.0 token URL |
| `oauth2_redirect_url` | string | `<none>` | URL to refer back to. This must be a URL registered to be used by your app. |
| `oauth2_client_id` | string | `<none>` | Client ID for the OAuth 2.0 server. |

In addition to these requirements, add a setting to `build.gradle`:
```gradle
android.defaultConfig.manifestPlaceholders = [
  'appAuthRedirectScheme': 'com.example.app'
]
``` 
This redirect scheme must be unique to your app, and match the prefix of the `oauth2_redirect_url` setting. For the above example, `oauth2_redirect_url` could be `com.example.app://oauth2/callback`.
