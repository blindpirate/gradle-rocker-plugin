package nu.studer.gradle.rocker

import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

@Unroll
class RockerFuncTest extends BaseFuncTest {

    void setup() {
        def gradleBuildCacheDir = new File(testKitDir, "caches/build-cache-1")
        gradleBuildCacheDir.deleteDir()
        gradleBuildCacheDir.mkdir()
    }

    void "can invoke rocker task derived from all-default configuration DSL"() {
        given:
        template('src/rocker/foo/Example.rocker.html')

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  foo {
    // `optimize` defaults to false
    // `extendsClass` defaults to null, deferring to the Rocker default
    // `extendsModelClass` defaults to null, deferring to the Rocker default
    // `javaVersion` defaults to current JVM version
    // `targetCharset` defaults to UTF-8
    // `templateDir` defaults to <projectDir>/src/rocker/<configName>
    // `outputDir` defaults to <buildDir>/generated-src/rocker/<configName>
    // `classDir` defaults to <buildDir>/rocker-hot-reload/<configName>
  }
}
"""

        when:
        def result = runWithArguments('compileFooRocker')

        then:
        fileExists('build/generated-src/rocker/foo/Example.java')
        fileContent('build/generated-src/rocker/foo/Example.java').contains('getModifiedAt')
        result.output.contains("Parsing 1 rocker template files")
        result.output.contains("Generated 1 rocker java source files")
        result.output.contains("Generated rocker configuration ${workspaceDir.canonicalFile}/build/rocker-hot-reload/foo/rocker-compiler.conf")
        result.task(':compileFooRocker').outcome == TaskOutcome.SUCCESS
    }

    void "can invoke rocker task derived from all-default configuration DSL with configuration cache"() {
        given:
        gradleVersion = GradleVersion.version('6.5')
        template('src/rocker/foo/Example.rocker.html')

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  foo {
  }
}
"""

        when:
        def result = runWithArguments('compileFooRocker', '--configuration-cache=on')

        then:
        fileExists('build/generated-src/rocker/foo/Example.java')
        result.output.contains("Calculating task graph as no configuration cache is available for tasks: compileFooRocker")
        result.output.contains("Generated 1 rocker java source files")
        result.task(':compileFooRocker').outcome == TaskOutcome.SUCCESS

        when:
        result = runWithArguments('compileFooRocker', '--configuration-cache=on')

        then:
        fileExists('build/generated-src/rocker/foo/Example.java')
        result.output.contains("Reusing configuration cache.")
        result.output.contains("Generated 1 rocker java source files")
        result.task(':compileFooRocker').outcome == TaskOutcome.UP_TO_DATE
    }

    void "can invoke rocker task derived from single-item configuration DSL"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  foo {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        def result = runWithArguments('compileFooRocker')

        then:
        fileExists('src/generated/rocker/Example.java')
        result.output.contains("Parsing 1 rocker template files")
        result.output.contains("Generated 1 rocker java source files")
        result.output.contains("Optimize flag on. Did not generate rocker configuration file")
        result.task(':compileFooRocker').outcome == TaskOutcome.SUCCESS
    }

    void "can invoke rocker task derived from configuration DSL with multiple items"() {
        given:
        template('src/rocker/main/Example.rocker.html')
        template('src/rocker/test/ExampleTest.rocker.html')

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker/main')
    outputDir = file('src/generated/rocker/main')
  }
  integTest {
    optimize = true
    templateDir = file('src/rocker/test')
    outputDir = file('src/generated/rocker/test')
  }
}
"""

        when:
        def result = runWithArguments('compileIntegTestRocker')

        then:
        fileExists('src/generated/rocker/test/ExampleTest.java')
        result.task(':compileIntegTestRocker').outcome == TaskOutcome.SUCCESS

        !fileExists('src/generated/rocker/main/Example.java')
        !result.task(':compileRocker')
    }

    void "rocker task derived from 'main' configuration omits the configuration name in the RockerCompile task instances"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  main {
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        def result = runWithArguments('compileRocker')

        then:
        fileExists('src/generated/rocker/Example.java')
        result.output.contains("Parsing 1 rocker template files")
        result.output.contains("Generated 1 rocker java source files")
        result.task(':compileRocker').outcome == TaskOutcome.SUCCESS
    }

    void "can compile Java source files generated by rocker as part of invoking Java compile task with the matching source set"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
    id 'java'  // provides 'main' sourceSet
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        def result = runWithArguments('classes')

        then:
        fileExists('src/generated/rocker/Example.java')
        fileExists('build/classes/java/main/Example.class') || fileExists('build/classes/main/Example.class')
        result.task(':compileRocker').outcome == TaskOutcome.SUCCESS
        result.task(':classes').outcome == TaskOutcome.SUCCESS
    }

    void "can reconfigure the output dir"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
    id 'java'
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}

rocker.main.outputDir = file('src/generated/rocker/other')

afterEvaluate {
  SourceSetContainer sourceSets = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets()
  SourceSet sourceSet = sourceSets.findByName('main')
  Set<File> dirs = sourceSet.getJava().getSrcDirs()
  dirs.eachWithIndex { dir, index ->
    println "\$dir---"
  }
}
"""

        when:
        def result = runWithArguments('classes')

        then:
        fileExists('src/generated/rocker/other/Example.java')
        fileExists('build/classes/java/main/Example.class') || fileExists('build/classes/main/Example.class')
        result.output.contains('dir/src/main/java---')
        result.output.contains('dir/src/generated/rocker/other---')
        !result.output.contains('dir/src/generated/rocker---')
        result.task(':compileRocker').outcome == TaskOutcome.SUCCESS
        result.task(':classes').outcome == TaskOutcome.SUCCESS
    }

    void "can set custom rocker version"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
    id 'java'
}

