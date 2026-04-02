package com.example.Vkus.service;

import com.example.Vkus.config.BackupProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DbBackupService {

    private final BackupProperties props;
    private final JdbcTemplate jdbc;
    private final AuditLogService audit;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    public DbBackupService(BackupProperties props, JdbcTemplate jdbc, AuditLogService audit) {
        this.props = props;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public Path backupsDir() throws IOException {
        Path dir = Paths.get(props.getDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir;
    }

    public List<BackupFile> listBackups() throws IOException {
        Path dir = backupsDir();
        if (!Files.exists(dir)) return List.of();

        try (var s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .map(p -> {
                        try {
                            return new BackupFile(
                                    p.getFileName().toString(),
                                    p.toAbsolutePath().toString(),
                                    Files.size(p),
                                    Files.getLastModifiedTime(p).toMillis()
                            );
                        } catch (IOException e) {
                            return new BackupFile(p.getFileName().toString(), p.toAbsolutePath().toString(), -1, -1);
                        }
                    })
                    .sorted(Comparator.comparingLong(BackupFile::getModifiedAtMillis).reversed())
                    .collect(Collectors.toList());
        }
    }

    public BackupFile createCustomBackup() throws Exception {
        DbConn c = parseJdbcUrl(jdbcUrl);

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "backup_" + c.dbName + "_" + ts + ".dump";
        Path out = backupsDir().resolve(filename).normalize();

        List<String> cmd = new ArrayList<>();
        cmd.add(props.getPgDump());
        cmd.add("-Fc");
        cmd.add("-f"); cmd.add(out.toString());
        cmd.add("-h"); cmd.add(c.host);
        cmd.add("-p"); cmd.add(String.valueOf(c.port));
        cmd.add("-U"); cmd.add(dbUser);
        cmd.add(c.dbName);

        try {
            runCommand(cmd, Map.of("PGPASSWORD", dbPassword), 600);

            long size = Files.size(out);
            audit.logSimple("DB_BACKUP_CREATE", Map.of(
                    "db", c.dbName,
                    "host", c.host,
                    "port", c.port,
                    "file", filename,
                    "sizeBytes", size
            ));

            return new BackupFile(
                    filename,
                    out.toAbsolutePath().toString(),
                    size,
                    Files.getLastModifiedTime(out).toMillis()
            );
        } catch (Exception e) {
            audit.logSimple("DB_BACKUP_CREATE_FAILED", Map.of(
                    "db", c.dbName,
                    "host", c.host,
                    "port", c.port,
                    "file", filename,
                    "error", safeMsg(e)
            ));
            throw e;
        }
    }

    public void restoreFromExisting(String fileName) throws Exception {
        DbConn c = parseJdbcUrl(jdbcUrl);
        try {
            Path file = safeResolve(fileName);
            restoreByExtension(file);

            audit.logSimple("DB_BACKUP_RESTORE", Map.of(
                    "db", c.dbName,
                    "file", fileName,
                    "source", "existing"
            ));
        } catch (Exception e) {
            audit.logSimple("DB_BACKUP_RESTORE_FAILED", Map.of(
                    "db", c.dbName,
                    "file", fileName,
                    "source", "existing",
                    "error", safeMsg(e)
            ));
            throw e;
        }
    }

    public void restoreFromUploaded(String originalName, InputStream data) throws Exception {
        DbConn c = parseJdbcUrl(jdbcUrl);

        String clean = StringUtils.hasText(originalName)
                ? Paths.get(originalName).getFileName().toString()
                : "uploaded.dump";

        Path target = backupsDir().resolve("uploaded_" + System.currentTimeMillis() + "_" + clean).normalize();

        try {
            try (OutputStream os = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                data.transferTo(os);
            }

            restoreByExtension(target);

            audit.logSimple("DB_BACKUP_RESTORE_UPLOAD", Map.of(
                    "db", c.dbName,
                    "file", target.getFileName().toString(),
                    "originalName", clean,
                    "source", "upload"
            ));
        } catch (Exception e) {
            audit.logSimple("DB_BACKUP_RESTORE_UPLOAD_FAILED", Map.of(
                    "db", c.dbName,
                    "file", target.getFileName().toString(),
                    "originalName", clean,
                    "source", "upload",
                    "error", safeMsg(e)
            ));
            throw e;
        }
    }

    public Path getFileForDownload(String fileName) throws IOException {
        return safeResolve(fileName);
    }

    public void deleteBackup(String fileName) throws Exception {
        DbConn c = parseJdbcUrl(jdbcUrl);
        try {
            Path file = safeResolve(fileName);
            Files.deleteIfExists(file);

            audit.logSimple("DB_BACKUP_DELETE", Map.of(
                    "db", c.dbName,
                    "file", fileName
            ));
        } catch (Exception e) {
            audit.logSimple("DB_BACKUP_DELETE_FAILED", Map.of(
                    "db", c.dbName,
                    "file", fileName,
                    "error", safeMsg(e)
            ));
            throw e;
        }
    }

    private void restoreByExtension(Path file) throws Exception {
        DbConn c = parseJdbcUrl(jdbcUrl);

        tryTerminateConnections(c.dbName);

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".dump") || name.endsWith(".backup")) {
            List<String> cmd = new ArrayList<>();
            cmd.add(props.getPgRestore());
            cmd.add("--clean");
            cmd.add("--if-exists");
            cmd.add("-h"); cmd.add(c.host);
            cmd.add("-p"); cmd.add(String.valueOf(c.port));
            cmd.add("-U"); cmd.add(dbUser);
            cmd.add("-d"); cmd.add(c.dbName);
            cmd.add(file.toString());

            runCommand(cmd, Map.of("PGPASSWORD", dbPassword), 900);
            return;
        }

        if (name.endsWith(".sql")) {
            List<String> cmd = new ArrayList<>();
            cmd.add(props.getPsql());
            cmd.add("-h"); cmd.add(c.host);
            cmd.add("-p"); cmd.add(String.valueOf(c.port));
            cmd.add("-U"); cmd.add(dbUser);
            cmd.add("-d"); cmd.add(c.dbName);
            cmd.add("-f"); cmd.add(file.toString());

            runCommand(cmd, Map.of("PGPASSWORD", dbPassword), 900);
            return;
        }

        throw new IllegalArgumentException("Неподдерживаемый формат. Разрешено: .dump/.backup/.sql");
    }

    private Path safeResolve(String fileName) throws IOException {
        Path dir = backupsDir();
        String clean = Paths.get(fileName).getFileName().toString();
        Path file = dir.resolve(clean).normalize();
        if (!file.startsWith(dir)) throw new SecurityException("Некорректный путь файла");
        if (!Files.exists(file)) throw new FileNotFoundException("Файл не найден: " + clean);
        return file;
    }

    private void tryTerminateConnections(String dbName) {
        try {
            jdbc.update("""
                SELECT pg_terminate_backend(pid)
                FROM pg_stat_activity
                WHERE datname = ? AND pid <> pg_backend_pid()
            """, dbName);
        } catch (Exception ignored) {
        }
    }

    private void runCommand(List<String> cmd, Map<String, String> env, int timeoutSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        pb.environment().putAll(env);

        Process p = pb.start();

        String output;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            output = br.lines().collect(Collectors.joining("\n"));
        }

        boolean finished = p.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("Команда превысила таймаут: " + String.join(" ", cmd));
        }

        int code = p.exitValue();
        if (code != 0) {
            throw new RuntimeException("Ошибка выполнения команды (code=" + code + "):\n" + output);
        }
    }

    private DbConn parseJdbcUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("spring.datasource.url пустой");
        }

        String cleaned = url.trim();

        if (cleaned.startsWith("jdbc:")) {
            cleaned = cleaned.substring(5);
        }

        // убрать скрытые/непечатные символы
        cleaned = cleaned.replace("\u200B", "")
                .replace("\uFEFF", "")
                .replace("\u00A0", "");

        // убрать query-параметры типа ?sslmode=prefer
        int q = cleaned.indexOf('?');
        if (q >= 0) {
            cleaned = cleaned.substring(0, q);
        }

        URI uri = URI.create(cleaned);

        String host = uri.getHost();
        int port = (uri.getPort() == -1) ? 5432 : uri.getPort();

        String path = uri.getPath();
        String dbName = (path != null && path.length() > 1) ? path.substring(1) : null;

        if (!StringUtils.hasText(host) || !StringUtils.hasText(dbName)) {
            throw new IllegalArgumentException("Не удалось распарсить spring.datasource.url: " + url);
        }

        return new DbConn(host, port, dbName);
    }

    private String safeMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        if (m.length() > 1200) return m.substring(0, 1200) + "...";
        return m;
    }

    public static class BackupFile {
        private final String name;
        private final String absolutePath;
        private final long sizeBytes;
        private final long modifiedAtMillis;

        public BackupFile(String name, String absolutePath, long sizeBytes, long modifiedAtMillis) {
            this.name = name;
            this.absolutePath = absolutePath;
            this.sizeBytes = sizeBytes;
            this.modifiedAtMillis = modifiedAtMillis;
        }

        public String getName() { return name; }
        public String getAbsolutePath() { return absolutePath; }
        public long getSizeBytes() { return sizeBytes; }
        public long getModifiedAtMillis() { return modifiedAtMillis; }

        public String getSizeMb() {
            if (sizeBytes < 0) return "—";
            double mb = sizeBytes / 1024.0 / 1024.0;
            return String.format(Locale.US, "%.2f", mb);
        }

        public String getModifiedAtPretty() {
            if (modifiedAtMillis <= 0) return "—";
            ZonedDateTime zdt = Instant.ofEpochMilli(modifiedAtMillis).atZone(ZoneId.systemDefault());
            return zdt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        }
    }

    private record DbConn(String host, int port, String dbName) {}
}