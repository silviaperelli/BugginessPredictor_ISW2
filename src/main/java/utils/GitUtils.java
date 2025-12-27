package utils;

import model.Release;
import org.eclipse.jgit.revwalk.RevCommit;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public class GitUtils {

    private GitUtils() {}

    /**
     * Determina a quale release appartiene un determinato commit, basandosi sulla data del commit.
     * Si assume che la lista delle release sia ordinata cronologicamente.
     */
    public static Release getReleaseOfCommit(RevCommit commit, List<Release> releaseList) {

        // Estrae la data del commit
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        LocalDate commitDate = LocalDate.parse(formatter.format(commit.getCommitterIdent().getWhen()));
        LocalDate lowerBoundDate = LocalDate.parse(formatter.format(new Date(0)));

        // Itera su ogni release per trovare quella che contiene la data del commit
        for (Release release : releaseList) {
            LocalDate dateOfRelease = release.getDate();
            // Un commit appartiene a una release se la sua data è compresa nell'intervallo
            // (data_release_precedente, data_release_corrente]
            if (commitDate.isAfter(lowerBoundDate) && !commitDate.isAfter(dateOfRelease)) {
                return release;
            }
            // Aggiorna il limite inferiore del periodo con la data della release appena controllata
            lowerBoundDate = dateOfRelease;
        }
        return null;
    }
}
