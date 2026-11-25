package aigest.rules

import aigest.config.Config.ResolvedConfig

import aigest.rules.DirMatcher.DirRule
import aigest.rules.ExtMatcher.ExtRule
import aigest.rules.IncludeOnlyMatcher.IncludeOnlyRule

import aigest.util.PathUtils

import groovy.transform.CompileStatic

@CompileStatic
final class RuleEngine {

    final Map<String, ExtRule> extRules
    final Map<String, DirRule> dirRules
    final IncludeOnlyRule includeOnly

    RuleEngine(
        Map<String, ExtRule> extRules,
        Map<String, DirRule> dirRules,
        IncludeOnlyRule includeOnly = IncludeOnlyRule.None.INSTANCE
    ) {
        this.extRules = Collections.unmodifiableMap(new LinkedHashMap<String, ExtRule>(extRules))
        this.dirRules = Collections.unmodifiableMap(new LinkedHashMap<String, DirRule>(dirRules))
        this.includeOnly = includeOnly ?: IncludeOnlyRule.None.INSTANCE
    }

    static RuleEngine fromConfig(ResolvedConfig config) {
        new RuleEngine(config.extRules, config.dirRules, config.includeOnly)
    }

    boolean hasIncludeOnly() {
        includeOnly.isActive()
    }

    boolean shouldSkipDirectory(String relativeDirPath) {
        PathUtils.normalizeRelPath(relativeDirPath).with {
            normDir ->
                includeOnly.isActive()
                    ? includeOnly.shouldSkipDirectory(normDir)
                    : DirMatcher.isDirectoryIgnored(dirRules, normDir)
        }
    }

    boolean shouldIgnoreFile(String relativeFilePath) {
        String normPath = PathUtils.normalizeRelPath(relativeFilePath)

        if (includeOnly.isActive()) {
            return !includeOnly.shouldIncludeFile(normPath)
        }

        int lastSlash = normPath.lastIndexOf('/')
        String fileName = lastSlash >= 0 ? normPath.substring(lastSlash + 1) : normPath
        String ext = PathUtils.extractExt(fileName)

        if (DirMatcher.shouldIgnore(dirRules, normPath, fileName, ext)) {
            return true
        }

        if (ExtMatcher.shouldIgnore(extRules, normPath, fileName, ext)) {
            return true
        }

        false
    }

}
