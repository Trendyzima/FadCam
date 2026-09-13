# `>_` FadCam

**Privacy-first Android camera and multimedia suite.**

FadCam is built for powerful camera, recording, and media-creation workflows without advertising or unnecessary clutter.

---

## 📦 Latest Debug APK

The latest production-room debug APK is published by the automated build pipeline.

**Artifact:** `FadCam-default-debug-apk`

To install the newest development build, open the repository's **Actions → Production Room Build → Artifacts** and download the latest successful APK.

> Debug builds are development builds. Use them only on devices where you understand the risks of test software.

---

## ✨ Features

- 🎥 Background video recording
- 🚗 Dashcam-style recording
- 📱 Screen recording
- 📡 Live streaming capabilities
- 🎛️ Remote camera control
- 🎬 Video playback and media handling
- 🎙️ Camera/video commentary workflows
- 🖼️ Video reaction and picture-in-picture workflows
- 🔊 Mixed camera and source-video audio
- 🔒 Privacy-focused, ad-free design

---

## 🎬 Reaction / Commentary PIP

FadCam includes an evolving creator workflow for video reactions and commentary.

The workflow is designed to let you:

1. Select a source video.
2. Keep the live camera as the main view.
3. Play the source video as a movable and resizable picture-in-picture layer.
4. Talk through the video using the device microphone.
5. Adjust source-video and microphone audio levels.
6. Pause and resume the recording workflow.
7. Finish automatically when the source video ends.
8. Export a final composition containing the camera view, source video, and mixed audio.

This is intended for reactions, commentary, demonstrations, tutorials, and other creator workflows.

---

## 🏗️ Technology

FadCam combines Android camera, media, storage, and recording technologies.

- **CameraX** — camera capture and preview
- **Media3 / ExoPlayer** — media playback
- **Media3 Muxer** — media output and container handling
- **FFmpeg** — video composition and audio mixing
- **Room** — local application data
- **OpenCV** — computer-vision processing where required
- **Android MediaStore** — exported media storage

The recording pipeline uses a patched Media3 build for functionality required by FadCam.

---

## 🧪 Build From Source

### Requirements

- Android Studio
- JDK 17
- Android SDK API 36
- The Gradle wrapper included in this repository

### Debug build

```bash
./gradlew :app:assembleDefaultDebug
```

APK output is generated under:

```text
app/build/outputs/apk/
```

The exact output directory depends on the selected build variant.

---

## 🔧 Engineering Principles

FadCam development follows a surgical repair approach:

- Preserve working architecture.
- Change the smallest necessary surface.
- Avoid destructive rewrites when repairing existing functionality.
- Keep recording and media pipelines deterministic.
- Validate builds after source changes.
- Treat media-resource cleanup and lifecycle handling as first-class concerns.
- Keep generated output separate from source code.

---

## 🛡️ Privacy

FadCam is designed around local-first media handling and a privacy-focused user experience.

The application should request only the Android permissions required for the features being used. Camera, microphone, storage, screen capture, and network functionality should remain explicit and feature-driven.

Always review permissions on your device before installing a development build.

---

## 📁 Project Structure

```text
app/
├── src/
│   ├── main/
│   │   ├── java/        Application source
│   │   ├── res/         Android resources
│   │   └── assets/      Bundled assets
│   └── default/         Default build-variant configuration
├── build.gradle.kts
└── ...

.github/
└── workflows/           Automated build workflows
```

---

## 🚧 Development Status

FadCam is under active development.

Current engineering work focuses on recording reliability, media composition, creator workflows, lifecycle safety, and automated APK builds.

Features and development builds may change between commits.

---

## 🤝 Contributing

Contributions should prioritize:

- Small, reviewable changes
- Reproducible fixes
- Build-safe implementations
- Clear commit messages
- Regression prevention
- Privacy and security
- Reliable media handling

When repairing an existing feature, preserve unrelated functionality and avoid replacing working systems unnecessarily.

---

## 📜 License

See the license files included in the repository for the applicable licensing terms.

---

<div align="center">

**FadCam — record, create, and control your media with privacy in mind.**

</div>
