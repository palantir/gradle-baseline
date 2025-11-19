# Test Migration Notes: BaselineTestingIntegrationTest

## Issues Discovered and Fixes

### Issue 1: Missing imports and incorrect class names
**Error**: Cannot find symbol for GradlePluginTests, RootProject, ProjectFile
**Investigation**: Looked at BaselineJavaVersionsIntegrationTest to see the correct import paths
**Fix**:
- `com.palantir.gradle.testing.junit.GradlePluginTests` (not `com.palantir.gradle.testing.GradlePluginTests`)
- `com.palantir.gradle.testing.project.RootProject` (not `com.palantir.gradle.testing.files.RootProject`)
- No `ProjectFile` class - need to use the return type from `buildGradle()`

### Issue 3: parentFile() method not found and dir() not available
**Error**: Cannot find symbol `parentFile()` on Plugins class, and `dir()` method not available
**Investigation**: Looking at examples, plugins can be chained with `.add().add()`. For directories, need to use different API patterns.
**Fix**: Chain plugin adds directly. For directory/file access, use `rootProject.file("path/to/file")` directly.

### Issue 4: Error Prone check preventing plugin strings in append()
**Error**: [GradleTestPluginsBlock] Plugins must be added using .plugins().add() method
**Investigation**: The framework has an Error Prone check that prevents `apply plugin` syntax in append() calls
**Fix**: Always use `.plugins().add()` for plugins, never put plugin declarations in append() strings.

### Issue 5: writeBinary() method not available
**Error**: Cannot find symbol `writeBinary()` in ArbitraryFile
**Investigation**: Need to write binary data to a file but no writeBinary method
**Fix**: Use `java.nio.file.Files.write(rootProject.file("path").path(), bytes)` to write binary data.

### Issue 2: executed() method not found
**Error**: Cannot find symbol `executed()` method in TaskResultAssert
**Investigation**: Checked TaskResultAssert and TaskOutcome enum. The available methods are `succeeded()`, `failed()`, `upToDate()`, `notOnTaskGraph()`.
**Fix**: When checking if a task was executed (not skipped/cached/up-to-date), use `.succeeded()` which checks for SUCCESS outcome. The original test's `wasExecuted` means the task actually ran, which corresponds to `succeeded()` in the new framework.
