package gui.frame;

import ai.AIManager;
import ai.reponse.ApplicableDiff;
import ai.reponse.DiffClassResponse;
import ai.reponse.DiffMethodResponse;
import file.FileLogger;
import gui.GuiUtils;
import gui.components.PromptResponsePanel;
import serialization.ApplicableDiffTypeList;
import serialization.JacksonMapper;
import translate.component.formatter.MemberFormatter;
import translate.dummy.DummyProject;
import translate.structure.complexity.ComplexityClass;
import translate.structure.complexity.ComplexityMethod;
import translate.translator.ComplexityTranslator;
import util.VirtualThreadExecutor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AIRefactorFrame extends JFrame {
    private final ComplexityTranslator complexityTranslator;
    private ExecutorService aiExecutor;

    private final JLabel correctCounter;
    private final JPanel cards;

    private int currentCard = 0;
    private final AtomicInteger cardSize = new AtomicInteger(0);

    private ArrayList<ApplicableDiff> responses = new ArrayList<>();
    private boolean[] selected = new boolean[0];
    private int correctAmount = 0;

    private final AtomicInteger generation = new AtomicInteger(0);
    private DummyProject dummyProject;
    private ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    public AIRefactorFrame(ComplexityTranslator translator) {
        super("AI Refactoring");
        super.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        super.setSize(1000, 600);

        AIManager.init();

        this.complexityTranslator = translator;
        this.dummyProject = new DummyProject(complexityTranslator);
        this.aiExecutor = VirtualThreadExecutor.create();

        CardLayout cardLayout = new CardLayout();
        this.correctCounter = new JLabel();
        this.cards = new JPanel(cardLayout);
        setupKeys(cards, cardLayout);
        setupMenu();

        super.add(correctCounter, BorderLayout.NORTH);
        super.add(cards, BorderLayout.CENTER);
        super.setVisible(true);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                aiExecutor.shutdownNow();
                backgroundExecutor.shutdownNow();
            }
        });
        FrameManager.addFrame(this);
        updateCounter();
    }

    private void setupKeys(JPanel cards, CardLayout layout) {
        JRootPane root = this.getRootPane();

        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
        am.put("next", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int cs = cardSize.get();
                if (cs == 0) return;
                currentCard = (currentCard + 1) % cs;
                layout.show(cards, String.valueOf(currentCard + 1));
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "previous");
        am.put("previous", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int cs = cardSize.get();
                if (cs == 0) return;
                currentCard = (currentCard - 1 + cs) % cs;
                layout.show(cards, String.valueOf(currentCard + 1));
            }
        });
    }

    private void setupMenu() {
        JMenuBar menuBar = new JMenuBar();

        menuBar.add(setupPromptMenu());
        menuBar.add(setupSaveMenu());
        menuBar.add(setupShowMenu());
        menuBar.add(setupApproveMenu());

        this.setJMenuBar(menuBar);
    }

    private JMenu setupPromptMenu() {
        JMenu promptsMenu = new JMenu("Prompts");

        JMenuItem refactorMethods = new JMenuItem("Refactor Methods");
        refactorMethods.addActionListener(this::refactorMethods);

        JMenuItem refactorClasses = new JMenuItem("Refactor Classes");
        refactorClasses.addActionListener(this::refactorClasses);


        JMenuItem resetPrompt = new JMenuItem("Reset");
        resetPrompt.addActionListener(this::reset);

        promptsMenu.add(refactorMethods);
        promptsMenu.add(refactorClasses);
        promptsMenu.add(resetPrompt);

        return promptsMenu;
    }

    private JMenu setupSaveMenu() {
        JMenu saveMenu = new JMenu("Save");

        JMenuItem exportAllPrompt = new JMenuItem("Export All");
        exportAllPrompt.addActionListener((_) -> export(true));

        JMenuItem exportSelectedPrompt = new JMenuItem("Export Selected");
        exportSelectedPrompt.addActionListener((_) -> export(false));

        JMenuItem importPrompt = new JMenuItem("Import");
        importPrompt.addActionListener(this::importAction);

        saveMenu.add(exportAllPrompt);
        saveMenu.add(exportSelectedPrompt);
        saveMenu.add(importPrompt);

        return saveMenu;
    }

    private JMenu setupShowMenu() {
        JMenu showMenu = new JMenu("Compare");

        JMenuItem showSelected = new JMenuItem("Selected");
        showSelected.addActionListener(e -> {
            FileLogger.LOGGER.info("Comparing selected");
            backgroundExecutor.submit(() -> dummyProject.showDiffs(getSelected()));
        });

        JMenuItem showAll = new JMenuItem("All");
        showAll.addActionListener(e -> {
            FileLogger.LOGGER.info("Comparing all");
            backgroundExecutor.submit(() -> dummyProject.showDiffs(
                    responses.stream().filter(d -> !d.isEmpty()).toList()
            ));
        });

        showMenu.add(showSelected);
        showMenu.add(showAll);

        return showMenu;
    }

    private JMenu setupApproveMenu() {
        JMenu approveMenu = new JMenu("Approve");

        JMenuItem approveSelected = new JMenuItem("Selected");
        approveSelected.addActionListener(_ -> {
            FileLogger.LOGGER.info("Applying selected");
            backgroundExecutor.submit(() ->
                dummyProject.applyDiffs(getSelected())
            );
        });

        JMenuItem approveAll = new JMenuItem("All");
        approveAll.addActionListener(_ -> {
            FileLogger.LOGGER.info("Applying all");
            backgroundExecutor.submit(() -> dummyProject.applyDiffs(
                    responses.stream().filter(d -> !d.isEmpty()).toList()
            ));
        });

        approveMenu.add(approveSelected);
        approveMenu.add(approveAll);

        return approveMenu;
    }

    public void refactorMethods(ActionEvent ignored) {
        FileLogger.LOGGER.info("Start AI evaluation methods");
        reset(null);
        var aiManager = AIManager.getInstance();

        var methods = complexityTranslator.getProblematicMethods();

        int totalSize = 0;
        for (var methodValues : methods.values()) {
            totalSize += methodValues.size();
        }

        initialize(totalSize);

        int gen = generation.get();
        for (var classEntry : methods.entrySet()) {
            String fqn = classEntry.getKey();
            Map<String, ComplexityMethod> methodMap = classEntry.getValue();

            for (var methodEntry : methodMap.values()) {

                this.aiExecutor.submit(() -> {

                    try {
                        var cclass = complexityTranslator.getClassResultMap().get(fqn);

                        DiffMethodResponse diff = aiManager.fixMethod(cclass, methodEntry);
                        if (gen != generation.get()) {
                            return;
                        }

                        SwingUtilities.invokeLater(() -> {
                            if (gen != generation.get()) {
                                return;
                            }

                            this.addCard(diff);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    private void refactorClasses(ActionEvent actionEvent) {
        FileLogger.LOGGER.info("Start AI evaluation classes");
        reset(null);
        var aiManager = AIManager.getInstance();

        Map<String, ComplexityClass> problematicClasses = complexityTranslator.getProblematicClasses();

        int totalSize = problematicClasses.size();
        initialize(totalSize);

        int gen = generation.get();
        for (var complexityClass : problematicClasses.values()) {

            this.aiExecutor.submit(() -> {

                try {

                    DiffClassResponse diff = aiManager.fixClass(complexityTranslator, complexityClass);
                    if (gen != generation.get()) {
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (gen != generation.get()) {
                            return;
                        }

                        this.addCard(diff);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        }
    }

    public void export(boolean all) {
        if (responses == null) FileLogger.LOGGER.warning("Empty responses");

        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }


        File exportFile = chooser.getSelectedFile();
        try {
            if (all) {
                JacksonMapper.getMapper()
                        .writerFor(ApplicableDiffTypeList.TYPE)
                        .writeValue(exportFile, responses);
            } else {
                JacksonMapper.getMapper()
                        .writerFor(ApplicableDiffTypeList.TYPE)
                        .writeValue(exportFile, getSelected());
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    null,
                    ex.getMessage(),
                    "Export error",
                    JOptionPane.ERROR_MESSAGE
            );
            FileLogger.LOGGER.warning(ex.getMessage());
        }
    }

    public void importAction(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File importFile = chooser.getSelectedFile();

        List<ApplicableDiff> diffs;
        try {
            diffs = JacksonMapper.getMapper()
                    .readerFor(ApplicableDiffTypeList.TYPE)
                    .readValue(importFile);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(
                    null,
                    "Could not read meaningful data",
                    "Import error",
                    JOptionPane.ERROR_MESSAGE
            );
            FileLogger.LOGGER.warning("Could not read meaningful data");
            ex.printStackTrace();
            return;
        }

        if (diffs.isEmpty() || diffs.stream().allMatch(ApplicableDiff::isEmpty)) {
            JOptionPane.showMessageDialog(
                    null,
                    "All read data was empty",
                    "Import error",
                    JOptionPane.ERROR_MESSAGE
            );
            FileLogger.LOGGER.warning("All read data was empty");
            return;
        }

        reset(null);

        int size = diffs.size();
        initialize(size);
        for (int i = 0; i < size; i++) {
            ApplicableDiff response = diffs.get(i);
            responses.set(i, response);
            addCard(response);
        }
    }

    public void reset(ActionEvent ignored) {
        // Remove all cards
        cards.removeAll();

        // Refresh the UI
        cards.revalidate();
        cards.repaint();

        responses = new ArrayList<>();
        selected = new boolean[0];

        dummyProject = new DummyProject(complexityTranslator);
        generation.incrementAndGet();

        currentCard = 0;
        aiExecutor.shutdownNow();
        aiExecutor = VirtualThreadExecutor.create();
        cardSize.set(0);
    }

    private void initialize(int size) {
        responses = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            responses.add(null);
        }

        selected = new boolean[size];
        correctAmount = 0;
    }

    public synchronized void addCard(ApplicableDiff diff) {
        int current = cardSize.getAndIncrement();
        String pageNString = String.valueOf(current + 1);
        responses.set(current, diff);
        JPanel panel = new JPanel();
        GuiUtils.setVerticalLayout(panel);

        String name = diff.origin().replace(MemberFormatter.PACKAGE_DELIMITER, ".");
        panel.add(new JLabel("Page " + pageNString + "/" + responses.size()));
        panel.add(new JLabel(name));
        panel.add(new PromptResponsePanel(diff, complexityTranslator));

        JCheckBox checkBox = new JCheckBox("Approve");
        checkBox.setFocusable(false);
        checkBox.addActionListener(e -> selected[current] = checkBox.isSelected());

        panel.add(checkBox);

        cards.add(panel, pageNString);
        cards.revalidate();
        cards.repaint();
        if (diff.isValid()) correctAmount++;

        updateCounter();
    }

    public ArrayList<ApplicableDiff> getSelected() {
        ArrayList<ApplicableDiff> selectedResponses = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            if (!selected[i]) continue;

            var response = responses.get(i);
            if (response.isEmpty()) continue;

            selectedResponses.add(response);
        }

        return selectedResponses;
    }

    private synchronized void updateCounter() {
        correctCounter.setText("Valid suggestions: " + correctAmount);
    }
}
