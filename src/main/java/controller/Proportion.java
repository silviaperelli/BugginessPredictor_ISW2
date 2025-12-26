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

/**
 * Implementa la tecnica Proportion per la stima della Injected Version (IV) dei ticket.
 */
public class Proportion {

    private List<Float> proportionList;
    private float totalProportion;
    static final int MIN_PROPORTIONS_FOR_INCREMENT = 5;
    static final int MIN_PROPORTIONS_FOR_MOVING_WINDOW = 10;
    static final int MOVING_WINDOW_SIZE = 5;

    // Progetti di riferimento usati per la stima a freddo (Cold Start)
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


    /**
     * Metodo principale per stimare e assegnare la Injected Version (IV) a un ticket.
     * Decide se utilizzare Cold Start o Increment
     */
    public void fixTicketWithProportion(Ticket ticket, List<Release> releasesList) throws IOException {
        int estimatedIV;
        float proportion;

        // Calcola Proportion
        // Se abbiamo meno di 5 valori storici, usa la stima basata su altri progetti (Cold Start)
        if(proportionList.size() < MIN_PROPORTIONS_FOR_INCREMENT){
            proportion = coldStart(ticket.getResolutionDate());
        }else{ // Altrimenti, usa la media dei valori storici del progetto corrente (Increment)
            proportion = increment();
        }

        // Usa la proporzione P per calcolare la IV
        estimatedIV = obtainIV(proportion, ticket);

        // Assegna la release corrispondente all'ID stimato come IV del ticket
        for(Release release : releasesList){
            if(estimatedIV == release.getId()){
                ticket.setIv(release);
                ticket.addAV(release); // La IV è anche la prima delle Affected Version
            }
        }
    }

    /**
     * Calcola la proporzione P per un ticket con IV già nota.
     * Il valore calcolato viene aggiunto alla lista storica per le stime future
     */
    public void addProportion(Ticket ticket) {
        int denominator;
        float proportion;
        int ov = ticket.getOv().getId(); // Opening Version
        int fv = ticket.getFv().getId(); // Fixed Version

        // Calcola P secondo la formula: P = (FV - IV) / (FV - OV)
        // Gestisce il caso in cui OV e FV coincidano per evitare divisione per zero
        if(ov == fv){
            denominator = 1;
        }else{
            denominator = fv-ov;
        }
        proportion = (float)(fv - ticket.getIv().getId())/denominator;

        // Aggiunge il valore P calcolato alla lista
        this.proportionList.add(proportion);
        this.totalProportion += proportion;

    }

    /**
     * Implementa la strategia "Increment": calcola P come la media di tutti i valori
     * di proporzione raccolti finora per il progetto corrente
     */
    private float increment() {
        return this.totalProportion / this.proportionList.size();
    }

    /**
     * Implementa la strategia "Cold Start": stima P quando non ci sono abbastanza dati storici.
     * Calcola la P media per altri progetti di riferimento e restituisce la mediana di questi valori
     */
    private float coldStart(LocalDate resolutionDate) throws IOException {

        List<Float> proportionListTemp = new ArrayList<>();

        // Itera sui progetti di riferimento definiti nell'enum
        for(Projects project: Projects.values()){
            // Per ogni progetto, estrae le release e i ticket risolti prima della data specificata
            JiraDataExtractor jiraExtractor = new JiraDataExtractor(project.toString().toUpperCase());
            List<Release> releaseList = jiraExtractor.getReleases();
            List<Ticket> allTickets = jiraExtractor.getFinalTickets(releaseList, false);
            List<Ticket> consistentTickets = JiraUtils.returnConsistentTickets(allTickets, resolutionDate);
            // Se il progetto ha abbastanza dati consistenti (almeno 5), calcola la sua P media
            if(consistentTickets.size() >= 5){

                Proportion proportion = new Proportion();
                for(Ticket t: consistentTickets){
                    proportion.addProportion(t);
                }
                proportionListTemp.add(proportion.increment());
            }
        }

        // Restituisce la mediana delle P medie calcolate dagli altri progetti
        return MathUtils.median(proportionListTemp);
    }

    /**
     * Applica la formula inversa per calcolare l'ID della Injected Version (IV) stimata,
     * a partire dalla proporzione P e dalle release OV e FV.
     */
    private int obtainIV(float proportion, Ticket ticket){
        int ov = ticket.getOv().getId();
        int fv = ticket.getFv().getId();
        int estimatedIV;

        if(ov!=fv){
            estimatedIV = max(1, (int)(fv - proportion*(fv - ov)));
        }else{
            estimatedIV = max(1, (int)(fv - proportion));
        }

        return estimatedIV;
    }
}
