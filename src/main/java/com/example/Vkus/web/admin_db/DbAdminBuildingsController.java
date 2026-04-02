package com.example.Vkus.web.admin_db;

import com.example.Vkus.entity.Building;
import com.example.Vkus.repository.BuildingRepository;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.web.dto.BuildingForm;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin-db/buildings")
public class DbAdminBuildingsController {

    private final BuildingRepository buildingRepository;
    private final AuditLogService audit;

    public DbAdminBuildingsController(BuildingRepository buildingRepository, AuditLogService audit) {
        this.buildingRepository = buildingRepository;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(value = "editId", required = false) Long editId) {

        List<Building> buildings = buildingRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Building::getId))
                .toList();

        model.addAttribute("buildings", buildings);

        BuildingForm form = new BuildingForm();

        if (editId != null) {
            buildingRepository.findById(editId).ifPresent(b -> {
                form.setId(b.getId());
                form.setName(b.getName());
                form.setAddress(b.getAddress());
            });
            model.addAttribute("editing", true);
        } else {
            model.addAttribute("editing", false);
        }

        model.addAttribute("buildingForm", form);
        return "admin-db/buildings";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("buildingForm") BuildingForm form,
                       BindingResult br,
                       RedirectAttributes ra,
                       Model model) {

        if (!br.hasFieldErrors("name")) {
            boolean nameTaken = buildingRepository.findByNameIgnoreCase(form.getName())
                    .filter(existing -> form.getId() == null || !existing.getId().equals(form.getId()))
                    .isPresent();
            if (nameTaken) {
                br.rejectValue("name", "duplicate", "Такое название уже существует");
            }
        }

        if (br.hasErrors()) {
            model.addAttribute("buildings", buildingRepository.findAll().stream()
                    .sorted(Comparator.comparing(Building::getId))
                    .toList());
            model.addAttribute("editing", form.getId() != null);
            return "admin-db/buildings";
        }

        Building b;
        Map<String, Object> before = null;

        if (form.getId() == null) {
            b = new Building();
        } else {
            b = buildingRepository.findById(form.getId()).orElseThrow();
            before = snapshotBuilding(b);
        }

        b.setName(form.getName().trim());
        b.setAddress(form.getAddress() == null ? null : form.getAddress().trim());

        buildingRepository.save(b);

        Map<String, Object> after = snapshotBuilding(b);

        if (form.getId() == null) {
            audit.log("BUILDING_CREATE", "building", b.getId(), Map.of("after", after));
            ra.addFlashAttribute("msg", "Здание добавлено");
        } else {
            audit.log("BUILDING_UPDATE", "building", b.getId(), Map.of("before", before, "after", after));
            ra.addFlashAttribute("msg", "Здание обновлено");
        }

        return "redirect:/admin-db/buildings";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Building b = buildingRepository.findById(id).orElse(null);
        if (b == null) {
            ra.addFlashAttribute("msg", "Здание уже удалено");
            return "redirect:/admin-db/buildings";
        }

        Map<String, Object> before = snapshotBuilding(b);

        try {
            buildingRepository.deleteById(id);
            audit.log("BUILDING_DELETE", "building", id, Map.of("before", before));
            ra.addFlashAttribute("msg", "Здание удалено");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("msg", "Нельзя удалить: здание используется (есть буфеты)");
        }

        return "redirect:/admin-db/buildings";
    }

    private Map<String, Object> snapshotBuilding(Building b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("name", b.getName());
        m.put("address", b.getAddress());
        return m;
    }
}