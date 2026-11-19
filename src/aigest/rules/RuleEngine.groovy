package aigest.rules

import aigest.config.Config.ResolvedConfig

import aigest.rules.DirMatcher.DirRule
import aigest.rules.ExtMatcher.ExtRule

import aigest.util.PathUtils

import groovy.transform.CompileStatic

@CompileStatic
final class RuleEngine {

    final Map<String, ExtRule> extRules
    final Map<String, DirRule> dirRules

    RuleEngine(Map<String, ExtRule> extRules, Map<String, DirRule> dirRules) {
        this.extRules = Collections.unmodifiableMap(new LinkedHashMap<String, ExtRule>(extRules))
        this.dirRules = Collections.unmodifiableMap(new LinkedHashMap<String, DirRule>(dirRules))
    }

    static RuleEngine fromConfig(ResolvedConfig config) {
        new RuleEngine(config.extRules, config.dirRules)
    }

    boolean shouldSkipDirectory(String relativeDirPath) {
        DirMatcher.isDirectoryIgnored(
            dirRules,
            PathUtils.normalizeRelPath(relativeDirPath))
    }

    boolean shouldIgnoreFile(String relativeFilePath) {
        String normPath = PathUtils.normalizeRelPath(relativeFilePath)
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