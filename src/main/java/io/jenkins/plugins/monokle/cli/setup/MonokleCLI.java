package io.jenkins.plugins.monokle.cli.setup;

import hudson.EnvVars;
import hudson.util.Secret;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class MonokleCLI {
    private EnvVars envVars;
    private String version;
    private String channel;
    private String namespace;
    private String url;
    private String organization;
    private String environment;
    private Secret apiToken;
    private Boolean debug;

    public MonokleCLI(PrintStream logger, EnvVars envVars) {
        MonokleLogger.init(logger);
        this.envVars = envVars;
        this.version = getEnvVar("MK_VERSION", "MONOKLE_VERSION");
        this.channel = getEnvVar("MK_CHANNEL", "MONOKLE_CHANNEL");
        this.namespace = getEnvVar("MK_NAMESPACE", "MONOKLE_NAMESPACE");
        this.url = getEnvVar("MK_URL", "MONOKLE_URL");
        this.organization = getEnvVar("MK_ORG", "MONOKLE_ORG");
        this.environment = getEnvVar("MK_ENV", "MONOKLE_ENV");
        this.apiToken = Secret.fromString(getEnvVar("MK_API_TOKEN", "MONOKLE_API_TOKEN"));
        String debugValue = envVars.get("MK_DEBUG", "MONOKLE_DEBUG");
        this.debug = debugValue != null && !debugValue.isEmpty();
    }

    public MonokleCLI(
            PrintStream logger,
            EnvVars envVars,
            String version,
            String channel,
            String namespace,
            String url,
            String organization,
            String environment,
            String apiToken) {
        MonokleLogger.init(logger);
        this.envVars = envVars;
        this.channel = channel;
        this.namespace = namespace;
        this.url = url;
        this.version = version;
        this.organization = organization;
        this.environment = environment;
        this.apiToken = Secret.fromString(apiToken);
    }

    private String getEnvVar(String mkKey, String monokleKey) {
        String value = envVars.get(mkKey);
        if (value == null) {
            value = envVars.get(monokleKey);
        }
        return value;
    }

    private void setDefaults() {
        envVars.put("NO_COLOR", "1");
        if (channel == null) {
            channel = "stable";
        }
    }

    public boolean setup() {
        try {
            peformSetup();
            return true;
        } catch (Exception e) {
            MonokleLogger.println("Error during setup: " + e.getMessage());
            if (debug) {
                e.printStackTrace(MonokleLogger.getPrintStream());
            }
            return false;
        }
    }

    private void checkEnvironmentVariables() throws Exception {
        List<String> missingVariables = new ArrayList<>();

        if (organization == null) {
            missingVariables.add("organization");
        }

        if (environment == null) {
            missingVariables.add("environment");
        }

        if (apiToken == null) {
            missingVariables.add("apiToken");
        }

        if (!missingVariables.isEmpty()) {
            throw new Exception(
                    "The following arguments are missing: " + String.join(", ", missingVariables)
                            + ". If you want to run in Cloud Mode, please provide these arguments directly or using their corresponding environment variables.");
        } else {
            MonokleLogger.println("Running in cloud mode...");
        }
    }

    private void peformSetup() throws Exception {
        setDefaults();

        // Boolean isCloudMode = (organization != null || environment != null ||
        // apiToken != null) ? true : false;

        // checkEnvironmentVariables();

        String binaryPath = findWritableBinaryPath();
        MonokleLogger.println("Binary path: " + binaryPath);

        String architecture = MonokleDetectors.detectArchitecture();
        String system = MonokleDetectors.detectSystem();

        // MonokleDetectors.detectKubectl(isCloudMode);

        var installedMonokleVersion = MonokleDetectors.detectMonokleCLI(channel, version);
        Boolean isInstalled = installedMonokleVersion != null && !installedMonokleVersion.isEmpty();
        String versionToInstall = null;

        if (!isInstalled) {
            versionToInstall = version != null ? version : MonokleDetectors.detectMonokleVersion(channel);
            MonokleLogger.println("Installing \"" + versionToInstall + "\" version...");
        } else if (installedMonokleVersion != null) {
            MonokleLogger.println("Currently installed version: " + installedMonokleVersion);
            if (version != null && !installedMonokleVersion.equals(version)) {
                MonokleLogger.println("Force install \"" + version + "\" version...");
                versionToInstall = version;
            }
        }

        if (versionToInstall != null) {
            installCLI(envVars, versionToInstall, system, architecture, binaryPath);
        }

        // configureContext(isCloudMode);
    }

    private static String findWritableBinaryPath() throws Exception {
        List<String> preferredPaths = Arrays.asList("/usr/local/bin", "/usr/bin");

        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) {
            throw new IllegalStateException("PATH environment variable is not set.");
        }

        List<String> detectedPaths = Arrays.stream(pathEnv.split(File.pathSeparator))
                .filter(path -> !path.isEmpty())
                .sorted((a, b) -> Integer.compare(a.length(), b.length()))
                .collect(Collectors.toList());

        List<String> writablePaths = detectedPaths.stream()
                .filter(path -> Files.isWritable(Paths.get(path)))
                .collect(Collectors.toList());

        return preferredPaths.stream()
                .filter(writablePaths::contains)
                .findFirst()
                .orElseGet(() -> writablePaths.isEmpty() ? null : writablePaths.get(0));
    }

    private static Artifact makeArtifact(String version, String system, String architecture) {
        var encodedVersion = URLEncoder.encode(version, StandardCharsets.UTF_8);
        if (system == "Darwin") {
            if (system == "amd64") {
                return new Artifact(
                        String.format(
                                "https://github.com/kubeshop/monokle-cli/releases/download/v%s/monokle-cli-macos-amd64",
                                encodedVersion),
                        "");
            }
            return new Artifact(
                    String.format(
                            "https://github.com/kubeshop/monokle-cli/releases/download/v%s/monokle-cli-macos-arm",
                            encodedVersion),
                    "");

        }
        if (system == "Linux") {
            return new Artifact(
                    String.format(
                            "https://github.com/kubeshop/monokle-cli/releases/download/v%s/monokle-cli-linux",
                            encodedVersion),
                    "");
        }
        if (system == "Windows") {
            return new Artifact(
                    String.format(
                            "https://github.com/kubeshop/monokle-cli/releases/download/v%s/monokle-cli-win.exe",
                            encodedVersion),
                    ".exe");
        }
        return null;
    }

    private static void installCLI(
            EnvVars envVars, String version, String system, String architecture, String binaryDirPath)
            throws Exception {

        Artifact artifact = makeArtifact(version, system, architecture);

        MonokleLogger.println("Downloading the artifact from \"" + artifact.getUrl() + "\"...");

        // Download the tar.gz file
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(artifact.getUrl())).build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        // Check response status code
        if (response.statusCode() != 200) {
            throw new IOException("Failed to download the Monokle CLI. HTTP status: " + response.statusCode());
        }

        Path monoklePath = Paths.get(binaryDirPath, "monokle" + artifact.getExtension());
        Files.copy(response.body(), monoklePath, StandardCopyOption.REPLACE_EXISTING);

        if ("Darwin".equals(system) || "Linux".equals(system)) {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr--r--");
            Files.setPosixFilePermissions(monoklePath, perms);
            MonokleLogger.println("Set execute permissions for " + monoklePath);
        }
    }

    private void configureContext(Boolean isCloudMode) throws Exception {
        List<String> command = new ArrayList<>();

        // TODO: add any commands needed for configuration

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("Failed to configure Monokle context with exit code: " + exitCode);
        } else {
            MonokleLogger.println("Context configured successfully.");
        }
    }
}
