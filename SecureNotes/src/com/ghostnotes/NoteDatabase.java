package com.ghostnotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * NoteDatabase - SQLite storage for encrypted notes.
 *
 * The database stores ONLY encrypted ciphertext - never plaintext.
 * Even if someone extracts the database file, all content is AES-256-GCM encrypted.
 * The database file is stored in private internal storage (inaccessible to other apps
 * without root access).
 */
public class NoteDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "ghost_notes.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE = "notes";
    private static final String COL_ID = "_id";
    private static final String COL_TITLE = "t";       // encrypted title
    private static final String COL_CONTENT = "c";    // encrypted content
    private static final String COL_CREATED = "ca";
    private static final String COL_UPDATED = "ua";

    private static NoteDatabase instance;

    public static synchronized NoteDatabase getInstance(Context ctx) {
        if (instance == null) {
            instance = new NoteDatabase(ctx.getApplicationContext());
        }
        return instance;
    }

    private NoteDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COL_TITLE + " TEXT NOT NULL," +
                COL_CONTENT + " TEXT NOT NULL," +
                COL_CREATED + " INTEGER NOT NULL," +
                COL_UPDATED + " INTEGER NOT NULL" +
                ")");
        // Index for faster ordering by update time
        db.execSQL("CREATE INDEX idx_updated ON " + TABLE + "(" + COL_UPDATED + " DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle schema migrations in future versions
    }

    /**
     * Insert a new note with encrypted title and content.
     * Returns the new note's ID, or -1 on failure.
     */
    public long insertNote(String encryptedTitle, String encryptedContent) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, encryptedTitle);
        cv.put(COL_CONTENT, encryptedContent);
        long now = System.currentTimeMillis();
        cv.put(COL_CREATED, now);
        cv.put(COL_UPDATED, now);
        return db.insert(TABLE, null, cv);
    }

    /**
     * Update an existing note's encrypted content.
     */
    public boolean updateNote(long id, String encryptedTitle, String encryptedContent) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_TITLE, encryptedTitle);
        cv.put(COL_CONTENT, encryptedContent);
        cv.put(COL_UPDATED, System.currentTimeMillis());
        return db.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * Delete a note by ID.
     */
    public boolean deleteNote(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE, COL_ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }

    /**
     * Get all notes ordered by last updated (newest first).
     * Returns notes with encrypted content - caller must decrypt.
     */
    public List<Note> getAllNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE,
                new String[]{COL_ID, COL_TITLE, COL_CONTENT, COL_CREATED, COL_UPDATED},
                null, null, null, null,
                COL_UPDATED + " DESC");

        while (cursor.moveToNext()) {
            Note note = new Note(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getLong(3),
                    cursor.getLong(4)
            );
            notes.add(note);
        }
        cursor.close();
        return notes;
    }

    /**
     * Get a single note by ID.
     */
    public Note getNoteById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE,
                new String[]{COL_ID, COL_TITLE, COL_CONTENT, COL_CREATED, COL_UPDATED},
                COL_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);

        Note note = null;
        if (cursor.moveToFirst()) {
            note = new Note(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getLong(3),
                    cursor.getLong(4)
            );
        }
        cursor.close();
        return note;
    }
}
