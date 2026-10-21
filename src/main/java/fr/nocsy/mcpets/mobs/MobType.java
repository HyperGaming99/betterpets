package fr.nocsy.mcpets.mobs;

/**
 * Which mob plugin a pet (or skin) uses. The name is also the key in the pet config.
 */
public enum MobType {
    MYTHICMOBS("MythicMob"),
    BETTERMOB("BetterMob");

    private final String configKey;

    MobType(final String configKey) {
        this.configKey = configKey;
    }

    /** Key in the pet config, e.g. "MythicMob: my_mob" or "BetterMob: my_mob". */
    public String getConfigKey() {
        return configKey;
    }
}
