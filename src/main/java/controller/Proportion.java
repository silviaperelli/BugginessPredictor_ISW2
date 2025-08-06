package controller;

import model.Release;
import model.Ticket;
import utils.JiraUtils;
import utils.MathUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;

import static java.lang.Math.max;

public class Proportion {

    private List<Float> proportionList;
    private float totalProportion;
    final int MIN_PROPORTIONS_FOR_INCREMENT = 5;
    final int MIN_PROPORTIONS_FOR_MOVING_WINDOW = 10;
    final int MOVING_WINDOW_SIZE = 5;

    private enum Projects {
        AVRO,
        SYNCOPE,
        STORM,
        ZOOKEEPER
    }

    public Proportion(){
        this.proportionList = new ArrayList<>();
        this.totalProportion = 0;
    }

    //method use to estimate IV
    public void fixTicketWithProportion(Ticket ticket, List<Release> releasesList) throws IOException {
        int estimatedIV;
        float proportion;

        //calculate proportion
        //if we have less than 5 ticket of which we know the proportion, use cold start
        if(proportionList.size() < MIN_PROPORTIONS_FOR_INCREMENT){
            proportion = coldStart(ticket.getResolutionDate());
            //System.out.println("Cold start: "+proportion);
        }else{ //otherwise use increment
            proportion = increment();
            //System.out.println("Increment: "+proportion);
        }
        /*else if (proportionList.size() < MIN_PROPORTIONS_FOR_MOVING_WINDOW) {
            proportion = increment();
        } else {
            proportion = movingWindowAverage(MOVING_WINDOW_SIZE);
        }*/

        //calculate IV
        estimatedIV = obtainIV(proportion, ticket);

        //set the estimated IV of the ticket
        for(Release release : releasesList){
            if(estimatedIV == release.getId()){
                ticket.setIv(release);
                ticket.addAV(release);
            }
        }
    }

    // method to calculate proportion on ticket with IV set and add P value to a list
    public void addProportion(Ticket ticket) {
        int denominator;
        float proportion;
        int ov = ticket.getOv().getId();
        int fv = ticket.getFv().getId();

        //calculate proportion
        if(ov == fv){ //to avoid denominator equal to 0
            denominator = 1;
        }else{
            denominator = fv-ov;
        }
        proportion = (float)(fv - ticket.getIv().getId())/denominator;

        //add proportion to the list
        this.proportionList.add(proportion);
        this.totalProportion += proportion;

    }

    //use method increment by computing p as the average among the defects fixed in previous versions
    private float increment() {
        return this.totalProportion / this.proportionList.size();
    }

    private float coldStart(LocalDate resolutionDate) throws IOException {

        List<Float> proportionListTemp = new ArrayList<>();

        for(Projects project: Projects.values()){
            //extract releases and tickets
            JiraDataExtractor jiraExtractor = new JiraDataExtractor(project.toString().toUpperCase());
            List<Release> releaseList = jiraExtractor.getReleases();
            List<Ticket> allTickets = jiraExtractor.getFinalTickets(releaseList, false);

            //need to obtain all tickets that have AV set
            List<Ticket> consistentTickets = JiraUtils.returnConsistentTickets(allTickets, resolutionDate);
            // if the consistent tickets are more than 5, add ticket to proportion and to a temporary list
            if(consistentTickets.size() >= 5){

                Proportion proportion = new Proportion();
                for(Ticket t: consistentTickets){
                    proportion.addProportion(t);
                }
                proportionListTemp.add(proportion.increment());
            }
        }

        // use cold start method, by computing the median among other projects
        return MathUtils.median(proportionListTemp);
    }

    private float movingWindowAverage(int windowSize) {
        // take the starting index for the window and take the last "windowSize" elements
        int listSize = proportionList.size();
        int startIndex = Math.max(0, listSize - windowSize);

        float sumOfWindowElements = 0;
        int elementsInWindow = 0;

        // sum elements in the window
        for (int i = startIndex; i < listSize; i++) {
            sumOfWindowElements += proportionList.get(i);
            elementsInWindow++;
        }

        if (elementsInWindow == 0) {
            return 0.0f;
        }

        return sumOfWindowElements / elementsInWindow;
    }

    private int obtainIV(float proportion, Ticket ticket){
        int ov = ticket.getOv().getId();
        int fv = ticket.getFv().getId();
        int estimatedIV;

        if(ov!=fv){
            //calculate IV, ID release must start from 0
            estimatedIV = max(1, (int)(fv - proportion*(fv - ov)));
        }else{
            estimatedIV = max(1, (int)(fv - proportion));
        }

        return estimatedIV;
    }
}
