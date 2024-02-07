package io.jenkins.plugins.monokle.cli.setup;

public class Artifact {
    String url;
    String extension;

    public Artifact(String url, String extension) {
        this.url = url;
        this.extension = extension;
    }

    public String getUrl() {
        return url;
    }

    public String getExtension() {
        return extension;
    }
}
