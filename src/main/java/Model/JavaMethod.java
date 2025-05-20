package Model;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JavaMethod {

    private String name;
    private String methodName;
    private String className;
    private Release release;
    private String content;
    private List<RevCommit> commits; //list of commits that modified the content
    private List<RevCommit> fixCommits;
    private MethodDeclaration declaration;
    private int numParameters;
    private int numAuthors;
    private int numRevisions; // methodHistories
    private int totalStmtAdded;
    private int totalStmtDeleted;

    //prevision metric
    private boolean buggy;

    //METRICS
    private Integer loc;

    public JavaMethod(String name, String methodName, String className, Release release, String content, MethodDeclaration declaration) {
        this.name = name;
        this.methodName = methodName;
        this.className = className;
        this.release = release;
        this.content = content;
        this.declaration = declaration;
        this.commits = new ArrayList<>();
        this.fixCommits = new ArrayList<>();
        this.buggy = false;
        this.loc = 0;
        this.numParameters = declaration != null ? declaration.getParameters().size() : 0;
        this.numAuthors = 0;
        this.numRevisions = 0;
        this.totalStmtAdded = 0;
        this.totalStmtDeleted = 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Release getRelease() {
        return release;
    }

    public void setRelease(Release release) {
        this.release = release;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public void addCommit(RevCommit commit) {
        commits.add(commit);
    }

    public void addFixCommit(RevCommit commit) {
        fixCommits.add(commit);
    }

    // Per identificare univocamente un metodo (nome + parametri) all'interno di una classe
    public static String getSignature(MethodDeclaration md) {
        return md.getSignature().asString();
    }

    public int getNumParameters() {
        return numParameters;
    }

    public void setNumParameters(int numParameters) {
        this.numParameters = numParameters;
    }

    public List<RevCommit> getCommits() {
        return commits;
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

    public void addStmtAdded(int count) { this.totalStmtAdded += count; }
    public void addStmtDeleted(int count) { this.totalStmtDeleted += count; }

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

    public void incrementNumRevisions() { this.numRevisions++; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavaMethod that = (JavaMethod) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(release.getId(), that.release.getId()); // Un metodo è unico per nome E release
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, release.getId());
    }

    @Override
    public String toString() {
        return "JavaMethod{" +
                "FQN='" + name + '\'' +
                ", release=" + release.getId() +
                ", buggy=" + buggy +
                '}';
    }

    public String getMethodName() {
        return methodName;
    }

    public String getClassName() {
        return className;
    }
}
