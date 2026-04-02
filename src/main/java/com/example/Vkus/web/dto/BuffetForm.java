package com.example.Vkus.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class BuffetForm {

    private Long id;

    @NotNull(message = "Выберите здание")
    private Long buildingId;

    @NotBlank(message = "Введите название буфета")
    @Size(max = 255, message = "Название слишком длинное")
    private String name;

    private Boolean isActive = true;

    // "HH:mm" из <input type="time">
    private String openTime;
    private String closeTime;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBuildingId() { return buildingId; }
    public void setBuildingId(Long buildingId) { this.buildingId = buildingId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }

    public String getOpenTime() { return openTime; }
    public void setOpenTime(String openTime) { this.openTime = openTime; }

    public String getCloseTime() { return closeTime; }
    public void setCloseTime(String closeTime) { this.closeTime = closeTime; }
}
