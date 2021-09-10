/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.baseline.services

import javax.annotation.Nullable
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion
import org.gradle.api.services.BuildServiceParameters
import spock.lang.Specification

class JarClassHasherTest extends Specification {

    def testJfr() {
       expect:
       JarClassHasher hasher = new JarClassHasher() {
           @Override
           public BuildServiceParameters.None getParameters() {
               return null;
           }
       };

       ResolvedArtifact artifact = getJfrArtifact();

       JarClassHasher.Result res = hasher.hashClasses(artifact);

       println(res.getHashesByClassName());
    }

    private ResolvedArtifact getJfrArtifact() {
        return new ResolvedArtifact() {
            @Override
            public File getFile() {
                return new File("/Users/fwindheuser/Downloads/jfr-logger-api-0.3.4.jar");
            }

            @Override
            public ResolvedModuleVersion getModuleVersion() {
                return new DefaultResolvedModuleVersion(new ModuleVersionIdentifier() {
                    // com.palantir.jfr.logger:jfr-logger-api:0.3.4
                    @Override
                    public String getVersion() {
                        return "0.3.4";
                    }

                    @Override
                    public String getGroup() {
                        return "com.palantir.jfr.logger";
                    }

                    @Override
                    public String getName() {
                        return "jfr-logger-api";
                    }

                    @Override
                    public ModuleIdentifier getModule() {
                        return DefaultModuleIdentifier.newId("com.palantir.jfr.logger", "jfr-logger-api");
                    }
                });
            }

            @Override
            public String getName() {
                return null;
            }

            @Override
            public String getType() {
                return null;
            }

            @Override
            public String getExtension() {
                return null;
            }

            @Nullable
            @Override
            public String getClassifier() {
                return null;
            }

            @Override
            public ComponentArtifactIdentifier getId() {
                return null;
            }
        };
    }
}
