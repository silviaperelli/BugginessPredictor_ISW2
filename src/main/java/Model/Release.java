package Model;

import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Release {
    private int id;
    //version name
    private String name;
    //version date
    private LocalDate date;
    //list of all commits related to that version
    private List<RevCommit> commitList;

    //list of all classes related to that version
    private List <JavaMethod> methods;

    public Release(String name, LocalDate date) {
        this.name = name;
        this.date = date;
        this.commitList = new ArrayList<>();
        this.methods = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<RevCommit> getCommitList() {
        return commitList;
    }

    public void addCommit(RevCommit commit){
        this.commitList.add(commit);
    }

    public List<JavaMethod> getMethods() {
        return methods;
    }

    public void addMethod(JavaMethod method){
        this.methods.add(method);
    }

    public void setClasses(List<JavaMethod> methods) {
        this.methods = methods;
    }

}

