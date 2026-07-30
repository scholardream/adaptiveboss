package com.scholardream.adaptiveboss.log;

import com.scholardream.adaptiveboss.AdaptiveBossMod;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Async NDJSON writer for fight logs.
 *
 * <p>Threading contract mirrors {@code BridgeClient}: the game thread only
 * offers immutable log lines to a queue and never touches the filesystem. A
 * single daemon thread drains the queue in batches, keeps one
 * {@link BufferedWriter} per open fight file, flushes after every batch, and
 * closes a file when the fight's {@code close} marker arrives. All lines for
 * one fight come from the server thread, so per-file ordering is preserved.
 */
public final class FightLogWriter {
    private static final int DRAIN_LIMIT = 256;

    private record Entry(Path file, String line, boolean closeMarker) {
    }

    private static final BlockingQueue<Entry> QUEUE = new LinkedBlockingQueue<>();
    private static final Map<Path, BufferedWriter> OPEN_FILES = new HashMap<>();

    private static volatile boolean running = false;
    private static Thread thread;

    private FightLogWriter() {
    }

    /** Enqueue one NDJSON line. Starts the writer thread lazily. Never blocks the game thread. */
    public static void submit(Path file, String line) {
        ensureStarted();
        QUEUE.offer(new Entry(file, line, false));
    }

    /** Enqueue an end-of-fight marker: everything before it is flushed, then the file is closed. */
    public static void closeFile(Path file) {
        ensureStarted();
        QUEUE.offer(new Entry(file, null, true));
    }

    /** Drain and close everything; called when the server stops. */
    public static synchronized void shutdown() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    private static synchronized void ensureStarted() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(FightLogWriter::runLoop, "AdaptiveBoss-FightLogWriter");
        thread.setDaemon(true);
        thread.start();
    }

    private static void runLoop() {
        List<Entry> batch = new ArrayList<>();
        while (running || !QUEUE.isEmpty()) {
            try {
                Entry first = QUEUE.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue; // idle heartbeat: re-check running
                }
                batch.add(first);
                QUEUE.drainTo(batch, DRAIN_LIMIT - 1);
                process(batch);
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                AdaptiveBossMod.LOGGER.warn("[AdaptiveBoss] fight log writer error", e);
            }
        }
        // final drain on the way out, then close every file still open
        QUEUE.drainTo(batch);
        try {
            process(batch);
        } catch (Exception e) {
            AdaptiveBossMod.LOGGER.warn("[AdaptiveBoss] fight log writer error during shutdown", e);
        }
        closeAll();
    }

    private static void process(List<Entry> batch) throws IOException {
        for (Entry entry : batch) {
            if (entry.closeMarker()) {
                BufferedWriter writer = OPEN_FILES.remove(entry.file());
                if (writer != null) {
                    writer.flush();
                    writer.close();
                }
                continue;
            }
            BufferedWriter writer = OPEN_FILES.computeIfAbsent(entry.file(), FightLogWriter::openFile);
            if (writer != null) {
                writer.write(entry.line());
                writer.newLine();
            }
        }
        for (BufferedWriter writer : OPEN_FILES.values()) {
            writer.flush();
        }
    }

    private static BufferedWriter openFile(Path file) {
        try {
            Files.createDirectories(file.getParent());
            return Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            AdaptiveBossMod.LOGGER.warn("[AdaptiveBoss] cannot open fight log {}", file, e);
            return null;
        }
    }

    private static void closeAll() {
        for (BufferedWriter writer : OPEN_FILES.values()) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException ignored) {
            }
        }
        OPEN_FILES.clear();
    }
}
