# Next version (unreleased)

PUT_CHANGELOG_HERE

# Version 5.0.0

_2025-09-01_

> [!NOTE]
> Starting with this release, the plugin no longer depends on the [JetBrains GraphQL plugin](https://plugins.jetbrains.com/plugin/8097-graphql).
> Instead, it includes a fork of that plugin's code which has been adapted to work better with Apollo Kotlin (#43).
>
> If you are upgrading the plugin from 4.x, the IDE will ask you to disable or uninstall the JetBrains GraphQL plugin,
> as both plugins cannot be used at the same time.

- Overhaul of the code generation mechanism (#62).<br>
  Code generation is now using the Apollo Compiler directly, instead of
  invoking the Gradle codegen task, when possible (projects using Apollo Kotlin v5+). This results in faster code generation
  and lower memory consumption. For projects using Apollo Kotlin < v5, the Gradle task is still invoked, but no longer with
  the`--continuous` flag, which causes a dedicated Gradle daemon to be busy at all times (#36).
- Better `@link` support (#55)
- Add received date to the cache viewer (#56)
- Support descriptions on executable definitions (#53)
- Make the graphql folder stand out (#54)
- Various crash and bug fixes

#### Compatibility

This version supports projects using Apollo Kotlin v3.x, v4.x and v5.x.
