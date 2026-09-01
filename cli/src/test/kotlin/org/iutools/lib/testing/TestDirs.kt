package org.iutools.lib.testing

import org.junit.jupiter.api.TestInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Port of ca.nrc.testing.TestDirs from
 * https://github.com/nrc-cnrc/java-utils (java-utils-core).
 *
 * Creates and manages a set of directories dedicated to one test method,
 * laid out under the build output tree as:
 *
 *   <build classes dir>/test-dirs/<test package>/<test class>/<test method>/
 *       inputs/               (wiped once at the start of each test run)
 *       outputs/              (wiped once at the start of each test run)
 *       persistent_resources/ (survives between runs)
 *
 * `persistent_resources` is where AssertRuntime keeps the timing baseline
 * for the current machine -- values that cannot live in the test source
 * because speed varies from machine to machine.
 *
 * Only works with JUnit 5 tests that receive a TestInfo parameter.
 *
 * NOT ported: the original's copyResourceFileToInputs() /
 * copyResourceDirToInputs() helpers. Nothing that reaches TestDirs in this
 * project uses them, and they pull in ca.nrc.file.ResourceGetter, which is
 * not part of this port.
 */
class TestDirs(private val testInfo: TestInfo) {

    fun baseDir(): Path {
        val method = testInfo.testMethod.get()
        val methName = method.name
        val className = method.declaringClass.name
        val classElts = className.split(".").toTypedArray()

        val allTestsDir = Paths.get(targetDir().toString(), "test-dirs")
        val testClassDir = Paths.get(allTestsDir.toString(), *classElts)
        if (!Files.exists(testClassDir)) {
            Files.createDirectories(testClassDir)
        }
        val testMethodDir = Paths.get(testClassDir.toString(), methName)
        if (!Files.exists(testMethodDir)) {
            Files.createDirectories(testMethodDir)
        }
        return testMethodDir
    }

    fun inputsDir(vararg relPath: String): Path {
        val inputs = Paths.get(baseDir().toString(), "inputs")
        ensureDirExists(inputs)
        ensureWasCleared(inputs)
        return ensureSubdir(inputs, relPath)
    }

    fun inputsFile(vararg relPath: String): Path =
        fileUnder(inputsDir(*relPath.copyOfRange(0, relPath.size - 1)), relPath)

    fun outputsDir(vararg relPath: String): Path {
        val outputs = Paths.get(baseDir().toString(), "outputs")
        ensureDirExists(outputs)
        ensureWasCleared(outputs)
        return ensureSubdir(outputs, relPath)
    }

    fun outputsFile(vararg relPath: String): Path =
        fileUnder(outputsDir(*relPath.copyOfRange(0, relPath.size - 1)), relPath)

    fun persistentResourcesDir(vararg relPath: String): Path {
        val resources = Paths.get(baseDir().toString(), "persistent_resources")
        ensureDirExists(resources)
        return ensureSubdir(resources, relPath)
    }

    fun persistentResourcesFile(vararg relPath: String): Path =
        fileUnder(persistentResourcesDir(*relPath.copyOfRange(0, relPath.size - 1)), relPath)

    private fun ensureSubdir(parent: Path, relPath: Array<out String>): Path {
        var sub = parent
        for (elt in relPath) {
            sub = Paths.get(sub.toString(), elt)
        }
        ensureDirExists(sub)
        return sub
    }

    private fun fileUnder(dir: Path, relPath: Array<out String>): Path =
        Paths.get(dir.toString(), relPath[relPath.size - 1])

    private fun ensureDirExists(dirPath: Path) {
        if (!dirPath.toFile().exists()) {
            Files.createDirectories(dirPath)
        }
    }

    /**
     * Wipe `dir` the first time it is requested in a test run, and only
     * then, so that repeated inputsDir()/outputsDir() calls within one test
     * don't destroy files the test itself just wrote. `clearedDirs` is
     * process-wide; the method is `@Synchronized` to match the original,
     * even though synchronizing on a fresh TestDirs instance per call does
     * not actually serialize access to that static set (faithful port of a
     * pre-existing quirk).
     */
    @Synchronized
    private fun ensureWasCleared(dir: Path) {
        if (!clearedDirs.contains(dir)) {
            dir.toFile().listFiles()?.forEach { it.delete() }
            clearedDirs.add(dir)
        }
    }

    private fun targetDir(): Path {
        val testClass = testInfo.testClass.get()
        val location = testClass.protectionDomain.codeSource.location
        // The original used getLocation().getPath(); toURI() resolves to the
        // same directory but does not break when the path contains spaces.
        val targetClassDir = Paths.get(location.toURI())
        return targetClassDir.parent
    }

    companion object {
        private val clearedDirs = mutableSetOf<Path>()
    }
}
