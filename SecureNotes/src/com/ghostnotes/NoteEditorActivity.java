package com.ghostnotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

/**
 * NoteEditorActivity - Create or edit a single note.
 *
 * Content is decrypted into memory only while this activity is active.
 * On pause/destroy, memory is cleared and the session is locked.
 * All saves encrypt content before storing to database.
 */
public class NoteEditorActivity extends Activity {

    public static final String EXTRA_NOTE_ID = "note_id";
    private static final long NO_NOTE = -1L;

    private EditText etTitle;
    private EditText etContent;
    private ImageButton btnDelete;

    private CryptoManager crypto;
    private NoteDatabase db;
    private long noteId = NO_NOTE;
    private boolean hasUnsavedChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SECURITY: Prevent screenshots and screen recording
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        setContentView(R.layout.activity_editor);

        crypto = new CryptoManager(this);
        db = NoteDatabase.getInstance(this);

        etTitle = (EditText) findViewById(R.id.etTitle);
        etContent = (EditText) findViewById(R.id.etContent);
        btnDelete = (ImageButton) findViewById(R.id.btnDelete);

        // Back button
        ((ImageButton) findViewById(R.id.btnBack)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleBack();
            }
        });

        // Save button
        ((ImageButton) findViewById(R.id.btnSave)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveNote();
            }
        });

        // Delete button
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        });

        // Load existing note if editing
        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, NO_NOTE);
        if (noteId != NO_NOTE) {
            loadNote(noteId);
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            // New note
            etTitle.requestFocus();
        }
    }

    private void loadNote(long id) {
        if (!crypto.isSessionActive()) {
            finish();
            return;
        }

        Note note = db.getNoteById(id);
        if (note == null) {
            finish();
            return;
        }

        String title = crypto.decryptNote(note.encryptedTitle);
        String content = crypto.decryptNote(note.encryptedContent);

        if (title == null || content == null) {
            Toast.makeText(this, R.string.encryption_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etTitle.setText(title);
        etContent.setText(content);
        // Move cursor to end of content
        etContent.setSelection(etContent.length());
    }

    private void saveNote() {
        if (!crypto.isSessionActive()) {
            finish();
            return;
        }

        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString();

        if (title.isEmpty() && content.trim().isEmpty()) {
            // Empty note - don't save
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // Encrypt both title and content
        String encTitle = crypto.encryptNote(title.isEmpty() ? "(untitled)" : title);
        String encContent = crypto.encryptNote(content);

        if (encTitle == null || encContent == null) {
            Toast.makeText(this, R.string.encryption_error, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success;
        if (noteId == NO_NOTE) {
            long newId = db.insertNote(encTitle, encContent);
            success = newId != -1;
            if (success) noteId = newId;
        } else {
            success = db.updateNote(noteId, encTitle, encContent);
        }

        if (success) {
            hasUnsavedChanges = false;
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, R.string.encryption_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (noteId != NO_NOTE) {
                            db.deleteNote(noteId);
                        }
                        clearEditorMemory();
                        setResult(RESULT_OK);
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void handleBack() {
        // Auto-save on back if there's content
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        if (!title.isEmpty() || !content.isEmpty()) {
            saveNote();
        } else {
            setResult(RESULT_CANCELED);
            clearEditorMemory();
            finish();
        }
    }

    /**
     * Securely clear decrypted content from editor memory
     */
    private void clearEditorMemory() {
        etTitle.setText("");
        etContent.setText("");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Clear displayed plaintext when app goes to background
        clearEditorMemory();
        // Lock the session - user must re-authenticate
        crypto.lockSession();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }
}
