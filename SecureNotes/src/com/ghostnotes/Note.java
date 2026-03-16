package com.ghostnotes;

/**
 * Note model - all fields are stored encrypted in the database.
 * encryptedTitle and encryptedContent are Base64-encoded AES-256-GCM ciphertext.
 * displayTitle and displayContent are decrypted in-memory only, never persisted.
 */
public class Note {
    public long id;
    public String encryptedTitle;
    public String encryptedContent;
    public long createdAt;
    public long updatedAt;

    // In-memory only - decrypted view, never stored
    public transient String displayTitle;
    public transient String displayContent;

    public Note() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Note(long id, String encTitle, String encContent, long created, long updated) {
        this.id = id;
        this.encryptedTitle = encTitle;
        this.encryptedContent = encContent;
        this.createdAt = created;
        this.updatedAt = updated;
    }
}
