package utils;

import controller.Proportion;
import model.Release;
import model.Ticket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class JiraUtils {

    /**
     * Classe di utilità che fornisce metodi helper per interagire con i dati estratti da Jira e per manipolarli
     */
    private JiraUtils(){}


    /**
     * Legge il contenuto JSON da un URL e lo restituisce come oggetto JSONObject
     */
    public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        } finally {
            is.close();
        }
    }

    /**
     * Metodo helper per leggere l'intero contenuto di un Reader e restituirlo come stringa
     */
    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    /**
     * Trova la prima release la cui data è uguale o successiva a una data specifica
     */
    public static Release getReleaseAfterOrEqualDate(LocalDate specificDate, List<Release> releasesList) {

        // Ordina le release per data
        releasesList.sort(Comparator.comparing(Release::getDate));

        // Scorre la lista e restituisce la prima release la cui data non è precedente a quella specificata
        for (Release release : releasesList) {
            if (!release.getDate().isBefore(specificDate)) {
                return release;
            }
        }
        return null;
    }


    /**
     * Converte un JSONArray di versioni (dal JSON di Jira) in una lista di oggetti Release
     */
    public static List<Release> returnAffectedVersions(JSONArray affectedVersionsArray, List<Release> releasesList) {
        List<Release> existingAffectedVersions = new ArrayList<>();

        // Itera sui nomi delle affected versions nel JSON
        for (int i = 0; i < affectedVersionsArray.length(); i++) {
            String affectedVersionName = affectedVersionsArray.getJSONObject(i).get("name").toString();

            // Cerca l'oggetto Release corrispondente nella lista delle release
            for (Release release : releasesList) {
                if (Objects.equals(affectedVersionName, release.getName())) {
                    existingAffectedVersions.add(release);
                    break;
                }
            }
        }
        existingAffectedVersions.sort(Comparator.comparing(Release::getDate));
        return existingAffectedVersions;
    }

    /**
     * Orchestra il processo di arricchimento dei ticket
     */
    public static List<Ticket> addIVandAV(List<Ticket> ticketsList, List<Release> releasesList) throws IOException {
        List<Ticket> finalTicketsList = new ArrayList<>();
        Proportion proportion = new Proportion();

        // Itera su tutti i ticket in ordine cronologico
        for(Ticket ticket: ticketsList){
            if(ticket.getAv().isEmpty()){
                // Se le AV (e quindi la IV) sono mancanti, usa Proportion per stimarle
                proportion.fixTicketWithProportion(ticket, releasesList);
                // Completa la lista delle AV
                completeAV(ticket, releasesList);
            }else{
                // Se la IV è nota, tiene traccia dell'informazione per calcolare P
                proportion.addProportion(ticket);
                // Completa la lista delle AV
                completeAV(ticket, releasesList);
            }
            finalTicketsList.add(ticket);
        }

        return finalTicketsList;

    }

    /**
     * Completa la lista delle Affected Versions (AV) di un ticket, aggiungendo tutte le release
     * comprese tra la Injected Version (IV) e la Fixed Version (FV)
     */
    private static void completeAV(Ticket ticket, List<Release> releasesList) {
        int iv = ticket.getIv().getId();
        int fv = ticket.getFv().getId();

        for(Release release : releasesList){
            // Aggiunge alla lista AV tutte le release il cui ID è strettamente compreso tra IV e FV
            if(release.getId() > iv && release.getId() < fv ){
                ticket.addAV(release);
            }
        }
    }

    /**
     * Filtra una lista di ticket per restituire solo quelli consistenti, usati per il Cold Start
     */
    public static List<Ticket> returnConsistentTickets(List<Ticket> ticketList, LocalDate resolutionDate) {
        List<Ticket> correctTicket = new ArrayList<>();

        for(Ticket ticket: ticketList){
            // Un ticket è adatto se ha almeno una AV nota e se è stato risolto prima della data di risoluzione del ticket
            if(!ticket.getAv().isEmpty() && ticket.getResolutionDate().isBefore(resolutionDate))  correctTicket.add(ticket);
        }

        return correctTicket;
    }
}
