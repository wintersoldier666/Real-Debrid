package com.ghostnotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * MainActivity - Shows the list of encrypted notes.
 *
 * Notes are decrypted in-memory for display only.
 * Locking the app clears all decrypted data from memory.
 *
 * Security: Auto-locks when app goes to background.
 */
public class MainActivity extends Activity {

    private ListView listNotes;
    private TextView tvEmpty;
    private NotesAdapter adapter;
    private List<Note> notes = new ArrayList<>();
    private CryptoManager crypto;
    private NoteDatabase db;

    private static final int REQ_EDIT = 1;
    private static final int REQ_NEW = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SECURITY: Block screenshots, screen recording, recent apps thumbnail
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        setContentView(R.layout.activity_main);

        crypto = new CryptoManager(this);
        db = NoteDatabase.getInstance(this);

        listNotes = (ListView) findViewById(R.id.listNotes);
        tvEmpty = (TextView) findViewById(R.id.tvEmpty);

        adapter = new NotesAdapter(this, notes);
        listNotes.setAdapter(adapter);

        // Open note on click
        listNotes.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Note note = notes.get(position);
                openEditor(note.id);
            }
        });

        // Long press to delete
        listNotes.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDelete(position);
                return true;
            }
        });

        // New note FAB
        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNewNote();
            }
        });

        // Lock button
        findViewById(R.id.btnLock).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lockAndExit();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If session was cleared (e.g. locked from another activity), return to login
        if (!crypto.isSessionActive()) {
            goToLogin();
            return;
        }

        loadNotes();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // AUTO-LOCK: Clear all decrypted data from memory when app goes to background
        clearDecryptedData();
        // Lock the crypto session
        crypto.lockSession();
    }

    private void loadNotes() {
        notes.clear();

        List<Note> rawNotes = db.getAllNotes();
        for (Note note : rawNotes) {
            // Decrypt each note's title and content for display
            String title = crypto.decryptNote(note.encryptedTitle);
            String content = crypto.decryptNote(note.encryptedContent);

            if (title != null && content != null) {
                note.displayTitle = title;
                note.displayContent = content;
                notes.add(note);
            }
            // Skip notes that fail to decrypt (shouldn't happen with correct key)
        }

        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
        listNotes.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void clearDecryptedData() {
        for (Note note : notes) {
            // Overwrite decrypted strings (Java GC will collect, but clear refs)
            note.displayTitle = null;
            note.displayContent = null;
        }
        notes.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void openEditor(long noteId) {
        Intent intent = new Intent(this, NoteEditorActivity.class);
        intent.putExtra(NoteEditorActivity.EXTRA_NOTE_ID, noteId);
        startActivityForResult(intent, REQ_EDIT);
    }

    private void openNewNote() {
        Intent intent = new Intent(this, NoteEditorActivity.class);
        startActivityForResult(intent, REQ_NEW);
    }

    private void confirmDelete(final int position) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Note note = notes.get(position);
                        db.deleteNote(note.id);
                        notes.remove(position);
                        adapter.notifyDataSetChanged();
                        tvEmpty.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
                        listNotes.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void lockAndExit() {
        clearDecryptedData();
        crypto.lockSession();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            // Re-load notes after edit/create
            if (crypto.isSessionActive()) {
                loadNotes();
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Back button locks the app
        lockAndExit();
    }
}
