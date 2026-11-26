package com.spectra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateIncidentNoteRequest {

    @NotBlank(message = "Author is required")
    @Size(max = 100, message = "Author must not exceed 100 characters")
    private String author;

    @NotBlank(message = "Note text is required")
    @Size(max = 2000, message = "Note text must not exceed 2000 characters")
    private String noteText;

    public CreateIncidentNoteRequest() {
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getNoteText() {
        return noteText;
    }

    public void setNoteText(String noteText) {
        this.noteText = noteText;
    }
}
