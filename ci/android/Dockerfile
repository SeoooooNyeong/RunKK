# Android CI 이미지: JDK 21 + Android SDK (platform 37, build-tools 36.0.0).
# 팀 PC와 CI의 도구 버전을 하나로 맞추고 매 잡마다 SDK를 내려받는 시간을 없애기 위해 직접 만든다.
# .gitlab-ci.yml의 android:ci-image 잡이 빌드해 프로젝트 레지스트리에 버전 태그(ANDROID_CI_IMAGE)로 올린다.
# 아래 버전 중 하나라도 바뀌면 태그를 올리고 이미지를 다시 빌드한다.
#
#   JDK 21          AGP 9.4(최소 JDK 17)와 Gradle 9.8이 돌고, Robolectric이 SDK 36 테스트에 JDK 21을 요구한다.
#   platform 37     android/app/build.gradle.kts compileSdk 37 (targetSdk 36). 37.0은 아직 미리보기 채널(--channel=3).
#   build-tools 36  AGP 9.x 기본값.
#
# 수동 빌드:  docker build -t <registry>/s15-final/s15p31a207/android-sdk:jdk21-sdk37-bt36.0.0 ci/android
FROM eclipse-temurin:21-jdk

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# developer.android.com/studio "Command line tools only" (2026-10 기준)
ARG CMDLINE_TOOLS=commandlinetools-linux-15859902_latest.zip
ARG CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583

RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip curl git \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p $ANDROID_HOME/cmdline-tools \
 && cd $ANDROID_HOME/cmdline-tools \
 && curl -fsSLo tools.zip https://dl.google.com/android/repository/$CMDLINE_TOOLS \
 && echo "$CMDLINE_TOOLS_SHA256  tools.zip" | sha256sum -c - \
 && unzip -q tools.zip && rm tools.zip && mv cmdline-tools latest \
 && yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platform-tools" "build-tools;36.0.0" \
 && sdkmanager --channel=3 "platforms;android-37.0"
