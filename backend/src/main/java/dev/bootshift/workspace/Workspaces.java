package dev.bootshift.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Lays out a run's files under {@code bootshift.workspace.root}:
 * {@code runs/<id>/repo} is the working copy (deleted when the run ends) and
 * {@code logs/<id>/<build>.log} holds build logs (kept for the report).
 */
@Component
public class Workspaces {

    private static final Logger log = LoggerFactory.getLogger(Workspaces.class);

    private final Path root;

    public Workspaces(Properties properties) {
        this.root = properties.root().toAbsolutePath().normalize();
    }

    /** @param root where working copies and logs live; must not be inside a synced or shared folder */
    @ConfigurationProperties("bootshift.workspace")
    public record Properties(Path root) {
    }

    /** The run's working copy directory. Its parent exists; the directory itself does not yet. */
    public Path repository(UUID runId) {
        Path dir = root.resolve("runs").resolve(runId.toString());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create workspace " + dir, e);
        }
        return dir.resolve("repo");
    }

    public Path log(UUID runId, String name) {
        return root.resolve("logs").resolve(runId.toString()).resolve(name + ".log");
    }

    /** Deletes the run's working copy. Links are deleted, never followed. */
    public void delete(UUID runId) {
        Path dir = root.resolve("runs").resolve(runId.toString());
        if (!Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    deleteFile(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                    deleteFile(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Could not fully delete workspace {}: {}", dir, e.getMessage());
        }
    }

    /** Git packs are read-only on Windows, which blocks deletion until the flag is cleared. */
    private static void deleteFile(Path path) throws IOException {
        try {
            Files.delete(path);
        } catch (java.nio.file.AccessDeniedException e) {
            DosFileAttributeView dos = Files.getFileAttributeView(path, DosFileAttributeView.class);
            if (dos == null) {
                throw e;
            }
            dos.setReadOnly(false);
            Files.delete(path);
        }
    }
}
