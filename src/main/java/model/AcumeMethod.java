package model;

public class AcumeMethod {
    private final int id;
    private final int size; // LOC del metodo
    private final double predictedProbability;
    private final String actualValue; // "yes" o "no"

    public AcumeMethod(int id, int size, double predictedProbability, String actualValue) {
        this.id = id;
        this.size = size;
        this.predictedProbability = predictedProbability;
        this.actualValue = actualValue;
    }

    public String getId() {
        return String.valueOf(id);
    }

    public String getSize() {
        return String.valueOf(size);
    }

    public String getPredictedProbability() {
        return String.valueOf(predictedProbability);
    }

    public String getActualValue() {
        return actualValue.toUpperCase();
    }
}