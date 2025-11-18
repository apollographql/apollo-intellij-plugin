# Next version (unreleased)

PUT_CHANGELOG_HERE

# Version 5.0.1

_2025-11-18_

This is a patch release that addresses a few issues and crashes.
Also, it improves support for Gradle-less code generation with Apollo Kotlin v5 by invoking the same version of the Apollo Compiler as the one used by the project.

- Invoke ApolloCompilerHelper dynamically with project's dependencies (#72)
- Run apollo-compiler in the background (#83)
- Use additional Gradle arguments when fetching models (#86)
- Don't rely on apollo-compiler when reading JSON fils for telemetry (#81)
- Add cache v0.3 support (#79)
- Cache viewer: look for dbs in `no_backup` (#92)
- Cache viewer: pull dbs from both databases and cache folders (#90)
- Default to LSP mode only once (#91)
- Tweak issue reporter (#77)
- Disable Kotlin inspections in non Kotlin files, because `resolve()` sometimes crashes in this case. (#78)
- Fix crash in LSP service (#93)
- Try to not crash when resolve throws (#73)
- Ignore some exception sometimes happening in inspections (#71)
- Do not crash when sourceLocation is null (#88)

# Version 5.0.0

_2025-09-01_

> [!NOTE]
> In previous versions, the plugin had a dependency on the [JetBrains GraphQL plugin](https://plugins.jetbrains.com/plugin/8097-graphql).
> Starting with v5. we have forked and integrated that plugin’s code to allow us to implement certain Apollo specific features (e.g. `@link` support).
>
> Since both plugins now handle `*.graphql` files, they can’t be used at the same time, and if you are upgrading the plugin from v4, the IDE will therefore ask you to choose which plugin to use.
>
> Note that at the moment, all features of the GraphQL plugin are still present in the Apollo one, and we aim to backport bug fixes and new features.

- Overhaul of the code generation mechanism (#62).<br>
  Code generation is now using the Apollo Compiler directly, instead of
  invoking the Gradle codegen task, when possible (projects using Apollo Kotlin v5+). This results in faster code generation
  and lower memory consumption. For projects using Apollo Kotlin < v5, the Gradle task is still invoked, but no longer with
  the`--continuous` flag, which avoids running a dedicated Gradle daemon (#36).
- Better `@link` support (#55)
- Add received date to the cache viewer (#56)
- Support descriptions on executable definitions (#53)
- Make the graphql folder stand out (#54)
- Various crash and bug fixes

#### Compatibility

This version supports projects using Apollo Kotlin v3.x, v4.x and v5.x.
