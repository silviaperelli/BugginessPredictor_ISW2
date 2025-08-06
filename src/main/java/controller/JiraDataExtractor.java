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

public class JiraDataExtractor {

    private final String projName;

    public JiraDataExtractor(String projName) {
        this.projName = projName.toUpperCase();
    }

    public List<Release> getReleases() throws IOException {
        List<Release> releaseList = new ArrayList<>();
        String url = "https://issues.apache.org/jira/rest/api/latest/project/" + this.projName;
        JSONObject json = JiraUtils.readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        for (int i=0; i < versions.length(); i++) {
            //obtaining single json object
            JSONObject releaseJsonObject = versions.getJSONObject(i);
            //creating Release model
            if (releaseJsonObject.has("releaseDate") && releaseJsonObject.has("name")) {
                String releaseDate = releaseJsonObject.get("releaseDate").toString();
                String releaseName = releaseJsonObject.get("name").toString();
                releaseList.add(new Release(releaseName, LocalDate.parse(releaseDate)));
            }
        }

        //sorting the versions based on the release date
        releaseList.sort(Comparator.comparing(Release::getDate));
        int j = 0;
        for (Release release : releaseList) {
            release.setId(++j);
        }
        return releaseList;
    }


    public List<Ticket> getTickets(List<Release> releasesList) throws IOException {

        int j;
        int i = 0;
        int total;
        List<Ticket> ticketsList = new ArrayList<>();
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
            for (; i < total && i < j; i++) {
                //Iterate through each bug to retrieve ID, creation date, resolution date and affected versions
                String key = issues.getJSONObject(i%1000).get("key").toString();
                JSONObject fields = issues.getJSONObject(i%1000).getJSONObject("fields");
                String creationDateString = fields.get("created").toString();
                String resolutionDateString = fields.get("resolutiondate").toString();
                LocalDate creationDate = LocalDate.parse(creationDateString.substring(0,10));
                LocalDate resolutionDate = LocalDate.parse(resolutionDateString.substring(0,10));
                JSONArray affectedVersionsArray = fields.getJSONArray("versions");

                //to obtain the opening version and the fixed version I use the creation date and the release date
                Release openingVersion = JiraUtils.getReleaseAfterOrEqualDate(creationDate, releasesList);
                Release fixedVersion =  JiraUtils.getReleaseAfterOrEqualDate(resolutionDate, releasesList);

                //obtaining the affected releases
                List<Release> affectedVersionsList = JiraUtils.returnAffectedVersions(affectedVersionsArray, releasesList);

                //checking if the ticket is not valid
                if(!affectedVersionsList.isEmpty() && openingVersion!=null && fixedVersion!=null && (!affectedVersionsList.get(0).getDate().isBefore(openingVersion.getDate()) || openingVersion.getDate().isAfter(fixedVersion.getDate()))){
                    continue;
                }

                //the opening version must be different from the first release
                if(openingVersion != null && fixedVersion != null && openingVersion.getId()!=releasesList.get(0).getId()){
                    ticketsList.add(new Ticket(key, creationDate, resolutionDate, openingVersion, fixedVersion, affectedVersionsList));
                }

            }
        } while (i < total);
        ticketsList.sort(Comparator.comparing(Ticket::getResolutionDate));
        return ticketsList;
    }

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
