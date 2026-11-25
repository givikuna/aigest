package aigest.config

import aigest.rules.DirMatcher.DirRule
import aigest.rules.ExtMatcher.ExtRule
import aigest.rules.IncludeOnlyMatcher.IncludeOnlyRule

import aigest.util.Result

import groovy.transform.CompileStatic

@CompileStatic
final class Config {

    private Config() { }

    static final class ProfileSpec {

        final boolean overrideGlobal
        final Map<String, ExtRule> extRules
        final Map<String, DirRule> dirRules
        final IncludeOnlyRule includeOnly

        ProfileSpec(
            boolean overrideGlobal,
            Map<String, ExtRule> extRules,
            Map<String, DirRule> dirRules,
            IncludeOnlyRule includeOnly = IncludeOnlyRule.None.INSTANCE
        ) {
            this.overrideGlobal = overrideGlobal
            this.extRules = Collections.unmodifiableMap(new LinkedHashMap<String, ExtRule>(extRules))
            this.dirRules = Collections.unmodifiableMap(new LinkedHashMap<String, DirRule>(dirRules))
            this.includeOnly = includeOnly ?: IncludeOnlyRule.None.INSTANCE
        }

    }

    static final class ConfigFile {

        public static final ConfigFile EMPTY = new ConfigFile(
            false,
            Collections.<String, ExtRule>emptyMap(),
            Collections.<String, DirRule>emptyMap(),
            Collections.<String, ProfileSpec>emptyMap(),
            IncludeOnlyRule.None.INSTANCE
        )

        final boolean overrideGlobal
        final Map<String, ExtRule> extRules
        final Map<String, DirRule> dirRules
        final Map<String, ProfileSpec> profiles
        final IncludeOnlyRule includeOnly

        ConfigFile(
            boolean overrideGlobal,
            Map<String, ExtRule> extRules,
            Map<String, DirRule> dirRules,
            Map<String, ProfileSpec> profiles,
            IncludeOnlyRule includeOnly = IncludeOnlyRule.None.INSTANCE
        ) {
            this.overrideGlobal = overrideGlobal
            this.extRules = Collections.unmodifiableMap(new LinkedHashMap<String, ExtRule>(extRules))
            this.dirRules = Collections.unmodifiableMap(new LinkedHashMap<String, DirRule>(dirRules))
            this.profiles = Collections.unmodifiableMap(new LinkedHashMap<String, ProfileSpec>(profiles))
            this.includeOnly = includeOnly ?: IncludeOnlyRule.None.INSTANCE
        }

    }

    static final class ResolvedConfig {

        final String activeProfile
        final Map<String, ExtRule> extRules
        final Map<String, DirRule> dirRules
        final IncludeOnlyRule includeOnly

        ResolvedConfig(
            String activeProfile,
            Map<String, ExtRule> extRules,
            Map<String, DirRule> dirRules,
            IncludeOnlyRule includeOnly = IncludeOnlyRule.None.INSTANCE
        ) {
            this.activeProfile = activeProfile
            this.extRules = Collections.unmodifiableMap(new LinkedHashMap<String, ExtRule>(extRules))
            this.dirRules = Collections.unmodifiableMap(new LinkedHashMap<String, DirRule>(dirRules))
            this.includeOnly = includeOnly ?: IncludeOnlyRule.None.INSTANCE
        }

    }

    static Result<ResolvedConfig, String> resolve(
        ConfigFile globalConfig,
        ConfigFile repoConfig,
        String requestedProfile
    ) {
        ConfigFile safeRepo = repoConfig != null ? repoConfig : ConfigFile.EMPTY
        ConfigFile safeGlobal = (safeRepo.overrideGlobal || globalConfig == null)
            ? ConfigFile.EMPTY
            : globalConfig

        if (requestedProfile == null || requestedProfile.trim().isEmpty()) {
            return Result.<ResolvedConfig, String>ok(new ResolvedConfig(
                'main',
                mergeMap(safeGlobal.extRules, safeRepo.extRules),
                mergeMap(safeGlobal.dirRules, safeRepo.dirRules),
                mergeIncludeOnly(safeGlobal.includeOnly, safeRepo.includeOnly)
            ))
        }

        String profileName = requestedProfile.trim()
        ProfileSpec repoProfile = safeRepo.profiles.get(profileName)
        ProfileSpec globalProfile = safeGlobal.profiles.get(profileName)

        if (repoProfile == null && globalProfile == null) {
            return Result.<ResolvedConfig, String>err(
                "Profile '${profileName}' not found in repository aigest.json or global config."
            )
        }

        if (repoProfile != null) {
            if (repoProfile.overrideGlobal) {
                return Result.<ResolvedConfig, String>ok(new ResolvedConfig(
                    profileName,
                    repoProfile.extRules,
                    repoProfile.dirRules,
                    repoProfile.includeOnly
                ))
            }

            Map<String, ExtRule> baseExt = globalProfile != null
                ? mergeMap(safeGlobal.extRules, globalProfile.extRules)
                : safeGlobal.extRules
            Map<String, DirRule> baseDir = globalProfile != null
                ? mergeMap(safeGlobal.dirRules, globalProfile.dirRules)
                : safeGlobal.dirRules
            IncludeOnlyRule baseIncludeOnly = globalProfile != null
                ? mergeIncludeOnly(safeGlobal.includeOnly, globalProfile.includeOnly)
                : safeGlobal.includeOnly

            return Result.<ResolvedConfig, String>ok(new ResolvedConfig(
                profileName,
                mergeMap(baseExt, repoProfile.extRules),
                mergeMap(baseDir, repoProfile.dirRules),
                mergeIncludeOnly(baseIncludeOnly, repoProfile.includeOnly)
            ))
        }

        if (globalProfile.overrideGlobal) {
            return Result.<ResolvedConfig, String>ok(new ResolvedConfig(
                profileName,
                globalProfile.extRules,
                globalProfile.dirRules,
                globalProfile.includeOnly
            ))
        }

        Result.<ResolvedConfig, String>ok(new ResolvedConfig(
            profileName,
            mergeMap(safeGlobal.extRules, globalProfile.extRules),
            mergeMap(safeGlobal.dirRules, globalProfile.dirRules),
            mergeIncludeOnly(safeGlobal.includeOnly, globalProfile.includeOnly)
        ))
    }

    private static IncludeOnlyRule mergeIncludeOnly(
        IncludeOnlyRule base,
        IncludeOnlyRule override
    ) {
        override != null && override.isActive()
            ? override
            : base ?: IncludeOnlyRule.None.INSTANCE
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
