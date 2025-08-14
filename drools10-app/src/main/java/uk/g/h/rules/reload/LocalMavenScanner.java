package uk.g.h.rules.reload;

import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.util.Optional;

/**
 * Detects new local SNAPSHOTs created by `mvn install` by reading
 * ~/.m2/.../maven-metadata-local.xml and comparing timestamp/buildNumber.
 */
public class LocalMavenScanner implements RulesScanner {

    private static final Logger LOG = LoggerFactory.getLogger(LocalMavenScanner.class);

    private final KieServices ks;
    private final String groupId, artifactId, version; // e.g., 1.0.0-SNAPSHOT
    private final Path m2Repo;                         // default ~/.m2/repository
    private String lastFingerprint = null;

    public LocalMavenScanner(KieServices ks, String groupId, String artifactId, String version, Path m2Repo) {
        this.ks = ks;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.m2Repo = (m2Repo != null) ? m2Repo
                : Paths.get(System.getProperty("user.home"), ".m2", "repository");
    }

    @Override
    public Optional<ReleaseId> detectUpdate() {
        try {
            Path baseDir = m2Repo
                    .resolve(groupId.replace('.', '/'))
                    .resolve(artifactId)
                    .resolve(version);
            Path meta = baseDir.resolve("maven-metadata-local.xml");

            LOG.debug("[rules] checking {}", meta);
            if (!Files.exists(meta)) {
                LOG.debug("[rules] metadata not found yet");
                return Optional.empty();
            }

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(meta.toFile());

            // Correct paths for local metadata
            String ts = text(doc, "versioning/snapshot/timestamp");
            String bn = text(doc, "versioning/snapshot/buildNumber");
            String lu = text(doc, "versioning/lastUpdated");

            String fp = (ts != null && bn != null) ? (ts + "#" + bn)
                    : (lu != null ? lu : String.valueOf(Files.getLastModifiedTime(meta).toMillis()));

            // When ts/bn exist, ensure the actual timestamped JAR is present
            if (ts != null && bn != null) {
                String base = version.replace("-SNAPSHOT", "");
                String jarName = artifactId + "-" + base + "-" + ts + "-" + bn + ".jar";
                Path jar = baseDir.resolve(jarName);
                if (!Files.exists(jar)) {
                    LOG.debug("[rules] timestamped jar not present yet: {}", jar);
                    return Optional.empty();
                }
            }

            LOG.debug("[rules] fingerprint current={} previous={}", fp, lastFingerprint);
            if (!fp.equals(lastFingerprint)) {
                lastFingerprint = fp;
                return Optional.of(ks.newReleaseId(groupId, artifactId, version));
            }
            return Optional.empty();
        } catch (Exception e) {
            LOG.warn("[rules] LocalMavenScanner error: {}", e.toString());
            return Optional.empty();
        }
    }

    private static String text(Document d, String path) {
        String[] parts = path.split("/");
        org.w3c.dom.Element node = d.getDocumentElement();
        for (String p : parts) {
            var list = node.getElementsByTagName(p);
            if (list.getLength() == 0) return null;
            node = (org.w3c.dom.Element) list.item(0);
        }
        return node.getTextContent();
    }

    /** For diagnostics (/rules/fingerprint). */
    public String currentFingerprint() { return lastFingerprint; }
}
