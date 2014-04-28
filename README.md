PAC Lab Shimmer Log
===================

An App for Monitoring Parkinson's Related Tremors
-------------------------------------------------

Building
--------
This is an android studio project
A file called local.properties must be added to the root directory
with a path to the android sdk like this:

Make sure to install [adb](http://developer.android.com/tools/help/adb.html)

```
sdk.dir=/Users/alexthornburg/adt/sdk
```
Now to compile and run

```
cd /your/project/directory
./gradlew build
(if app is already installed) adb -d uninstall io.pacmonitorandroid.app
adb -d install app/build/apk/app-debug-unaligned.apk

```

About
-----

This logs data to a google cloud datastore instance and the json endpoints can be found here:

[All data](https://cloudbackend-dot-handy-reference-545.appspot.com/api/patient/all.json)
[Last second or so](https://cloudbackend-dot-handy-reference-545.appspot.com/api/patient/last-second.json)
