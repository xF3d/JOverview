package gui.frame;

import gui.components.ClassTableMetricsPanel;
import gui.components.ComplexityGridPanel;
import gui.components.ReferenceController;
import gui.components.SystemChartPanel;
import lombok.Getter;
import translate.complexity.result.BoundedResult;
import translate.translator.ComplexityTranslator;

import javax.swing.*;
import java.util.List;

@Getter
public class CompareFrame extends JFrame {
    private final JPanel content = new JPanel();

    public CompareFrame(
            ComplexityTranslator originalTranslator,
            ComplexityTranslator dummyTranslator,
            int changesApplied
    ) {
        super("Comparing");

        var displayOriginal = originalTranslator.evaluateSystem().displayList();
        var displayDummy = dummyTranslator.evaluateSystem().displayList();

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(new JLabel("Changes applied: " + changesApplied));

        SystemChartPanel chartPanel = new SystemChartPanel();
        chartPanel.addProject("Original", displayOriginal[0]);
        chartPanel.addProject("Updated", displayDummy[0]);

        content.add(chartPanel);

        addGridPanel("Original metrics: ", displayOriginal[1]);
        addGridPanel("Updated metrics: ", displayDummy[1]);

        this.content.add(new ReferenceController(
                "<html><p>Updated Classes in the system</p> <br></html>",
                dummyTranslator.getClassesArray(),
                (_) -> new ClassTableMetricsPanel(dummyTranslator)
        ));

        setContentPane(new JScrollPane(content));

        //JPanel testResults = new JPanel(new BorderLayout());

        //testResults.add(new TestResultPanel("Original", originalResult), BorderLayout.WEST);
        //testResults.add(new TestResultPanel("Updated", dummyResult), BorderLayout.EAST);
        //content.add(testResults);

        setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setVisible(true);

        FrameManager.addFrame(this);
    }

    private void addGridPanel(String title, List<BoundedResult> resultList) {
        content.add(new JLabel(title));

        var metricPanel = new ComplexityGridPanel();
        metricPanel.addMetrics(resultList);

        content.add(metricPanel);
    }
}
