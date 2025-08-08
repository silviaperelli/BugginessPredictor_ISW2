package model;

import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

public class JavaMethod {
    private final String fullyQualifiedName;
    private final Release release;
    private boolean buggy;
    private final List<RevCommit> commits;
    private final List<RevCommit> fixCommits;
    private String bodyHash;

    // --- METRICHE ---

    // Metriche di Complessità e Dimensione
    private int loc;
    private int numParameters;
    private int numBranches;
    private int nestingDepth;
    private int numCodeSmells;
    private int numLocalVariables;

    // Metriche Storiche (Process Metrics)
    private int numRevisions;
    private int numAuthors;
    private int totalStmtAdded;
    private int totalStmtDeleted;
    private int maxChurn;
    private double avgChurn;
    private int hasFixHistory;


    public JavaMethod(String fullyQualifiedName, Release release) {
        this.fullyQualifiedName = fullyQualifiedName;
        this.release = release;
        this.commits = new ArrayList<>();
        this.fixCommits = new ArrayList<>();
        this.buggy = false;

        // Inizializza tutte le metriche
        this.loc = 0;
        this.numParameters = 0;
        this.numBranches = 0;
        this.nestingDepth = 0;
        this.numCodeSmells = 0;
        this.numRevisions = 0;
        this.numAuthors = 0;
        this.totalStmtAdded = 0;
        this.totalStmtDeleted = 0;
        this.maxChurn = 0;
        this.avgChurn = 0.0;
        this.hasFixHistory = 0;
        this.numLocalVariables = 0;
    }

    public static String getSignature(MethodDeclaration md) {
        return md.getSignature().asString();
    }

    // --- METODI HELPER PER AGGIORNARE LE METRICHE ---
    public void addCommit(RevCommit commit) { this.commits.add(commit); }
    public void addFixCommit(RevCommit commit) { this.fixCommits.add(commit); }
    public void incrementNumRevisions() { this.numRevisions++; }
    public void addStmtAdded(int added) { this.totalStmtAdded += added; }
    public void addStmtDeleted(int deleted) { this.totalStmtDeleted += deleted; }

    public RevCommit getFirstCommit() {
        if (commits.isEmpty()) return null;
        return commits.stream().min(Comparator.comparing(RevCommit::getCommitTime)).orElse(null);
    }

    // Metodo da chiamare alla fine per calcolare le metriche aggregate
    public void computeFinalMetrics() {

        // Calcola AvgChurn
        if (this.numRevisions > 0) {
            this.avgChurn = (double) (this.totalStmtAdded + this.totalStmtDeleted) / this.numRevisions;
        }
    }

    // --- GETTERS E SETTERS PER TUTTE LE METRICHE ---
    public String getFullyQualifiedName() { return fullyQualifiedName; }
    public Release getRelease() { return release; }
    public boolean isBuggy() { return buggy; }
    public void setBuggy(boolean buggy) { this.buggy = buggy; }
    public List<RevCommit> getCommits() { return commits; }
    public String getBodyHash() { return bodyHash; }
    public void setBodyHash(String bodyHash) { this.bodyHash = bodyHash; }
    public int getLoc() { return loc; }
    public void setLoc(int loc) { this.loc = loc; }
    public int getNumParameters() { return numParameters; }
    public void setNumParameters(int numParameters) { this.numParameters = numParameters; }
    public int getNumBranches() { return numBranches; }
    public void setNumBranches(int numBranches) { this.numBranches = numBranches; }
    public int getNestingDepth() { return nestingDepth; }
    public void setNestingDepth(int nestingDepth) { this.nestingDepth = nestingDepth; }
    public int getNumCodeSmells() { return numCodeSmells; }
    public void setNumCodeSmells(int numCodeSmells) { this.numCodeSmells = numCodeSmells; }
    public int getNumRevisions() { return numRevisions; }
    public void setNumRevisions(int numRevisions) { this.numRevisions = numRevisions; }
    public int getNumAuthors() { return numAuthors; }
    public void setNumAuthors(int numAuthors) { this.numAuthors = numAuthors; }
    public int getTotalStmtAdded() { return totalStmtAdded; }
    public int getTotalStmtDeleted() { return totalStmtDeleted; }
    public void setTotalStmtAdded(int totalStmtAdded) { this.totalStmtAdded = totalStmtAdded; }
    public void setTotalStmtDeleted(int totalStmtDeleted) { this.totalStmtDeleted = totalStmtDeleted; }
    public int getMaxChurn() { return maxChurn; }
    public double getAvgChurn() { return avgChurn; }
    public void setMaxChurn(int maxChurn) { this.maxChurn = maxChurn; }
    public void setAvgChurn(double avgChurn) { this.avgChurn = avgChurn; }
    public int getHasFixHistory() { return hasFixHistory; }
    public void setHasFixHistory(int hasFixHistory) { this.hasFixHistory = hasFixHistory; }
    public int getNumLocalVariables() { return numLocalVariables; }
    public void setNumLocalVariables(int numLocalVariables) { this.numLocalVariables = numLocalVariables; }
}