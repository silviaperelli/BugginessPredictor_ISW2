package model;

import weka.classifiers.Classifier;

/**
 * Classe modello che funge da "wrapper" per un classificatore Weka.
 * Il suo scopo è raggruppare un'istanza di un `Classifier` Weka con i metadati che descrivono la sua configurazione
 */
public class WekaClassifier {
    private final Classifier classifier;
    private final String name;

    // Stringhe che descrivono le tecniche applicate
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
