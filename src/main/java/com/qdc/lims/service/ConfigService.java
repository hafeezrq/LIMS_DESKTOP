package com.qdc.lims.service;

import com.qdc.lims.entity.SystemConfiguration;
import com.qdc.lims.repository.SystemConfigurationRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service to manage system configuration settings.
 * Caches settings in memory for performance.
 */
@Service
public class ConfigService {

    private static final String LEGACY_REPORT_FOOTER_DEFAULT =
            "This is a computer generated report and does not require a signature.";

    @Autowired
    private SystemConfigurationRepository configRepository;

    private Map<String, String> cache = new HashMap<>();

    @PostConstruct
    public void init() {
        refreshCache();
        ensureDefaults();
        updateLabProfileCompletionFlag();
    }

    public void refreshCache() {
        List<SystemConfiguration> configs = configRepository.findAll();
        cache.clear();
        for (SystemConfiguration config : configs) {
            cache.put(config.getKey(), config.getValue());
        }
    }

    private void ensureDefaults() {
        createIfNotExists("APP_NAME", "LIMS", "General");

        // Whitelabel clinic profile fields intentionally default to blank so the
        // first-run experience prompts for real lab details.
        createIfNotExists("CLINIC_NAME", "", "General");
        createIfNotExists("CLINIC_ADDRESS", "", "General");
        createIfNotExists("CLINIC_PHONE", "", "General");
        createIfNotExists("CLINIC_EMAIL", "", "General");

        createIfNotExists("LAB_PROFILE_COMPLETED", "false", "General");

        createIfNotExists("CURRENCY_SYMBOL", "AUTO", "Billing");
        createIfNotExists("TAX_RATE_PERCENT", "0.0", "Billing");

        createIfNotExists("REQUIRE_PASSWORD_RECEPTION_LAB", "true", "Security");
        createIfNotExists("SESSION_TIMEOUT_ENABLED", "false", "Security");
        createIfNotExists("CANCEL_APPROVAL_KEY_HASH", "", "Security");

        createIfNotExists("REPORT_HEADER_TEXT", "", "Reports");
        createIfNotExists("REPORT_FOOTER_TEXT", "", "Reports");
        createIfNotExists("REPORT_LOGO_PATH", "", "Reports");
        createIfNotExists("REPORT_PATIENT_INFO_OFFSET_Y_MM", "0", "Reports");
        clearLegacyReportFooterDefault();

        refreshCache();
    }

    private void clearLegacyReportFooterDefault() {
        configRepository.findByKey("REPORT_FOOTER_TEXT").ifPresent(config -> {
            String value = config.getValue() == null ? "" : config.getValue().trim();
            if (LEGACY_REPORT_FOOTER_DEFAULT.equals(value)) {
                config.setValue("");
                configRepository.save(config);
            }
        });
    }

    private void createIfNotExists(String key, String defaultValue, String category) {
        if (!configRepository.existsById(key)) {
            SystemConfiguration config = new SystemConfiguration();
            config.setKey(key);
            config.setValue(defaultValue);
            config.setDescription("System Setting: " + key);
            config.setCategory(category);
            configRepository.save(config);
        }
    }

    public String get(String key) {
        return cache.getOrDefault(key, "");
    }

    public String get(String key, String defaultValue) {
        return cache.getOrDefault(key, defaultValue);
    }

    /**
     * Retrieves a trimmed configuration value.
     *
     * @param key configuration key
     * @return trimmed value, or an empty string if missing
     */
    public String getTrimmed(String key) {
        return getTrimmed(key, "");
    }

    /**
     * Retrieves a trimmed configuration value with a default.
     *
     * @param key          configuration key
     * @param defaultValue default value
     * @return trimmed value
     */
    public String getTrimmed(String key, String defaultValue) {
        String value = get(key, defaultValue);
        return value == null ? "" : value.trim();
    }

    public void set(String key, String value) {
        setInternal(key, value, true);
    }

    /**
     * Updates and persists the lab profile completion flag based on required
     * branding fields.
     */
    public void updateLabProfileCompletionFlag() {
        boolean complete = !getTrimmed("CLINIC_NAME").isBlank()
                && !getTrimmed("CLINIC_ADDRESS").isBlank()
                && !getTrimmed("CLINIC_PHONE").isBlank();

        boolean current = Boolean.parseBoolean(getTrimmed("LAB_PROFILE_COMPLETED", "false"));
        if (current != complete) {
            setInternal("LAB_PROFILE_COMPLETED", Boolean.toString(complete), false);
        }
    }

    /**
     * @return whether the lab profile is complete
     */
    public boolean isLabProfileComplete() {
        updateLabProfileCompletionFlag();
        return Boolean.parseBoolean(getTrimmed("LAB_PROFILE_COMPLETED", "false"));
    }

    private void setInternal(String key, String value, boolean updateProfileFlag) {
        Optional<SystemConfiguration> opt = configRepository.findByKey(key);
        SystemConfiguration config;

        if (opt.isPresent()) {
            config = opt.get();
        } else {
            config = new SystemConfiguration();
            config.setKey(key);
            config.setDescription("Setting"); // Default description
            config.setCategory("General"); // Default category
        }

        config.setValue(value);
        configRepository.save(config);
        cache.put(key, value);

        if (updateProfileFlag && isProfileField(key)) {
            updateLabProfileCompletionFlag();
        }
    }

    private boolean isProfileField(String key) {
        return "CLINIC_NAME".equals(key)
                || "CLINIC_ADDRESS".equals(key)
                || "CLINIC_PHONE".equals(key)
                || "CLINIC_EMAIL".equals(key);
    }
}
