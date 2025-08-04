package Model;

import weka.classifiers.Classifier;

public class WekaClassifier {
    private final Classifier classifier;
    private final String name;

    private final String featureSelection;
    private final String sampling;
    private final String costSensitive;

    public WekaClassifier(Classifier classifier, String name, String featureSelection, String sampling, String costSensitive) {
        this.classifier = classifier;
        this.name = name;
        this.featureSelection = featureSelection;
        this.sampling = sampling;
        this.costSensitive = costSensitive;
    }

    public Classifier getClassifier() { return classifier; }
    public String getName() { return name; }
    public String getFeatureSelection() { return featureSelection; }
    public String getSampling() { return sampling; }
    public String getCostSensitive() { return costSensitive; }
}
