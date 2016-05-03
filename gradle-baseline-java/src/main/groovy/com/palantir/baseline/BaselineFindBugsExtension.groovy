package com.palantir.baseline

import java.util.regex.Pattern

class BaselineFindBugsExtension {
    def List<Pattern> exclusions = [Pattern.compile("/generated/")]

    def exclude(Pattern exclude) {
        exclusions.add(exclude)
    }
}
