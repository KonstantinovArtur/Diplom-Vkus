package com.example.Vkus.web.admin_db;

import com.example.Vkus.service.DbBackupService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
@RequestMapping("/admin-db/backups")
@PreAuthorize("hasRole('DB_ADMIN')")
public class AdminDbBackupController {

    private final DbBackupService backupService;

    public AdminDbBackupController(DbBackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping
    public String page(Model model) throws Exception {
        model.addAttribute("backups", backupService.listBackups());
        return "admin-db/backups";
    }

    @PostMapping("/create")
    public String createBackup(RedirectAttributes ra) {
        try {
            backupService.createCustomBackup();
            ra.addFlashAttribute("ok", "Бэкап создан");
        } catch (Exception e) {
            ra.addFlashAttribute("err", normalizeMsg(e.getMessage()));
        }
        return "redirect:/admin-db/backups";
    }

    @GetMapping("/download/{name}")
    public ResponseEntity<InputStreamResource> download(@PathVariable("name") String name) throws Exception {
        Path file = backupService.getFileForDownload(name);

        InputStream is = Files.newInputStream(file);
        var res = new InputStreamResource(is);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getFileName().toString() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(file))
                .body(res);
    }

    @PostMapping("/restore")
    public String restoreExisting(@RequestParam("name") String name, RedirectAttributes ra) {
        try {
            backupService.restoreFromExisting(name);
            ra.addFlashAttribute("ok", "Восстановление выполнено");
        } catch (Exception e) {
            ra.addFlashAttribute("err", normalizeMsg(e.getMessage()));
        }
        return "redirect:/admin-db/backups";
    }

    @PostMapping("/restore-upload")
    public String restoreUpload(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("err", "Файл не выбран");
            return "redirect:/admin-db/backups";
        }
        try (InputStream is = file.getInputStream()) {
            backupService.restoreFromUploaded(file.getOriginalFilename(), is);
            ra.addFlashAttribute("ok", "Восстановление из файла выполнено");
        } catch (Exception e) {
            ra.addFlashAttribute("err", normalizeMsg(e.getMessage()));
        }
        return "redirect:/admin-db/backups";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("name") String name, RedirectAttributes ra) {
        try {
            backupService.deleteBackup(name);
            ra.addFlashAttribute("ok", "Бэкап удалён");
        } catch (Exception e) {
            ra.addFlashAttribute("err", normalizeMsg(e.getMessage()));
        }
        return "redirect:/admin-db/backups";
    }

    private String normalizeMsg(String msg) {
        if (msg == null || msg.isBlank()) return "Ошибка выполнения операции";
        if (msg.length() > 1500) return msg.substring(0, 1500) + "...";
        return msg;
    }
}