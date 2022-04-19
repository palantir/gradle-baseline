/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;    http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License.
 */

package com.palantir.baseline.plugins.javaversions;

import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;

final class AzulJdkDownloader {
    private static final Attribute<Boolean> EXTRACTED_ATTRIBUTE = Attribute.of("azul-jdk.extracted", Boolean.class);
    private static final String AZUL_JDK = "azul-jdk";

    private final Project rootProject;

    AzulJdkDownloader(Project rootProject) {
        this.rootProject = rootProject;

        if (rootProject != rootProject.getRootProject()) {
            throw new IllegalArgumentException("Must pass in the root project");
        }

        String jdkBaseUrl = property("base-url").orElse("https://cdn.azul.com/zulu/bin/");

        rootProject.getRepositories().ivy(ivy -> {
            ivy.setName(AZUL_JDK);
            ivy.setUrl(jdkBaseUrl);
            ivy.patternLayout(patternLayout -> patternLayout.artifact("[module].[ext]"));
            ivy.metadataSources(metadataSources -> metadataSources.artifact());
            ivy.content(repositoryContentDescriptor -> {
                repositoryContentDescriptor.includeGroup(AZUL_JDK);
            });
        });

        rootProject.getDependencies().getAttributesSchema().attribute(EXTRACTED_ATTRIBUTE);
        rootProject.getDependencies().getComponents().all(componentMetadataDetails -> {
            if (AZUL_JDK.equals(componentMetadataDetails.getId().getGroup())) {
                componentMetadataDetails.attributes(attributes -> attributes.attribute(EXTRACTED_ATTRIBUTE, false));
            }
        });
        rootProject.getDependencies().registerTransform(ExtractJdk.class, transform -> {
            transform.getFrom().attribute(EXTRACTED_ATTRIBUTE, false);
            transform.getTo().attribute(EXTRACTED_ATTRIBUTE, true);
        });

        rootProject
                .getRepositories()
                .matching(repo -> !repo.getName().equals(AZUL_JDK))
                .configureEach(artifactRepository -> {
                    artifactRepository.content(content -> content.excludeGroup(AZUL_JDK));
                });
    }

    public Path downloadJdkFor(JdkSpec jdkSpec) {
        Configuration configuration = rootProject
                .getConfigurations()
                .detachedConfiguration(rootProject
                        .getDependencies()
                        .create(String.format(
                                "%s:zulu%s-ca-jdk%s-%s_%s:@zip",
                                AZUL_JDK, jdkSpec.zuluVersion(), jdkSpec.javaVersion(), jdkSpec.os(), jdkSpec.arch())));
        configuration.attributes(attributes -> attributes.attribute(EXTRACTED_ATTRIBUTE, true));
        return Iterables.getOnlyElement(configuration.resolve()).toPath();
    }

    private Optional<String> property(String name) {
        return Optional.ofNullable((String) rootProject.findProperty("com.palantir.baseline-java-versions." + name));
    }
}
