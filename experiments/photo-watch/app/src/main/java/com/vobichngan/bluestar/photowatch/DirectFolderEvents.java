package com.vobichngan.bluestar.photowatch;

import android.net.Uri;
import android.os.Environment;
import android.os.FileObserver;
import android.os.SystemClock;
import android.provider.DocumentsContract;

import java.io.File;

/**
 * Best-effort direct native Linux filesystem events, only for the exact
 * user-selected primary:DCIM/Camera folder.
 *
 * SAF grants *URI* access, not raw filesystem access. On some Android 16
 * devices FileObserver may receive no events due to scoped-storage/FUSE.
 * The service always retains a SAF/MediaStore fallback; never infer that
 * startWatching() means the kernel delivered any event.
 *
 * Never opens file content or requires MANAGE_EXTERNAL_STORAGE/Root.
 */
public final class DirectFolderEvents {
    public interface Callback {
        void onImageFileEvent(String name, long eventElapsedMillis);
    }

    private final FileObserver observer;
    private final String directory;

    private DirectFolderEvents(FileObserver observer, String directory) {
        this.observer = observer;
        this.directory = directory;
    }

    public static DirectFolderEvents start(Uri tree, Callback onFile) {
        try {
            String documentId = DocumentsContract.getTreeDocumentId(tree);
            // Do not convert arbitrary SAF IDs to filesystem paths.
            if (!"primary:DCIM/Camera".equalsIgnoreCase(documentId)) return null;
            File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            File camera = new File(dcim, "Camera");
            if (!camera.isDirectory()) return null;
            FileObserver watcher = new FileObserver(camera,
                    FileObserver.CREATE | FileObserver.MOVED_TO |
                    FileObserver.MODIFY | FileObserver.CLOSE_WRITE) {
                @Override public void onEvent(int event, String path) {
                    if (path == null || path.isEmpty()) return;
                    int type = event & FileObserver.ALL_EVENTS;
                    if ((type & (FileObserver.CREATE | FileObserver.MOVED_TO |
                            FileObserver.MODIFY | FileObserver.CLOSE_WRITE)) == 0) return;
                    try {
                        onFile.onImageFileEvent(path, SystemClock.elapsedRealtime());
                    } catch (RuntimeException ignored) {
                        // FileObserver callbacks must never throw on its shared thread.
                    }
                }
            };
            watcher.startWatching();
            return new DirectFolderEvents(watcher, camera.getAbsolutePath());
        } catch (RuntimeException | SecurityException ignored) {
            return null;
        }
    }

    public String directory() { return directory; }

    public void stop() { observer.stopWatching(); }
}
