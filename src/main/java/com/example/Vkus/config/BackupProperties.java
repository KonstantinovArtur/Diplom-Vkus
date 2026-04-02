package com.example.Vkus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vkus.backup")
public class BackupProperties {
    private String dir = "backups";
    private String pgDump = "pg_dump";
    private String pgRestore = "pg_restore";
    private String psql = "psql";

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }

    public String getPgDump() { return pgDump; }
    public void setPgDump(String pgDump) { this.pgDump = pgDump; }

    public String getPgRestore() { return pgRestore; }
    public void setPgRestore(String pgRestore) { this.pgRestore = pgRestore; }

    public String getPsql() { return psql; }
    public void setPsql(String psql) { this.psql = psql; }
}