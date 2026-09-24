# AutoX.js v7
<p align="center"> 
  
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/aiselp/AutoX/total)
![GitHub Issues or Pull Requests](https://img.shields.io/github/issues/aiselp/AutoX)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/aiselp/AutoX/android-test.yml)
![GitHub Release](https://img.shields.io/github/v/release/aiselp/AutoX)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/ca72518c8bd548f9a350d5a15e2ed9ea)](https://app.codacy.com/gh/aiselp/AutoX/dashboard?utm_source=gh&utm_medium=referral&utm_content=&utm_campaign=Badge_grade)

</p>

[中文文档](README_zh.md)

## Introduction

A JavaScript runtime and development environment for Android built on top of the Accessibility Service. Its goal is to be something like JsBox or Workflow.

This project is derived from [hyb1996](https://github.com/hyb1996/Auto.js) Auto.js and is named AutoX.js (a modified version of Auto.js). What you are looking at is the project built on top of the original 4.1 release. Below we explain how the project itself is developed and run, and more developers are welcome to join in maintaining and upgrading it. [hyb1996](https://github.com/hyb1996/Auto.js) is licensed under the
[Mozilla Public License Version 2.0](https://github.com/hyb1996/NoRootScriptDroid/blob/master/LICENSE.md)
+**Non-Commercial Use**. For a number of reasons, this product is distributed under the [GPL-V2](https://opensource.org/licenses/GPL-2.0) license, so both contributors and users must comply with the requirements of MPL-2.0 + Non-Commercial Use and GPL-V2.

About the two licenses:

* GPL-V2 [https://opensource.org/licenses/GPL-2.0](https://opensource.org/license/gpl-2-0/)
* MPL-2 (https://www.mozilla.org/MPL/2.0)

### AutoX.js today:

* AutoX.js documentation: https://autox-doc.vercel.app/
* Source code: https://github.com/aiselp/AutoX/
* PC-side development: [VS Code extension](https://marketplace.visualstudio.com/items?itemName=aaroncheng.auto-js-vsce-fixed)
* Official forum: [www.autoxjs.com](http://www.autoxjs.com)
* AutoX.js [changelog](CHANGELOG.md)

### Download:
[releases](https://github.com/aiselp/AutoX/releases)
If the download is too slow, right-click and copy the link of the APK in Release Assets, then paste it into a GitHub mirror/accelerator such as [http://toolwa.com/github/](http://toolwa.com/github/).

#### APK variants:
Only the following two variants are currently provided:
- arm64-v8a: for 64-bit ARM devices (mainstream flagship phones)
- mini-arm64-v8a: with some non-essential resources removed; download them on demand while using the app

### Features

1. Simple, easy-to-use automation functions implemented through the Accessibility Service
2. Floating window for recording and running scripts
3. A more professional and powerful selector API for finding, traversing, inspecting and operating on-screen widgets, similar to Google's UiAutomator — you can also use it as a mobile UI testing framework
4. JavaScript as the scripting language, with code completion, variable renaming, code formatting, find & replace and more, so it can be used as a JavaScript IDE
5. Support for writing UI with E4X and packaging JavaScript into APK files, which lets you build small utility apps
6. Root support for more powerful screen tapping, swiping and recording, plus running shell commands. Recordings can produce JS or binary files, and playback of recorded actions is fairly smooth
7. Functions for capturing the screen, saving screenshots, color finding and image matching
8. Usable as a Tasker plug-in, so it can handle daily workflows together with Tasker
9. A built-in layout analysis tool, similar to Android Studio's Layout Inspector, for inspecting the UI hierarchy and bounds and reading widget information

Unlike macro recorders such as AnJian JingLing (Auto Clicker), the main differences are:

1. Auto.js focuses on automation and workflows, making everyday life and work easier — for example, muting notifications when a game starts, or starting a WeChat video call with a specific contact in one tap (a real problem discussed on Zhihu, where elderly people struggle with the complicated steps of a WeChat video call with their children)
2. Auto.js is more compatible. Coordinate-based macro tools easily run into resolution problems, while widget-based Auto.js does not
3. Auto.js does not need root for most tasks. Only functions that require precise coordinates for tapping and swiping need root
4. Auto.js can also be used for building UIs, so it is more than just a scripting tool

### What's new in v7 🎉

- [x] A brand new UI based on Material Design 3
- [x] Support for [Shizuku](https://shizuku.rikka.app/introduction/) and embedded scripts, so Shizuku-based APIs can be debugged dynamically without repeatedly building a debug APK
- [x] A new [Node.js engine](https://github.com/caoccao/Javet?tab=readme-ov-file) that supports a large number of npm packages and can interoperate with Java
- [x] Many modules migrated to TypeScript with type declarations, so scripts can be written in TS with more complete type hints
- [x] A brand new UI framework based on Vue 3 and Jetpack Compose, letting you write data-reactive Material Design 3 interfaces with Vue 3
- [ ] A new generation of Node.js-based APIs (the v7 API), providing many non-blocking modules (in progress)
- [x] Improved app packaging and signing management, with support for packaging Node.js engine scripts and configuring special permission requests
- [x] [Rhino](https://github.com/mozilla/rhino/) upgraded to the stable 1.8.0 release, supporting more ES6+ syntax

### Examples
Some examples can be found [here](https://github.com/aiselp/AutoX/tree/setup-v7/app/src/main/assets/sample), or viewed and run directly inside the app.

### Building
Requirements: Java 17

Commands are run in the project root directory. On Windows PowerShell below 7.0, use `;` to separate commands instead of `&&`.

**Since 7.0, you need to build the JS modules before building the app. Make sure Node.js 20+ is installed.**

```shell
./gradlew autojs:buildJsModule
```
This only needs to be run once; run it again after changing the module code to pick up the updates.

##### Building the documentation

```shell
./gradlew app:buildDocs
````
This only needs to be run once; run it again after changing the documentation to pick up the updates.

##### Install a debug build to a device locally:
```shell
./gradlew app:buildDebugTemplateApp && ./gradlew app:assembleV7Debug && ./gradlew app:installV7Debug
# or
./gradlew app:buildDebugTemplateApp ; ./gradlew app:assembleV7Debug ; ./gradlew app:installV7Debug
```
The generated debug APK is under `app/build/outputs/apk/v6/debug` and uses the default signature.

##### Build a release version locally:
```shell
./gradlew app:buildTemplateApp && ./gradlew app:assembleV7
# or
./gradlew app:buildTemplateApp ; ./gradlew app:assembleV7
```
The generated APK is unsigned and located under `app/build/outputs/apk/v6/release`; it must be signed before installation.

##### Run a debug build to a device from Android Studio:
First run:

```shell
./gradlew app:buildDebugTemplateApp
```

Then click the Run button in Android Studio.

##### Build, sign and release from Android Studio:
First run:

```shell
./gradlew app:buildTemplateApp
```

Then in Android Studio choose "Build" -> "Generate Signed Bundle / APK..." -> select "APK" -> "Next" -> choose or create a keystore -> "Next" -> select "v7Release" -> "Finish".
The generated APK is under `app/v7/release`.

### Testing
Some script functionality tests have been added to the `autojs` module. To run them:

1. Prepare an Android device and connect it to your computer with adb
2. Build the module once with the latest Android Studio: `./gradlew autojs:assemble`
3. Open a test class under `autojs/src/androidTest`
4. Click the Run button next to the class name to start the test
5. Depending on the device, you may need to allow the test APK installation on the phone
