package controller;

import model.Release;
import model.Ticket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import utils.JiraUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Classe responsabile dell'estrazione dei dati da Jira.
 * Si connette all'API di Jira per recuperare le informazioni sulle release e sui ticket di bug
 */
public class JiraDataExtractor {

    private final String projName;

    public JiraDataExtractor(String projName) {
        this.projName = projName.toUpperCase();
    }


    /**
     * Recupera tutte le release di un progetto da Jira.
     * Filtra solo le release che hanno una data di rilascio specificata, le ordina cronologicamente
     * e assegna loro un ID numerico progressivo.
     */
    public List<Release> getReleases() throws IOException {
        List<Release> releaseList = new ArrayList<>();
        String url = "https://issues.apache.org/jira/rest/api/latest/project/" + this.projName;
        JSONObject json = JiraUtils.readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        for (int i=0; i < versions.length(); i++) {
            JSONObject releaseJsonObject = versions.getJSONObject(i);
            // Considera solo le versioni che hanno sia un nome che una data di rilascio
            if (releaseJsonObject.has("releaseDate") && releaseJsonObject.has("name")) {
                String releaseDate = releaseJsonObject.get("releaseDate").toString();
                String releaseName = releaseJsonObject.get("name").toString();
                releaseList.add(new Release(releaseName, LocalDate.parse(releaseDate)));
            }
        }

        // Ordina le release per data e assegna un ID sequenziale
        releaseList.sort(Comparator.comparing(Release::getDate));
        int j = 0;
        for (Release release : releaseList) {
            release.setId(++j);
        }
        return releaseList;
    }


    /**
     * Recupera tutti i ticket di tipo "Bug" che sono stati risolti e chiusi
     */
    public List<Ticket> getTickets(List<Release> releasesList) throws IOException {

        int j;
        int i = 0;
        int total;
        List<Ticket> ticketsList = new ArrayList<>();
        // Ciclo per gestire la paginazione dell'API di Jira
        do {
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + this.projName + "%22AND%22issueType%22=%22Bug%22AND" +
                    "(%22status%22=%22Closed%22OR%22status%22=%22Resolved%22)" +
                    "AND%22resolution%22=%22Fixed%22&fields=key,versions,created,resolutiondate&startAt="
                    + i + "&maxResults=" + j;
            JSONObject json = JiraUtils.readJsonFromUrl(url);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");

            // Itera sui ticket scaricati
            for (; i < total && i < j; i++) {
                // Analizza il JSON di un singolo ticket e ne estrae i dati
                String key = issues.getJSONObject(i%1000).get("key").toString();
                JSONObject fields = issues.getJSONObject(i%1000).getJSONObject("fields");
                String creationDateString = fields.get("created").toString();
                String resolutionDateString = fields.get("resolutiondate").toString();
                LocalDate creationDate = LocalDate.parse(creationDateString.substring(0,10));
                LocalDate resolutionDate = LocalDate.parse(resolutionDateString.substring(0,10));
                JSONArray affectedVersionsArray = fields.getJSONArray("versions");

                // Calcola Opening Version (OV) e Fixed Version (FV) basandosi sulle date
                Release openingVersion = JiraUtils.getReleaseAfterOrEqualDate(creationDate, releasesList);
                Release fixedVersion =  JiraUtils.getReleaseAfterOrEqualDate(resolutionDate, releasesList);

                // Ottiene la lista di Affected Versions
                List<Release> affectedVersionsList = JiraUtils.returnAffectedVersions(affectedVersionsArray, releasesList);

                // Scarta i ticket dove le Affected Version (AV) sono incoerenti con la Opening Version (OV), o dove la OV è successiva alla FV
                if(!affectedVersionsList.isEmpty() && openingVersion!=null && fixedVersion!=null && (!affectedVersionsList.get(0).getDate().isBefore(openingVersion.getDate()) || openingVersion.getDate().isAfter(fixedVersion.getDate()))){
                    continue;
                }

                // Scarta i ticket la cui OV coincide con la prima release del progetto
                if(openingVersion != null && fixedVersion != null && openingVersion.getId()!=releasesList.get(0).getId()){
                    ticketsList.add(new Ticket(key, creationDate, resolutionDate, openingVersion, fixedVersion, affectedVersionsList));
                }

            }
        } while (i < total);
        ticketsList.sort(Comparator.comparing(Ticket::getResolutionDate));
        return ticketsList;
    }

    /**
     * Metodo che orchestra l'estrazione e l'arricchimento dei ticket con IV e AV calcolate
     */
    public List<Ticket> getFinalTickets(List<Release> releasesList, boolean fix) throws IOException, JSONException {
        List<Ticket> ticketsList = getTickets(releasesList);

        if(fix) {
            List<Ticket> newTicketList = JiraUtils.addIVandAV(ticketsList, releasesList);
            newTicketList.sort(Comparator.comparing(Ticket::getResolutionDate));
            return newTicketList;
        }else{
            return ticketsList;
        }
    }

}
