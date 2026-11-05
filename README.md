# LiveCallCaptions (Android beta)

A trial Android app with a floating caption button, microphone speech-to-text and English ↔ Persian on-device translation. Source speech capture during normal cellular calls is restricted by Android; this app cannot promise access to either side of a SIM voice call. Start captions from the app before opening the dialer.

See `.github/workflows/build-apk.yml` after the source files are uploaded. Download the debug APK from the GitHub Actions run's **Artifacts** section.

## Privacy
Transcripts are displayed locally, not stored or uploaded by this app. ML Kit may initially download language models. Android's installed speech recognition service may process audio according to the provider's terms.
