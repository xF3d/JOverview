package file;

import translate.TranslatorsConfig;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

public class DirectoryExplorer {

    private final FileHandler fileHandler;

    public DirectoryExplorer (FileHandler fileHandler){
        this.fileHandler=fileHandler;
    }


    private static final FileFilter fileFilter = pathname -> pathname.toString().endsWith(".java");

    public void explore(File root) {
        Deque<File> stack = new ArrayDeque<>();
        stack.push(root);

        TranslatorsConfig cfg = Config.getInstance().getConfig();
        ArrayList<File> filesFound = new ArrayList<>();
        while (!stack.isEmpty()) {
            File file = stack.pop();
            if (!file.isDirectory()) {
                FileLogger.LOGGER.info("Adding file: " + file);
                filesFound.add(file);
                continue;
            }


            if (cfg.isSkipDir(file)) continue;
            File[] files = file.listFiles();
            if (files == null) {
                continue;
            }

            for (File f : files) {
                if (f.isDirectory() || fileFilter.accept(f)) {
                    stack.push(f);
                }
            }
        }

        fileHandler.handle(filesFound);
    }
}
