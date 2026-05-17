FROM mcr.microsoft.com/devcontainers/java:1-21-bullseye

# git/curl/wget/unzip はベースイメージ同梱済み → apt-get不要

# Gradle wrapper cache dir
RUN mkdir -p /root/.gradle && chmod -R 777 /root/.gradle

# Set JVM memory for Gradle
ENV GRADLE_OPTS="-Xmx4g -Xms1g -Dfile.encoding=UTF-8"
