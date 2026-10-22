![App Icon](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)
# BetterUntis (fork)

An alternative mobile client for the Untis timetable system.

This is a fork of [SapuSeven/BetterUntis](https://github.com/SapuSeven/BetterUntis) that adds
message attachments and drops every dependency on Google services. It is distributed as an APK
through the [releases](../../releases) of this repository.

## Differences from upstream

### Message attachments

The Info Center used to show *that* a message has attachments, but not the attachments themselves.

- All attachments of a message are listed in the message detail and in the attachment dialog,
  and can be downloaded and opened with the app of your choice (PDF, images, office documents, …).
- Image attachments are shown inline below the message content. Tapping one opens a full screen
  viewer with pinch and double tap zoom.
- Every download state is visible: a placeholder while it runs, and an error block with the
  reason and a retry action when it failed.
- Attachments are cached on disk (bounded to 64 MB, oldest files are evicted first) and the signed
  download URL is reused for a few minutes, so reopening a message or switching between the Info
  Center tabs does not download anything twice. Interrupted downloads are written to a temporary
  file and renamed on completion, so a truncated file can never be mistaken for a cached one.

### Direct access to messages

The home screen has a bottom navigation bar with the four main sections: timetable, messages,
free rooms and settings. Messages are one tap away instead of *menu → Info Center → Messages*.
Inside the Info Center, the back arrow closes an open message first and only leaves the screen on
a second back press.

### No Google services

The `gms` product flavor and the `google-services` Gradle plugin have been removed, only the FOSS
flavor is built:

- no Firebase, no Google Play Services, no Google hosted push notifications,
- no `google-services.json`, no GMS dependency at all,
- smaller APK, works on de-Googled devices.

Crash reporting through Sentry is not wired up in this fork either, nothing is sent anywhere.

### Fork identity

- The application id is derived from the repository owner by the release workflow instead of
  hardcoding the upstream id:
  `com.github.<owner>.betteruntis` (this fork: `com.github.saimonn.betteruntis`, debug builds
  add a `.debug` suffix). The source packages stay `com.sapuseven.untis`, they are not part of
  the app identity.
- Releases are signed with the key of this fork, so they install next to the official app
  (F-Droid, Play Store) instead of replacing it. The two installations do not share any data.
- The about screen (repository, releases, update check, contributors) and the application id are
  injected from the GitHub repository at build time, see `.github/workflows/deploy.yml`.

## Install

### Obtainium

1. Install [Obtainium](https://github.com/ImranR98/Obtainium).
2. Add `https://github.com/saimonn/BetterUntis` as an app source.
3. Let Obtainium install the latest release and keep it up to date.

### Manually

Download the `BetterUntis-<version>.apk` file from the [latest release](../../releases/latest).

Installing over a build that uses the upstream package name (`com.sapuseven.untis`) requires
uninstalling it first: Android refuses updates that are signed with a different key, and this fork
now uses its own package name anyway. Uninstalling removes the stored accounts, settings and
bookmarks of the old installation.

## Top features

- Modern and intuitive user interface
- Simple and straight-forward login process
- Fast timetable loading with smart caching
- Multiple separate accounts/profiles
- RoomFinder to quickly find free rooms
- Info Center for messages (with attachments), events, contact hours and own absences
- Class management features for teachers (absence checking, lesson topic editing, …)
- Break notifications and auto-mute during lessons

## Build

Requirements: JDK 17 and an Android SDK with platform 35 (`compileSdk 35`, `minSdk 21`).

```bash
./gradlew assembleRelease
```

The build reads a few optional values from the environment (or the Gradle property of the same
name), which is how the release workflow configures the app:

| Variable            | Default                  | Meaning                                                     |
|---------------------|--------------------------|-------------------------------------------------------------|
| `VERSION_NAME`      | `5.0.0-beta04`           | `versionName` shown to the user, set from the release tag    |
| `APPLICATION_ID`    | `com.sapuseven.untis`    | package name the APK is installed under                      |
| `GITHUB_REPOSITORY` | `SapuSeven/BetterUntis`  | repository used by the about screen and the update check     |

Release signing uses `app/BetterUntis.jks` together with `KEYSTORE_PASSWORD` and
`KEY_RELEASE_PASSWORD` (or the corresponding entries of `app/signing.properties`). Without a
keystore the release build is unsigned.

## Translations

Translations are maintained upstream on [Weblate](https://hosted.weblate.org/engage/betteruntis/).
This fork only adds a few English strings for its own features and does not ship its own
translation project.

## Upstream

- Repository: [SapuSeven/BetterUntis](https://github.com/SapuSeven/BetterUntis)
- F-Droid: [com.sapuseven.untis](https://f-droid.org/packages/com.sapuseven.untis)
- Google Play: [BetterUntis](https://play.google.com/store/apps/details?id=com.sapuseven.untis)
- Chat: [#github-betteruntis:sapuseven.com](https://matrix.to/#/#github-betteruntis:sapuseven.com)

If you want to support the original project, use the upstream links, not this fork.

## License

GNU General Public License v3, see [LICENSE](LICENSE).
