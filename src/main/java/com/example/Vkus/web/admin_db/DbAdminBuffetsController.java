package com.example.Vkus.web.admin_db;

import com.example.Vkus.entity.Buffet;
import com.example.Vkus.entity.Building;
import com.example.Vkus.repository.BuffetRepository;
import com.example.Vkus.repository.BuildingRepository;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.web.dto.BuffetForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin-db/buffets")
public class DbAdminBuffetsController {

    private final BuffetRepository buffetRepository;
    private final BuildingRepository buildingRepository;
    private final AuditLogService audit;

    public DbAdminBuffetsController(BuffetRepository buffetRepository,
                                    BuildingRepository buildingRepository,
                                    AuditLogService audit) {
        this.buffetRepository = buffetRepository;
        this.buildingRepository = buildingRepository;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(value = "edit", required = false) Long editId) {

        List<Buffet> buffets = buffetRepository.findAllWithBuildingOrdered();
        List<Building> buildings = buildingRepository.findAllByOrderByNameAsc();

        model.addAttribute("buffets", buffets);
        model.addAttribute("buildings", buildings);

        BuffetForm form = new BuffetForm();
        if (editId != null) {
            Buffet b = buffetRepository.findById(editId).orElse(null);
            if (b != null) {
                form.setId(b.getId());
                form.setBuildingId(b.getBuilding().getId());
                form.setName(b.getName());
                form.setIsActive(Boolean.TRUE.equals(b.getIsActive()));
                form.setOpenTime(b.getOpenTime() != null ? b.getOpenTime().toString() : null);
                form.setCloseTime(b.getCloseTime() != null ? b.getCloseTime().toString() : null);
            }
        } else {
            // можно поставить здание по умолчанию, если есть
            if (!buildings.isEmpty()) form.setBuildingId(buildings.get(0).getId());
        }

        model.addAttribute("form", form);
        model.addAttribute("editId", editId);

        return "admin-db/buffets";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") BuffetForm form,
                       BindingResult br,
                       Model model,
                       RedirectAttributes ra) {

        // чтобы страница могла перерисоваться при ошибках
        List<Building> buildings = buildingRepository.findAllByOrderByNameAsc();
        model.addAttribute("buildings", buildings);
        model.addAttribute("buffets", buffetRepository.findAllWithBuildingOrdered());

        // 1) Здание существует?
        Building building = null;
        if (form.getBuildingId() != null) {
            building = buildingRepository.findById(form.getBuildingId()).orElse(null);
        }
        if (building == null) {
            br.rejectValue("buildingId", "buildingId.invalid", "Выберите корректное здание");
        }

        // 2) Уникальность (building_id, name)
        String name = form.getName() == null ? null : form.getName().trim();
        if (building != null && name != null && !name.isBlank()) {
            boolean exists = (form.getId() == null)
                    ? buffetRepository.existsByBuilding_IdAndNameIgnoreCase(building.getId(), name)
                    : buffetRepository.existsByBuilding_IdAndNameIgnoreCaseAndIdNot(building.getId(), name, form.getId());

            if (exists) {
                br.rejectValue("name", "name.duplicate", "В этом здании уже есть буфет с таким названием");
            }
        }

        // 3) Парсинг времени + логика
        LocalTime open = parseTime(form.getOpenTime(), br, "openTime", "Некорректное время открытия");
        LocalTime close = parseTime(form.getCloseTime(), br, "closeTime", "Некорректное время закрытия");

        // если оба заданы — можно проверять порядок
        if (open != null && close != null) {
            // если у тебя бывают ночные смены (22:00-06:00), то убери эту проверку
            if (!close.isAfter(open)) {
                br.rejectValue("closeTime", "closeTime.invalid", "Закрытие должно быть позже открытия");
            }
        }

        Buffet buffet;
        Map<String, Object> before = null;

        if (form.getId() == null) {
            buffet = new Buffet();
        } else {
            buffet = buffetRepository.findById(form.getId()).orElse(null);
            if (buffet == null) {
                br.reject("notFound", "Буфет не найден");
                model.addAttribute("editId", form.getId());
                return "admin-db/buffets";
            }
            before = snapshotBuffet(buffet);
        }

        if (br.hasErrors()) {
            model.addAttribute("editId", form.getId());
            return "admin-db/buffets";
        }

        boolean isCreate = (form.getId() == null);

        buffet.setBuilding(building);
        buffet.setName(name);
        buffet.setIsActive(form.getIsActive() != null ? form.getIsActive() : Boolean.FALSE);
        buffet.setOpenTime(open);
        buffet.setCloseTime(close);

        buffetRepository.save(buffet);

        Map<String, Object> after = snapshotBuffet(buffet);

        if (isCreate) {
            audit.log("BUFFET_CREATE", "buffet", buffet.getId(), Map.of(
                    "after", after
            ));
            ra.addFlashAttribute("ok", "Буфет добавлен");
        } else {
            audit.log("BUFFET_UPDATE", "buffet", buffet.getId(), Map.of(
                    "before", before,
                    "after", after
            ));
            ra.addFlashAttribute("ok", "Изменения сохранены");
        }

        return "redirect:/admin-db/buffets";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Buffet b = buffetRepository.findById(id).orElse(null);
        if (b != null) {
            Map<String, Object> before = snapshotBuffet(b);
            buffetRepository.deleteById(id);

            audit.log("BUFFET_DELETE", "buffet", id, Map.of(
                    "before", before
            ));

            ra.addFlashAttribute("ok", "Буфет удалён");
        }
        return "redirect:/admin-db/buffets";
    }

    private LocalTime parseTime(String raw, BindingResult br, String field, String msg) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalTime.parse(raw.trim());
        } catch (DateTimeParseException e) {
            br.rejectValue(field, field + ".invalid", msg);
            return null;
        }
    }

    private Map<String, Object> snapshotBuffet(Buffet b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("buildingId", b.getBuilding() != null ? b.getBuilding().getId() : null);
        m.put("buildingName", b.getBuilding() != null ? b.getBuilding().getName() : null);
        m.put("name", b.getName());
        m.put("isActive", b.getIsActive());
        m.put("openTime", b.getOpenTime() != null ? b.getOpenTime().toString() : null);
        m.put("closeTime", b.getCloseTime() != null ? b.getCloseTime().toString() : null);
        return m;
    }
}