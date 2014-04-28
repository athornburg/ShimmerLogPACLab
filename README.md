PAC Lab Shimmer Log
===================

An App for Monitoring Parkinson's Related Tremors
-------------------------------------------------

Building
--------
This is an android studio project
A file called local.properties must be added to the root directory
with a path to the android sdk like this:

'''
sdk.dir=/Users/alexthornburg/adt/sdk
'''

This logs data to a google cloud datastore instance and the json endpoints can be found here:

https://cloudbackend-dot-handy-reference-545.appspot.com/api/patient/all.json
https://cloudbackend-dot-handy-reference-545.appspot.com/api/patient/last-second.json
