package model;

import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe modello che rappresenta una singola versione (release) del software
 */
public class Release {
    private int id;
    private String name; // Nome della versione
    private LocalDate date; // Data di rilascio ufficiale della versione

    private List<RevCommit> commitList; // Lista di tutti i commit avvenuti nel periodo di questa release
    private List <JavaMethod> methods; // Lista di tutti i metodi presenti nello snapshot di questa release

    public Release(String name, LocalDate date) {
        this.name = name;
        this.date = date;
        this.commitList = new ArrayList<>();
        this.methods = new ArrayList<>();
    }

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDate() {
        return date;
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

