package aigest.config

import aigest.rules.DirMatcher.DirRule
import aigest.rules.ExtMatcher.ExtRule

import groovy.transform.CompileStatic

@CompileStatic
final class Config {

    private Config() { }

    static final class ProfileSpec {

        final boolean overrideGlobal
        final Map<String, ExtRule> extRules
        final Map<String, DirRule> dirRules

        ProfileSpec(
            boolean overrideGlobal,
            Map<String, ExtRule> extRules,
            Map<String, DirRule> dirRules
        ) {
            this.overrideGlobal = overrideGlobal
            this.extRules = Collections.unmodifiableMap(new LinkedHashMap<String, ExtRule>(extRules))
            this.dirRules = Collections.unmodifiableMap(new LinkedHashMap<String, DirRule>(dirRules))
        }

    }

    static final class ConfigFile {

        public static final ConfigFile EMPTY = new ConfigFile(
            false,
            Collections.<String, ExtRule>emptyMap(),
            Collections.<String, DirRule>emptyMap(),
            Collections.<String, ProfileSpec>emptyMap()
        )

        final boolean overrideGlobal
        final Map<String, ExtRule> extRules
        final Map<String, DirRule> dirRules
        final Map<String, ProfileSpec> profiles

        ConfigFile(
            boolean overrideGlobal,
            Map<String, ExtRule> extRules,
            Map<String, DirRule> dirRules,
            Map<String, ProfileSpec> profiles
        ) {
            this.overrideGlobal = overrideGlobal
            this.extRules = Collections.unmodifiableMap(new LinkedHashMap<String, ExtRule>(extRules))
            this.dirRules = Collections.unmodifiableMap(new LinkedHashMap<String, DirRule>(dirRules))
            this.profiles = Collections.unmodifiableMap(new LinkedHashMap<String, ProfileSpec>(profiles))
        }

    }

    static final class ResolvedConfig {

        final String activeProfile
        final Map<String, ExtRule> extRules
        final Map<String, DirRule> dirRules

        ResolvedConfig(
            String activeProfile,
            Map<String, ExtRule> extRules,
            Map<String, DirRule> dirRules
        ) {
            this.activeProfile = activeProfile
            this.extRules = Collections.unmodifiableMap(new LinkedHashMap<String, ExtRule>(extRules))
            this.dirRules = Collections.unmodifiableMap(new LinkedHashMap<String, DirRule>(dirRules))
        }

    }

    static ResolvedConfig resolve(
        ConfigFile globalConfig,
        ConfigFile repoConfig,
        String requestedProfile
    ) {
        ConfigFile safeGlobal = globalConfig != null ? globalConfig : ConfigFile.EMPTY
        ConfigFile safeRepo = repoConfig != null ? repoConfig : ConfigFile.EMPTY

        if (requestedProfile == null || requestedProfile.trim().isEmpty()) {
            if (safeRepo.overrideGlobal) {
                return new ResolvedConfig('main', safeRepo.extRules, safeRepo.dirRules)
            }
            return new ResolvedConfig(
                'main',
                mergeMap(safeGlobal.extRules, safeRepo.extRules),
                mergeMap(safeGlobal.dirRules, safeRepo.dirRules)
            )
        }

        String profileName = requestedProfile.trim()
        ProfileSpec repoProfile = safeRepo.profiles.get(profileName)
        ProfileSpec globalProfile = safeGlobal.profiles.get(profileName)

        if (repoProfile == null && globalProfile == null) {
            throw new IllegalArgumentException(
                "Profile '${profileName}' not found in repository aigest.json or global config."
            )
        }

        if (repoProfile != null) {
            boolean shouldIgnoreGlobal = safeRepo.overrideGlobal || repoProfile.overrideGlobal
            if (shouldIgnoreGlobal) {
                return new ResolvedConfig(profileName, repoProfile.extRules, repoProfile.dirRules)
            }
            Map<String, ExtRule> baseExt = globalProfile != null
                ? mergeMap(safeGlobal.extRules, globalProfile.extRules)
                : safeGlobal.extRules
            Map<String, DirRule> baseDir = globalProfile != null
                ? mergeMap(safeGlobal.dirRules, globalProfile.dirRules)
                : safeGlobal.dirRules

            return new ResolvedConfig(
                profileName,
                mergeMap(baseExt, repoProfile.extRules),
                mergeMap(baseDir, repoProfile.dirRules)
            )
        }

        if (globalProfile.overrideGlobal) {
            return new ResolvedConfig(profileName, globalProfile.extRules, globalProfile.dirRules)
        }

        new ResolvedConfig(
            profileName,
            mergeMap(safeGlobal.extRules, globalProfile.extRules),
            mergeMap(safeGlobal.dirRules, globalProfile.dirRules)
        )
    }

    private static <K, V> Map<K, V> mergeMap(Map<K, V> base, Map<K, V> override) {
        Map<K, V> merged = new LinkedHashMap<K, V>()

        if (base != null) {
            merged.putAll(base)
        }

        if (override != null) {
            merged.putAll(override)
        }

        Collections.unmodifiableMap(merged)
    }

}