repositories {
    jcenter()
}

rockerVersion = '0.15.0'

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        def result = runWithArguments('dependencies')

        then:
        result.output.contains('com.fizzed:rocker-compiler -> 0.15.0') || result.output.contains('com.fizzed:rocker-compiler: -> 0.15.0')
        result.output.contains('com.fizzed:rocker-runtime -> 0.15.0')  || result.output.contains('com.fizzed:rocker-runtime: -> 0.15.0')
    }

    void "can set custom target charset"() {
        given:
        template('src/rocker/Example.rocker.html', 'äÄüÜöÖ')

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  foo {
    targetCharset= 'ISO-8859-1'
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        def result = runWithArguments('compileFooRocker')

        then:
        fileContent('src/generated/rocker/Example.java').contains('äÄüÜöÖ')
        result.task(':compileFooRocker').outcome == TaskOutcome.SUCCESS
    }

    void "participates in incremental build"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
    id 'java'
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        def result = runWithArguments('compileRocker')

        then:
        fileExists('src/generated/rocker/Example.java')
        result.task(':compileRocker').outcome == TaskOutcome.SUCCESS

        when:
        result = runWithArguments('compileRocker')

        then:
        result.task(':compileRocker').outcome == TaskOutcome.UP_TO_DATE
    }

    void "detects when task is not uptodate anymore"() {
        given:
        template("${templateDirFirst}/Example1.rocker.html")
        template("${templateDirSecond}/Example2.rocker.html")

        when:
        rockerMainBuildFile(optimizeFirst, templateDirFirst, outputDirFirst)

        def result = runWithArguments('compileRocker')

        then:
        fileExists("${outputDirFirst}/Example1.java")
        result.task(':compileRocker').outcome == TaskOutcome.SUCCESS

        when:
        rockerMainBuildFile(optimizeSecond, templateDirSecond, outputDirSecond)

        result = runWithArguments('compileRocker')

        then:
        fileExists("${outputDirSecond}/Example2.java")
        result.task(':compileRocker').outcome == TaskOutcome.SUCCESS

        where:
        optimizeFirst | optimizeSecond | templateDirFirst | templateDirSecond | outputDirFirst          | outputDirSecond
        true          | false          | 'src/rocker'     | 'src/rocker'      | 'src/generated/rocker'  | 'src/generated/rocker'
        true          | true           | 'src/rocker1'    | 'src/rocker2'     | 'src/generated/rocker'  | 'src/generated/rocker'
        true          | true           | 'src/rocker'     | 'src/rocker'      | 'src/generated/rocker1' | 'src/generated/rocker2'
    }

    void "generates exactly the same output for the same input iff optimize=true (was #optimize)"() {
        given:
        template('src/rocker/main/Example.rocker.html')

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = $optimize
    templateDir = file('src/rocker/main')
    outputDir = file('src/generated/rocker/main')
  }
}
"""

        when:
        def resultFirstRun = runWithArguments('compileRocker')
        def contentFirstRun = fileContent('src/generated/rocker/main/Example.java')

        then:
        resultFirstRun.task(':compileRocker').outcome == TaskOutcome.SUCCESS
        contentFirstRun

        when:
        updateLastModified('src/rocker/main/Example.rocker.html')
        def resultSecondRun = runWithArguments('cleanCompileRocker', 'compileRocker')
        def contentSecondRun = fileContent('src/generated/rocker/main/Example.java')

        then:
        resultSecondRun.task(':compileRocker').outcome == TaskOutcome.SUCCESS
        (contentSecondRun == contentFirstRun) == optimize
        contentSecondRun.contains('getModifiedAt') == !optimize

        where:
        optimize << [true, false]
    }

    void "task output is cacheable if optimize flag is on"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  foo {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}

"""

        when:
        def result = runWithArguments('compileFooRocker', '--build-cache')

        then:
        result.task(':compileFooRocker').outcome == TaskOutcome.SUCCESS

        when:
        result = runWithArguments('cleanCompileFooRocker', 'compileFooRocker', '--build-cache')

        then:
        result.task(':compileFooRocker').outcome == TaskOutcome.FROM_CACHE
    }

    void "task output is not cacheable if optimize flag is off"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  foo {
    optimize = false
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}

