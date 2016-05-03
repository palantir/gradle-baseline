package com.palantir.baseline

class BaselineFindBugsExtension {
    def List<String> exclusions = ["/generated/"]

    def exclude(String exclude) {
        exclusions.add(exclude)
    }
}
