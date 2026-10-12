package com.uprfvx.random.web;

/*----------------------------------------------------------------------------*/
/*--  JobRunner.java - runs a randomization for the web interface.          --*/
/*--                                                                        --*/
/*--  Part of "Pokemon Randomizer YAO", a fork of                           --*/
/*--  "Universal Pokemon Randomizer FVX" (UPR-FVX), licensed under the GPL  --*/
/*--  version 3 or (at your option) any later version. See LICENSE.txt.     --*/
/*----------------------------------------------------------------------------*/

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the program's own command line randomizer in a separate Java process, with the settings written to a file, and
 * collects what it prints. A separate process means a large 3DS game never competes for memory with the web server, a
 * crash cannot take the interface down, and the result is exactly what the command line gives.
 */
final class JobRunner {

    /** One run, as seen by the interface. */
    static final class Job {
        final String id;
        final long startedAt = System.currentTimeMillis();
        private final List<String> lines = new ArrayList<>();
        volatile String status = "running";   // running, done, failed, cancelled
        volatile int exitCode = -1;
        volatile String output;
        volatile String guide;
        volatile String logFile;
        /** Run once when the job succeeded (added 12/10/2026, used to move the log next to a LayeredFS folder). */
        volatile Runnable afterSuccess;
        private volatile Process process;

        Job(String id) {
            this.id = id;
        }

        synchronized void add(String line) {
            if (lines.size() < 20000) {
                lines.add(line);
            }
        }

        synchronized List<String> linesFrom(int from) {
            return new ArrayList<>(lines.subList(Math.min(Math.max(from, 0), lines.size()), lines.size()));
        }

        synchronized int lineCount() {
            return lines.size();
        }
    }

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    Job get(String id) {
        return id == null ? null : jobs.get(id);
    }

    boolean anyRunning() {
        return jobs.values().stream().anyMatch(j -> j.status.equals("running"));
    }

    /**
     * Starts the command line randomizer.
     *
     * @param command the full command, as produced by {@link #command}
     */
    Job start(List<String> command, File workingDirectory, String output, String guide, String logFile) {
        return start(command, workingDirectory, output, guide, logFile, null);
    }

    Job start(List<String> command, File workingDirectory, String output, String guide, String logFile,
              Runnable afterSuccess) {
        Job job = new Job("job" + counter.incrementAndGet());
        job.afterSuccess = afterSuccess;
        job.output = output;
        job.guide = guide;
        job.logFile = logFile;
        jobs.put(job.id, job);
        Thread thread = new Thread(() -> run(job, command, workingDirectory), "randomize-" + job.id);
        thread.setDaemon(true);
        thread.start();
        return job;
    }

    private void run(Job job, List<String> command, File workingDirectory) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            job.process = process;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    job.add(line);
                }
            }
            int code = process.waitFor();
            job.exitCode = code;
            if (job.status.equals("cancelled")) {
                return;
            }
            boolean success = code == 0 && job.linesFrom(0).stream().anyMatch(l -> l.startsWith("Randomized successfully"));
            if (success && job.afterSuccess != null) {
                try {
                    job.afterSuccess.run();
                } catch (RuntimeException e) {
                    job.add("A file could not be moved after the run: " + e);
                }
            }
            job.status = success ? "done" : "failed";
        } catch (IOException | InterruptedException | RuntimeException e) {
            job.add("The randomizer could not be started: " + e);
            job.status = "failed";
        }
    }

    /** Moves a file, replacing the target; a missing source is not an error (the run may not have written it). */
    static void moveFile(File from, File to) {
        if (!from.isFile()) {
            return;
        }
        try {
            java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    boolean cancel(String id) {
        Job job = get(id);
        if (job == null || !job.status.equals("running")) {
            return false;
        }
        job.status = "cancelled";
        Process process = job.process;
        if (process != null) {
            process.destroy();
        }
        job.add("Cancelled.");
        return true;
    }

    /** Runs the sprite export of the command line and returns what it printed; the exit code is in the first line. */
    static List<String> exportSprites(String romPath, String folder, File workingDirectory) {
        List<String> command = new ArrayList<>();
        String javaHome = System.getProperty("java.home", "");
        command.add(javaHome.isEmpty() ? "java" : new File(new File(javaHome, "bin"), "java").getPath());
        command.add("-Xmx2g");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.uprfvx.random.gui.RandomizerGUI");
        command.add("cli");
        command.add("-i");
        command.add(romPath);
        command.add("--export-player-sprites");
        command.add(folder);
        List<String> lines = new ArrayList<>();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            lines.add(0, "exit " + process.waitFor());
        } catch (IOException | InterruptedException e) {
            lines.add(0, "exit -1");
            lines.add("The export could not run: " + e);
        }
        return lines;
    }

    /** The command line that runs the randomizer. */
    static List<String> command(String romPath, String outputPath, String settingsFile, String seed,
                                boolean threeDs, boolean directory, boolean acceptCodePatches, String guidePath,
                                boolean log) {
        List<String> command = new ArrayList<>();
        String javaHome = System.getProperty("java.home", "");
        command.add(javaHome.isEmpty() ? "java" : new File(new File(javaHome, "bin"), "java").getPath());
        command.add("-Xmx" + (threeDs ? "6g" : "2g"));
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("com.uprfvx.random.gui.RandomizerGUI");
        command.add("cli");
        command.add("-i");
        command.add(romPath);
        command.add("-o");
        command.add(outputPath);
        command.add("-s");
        command.add(settingsFile);
        if (seed != null && !seed.isBlank()) {
            command.add("-z");
            command.add(seed);
        }
        // Modified for YAO (12/10/2026): -d (a LayeredFS folder) is now its own decision, since a 3DS game can also
        // be saved as one CXI file.
        if (threeDs && directory) {
            command.add("-d");
        }
        if (log) {
            command.add("-l");
        }
        if (acceptCodePatches) {
            command.add("--accept-code-patches");
        }
        if (guidePath != null && !guidePath.isBlank()) {
            command.add("--html-guide");
            command.add(guidePath);
        }
        return command;
    }
}