"""

        when:
        def result = runWithArguments('compileFooRocker', '--build-cache')

        then:
        result.task(':compileFooRocker').outcome == TaskOutcome.SUCCESS

        when:
        result = runWithArguments('cleanCompileFooRocker', 'compileFooRocker', '--build-cache')

        then:
        result.task(':compileFooRocker').outcome == TaskOutcome.SUCCESS
    }

    void "does not clean sources generated by rocker as part of Gradle's clean life-cycle task"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
    id 'java'
}

repositories {
    jcenter()
}

rocker {
  main {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}
"""

        when:
        runWithArguments('compileRocker')

        then:
        fileExists('src/generated/rocker/Example.java')

        when:
        def result = runWithArguments('clean')

        then:
        fileExists('src/generated/rocker/Example.java')
        !result.task(':cleanCompileRocker')
    }

    void "can customize java execution and handle execution result"() {
        given:
        exampleTemplate()

        and:
        buildFile << """
plugins {
    id 'nu.studer.rocker'
}

repositories {
    jcenter()
}

rocker {
  foo {
    optimize = true
    templateDir = file('src/rocker')
    outputDir = file('src/generated/rocker')
  }
}

compileFooRocker {
  def out = new ByteArrayOutputStream()
  javaExecSpec = { JavaExecSpec s ->
    s.standardOutput = out
    s.errorOutput = out
    s.ignoreExitValue = true
  }
  execResultHandler = { ExecResult r ->
    if (r.exitValue == 0) {
      println('Rocker template compilation succeeded')
    }
  }
}
"""

        when:
        def result = runWithArguments('compileFooRocker')

        then:
        fileExists('src/generated/rocker/Example.java')
        result.output.contains("Rocker template compilation succeeded")
        result.task(':compileFooRocker').outcome == TaskOutcome.SUCCESS
    }

    def "only changed templates are regenerated when optimize=#optimize"() {
        given:
        exampleTemplate()
        def updatedTemplate = template('src/rocker/Updated.rocker.html')

        and:
        rockerMainBuildFile(optimize, 'src/rocker', 'src/generated/rocker')

        when:
        def result = runWithArguments('compileRocker')

        then:
        def exampleLastModified = lastModified('src/generated/rocker/Example.java')
        def updatedLastModified = lastModified('src/generated/rocker/Updated.java')
        result.output.contains('Generated 2 rocker java source files')

        when:
        updatedTemplate << "Some more content"

        and:
        sleep(1000) // lazy workaround for the fact that Java's last modified time accuracy is pretty poor
        result = runWithArguments('compileRocker')

        then:
        exampleLastModified == lastModified('src/generated/rocker/Example.java')
        updatedLastModified < lastModified('src/generated/rocker/Updated.java')
        result.output.contains('Generated 1 rocker java source files')

        where:
        optimize << [true, false]
    }

    def "removed templates are cleaned up when optimize=#optimize"() {
        given:
        exampleTemplate()
        def deletedTemplate = template('src/rocker/Deleted.rocker.html')

        and:
        rockerMainBuildFile(optimize, 'src/rocker', 'src/generated/rocker')

        when:
        def result = runWithArguments('compileRocker')

        then:
        fileExists('src/generated/rocker/Example.java')
        fileExists('src/generated/rocker/Deleted.java')
        result.output.contains('Generated 2 rocker java source files')

        when:
        deletedTemplate.delete()

        and:
        result = runWithArguments('compileRocker')

        then:
        fileExists('src/generated/rocker/Example.java')
        !fileExists('src/generated/rocker/Deleted.java')
        !(result.output =~ /Generated \d+ rocker java source files/)

        where:
        optimize << [true, false]
    }

    def "changed templates are regenerated and removed templates are cleaned up when optimize=#optimize"() {
        given:
        def updatedTemplate = template('src/rocker/Updated.rocker.html')
        def deletedTemplate = template('src/rocker/Deleted.rocker.html')

        and:
        rockerMainBuildFile(optimize, 'src/rocker', 'src/generated/rocker')

        when:
        def result = runWithArguments('compileRocker')

        then:
        fileExists('src/generated/rocker/Updated.java')
        fileExists('src/generated/rocker/Deleted.java')
        result.output.contains('Generated 2 rocker java source files')

        when:
        updatedTemplate << "Some more content"
        deletedTemplate.delete()

        and:
        result = runWithArguments('compileRocker')

        then:
        fileExists('src/generated/rocker/Updated.java')
        !fileExists('src/generated/rocker/Deleted.java')
        result.output =~ /Generated 1 rocker java source files/

        where:
        optimize << [true, false]
    }

    def "subsequent incremental builds produce correct output when optimize=#optimize"() {
        given:
        def updatedTemplate = template('src/rocker/Initial.rocker.html')

        and:
        rockerMainBuildFile(optimize, 'src/rocker', 'src/generated/rocker')

        when:
        def result = runWithArguments('compileRocker')

        then:
        fileExists('src/generated/rocker/Initial.java')
        result.output.contains('Generated 1 rocker java source files')

        when:
        def newTemplate = template('src/rocker/New.rocker.html')

        and:
        result = runWithArguments('compileRocker')

        then:
        fileExists('src/generated/rocker/Initial.java')
        fileExists('src/generated/rocker/New.java')
        result.output =~ /Generated 1 rocker java source files/

        when:
        newTemplate.delete()
        template('src/rocker/Renamed.rocker.html')

        and:
        result = runWithArguments('compileRocker')

        then:
        fileExists('src/generated/rocker/Initial.java')
        fileExists('src/generated/rocker/Renamed.java')
        !fileExists('src/generated/rocker/New.java')
        result.output =~ /Generated 1 rocker java source files/

        where:
        optimize << [true, false]
    }

    @SuppressWarnings("GroovyAccessibility")
    private Writer rockerMainBuildFile(boolean optimize, String templateDir, String outputDir, String rockerVersion = RockerVersion.DEFAULT) {
        buildFile.newWriter().withWriter { w ->
            w << """
plugins {
    id 'nu.studer.rocker'
    id 'java'
}

repositories {
    jcenter()
}

rockerVersion = '${rockerVersion}'

rocker {
  main {
    optimize = ${Boolean.toString(optimize)}
    templateDir = file('${templateDir}')
    outputDir = file('${outputDir}')
  }
}
"""
        }
    }

    private File exampleTemplate() {
        template('src/rocker/Example.rocker.html')
    }

    private File template(String fileName) {
        return template(fileName, '')
    }

    private File template(String fileName, String customText) {
        return file(fileName) << """
@args (String message)
Hello @message!$customText
"""
    }

    private boolean fileExists(String filePath) {
        def file = new File(workspaceDir, filePath)
        file.exists() && file.file
    }

    private String fileContent(String filePath) {
        assert fileExists(filePath)
        def file = new File(workspaceDir, filePath)
        file.text
    }

    private long lastModified(String filePath) {
        assert fileExists(filePath)
        def file = new File(workspaceDir, filePath)
        return Files.readAttributes(file.toPath(), BasicFileAttributes).lastModifiedTime().toMillis()
    }

    private String updateLastModified(String filePath) {
        assert fileExists(filePath)
        def file = new File(workspaceDir, filePath)
        file.setLastModified(System.currentTimeMillis() + 1000)
    }

}
