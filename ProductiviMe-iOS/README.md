# ProductiviMe iOS

Native SwiftUI app for planning activities, generating daily schedules, tracking completion, and setting productivity goals with optional incentives.

## Open the app

Open `ProductiviMe.xcodeproj` in Xcode and select the `ProductiviMe` scheme. Choose an iOS simulator or configure your signing team for a physical device. The current project deployment target is iOS 26.5.

## Regression checks

From this directory, run:

```sh
bash Tests/run-tests.sh
```

See `Tests/README.md` for additional manual UI checks. App data is stored locally using UserDefaults.
