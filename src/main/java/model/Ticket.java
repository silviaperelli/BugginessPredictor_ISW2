package model;

import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe modello che rappresenta un singolo ticket di bug estratto da Jira
 */
public class Ticket {

    private String ticketID;
    private LocalDate creationDate; // Data in cui il ticket è stato aperto
    private LocalDate resolutionDate; // Data in cui il ticket è stato risolto

    private Release iv; // Injected Version: la release in cui il bug è stato introdotto
    private Release ov; // Opening Version: la release in cui il bug è stato scoperto
    private Release fv; // Fixed Version: la release in cui il bug è stato corretto
    private List<Release> av; // Affected Versions: la lista di tutte le release affette dal bug
    private List<RevCommit> commitList; // Lista dei commit di Git che hanno risolto questo bug

    public Ticket(String ticketID, LocalDate creationDate, LocalDate resolutionDate, Release ov, Release fv, List<Release> av) {
        this.ticketID = ticketID;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;

        // La Injected Version (IV) è la prima delle Affected Versions (se la lista non è vuota)
        if(av.isEmpty()){
            iv = null;
        }else{
            iv = av.get(0);
        }
        this.ov = ov;
        this.fv = fv;
        this.av = av;
        this.commitList = new ArrayList<>();
    }

    public String getTicketID() {
        return ticketID;
    }
    public void setTicketID(String ticketID) {
        this.ticketID = ticketID;
    }

    public LocalDate getCreationDate() {
        return creationDate;
    }
    public void setCreationDate(LocalDate creationDate) {
        this.creationDate = creationDate;
    }

    public LocalDate getResolutionDate() {
        return resolutionDate;
    }
    public void setResolutionDate(LocalDate resolutionDate) {
        this.resolutionDate = resolutionDate;
    }

    public Release getIv() {
        return iv;
    }
    public void setIv(Release iv) {
        this.iv = iv;
    }

    public Release getOv() {
        return ov;
    }
    public void setOv(Release ov) {
        this.ov = ov;
    }

    public Release getFv() {
        return fv;
    }
    public void setFv(Release fv) {
        this.fv = fv;
    }

    public List<Release> getAv() {
        return av;
    }
    public void setAv(List<Release> av) {
        this.av = av;
    }

    // Aggiunge una singola release alla lista delle Affected Versions
    public void addAV(Release release) {
        this.av.add(release);
    }

    public List<RevCommit> getCommitList() {
        return commitList;
    }

    // Aggiunge un commit di fix a questo ticket
    public void addCommit(RevCommit commit){
        this.commitList.add(commit);
    }
}
