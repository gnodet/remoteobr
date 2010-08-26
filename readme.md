![RemoteOBR]
===============

Description
-----------

[RemoteOBR] is a client/server implementation of the OSGi Bundle Repository.

Synopsis
--------

The OBR implementation from Felix, despite the recent enhancements, still suffer from a few problems:  
* given OBR repositories can be huge, the time to download and parse the repositories can be too long for a user
* the memory consumption for a large repository is huge and can take most of the available memory, making the runtime useless

Features
--------

* Single server to hold all the repositories
* Lazy-loading of resources

