package com.pathmind.data;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Runs workspace import/export file I/O away from the client thread.
 */
public final class WorkspaceFileAccess {
    private static final ExecutorService FILE_IO_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Pathmind-Workspace-File-IO");
        thread.setDaemon(true);
        return thread;
    });
    private static final ReentrantLock EXPORT_LOCK = new ReentrantLock();

    private WorkspaceFileAccess() {
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, FILE_IO_EXECUTOR);
    }

    public static <T> CompletableFuture<T> supplyExportAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            EXPORT_LOCK.lock();
            try {
                return supplier.get();
            } finally {
                EXPORT_LOCK.unlock();
            }
        }, FILE_IO_EXECUTOR);
    }
}
