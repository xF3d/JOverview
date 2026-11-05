package translate.dummy;

import ai.reponse.ApplicableDiff;
import file.FileLogger;
import file.FolderSync;
import gui.frame.CompareFrame;
import gui.frame.MainFrame;
import translate.translator.ComplexityTranslator;
import translate.translator.SimpleTranslator;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DummyProject {
    private final Path dummyLocation;
    private CompletableFuture<Void> copyingTask;
    private SimpleTranslator dummyTranslator = null;
    private final ComplexityTranslator originalTranslator;

    public DummyProject(Path dummyLocation, ComplexityTranslator originalTranslator) {
        this.dummyLocation = dummyLocation;
        this.originalTranslator = originalTranslator;

        recopy();
    }

    public DummyProject(ComplexityTranslator originalTranslator) {
        this(getTempProject(), originalTranslator);
    }

    synchronized public void showDiffs(List<? extends ApplicableDiff> diffs) {
        init();

        for (var diff : diffs) {
            diff.apply(dummyTranslator, diff.getPath(dummyTranslator));
        }

        FileLogger.LOGGER.info("Applied diffs to dummy project");

        ComplexityTranslator tempTranslator = new ComplexityTranslator();
        tempTranslator.execute(dummyLocation.toFile());

        FileLogger.LOGGER.info("Calculating new metrics of dummy project");

        new CompareFrame(originalTranslator, tempTranslator, diffs.size());

        recopy();
    }

    public void applyDiffs(List<? extends ApplicableDiff> diffs) {
        init();

        for (var diff : diffs) {
            diff.apply(originalTranslator, diff.getPath(originalTranslator));
        }

        FileLogger.LOGGER.info("Applied diffs to original project");
    }

    private void init() {
        if (dummyTranslator != null) return;

        try {
            copyingTask.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        this.dummyTranslator = new SimpleTranslator();
        this.dummyTranslator.execute(dummyLocation.toFile());
        FileLogger.LOGGER.info("Initialized translator for dummy project");
    }

    private void recopy() {
        File mainProject = MainFrame.getSelectedDirectory();

        dummyTranslator = null;
        try (ExecutorService exe = Executors.newVirtualThreadPerTaskExecutor()) {
            copyingTask = CompletableFuture.runAsync(
                    () -> {
                        try {
                            FolderSync.sync(mainProject.toPath(), dummyLocation);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, exe
            );
        }
    }

    public static Path getTempProject() {
        Path folder = Paths.get(
                System.getProperty("user.home"),
                "JOverview",
                "tempproject"
        );

        try {
            Files.createDirectories(folder); // creates it if missing
        } catch (IOException e) {
            e.printStackTrace();
        }

        return folder;
    }
}
