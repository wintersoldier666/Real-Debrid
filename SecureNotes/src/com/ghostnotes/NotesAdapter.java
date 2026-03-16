package com.ghostnotes;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesAdapter extends ArrayAdapter<Note> {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault());

    public NotesAdapter(Context context, List<Note> notes) {
        super(context, 0, notes);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_note, parent, false);
            holder = new ViewHolder();
            holder.tvTitle = (TextView) convertView.findViewById(R.id.tvTitle);
            holder.tvPreview = (TextView) convertView.findViewById(R.id.tvPreview);
            holder.tvDate = (TextView) convertView.findViewById(R.id.tvDate);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Note note = getItem(position);
        if (note != null) {
            // Display decrypted title (already decrypted by MainActivity)
            String title = note.displayTitle;
            if (title == null || title.trim().isEmpty()) {
                title = "(no title)";
            }
            holder.tvTitle.setText(title);

            // Show content preview
            String content = note.displayContent;
            if (content == null || content.trim().isEmpty()) {
                holder.tvPreview.setText("(empty note)");
            } else {
                holder.tvPreview.setText(content);
            }

            holder.tvDate.setText(dateFormat.format(new Date(note.updatedAt)));
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView tvTitle;
        TextView tvPreview;
        TextView tvDate;
    }
}
