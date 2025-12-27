package model;

import java.util.Locale;

/**
 * Classe modello che rappresenta i risultati della valutazione di un classificatore in una specifica iterazione di un esperimento
 */
public class ClassifierEvaluation {

    private String project;
    private int iteration;
    private String classifierName; // Nome del classificatore di base
    private String featureSelection; // Tecnica di selezione delle feature usata (es. "BestFirst")
    private String sampling; // Tecnica di bilanciamento usata (es. "SMOTE")
    private String costSensitive; // Tecnica di gestione del costo degli errori usata

    // Metriche di performance
    private double precision;
    private double recall;
    private double auc;
    private double kappa;
    private double f1Score;
    private double mcc;

    public static final String CSV_HEADER = "Project,Iteration,Classifier,FeatureSelection,Sampling,CostSensitive,Precision,Recall,AUC,Kappa,F1-Score,MCC";

    public ClassifierEvaluation(String project, int iteration, String classifierName,
                                String featureSelection, String sampling, String costSensitive,
                                EvaluationMetrics metrics) {
        this.project = project;
        this.iteration = iteration;
        this.classifierName = classifierName;
        this.featureSelection = featureSelection;
        this.sampling = sampling;
        this.costSensitive = costSensitive;

        // Estraiamo i valori dall'oggetto metrics per popolare i campi interni
        this.precision = metrics.precision;
        this.recall = metrics.recall;
        this.auc = metrics.auc;
        this.kappa = metrics.kappa;
        this.f1Score = metrics.f1Score;
        this.mcc = metrics.mcc;
    }


    // Classe interna statica per raggruppare le metriche di valutazione
    public static class EvaluationMetrics {
        double precision;
        double recall;
        double auc;
        double kappa;
        double f1Score;
        double mcc;

        public EvaluationMetrics(double precision, double recall, double auc,
                                 double kappa, double f1Score, double mcc) {
            this.precision = precision;
            this.recall = recall;
            this.auc = auc;
            this.kappa = kappa;
            this.f1Score = f1Score;
            this.mcc = mcc;
        }
    }

    public static String getCsvHeader() {
        return CSV_HEADER;
    }

    public String toCsvString() {
        return String.format(Locale.US, "%s,%d,%s,%s,%s,%s,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f",
                project,
                iteration,
                classifierName,
                featureSelection,
                sampling,
                costSensitive,
                precision,
                recall,
                auc,
                kappa,
                f1Score,
                mcc);
    }
}
