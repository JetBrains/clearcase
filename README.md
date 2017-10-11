[![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

IntelliJ ClearCase Integration
==

The plugin provides IntelliJ integration with IBM Rational ClearCase.

Supported TFS versions: up to TFS 2015.

The following features are available:
* Dedicated page under the Version Control node in the Settings/Preferences dialog.</li>
* When ClearCase is enabled, the ClearCase node appears on the VCS menu, and on the context menu of the editor.


###To build and run the plugin:

1. Clone the project and open in IDEA
2. Configure IntelliJ Platform Plugin SDK called **IntelliJ IDEA SDK** pointing to the existing IDEA installation using Project Settings
3. Run using provided **Plugin** run configuration
4. After applying hte needed changes use *Build - Prepare Plugin Module for deployment* to generate the jar
5. Load the jar using *Settings/Preferences - Plugins*