package model;

import org.eclipse.jgit.revwalk.RevCommit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Ticket {

    private String ticketID;
    private LocalDate creationDate;
    private LocalDate resolutionDate;
    private Release iv;
    private Release ov;
    private Release fv;
    private List<Release> av;
    private List<RevCommit> commitList;

    public Ticket(String ticketID, LocalDate creationDate, LocalDate resolutionDate, Release ov, Release fv, List<Release> av) {
        this.ticketID = ticketID;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
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

    public LocalDate getCreationDate() {
        return creationDate;
    }

    public LocalDate getResolutionDate() {
        return resolutionDate;
    }

    public Release getIv() {
        return iv;
    }

    public Release getOv() {
        return ov;
    }

    public Release getFv() {
        return fv;
    }

    public List<Release> getAv() {
        return av;
    }

    public void setTicketID(String ticketID) {
        this.ticketID = ticketID;
    }

    public void setCreationDate(LocalDate creationDate) {
        this.creationDate = creationDate;
    }

    public void setResolutionDate(LocalDate resolutionDate) {
        this.resolutionDate = resolutionDate;
    }

    public void setIv(Release iv) {
        this.iv = iv;
    }

    public void setOv(Release ov) {
        this.ov = ov;
    }

    public void setFv(Release fv) {
        this.fv = fv;
    }

    public void setAv(List<Release> av) {
        this.av = av;
    }

    public void addAV(Release release) {
        this.av.add(release);
    }

    public List<RevCommit> getCommitList() {
        return commitList;
    }

    public void addCommit(RevCommit commit){
        this.commitList.add(commit);
    }
}
