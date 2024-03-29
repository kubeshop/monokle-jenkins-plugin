package io.jenkins.plugins.monokle.cli.setup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class MonokleDetectors {

    public static void detectKubectl(boolean isCloudMode) throws Exception {
        if (!isCloudMode) {
            ProcessBuilder processBuilder = new ProcessBuilder("kubectl", "version", "--client");
            try {
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    MonokleLogger.println("kubectl: detected.");
                } else {
                    throw new Exception(
                            "You do not have kubectl installed. Most likely you need to configure your workflow to initialize connection with Kubernetes cluster.");
                }
            } catch (IOException | InterruptedException e) {
                throw new Exception("kubectl: not available.");
            }
        } else {
            MonokleLogger.println("Monokle: kubectl ignored for Cloud integration");
        }
    }

    public static String detectMonokleCLI(String channel, String forcedVersion) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder("which", "monokle");
        boolean isInstalled = false;
        try {
            Process process = processBuilder.start();
            isInstalled = process.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            // Ignore
        }

        if (isInstalled) {
            MonokleLogger.println("Looks like you already have the Monokle CLI installed. Checking version...");

            ProcessBuilder versionProcessBuilder = new ProcessBuilder("monokle", "version");
            Map<String, String> versionProcessEnv = versionProcessBuilder.environment();
            versionProcessEnv.put("NO_COLOR", "1");
            try {
                Process versionProcess = versionProcessBuilder.start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(versionProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    // Extract version from the output
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("Client Version ")) {
                            return line.replace("Client Version ", "");
                        }
                    }
                }
            } catch (Exception e) {
                MonokleLogger.println("   Failed to detect installed version: " + e.getMessage());
            }
        }

        return null;
    }

    public static String detectMonokleVersion(String channel) throws Exception {
        MonokleLogger.println("Detecting the latest version for minimum of \"" + channel + "\" channel...");
        String version = null;
        HttpClient client = HttpClient.newHttpClient();
        String releasesUrl = "https://api.github.com/repos/kubeshop/monokle-cli/releases";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(channel.equals("stable") ? releasesUrl + "/latest" : releasesUrl))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (channel.equals("stable")) {
            JSONObject release = new JSONObject(response.body());
            version = release.optString("tag_name", null);
        } else {
            JSONArray releases = new JSONArray(response.body());
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.getJSONObject(i);
                String tag = release.getString("tag_name");
                String releaseChannel = tag.matches("-([^0-9]+)") ? tag.replaceAll("-([^0-9]+)", "$1") : "stable";
                if (releaseChannel.equals(channel) || releaseChannel.equals("stable")) {
                    version = tag;
                    break;
                }
            }
        }

        if (version == null) {
            throw new IOException("Not found any version matching criteria.");
        }

        version = version.replaceFirst("^v", "");

        MonokleLogger.println("   Latest version: " + version);

        return version.replaceFirst("^v", "");
    }

    public static String detectSystem() throws Exception {
        String osName = System.getProperty("os.name");
        switch (osName) {
            case "Linux":
                return "Linux";
            case "Mac OS X":
                return "Darwin";
            case "Windows 10":
            case "Windows 7":
                return "Windows";
            default:
                throw new Exception("We do not support this OS yet.");
        }
    }

    public static String detectArchitecture() throws Exception {
        String osArch = System.getProperty("os.arch");
        switch (osArch) {
            case "x86_64":
            case "amd64":
                return "amd64";
            case "aarch64":
                return "arm64";
            default:
                throw new Exception("We do not support this architecture yet.");
        }
    }
}
