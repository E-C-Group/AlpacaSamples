# Stand-Alone Alpaca Application

This is an example stand alone application built using Alpaca.

## Requirements
- [Java JDK 1.8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
- [Gradle](https://gradle.org)


## Usage
- Edit the `build.gradle` file 
    - Modify the `alpacaLocation` variable to point to a local Alpaca install.
    - Modify the `mainClassName` variable to point to the entry point for the application.
- Build the application from the command line using `gradle distTar`.
    - More build options can be found at the Gradle [application plugin page](https://docs.gradle.org/current/userguide/application_plugin.html).
- The built application can be found under `build/distributions`.