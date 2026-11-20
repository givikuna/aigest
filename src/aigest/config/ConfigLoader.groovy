package aigest.config

import aigest.rules.DirMatcher.DirAllow
import aigest.rules.DirMatcher.DirIgnore
import aigest.rules.DirMatcher.DirRule

import aigest.rules.ExtMatcher.ExtAllow
import aigest.rules.ExtMatcher.ExtIgnore
import aigest.rules.ExtMatcher.ExtRule

import aigest.rules.RegexSet

import aigest.util.PathUtils
import aigest.util.Result

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
final class ConfigLoader {

    private ConfigLoader() { }

    static Result<Config.ConfigFile, String> loadRepo(Path, repoRoot) {
        load(repoRoot.resolve('aigest.json'))
    }

    static Result<Config.ConfigFile, String> load(Path, file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Result.<Config.ConfigFile, String>ok(Config.ConfigFile.EMPTY)
        }

        return Result.<Config.ConfigFile>trying("parsing ${file}") {
            Object parsed = new JsonSlurper().parse(file.toFile())

            if (!(parsed instanceof Map)) {
                return Config.ConfigFile.EMPTY
            }

            return fromMap((Map) parsed)
        }
    }

    private static Config.ConfigFile fromMap(Map root) {
        boolean overrideGlobal = truthy(root.get('override'))

        Map<String, ExtRule> extRules = parseExtRules(root.get('ext'))
        Map<String, DirRule> dirRules = parseDirRules(root.get('dir'))
        Map<String, Config.ProfileSpec> profiles =
                                    parseProfiles(root.get('profiles'))

        new Config.ConfigFile(
            overrideGlobal,
            extRules,
            dirRules,
            profiles)
    }

    private static Map<String, ExtRule> parseExtRules(Object raw) {
        Map<String, ExtRule> result = new LinkedHashMap<>()

        if (!(raw instanceof Map)) return result

        for (Map.Entry entry : ((Map) raw).entrySet()) {
            String key = String.valueOf(entry.key).toLowerCase(Locale.ROOT)
            ExtRule rule = parseExtRule(entry.value)

            if (rule != null) {
                result.put(key, rule)
            }
        }

        return result
    }

    private static ExtRule parseExtRule(Object raw) {
        if (!(raw instanceof Map)) return null

        Map m = (Map) raw

        if (truthy(m.get('ignore'))) {
            return ExtIgnore.INSTANCE
        }

        return new ExtAllow(
            toStringList(m.get('ignoreAllIn')),
            toRegexSetMap(m.get('ignoreAllRegexIn')),
            RegexSet.fromStrings(toStringList(m.get('ignoreAllRegex')))
        )
    }

    private static Map<String, DirRule> parseDirRules(Object raw) {
        Map<String, DirRule> result = new LinkedHashMap<>()

        if (!(raw instanceof Map)) {
            return result
        }

        for (Map.Entry entry : ((Map) raw).entrySet()) {
            String key = PathUtils.normalizeRelPath(String.valueOf(entry.key))
            DirRule rule = parseDirRule(entry.value)

            if (rule != null) {
                result.put(key, rule)
            }
        }

        return result
    }

    private static DirRule parseDirRule(Object raw) {
        if (!(raw instanceof Map)) return null

        Map m = (Map) raw

        if (truthy(m.get('ignore'))) {
            return DirIgnore.INSTANCE
        }

        List<String> ignoreAllOfExt = toStringList(m.get('ignoreAllOfExt'))
        Object rawRegex = m.get('ignoreAllRegex')

        RegexSet unbounded = RegexSet.EMPTY
        Map<Integer, RegexSet> depthMap = new LinkedHashMap<>()

        if (rawRegex instanceof List) {
            unbounded = RegexSet.fromStrings(toStringList(rawRegex))
        } else if (rawRegex instanceof Map) {
            for (Map.Entry e : ((Map) rawRegex).entrySet()) {
                String k = String.valueOf(e.key)
                List<String> patterns = toStringList(e.value)

                if (k.equalsIgnoreCase('Infinity')) {
                    unbounded = RegexSet.fromStrings(patterns)
                } else {
                    try {
                        depthMap.put(Integer.parseInt(k), RegexSet.fromStrings(patterns))
                    } catch (NumberFormatException ignored) {
                    // handler
                    }
                }
            }
        }

        return new DirAllow(ignoreAllOfExt, unbounded, depthMap)
    }

    private static Map<String, Config.ProfileSpec> parseProfiles(Object raw) {
        Map<String, Config.ProfileSpec> result = new LinkedHashMap<>()
        if (!(raw instanceof Map)) return result

        for (Map.Entry entry : ((Map) raw).entrySet()) {
            if (!(entry.value instanceof Map)) continue
            Map m = (Map) entry.value

            result.put(String.valueOf(entry.key), new Config.ProfileSpec(
                truthy(m.get('override')),
                parseExtRules(m.get('ext')),
                parseDirRules(m.get('dir'))
            ))
        }
        return result
    }

    private static boolean truthy(Object o) {
        o instanceof Boolean && (Boolean) o
    }

    private static List<String> toStringList(Object raw) {
        if (raw instanceof List) {
            return ((List) raw).collect { Object o -> String.valueOf(o) }
        }

        return Collections.<String>emptyList()
    }

    private static Map<String, RegexSet> toRegexSetMap(Object raw) {
        Map<String, RegexSet> result = new LinkedHashMap<>()

        if (!(raw instanceof Map)) {
            return result
        }

        for (Map.Entry e : ((Map) raw).entrySet()) {
            result.put(
                PathUtils.normalizeRelPath(String.valueOf(e.key)),
                RegexSet.fromStrings(toStringList(e.value))
            )
        }

        return result
    }

}
