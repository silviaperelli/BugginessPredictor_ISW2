// In Model/AcumeMethod.java
package Model;

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

    // Getters che restituiscono già le stringhe per il CSV, come ha fatto la collega
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
        // ACUME si aspetta 'YES' e 'NO' in maiuscolo
        return actualValue.toUpperCase();
    }
}