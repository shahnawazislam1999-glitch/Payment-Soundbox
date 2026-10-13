@echo off
rem Minimal Gradle wrapper launcher (downloads gradle-wrapper.jar if missing)
set "APP_HOME=%~dp0"
set "JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
if not exist "%JAR%" powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar' -OutFile '%JAR%'"
java -Dorg.gradle.appname=gradlew -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
