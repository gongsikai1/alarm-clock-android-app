package com.doubao.audioalarm.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 通过 SAF（DocumentsContract）递归扫描用户选定文件夹内的音频文件 */
public class AudioScanner {

    public static final String[] AUDIO_EXT = {
            "mp3", "m4a", "aac", "wav", "ogg", "oga", "flac", "opus", "amr", "wma", "mid", "midi", "mp4", "3gp"
    };

    public static class AudioItem {
        public String uri;
        public String name;
        public long size;
    }

    /** 返回文件夹显示名 */
    public static String folderName(Context ctx, Uri treeUri) {
        try {
            String docId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
            Cursor c = ctx.getContentResolver().query(docUri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        String n = c.getString(0);
                        if (n != null && !n.isEmpty()) return n;
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }
        String path = treeUri.getLastPathSegment();
        return path == null ? "已选文件夹" : path;
    }

    public static List<AudioItem> scan(Context ctx, Uri treeUri, int maxCount) {
        List<AudioItem> out = new ArrayList<>();
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            walk(ctx, treeUri, rootId, 0, out, maxCount);
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void walk(Context ctx, Uri treeUri, String parentDocId, int depth,
                             List<AudioItem> out, int maxCount) {
        if (amp(out) >= maxCount || depth > 4) return;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId);
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(childrenUri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
            }, null, null, null);
            if (c == null) return;
            while (c.moveToNext() && amp(out) < maxCount) {
                String docId = c.getString(0);
                String name = c.getString(1) == null ? "" : c.getString(1);
                String mime = c.getString(2) == null ? "" : c.getString(2);
                long size = c.isNull(3) ? 0 : c.getLong(3);

                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    walk(ctx, treeUri, docId, depth + 1, out, maxCount);
                } else if (mime.startsWith("audio/") || isAudioName(name)) {
                    AudioItem item = new AudioItem();
                    item.uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString();
                    item.name = name;
                    item.size = size;
                    out.add(item);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    private static int amp(List<AudioItem> out) {
        return out.size();
    }

    private static boolean isAudioName(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (String e : AUDIO_EXT) if (e.equals(ext)) return true;
        return false;
    }
}
