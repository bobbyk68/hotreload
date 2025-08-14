
package uk.g.h.rules.reload;

import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class RemoteMavenScanner implements RulesScanner {

    private final KieServices ks;
    private final String groupId, artifactId, version; // e.g. "1.0.0-SNAPSHOT"
    private final URI metadataUri;                      // .../uk/g/h/rules-kjar/1.0.0-SNAPSHOT/maven-metadata.xml
    private final HttpClient client;

    private final AtomicReference<String> lastEtag = new AtomicReference<>(null);
    private final AtomicReference<String> lastLastMod = new AtomicReference<>(null);
    private volatile String lastFingerprint = null;

    private final String basicAuthHeader; // "Basic <base64>" or null (anonymous)

    public RemoteMavenScanner(KieServices ks,
                              String groupId, String artifactId, String version,
                              URI metadataUri,
                              String repoUser, String repoPass) {
        this.ks = ks;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.metadataUri = metadataUri;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        if (repoUser != null && !repoUser.isBlank()) {
            String token = Base64.getEncoder()
                    .encodeToString((repoUser + ":" + (repoPass == null ? "" : repoPass))
                    .getBytes(StandardCharsets.UTF_8));
            this.basicAuthHeader = "Basic " + token;
        } else {
            this.basicAuthHeader = null;
        }
    }

    @Override
    public Optional<ReleaseId> detectUpdate() {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(metadataUri)
                    .timeout(Duration.ofSeconds(5))
                    .GET();

            if (basicAuthHeader != null) b.header("Authorization", basicAuthHeader);
            String etag = lastEtag.get();
            String lastMod = lastLastMod.get();
            if (etag != null) b.header("If-None-Match", etag);
            if (lastMod != null) b.header("If-Modified-Since", lastMod);

            HttpResponse<byte[]> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 304) return Optional.empty();  // no change
            if (resp.statusCode() != 200) return Optional.empty();  // transient issue

            resp.headers().firstValue("ETag").ifPresent(lastEtag::set);
            resp.headers().firstValue("Last-Modified").ifPresent(lastLastMod::set);

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(resp.body()));

            String ts = xmlText(doc, "versioning/snapshot/timestamp");
            String bn = xmlText(doc, "versioning/snapshot/buildNumber");
            String lu = xmlText(doc, "versioning/lastUpdated");

            String fp = (ts != null && bn != null) ? ts + "#" + bn
                    : (lu != null ? lu : Integer.toString(java.util.Arrays.hashCode(resp.body())));

            if (!fp.equals(lastFingerprint)) {
                lastFingerprint = fp;
                return Optional.of(ks.newReleaseId(groupId, artifactId, version));
            }
            return Optional.empty();
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }

    private static String xmlText(Document d, String path) {
        String[] parts = path.split("/");
        org.w3c.dom.Element node = d.getDocumentElement();
        for (String p : parts) {
            var list = node.getElementsByTagName(p);
            if (list.getLength() == 0) return null;
            node = (org.w3c.dom.Element) list.item(0);
        }
        return node.getTextContent();
    }
}
