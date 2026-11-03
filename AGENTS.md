# How I work on Paragony

I'm building a personal Android app for receipts and expenses. I write in Polish, so please reply in Polish. I use English for code, identifiers, documentation and commit messages. I keep the Android interface in Polish and put its text in `strings.xml`.

I keep money in grosze and quantities in thousandths using `Long`. I put arithmetic in the Android-independent `money` package and test rounding and allocation there.

I treat a model reading as a suggestion. I validate its numbers in code and keep the printed values when they disagree. Please ask me before overwriting a correction marked `editedByUser`.

I use UUIDs and soft deletion through `deletedAt`. For every Room schema change I want an explicit migration and a migration test.

I use fictional receipts for public tests. I keep API keys, real photos and reading results out of Git. I build the app in Android Studio. If a change cannot be tested on a device, tell me which check I still need to run.
