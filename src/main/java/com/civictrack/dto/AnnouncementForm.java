package com.civictrack.dto;

import com.civictrack.enums.AnnouncementSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class AnnouncementForm {

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title is too long")
    private String title;

    @NotBlank(message = "Message is required")
    @Size(max = 1000, message = "Message is too long")
    private String message;

    @NotNull(message = "Choose a type")
    private AnnouncementSeverity severity = AnnouncementSeverity.INFO;

    /** Optional: the announcement disappears after the end of this day. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiresOn;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public AnnouncementSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AnnouncementSeverity severity) {
        this.severity = severity;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(LocalDate expiresOn) {
        this.expiresOn = expiresOn;
    }
}
