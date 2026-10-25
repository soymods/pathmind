package com.pathmind.compat;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * SDL3-backed replacement for the LWJGL tinyfd dialogs, which Minecraft 26.3 no
 * longer ships. Keeps the blocking contract the workspace import/export
 * controller already relies on while presenting the tinyfd method shapes.
 */
public final class SdlFileDialogs {
    private static final long DIALOG_TIMEOUT_MINUTES = 5L;
    // LWJGL callbacks and filter buffers must outlive the native dialog.
    private static final List<Object> RETAINED = new CopyOnWriteArrayList<>();

    private SdlFileDialogs() {
    }

    public static String tinyfd_openFileDialog(CharSequence title, CharSequence defaultPathAndFile,
            PointerBuffer filterPatterns, CharSequence singleFilterDescription, boolean allowMultipleSelects) {
        return show(defaultPathAndFile, singleFilterDescription, false, allowMultipleSelects);
    }

    public static String tinyfd_saveFileDialog(CharSequence title, CharSequence defaultPathAndFile,
            PointerBuffer filterPatterns, CharSequence singleFilterDescription) {
        return show(defaultPathAndFile, singleFilterDescription, true, false);
    }

    private static String show(CharSequence defaultPathAndFile, CharSequence filterDescription, boolean save,
            boolean allowMultiple) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> selection = new AtomicReference<>();
        AtomicReference<SDL_DialogFileCallback> callbackRef = new AtomicReference<>();
        AtomicReference<SDL_DialogFileFilter.Buffer> filtersRef = new AtomicReference<>();
        Runnable request = () -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(2);
                filtersRef.set(filters);
                if (filterDescription != null) {
                    filters.get(0).name(stack.UTF8(filterDescription.toString()));
                    filters.get(0).pattern(stack.UTF8("json"));
                }
                SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
                    if (filelist != 0L) {
                        long first = MemoryUtil.memGetAddress(filelist);
                        if (first != 0L) {
                            selection.set(MemoryUtil.memUTF8(first));
                        }
                    }
                    latch.countDown();
                });
                callbackRef.set(callback);
                long window = windowHandle();
                String defaultLocation = defaultLocation(defaultPathAndFile);
                if (save) {
                    SDLDialog.SDL_ShowSaveFileDialog(callback, 0L, window, filters, defaultLocation);
                } else {
                    SDLDialog.SDL_ShowOpenFileDialog(callback, 0L, window, filters, defaultLocation, allowMultiple);
                }
            } catch (Throwable error) {
                latch.countDown();
            }
        };
        Minecraft client = Minecraft.getInstance();
        if (client != null && !client.isSameThread()) {
            client.execute(request);
        } else {
            request.run();
        }
        try {
            latch.await(DIALOG_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        boolean completed = latch.getCount() == 0L;
        release(callbackRef.get(), completed);
        release(filtersRef.get(), completed);
        return selection.get();
    }

    private static void release(Object resource, boolean completed) {
        if (resource == null) {
            return;
        }
        if (!completed) {
            RETAINED.add(resource);
        } else if (resource instanceof SDL_DialogFileCallback callback) {
            callback.free();
        } else if (resource instanceof SDL_DialogFileFilter.Buffer filters) {
            filters.free();
        }
    }

    private static long windowHandle() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.getWindow() != null ? client.getWindow().handle() : 0L;
    }

    private static String defaultLocation(CharSequence defaultPathAndFile) {
        if (defaultPathAndFile == null) {
            return null;
        }
        File file = new File(defaultPathAndFile.toString());
        File directory = file.isDirectory() ? file : file.getParentFile();
        return directory != null ? directory.getAbsolutePath() : file.getAbsolutePath();
    }
}
