youtrack-plugin
===============

# License #
youtrack-plugin is released under the MIT License. See the bundled LICENSE file for details.

# About #
This is a plugin for Jenkins_ that aims for providing support for [YouTrack](http://www.jetbrains.com/youtrack) inside [Jenkins](http://jenkins-ci.org).

# Requirements #

This plugin requires the [jquery plugin](https://wiki.jenkins-ci.org/display/JENKINS/jQuery+Plugin) to be installed

# Usage #

YouTrack sites can be set up under global configuration. For each job a site can be selected, and specific options can be
set.

There is also a build step to update the a YouTrack Build bundle with the build.

## Pipeline support ##

YouTrack Plugin provides a set of instructions to manage issues from pipeline.

To enable YouTrack Pipeline services you have to active YouTrack into project settings
and provide a YouTrack Project Code.

### Create issues

To create a new issue from pipeline you should use `ytCreateIssue()` command.

You can customize issue usign this properties:

* project: youtrack project code where add the new issue (by default the same of jenkins project)
* summary: issue title
* description: issue description
* command: command to execute when issue is created
* attachBuildlog: boolean, attach or not buildlog (true by default)