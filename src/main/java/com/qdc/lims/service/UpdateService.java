package com.qdc.lims.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Checks GitHub releases and downloads the Windows installer.
 */
@Service
public class UpdateService {

    private static final String REPO_SLUG = System.getProperty("lims.update.repo", "hafeezrq/LIMS_DESKTOP");
    private static final String RELEASES_URL = "https://api.github.com/repos/" + REPO_SLUG + "/releases/latest";
    private static final String USER_AGENT = "LIMS-Desktop-Update-Checker";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public UpdateCheckResult checkForUpdates() {
        try {
            GitHubRelease release = fetchLatestRelease();
            if (release == null || release.tag_name == null) {
                return UpdateCheckResult.error("No release information found.");
            }

            String currentVersion = getCurrentVersion();
            String latestTag = release.tag_name;
            String latestVersion = normalizeVersion(latestTag);
            String assetUrl = pickInstallerAsset(release.assets);

            UpdateStatus status = compareVersions(currentVersion, latestVersion);
            return new UpdateCheckResult(status, currentVersion, latestTag, latestVersion, assetUrl, release.body, null);
        } catch (Exception e) {
            return UpdateCheckResult.error(e.getMessage());
        }
    }

    public boolean isInstallerSupported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    public Path downloadInstaller(String assetUrl) throws IOException, InterruptedException {
        if (assetUrl == null || assetUrl.isBlank()) {
            throw new IllegalArgumentException("Installer URL is missing.");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(assetUrl))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Download failed (" + response.statusCode() + ").");
        }

        Path tempFile = Files.createTempFile("lims-update-", ".exe");
        try (InputStream input = response.body()) {
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    public void launchInstaller(Path installerPath) throws IOException {
        if (installerPath == null || !Files.exists(installerPath)) {
            throw new IOException("Installer file not found.");
        }
        if (!isInstallerSupported()) {
            throw new IOException("Installer updates are only supported on Windows.");
        }
        new ProcessBuilder("cmd", "/c", "start", "", installerPath.toString()).start();
    }

    private GitHubRelease fetchLatestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_URL))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            String details = response.body();
            if (details != null && !details.isBlank()) {
                details = details.replaceAll("\\s+", " ").trim();
            }
            throw new IOException("GitHub API returned " + response.statusCode()
                    + (details == null || details.isBlank() ? "" : (": " + details)));
        }
        return mapper.readValue(response.body(), GitHubRelease.class);
    }

    private String pickInstallerAsset(List<GitHubAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return null;
        }
        return assets.stream()
                .filter(a -> a != null && a.name != null && a.name.toLowerCase(Locale.ROOT).endsWith(".exe"))
                .sorted(Comparator.comparing(a -> a.name))
                .map(a -> a.browser_download_url)
                .findFirst()
                .orElse(null);
    }

    private String getCurrentVersion() {
        Optional<String> fromBuildInfo = readBuildInfoVersion();
        if (fromBuildInfo.isPresent()) {
            String normalized = normalizeVersion(fromBuildInfo.get());
            if (!normalized.isBlank() && !normalized.contains("@")) {
                return normalized;
            }
        }
        Optional<String> fromPomProps = readPomPropertiesVersion();
        if (fromPomProps.isPresent()) {
            return normalizeVersion(fromPomProps.get());
        }
        String fromPackage = UpdateService.class.getPackage().getImplementationVersion();
        if (fromPackage != null && !fromPackage.isBlank()) {
            return normalizeVersion(fromPackage);
        }
        return "unknown";
    }

    private Optional<String> readBuildInfoVersion() {
        try (InputStream stream = UpdateService.class.getResourceAsStream("/build-info.properties")) {
            if (stream == null) {
                return Optional.empty();
            }
            Properties props = new Properties();
            props.load(stream);
            String version = props.getProperty("app.version", "").trim();
            if (version.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(version);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> readPomPropertiesVersion() {
        try (InputStream stream = UpdateService.class
                .getResourceAsStream("/META-INF/maven/com.qdc/lims/pom.properties")) {
            if (stream == null) {
                return Optional.empty();
            }
            Properties props = new Properties();
            props.load(stream);
            String version = props.getProperty("version", "").trim();
            if (version.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(version);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private UpdateStatus compareVersions(String currentRaw, String latestRaw) {
        List<Integer> current = parseVersion(currentRaw);
        List<Integer> latest = parseVersion(latestRaw);

        if (current.isEmpty() || latest.isEmpty()) {
            return UpdateStatus.UNKNOWN;
        }

        int max = Math.max(current.size(), latest.size());
        for (int i = 0; i < max; i++) {
            int a = i < current.size() ? current.get(i) : 0;
            int b = i < latest.size() ? latest.get(i) : 0;
            if (a < b) {
                return UpdateStatus.UPDATE_AVAILABLE;
            }
            if (a > b) {
                return UpdateStatus.UP_TO_DATE;
            }
        }
        return UpdateStatus.UP_TO_DATE;
    }

    private String normalizeVersion(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        cleaned = cleaned.replace("LIMS_v", "");
        cleaned = cleaned.replace("LIMS-", "");
        cleaned = cleaned.replace("v", "");
        cleaned = cleaned.replace("_", ".");
        return cleaned.trim();
    }

    private List<Integer> parseVersion(String value) {
        if (value == null) {
            return List.of();
        }
        String cleaned = normalizeVersion(value);
        if (cleaned.isBlank() || "unknown".equalsIgnoreCase(cleaned)) {
            return List.of();
        }
        String[] parts = cleaned.split("[^0-9]+");
        List<Integer> nums = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            try {
                nums.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
            }
        }
        return nums;
    }

    public enum UpdateStatus {
        UPDATE_AVAILABLE,
        UP_TO_DATE,
        UNKNOWN,
        ERROR
    }

    public static class UpdateCheckResult {
        private final UpdateStatus status;
        private final String currentVersion;
        private final String latestTag;
        private final String latestVersion;
        private final String assetUrl;
        private final String releaseNotes;
        private final String message;

        public UpdateCheckResult(UpdateStatus status,
                String currentVersion,
                String latestTag,
                String latestVersion,
                String assetUrl,
                String releaseNotes,
                String message) {
            this.status = status;
            this.currentVersion = currentVersion;
            this.latestTag = latestTag;
            this.latestVersion = latestVersion;
            this.assetUrl = assetUrl;
            this.releaseNotes = releaseNotes;
            this.message = message;
        }

        public static UpdateCheckResult error(String message) {
            return new UpdateCheckResult(UpdateStatus.ERROR, "unknown", null, null, null, null, message);
        }

        public UpdateStatus getStatus() {
            return status;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getLatestTag() {
            return latestTag;
        }

        public String getLatestVersion() {
            return latestVersion;
        }

        public String getAssetUrl() {
            return assetUrl;
        }

        public String getReleaseNotes() {
            return releaseNotes;
        }

        public String getMessage() {
            return message;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GitHubRelease {
        public String tag_name;
        public String body;
        public List<GitHubAsset> assets;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GitHubAsset {
        public String name;
        public String browser_download_url;
    }
}
