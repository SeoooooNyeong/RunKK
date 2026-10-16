# Android CI 이미지: JDK 21 + Android SDK (platform 37, build-tools 36). .gitlab-ci.yml의 android:ci-image 잡이 빌드·푸시한다.
# 수동 빌드:  docker build -t <registry>/s15-final/s15p31a207/android-sdk:37 ci/android
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
 && sdkmanager "platform-tools" "platforms;android-37" "build-tools;36.0.0"
