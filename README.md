# Paragony

I'm building an Android app to track my purchases from Polish receipts, store app screenshots and manual entries. The project currently includes receipt validation, expense groups, a local Room database and a Compose navigation shell. The standalone Python prototype can read receipt images with Gemini. Receipt capture in the Android app is still in development.

## Running the app

I open the folder containing `settings.gradle.kts` in Android Studio, sync Gradle and run the `app` configuration on an emulator or Android device. I keep the interface in Polish.

## Checks

| Command | What I use it for |
| --- | --- |
| `./gradlew test` | Run local JVM tests for receipt parsing, money calculations and group membership. |
| `./gradlew connectedAndroidTest` | Run database tests on a connected device or emulator. |
| `python tools/check_group_sql.py` | Compare the SQL group totals with an independent implementation. |
| `python -m unittest discover -s prototype/reading -v` | Test the receipt prototype offline with synthetic data. |

On Windows I use `gradlew.bat` in place of `./gradlew`.

## Project layout

- `app/src/main/java/pl/paragony/money/` handles prices, VAT and discount allocation using integers.
- `app/src/main/java/pl/paragony/reading/` parses receipt JSON and checks totals.
- `app/src/main/java/pl/paragony/data/` contains the Room entities, queries and migration definitions.
- `app/src/main/java/pl/paragony/groups/` implements expense group membership and item overrides.
- `testdata/reading/expected/` contains nine fictional receipts. I keep real photos, keys and reading results outside version control.

[Receipt prototype setup](prototype/reading/README.md) | [Design notes](docs/design.md)
