[![obsolete JetBrains project](http://jb.gg/badges/obsolete.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

IntelliJ ClearCase Integration
==

The plugin is [officially not supported](https://blog.jetbrains.com/idea/2017/10/end-of-support-for-visual-sourcesafe-and-clearcase/).
The code and the plugin binary is provided as is.
The binary can be downloaded [at the Marketplace](https://plugins.jetbrains.com/plugin/10095-clearcase-integration).

## Description

The plugin provides IntelliJ integration with IBM Rational ClearCase.

The following features are available:
* Dedicated page under the Version Control node in the Settings/Preferences dialog.</li>
* When ClearCase is enabled, the ClearCase node appears on the VCS menu, and on the context menu of the editor.


## To build and run the plugin

1. Clone the project and open in IDEA
2. Configure IntelliJ Platform Plugin SDK called **IntelliJ IDEA SDK** pointing to the existing IDEA installation using Project Settings
3. Run using provided **Plugin** run configuration
4. After applying hte needed changes use *Build - Prepare Plugin Module for deployment* to generate the jar
5. Load the jar using *Settings/Preferences - Plugins*
