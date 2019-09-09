package com.palantir.baseline.util;

import java.io.File;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.util.GFileUtils;

public final class GitUtils {
    private static final Pattern GIT_ORIGIN = Pattern.compile("url = git@([^:]+):([^.]+).git");


    public static Optional<String> maybeGitHubUri() {
        try {
            String gitConfigContents = GFileUtils.readFile(new File(".git/config'"));
            Matcher matcher = GIT_ORIGIN.matcher(gitConfigContents);
            if (!matcher.find()) {
                return Optional.of(String.format("https://%s/%s", matcher.group(1), matcher.group(2)));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private GitUtils() { }
}
