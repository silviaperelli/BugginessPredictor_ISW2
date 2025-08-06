package model;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JavaMethod {

    private String fullyQualifiedName; // Es: com/example/MyClass.java/myMethod(int,String)
    private Release release;
    private String bodyHash;
    private String content;
    private List<RevCommit> commits; //list of commits that modified the content of the method
    private List<RevCommit> fixCommits; //list of commits that fixed the method

    //prevision metric
    private boolean buggy;

    //metrics
    private Integer loc;
    private int numParameters;
    private int numAuthors;
    private int numRevisions; // methodHistories
    private int totalStmtAdded; // stmtAdded
    private int totalStmtDeleted; //stmtDeleted

    private int numBranches;
    private int numLocalVariables; // number of local variables
    private int maxChurn;   // maxChurn (stmtAddedInOneCommit + stmtDeletedInOneCommit)
    private double avgChurn;  // avgChurn = (totalStmtAdded + totalStmtDeleted) / numRevisions
    private int nestingDepth;

    private int hasFixHistory;

    public JavaMethod(String fullyQualifiedName, Release release) {
        this.fullyQualifiedName = fullyQualifiedName;
        this.release = release;
        this.commits = new ArrayList<>();
        this.fixCommits = new ArrayList<>();
        this.buggy = false;

        this.loc = 0;
        this.numRevisions = 0;
        this.numAuthors = 0;
        this.totalStmtAdded = 0;
        this.totalStmtDeleted = 0;

        this.numBranches = 0;
        this.numLocalVariables = 0;
        this.maxChurn = 0;
        this.avgChurn = 0.0;
        this.nestingDepth = 0;
        this.hasFixHistory = 0;

    }

    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }

    public void setFullyQualifiedName(String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
    }


    public Release getRelease() {
        return release;
    }

    public void setRelease(Release release) {
        this.release = release;
    }

    public int getHasFixHistory() {
        return hasFixHistory;
    }

    public void setHasFixHistory(int hasFixHistory) {
        this.hasFixHistory = hasFixHistory;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getNumLocalVariables() {
        return numLocalVariables;
    }

    public void setNumLocalVariables(int numLocalVariables) {
        this.numLocalVariables = numLocalVariables;
    }

    public boolean isBuggy() {
        return buggy;
    }

    public void setBuggy(boolean buggy) {
        this.buggy = buggy;
    }

    public Integer getLoc() {
        return loc;
    }

    public void setLoc(Integer loc) {
        this.loc = loc;
    }

    public List<RevCommit> getCommits() {
        return commits;
    }

    public void addCommit(RevCommit commit) {
        commits.add(commit);
    }

    public List<RevCommit> getFixCommits() {
        return fixCommits;
    }

    public void addFixCommit(RevCommit commit) {
        fixCommits.add(commit);
    }

    public int getNumParameters() {
        return numParameters;
    }

    public void setNumParameters(int numParameters) {
        this.numParameters = numParameters;
    }

    public int getNumAuthors() {
        return numAuthors;
    }

    public void setNumAuthors(int numAuthors) {
        this.numAuthors = numAuthors;
    }

    public int getNumRevisions() {
        return numRevisions;
    }

    public void setNumRevisions(int numRevisions) {
        this.numRevisions = numRevisions;
    }

    public int getTotalStmtAdded() {
        return totalStmtAdded;
    }

    public void setTotalStmtAdded(int totalStmtAdded) {
        this.totalStmtAdded = totalStmtAdded;
    }

    public int getTotalStmtDeleted() {
        return totalStmtDeleted;
    }

    public void setTotalStmtDeleted(int totalStmtDeleted) {
        this.totalStmtDeleted = totalStmtDeleted;
    }

    public int getNumBranches() {
        return numBranches;
    }

    public void setNumBranches(int numBranches) {
        this.numBranches = numBranches;
    }

    public int getMaxChurn() {
        return maxChurn;
    }

    public void setMaxChurn(int maxChurn) {
        this.maxChurn = maxChurn;
    }

    public double getAvgChurn() {
        return avgChurn;
    }

    public void setAvgChurn(double avgChurn) {
        this.avgChurn = avgChurn;
    }

    public int getNestingDepth() {
        return nestingDepth;
    }

    public void setNestingDepth(int nestingDepth) {
        this.nestingDepth = nestingDepth;
    }

    // Per identificare univocamente un metodo (nome + parametri) all'interno di una classe
    public static String getSignature(MethodDeclaration md) {
        return md.getSignature().asString();
    }

    public void addStmtAdded(int count) { this.totalStmtAdded += count; }
    public void addStmtDeleted(int count) { this.totalStmtDeleted += count; }

    public void incrementNumRevisions() { this.numRevisions++; }

    public String getBodyHash() {
        return bodyHash;
    }

    public void setBodyHash(String bodyHash) {
        this.bodyHash = bodyHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaMethod that = (JavaMethod) o;
        return Objects.equals(fullyQualifiedName, that.fullyQualifiedName) &&
                Objects.equals(release.getId(), that.release.getId()); // Un metodo è unico per nome E release
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullyQualifiedName, release.getId());
    }

    @Override
    public String toString() {
        return "JavaMethod{" +
                "FQN='" + fullyQualifiedName + '\'' +
                ", release=" + release.getId() +
                ", buggy=" + buggy +
                '}';
    }
}
