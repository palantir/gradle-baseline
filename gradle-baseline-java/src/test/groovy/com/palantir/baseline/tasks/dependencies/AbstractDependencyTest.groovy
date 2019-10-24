/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.baseline.tasks.dependencies

import com.palantir.baseline.AbstractPluginTest
import nebula.test.multiproject.MultiProjectIntegrationInfo
import org.gradle.testkit.runner.BuildResult

import java.nio.file.Files

class AbstractDependencyTest extends AbstractPluginTest {

    BuildResult runTask(String task) {
        with(task, '--stacktrace', '--info').withDebug(true).build()
    }

    protected String getStandardBuildFile() {
        return cleanFileContents('''
        plugins {
            id 'java'
            id 'com.palantir.baseline-dependencies-v2'
        }
    ''')
    }

    protected static String cleanFileContents(String contents, boolean includeTrailingEol = false) {
        String result = contents.stripIndent().trim()
        if (includeTrailingEol) {
            result = result + "\n";
        }
        return result
    }

    /**
     * Helpful method to make calls without surrounding parenthesis.  Makes for more fluent reading tests
     * @param contents
     * @return
     */
    protected static String cleanFileContentsWithEol(String contents) {
        return cleanFileContents(contents, true)
    }

    /**
     * Sets up a simple project with a
     */
    protected void setupTransitiveJarDependencyProject() {
        buildFile << standardBuildFile
        buildFile << '''
        repositories {
            mavenCentral()
        }
        dependencies {
            compile 'com.fasterxml.jackson.datatype:jackson-datatype-guava:2.9.9' // pulls in guava transitively
        }
        '''

        file('src/main/java/pkg/Foo.java') << cleanFileContents('''
        package pkg;
        public class Foo {
            void foo() {
                com.google.common.collect.ImmutableList.of();
            }
        }
        ''')
    }

    /**
     * Sets up a multi-module project with 3 sub projects
     * 1) sub-project-no-deps - no dependencies at all
     * 2) sub-project-with-deps - depends on the first one (module dependency)
     * 3) sub-project-jar-deps - depends on a jar
     *
     * The root project then is setup to have transitive dependencies on sub-project-no-deps and the jar in sub-project-jar-deps.
     */
    protected Map<String, MultiProjectIntegrationInfo> setupMultiProject() {
        buildFile << standardBuildFile
        buildFile << '''
        allprojects {
            apply plugin: 'java'
            apply plugin: 'com.palantir.baseline-dependencies-v2'
        }
        repositories {
            maven {
              url "https://artifactory.palantir.build/artifactory/all-jar/"
            }
            mavenLocal()
        }
        dependencies {
            compile project(':sub-project-with-deps')
            compile project(':sub-project-jar-deps')

            testCompile 'com.fasterxml.jackson.datatype:jackson-datatype-guava:2.9.9' // pulls in guava transitively
            testCompile group: 'junit', name: 'junit', version: '4.12'
        }
        '''

        Map<String, MultiProjectIntegrationInfo> subProjects =
                multiProject.create(['sub-project-no-deps', 'sub-project-with-deps', 'sub-project-jar-deps'])

        //sub-project-no-deps has no dependencies
        def directory = subProjects['sub-project-no-deps'].directory
        File subProject1Class = createFile('src/main/java/com/p1/TestClassNoDeps.java', directory)
        subProject1Class << "package com.p1; public class TestClassNoDeps {}"

        //write a second class to be referenced in a different place
        subProject1Class = createFile('src/main/java/com/p1/TestClassNoDeps2.java', directory)
        subProject1Class << "package com.p1; public class TestClassNoDeps2 {}"

        //*********** setup sub-project-with-deps ***********
        //declare dependency between sub-projects
        subProjects['sub-project-with-deps'].buildGradle << cleanFileContents('''
            dependencies {
                compile project(':sub-project-no-deps')
            }
        ''')

        //write class in sub-project-with-deps that uses TestClassNoDeps
        File myClass2 = createFile('src/main/java/com/p2/TestClassWithDeps.java', subProjects['sub-project-with-deps'].directory)
        myClass2 << cleanFileContents('''
        package com.p2;
        import com.p1.TestClassNoDeps;
        public class TestClassWithDeps {
            void foo() {
                System.out.println (new TestClassNoDeps());
            }
        }
        ''')

        //*********** setup sub-project-jar-deps ***********
        subProjects['sub-project-jar-deps'].buildGradle << cleanFileContents('''
        repositories {
            maven {
              url "https://artifactory.palantir.build/artifactory/all-jar/"
            }
            mavenLocal()
        }
        dependencies {
            compile 'com.google.guava:guava:27.0.1-jre' 
        }
        ''')

        File myClass3 = createFile('src/main/java/com/p3/TestClassWithJarDep.java', subProjects['sub-project-jar-deps'].directory)
        myClass3 << cleanFileContents('''
        package com.p3;
        public class TestClassWithJarDep {
            void foo() {
                com.google.common.collect.ImmutableList.of();
            }
        }
        ''')

        //*********** setup main project ***********
        //Create source file in root project that uses TestClassNoDeps2
        File myRootClass = createFile('src/main/java/com/p0/RootTestClassWithDeps.java')
        Files.createDirectories(myRootClass.toPath().getParent())
        myRootClass << cleanFileContents('''
        package com.p0;
        import com.p1.TestClassNoDeps2;
        public class RootTestClassWithDeps {
            void foo() {
                System.out.println (new TestClassNoDeps2());
            }
        }
        ''')

        //create source file that uses guava
        createFile('src/main/java/com/p0/RootTestClassWithJarDep.java') << cleanFileContents('''
        package com.p0;
        import com.google.common.collect.ImmutableList;
        public class RootTestClassWithJarDep {
            public ImmutableList foo() {
                return ImmutableList.of();
            }
        }
        ''')

        //create test source file that uses guava
        createFile('src/test/java/com/p0/RootTestClassWithJarDepTest.java') << cleanFileContents('''
        package com.p0;
        
        import org.junit.Test;

        public class RootTestClassWithJarDepTest {
            @Test
            public void some_test() {
                com.google.common.collect.ImmutableList.of();
            }
        }
        ''')

        return subProjects
    }

}
