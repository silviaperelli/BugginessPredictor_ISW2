import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import os

# --- CONFIGURAZIONE ---
# Modifica solo questa riga per cambiare progetto
nome_progetto = 'BOOKKEEPER'

# Percorsi dei file di input e cartella di output
file_risultati_cv = f'../wekaFiles/{nome_progetto.lower()}/classificationResults_cv.csv'
file_risultati_temporal = f'../wekaFiles/{nome_progetto.lower()}/classificationResults_temporal.csv'
cartella_output = nome_progetto.lower()

# --- FUNZIONI HELPER ---

def crea_etichetta_tecnica(row):
    """Crea un'etichetta descrittiva per le tecniche di classificazione usate."""
    fs = row['FeatureSelection']
    samp = row['Sampling']
    cs = row['CostSensitive']

    if fs != 'none' and samp == 'none' and cs == 'none':
        return 'BestFirst'
    if fs == 'none' and samp != 'none' and cs == 'none':
        return 'SMOTE'
    if fs == 'none' and samp == 'none' and cs != 'none':
        return 'Sensitive'
    return 'none'

def crea_e_salva_boxplot_prec_recall(file_input, file_output, titolo_principale):
    """
    Carica i dati da un file CSV, crea un boxplot combinato per Precision/Recall e lo salva.
    """
    print(f"--- Inizio elaborazione Precision/Recall per: {titolo_principale} ---")

    # 1. CARICAMENTO E PREPARAZIONE DEI DATI
    print(f"Caricamento dati dal file: {file_input}")
    try:
        df = pd.read_csv(file_input)
    except FileNotFoundError:
        print(f"ERRORE: File '{file_input}' non trovato. Salto questo grafico.")
        return

    df['Tecnica'] = df.apply(crea_etichetta_tecnica, axis=1)
    ordine_tecniche = ['none', 'BestFirst', 'SMOTE', 'Sensitive']

    # 2. CREAZIONE DEL GRAFICO COMBINATO
    print("Creazione del grafico combinato per Precision e Recall...")

    fig, axes = plt.subplots(nrows=2, ncols=3, figsize=(18, 10), sharex=True, sharey='row')
    classificatori = ['IBk', 'NaiveBayes', 'RandomForest']
    colori = {'Precision': 'lightcoral', 'Recall': 'mediumpurple'} # Colori diversi per distinguerlo
    larghezza_box = 0.5

    for i, clf in enumerate(classificatori):
        dati_clf = df[df['Classifier'] == clf]

        # --- CORREZIONE: Grafico PRECISION (RIGA SUPERIORE) ---
        sns.boxplot(
            data=dati_clf, x='Tecnica', y='Precision', ax=axes[0, i], # <-- Corretto da F1-Score
            color=colori['Precision'], order=ordine_tecniche,
            width=larghezza_box
        )
        axes[0, i].set_title(f'{clf}')
        axes[0, i].set_xlabel('')
        axes[0, i].set_ylabel('Precision') # <-- Etichetta corretta

        # --- CORREZIONE: Grafico RECALL (RIGA INFERIORE) ---
        sns.boxplot(
            data=dati_clf, x='Tecnica', y='Recall', ax=axes[1, i], # <-- Corretto da AUC
            color=colori['Recall'], order=ordine_tecniche,
            width=larghezza_box
        )
        axes[1, i].set_ylabel('Recall') # <-- Etichetta corretta
        axes[1, i].set_xlabel('')
        axes[1, i].tick_params(axis='x', rotation=45)

    # 3. FORMATTAZIONE FINALE
    fig.suptitle(titolo_principale, fontsize=18, y=0.97)
    plt.tight_layout(rect=[0, 0, 1, 0.95])

    # Assicura che la cartella di output esista
    os.makedirs(os.path.dirname(file_output), exist_ok=True)

    plt.savefig(file_output, dpi=300)
    print(f"Grafico salvato con successo come: {file_output}")
    plt.close(fig) # Chiude la figura per liberare memoria


# --- ESECUZIONE PRINCIPALE ---

# Grafico Precision/Recall per la Cross-Validation
crea_e_salva_boxplot_prec_recall(
    file_input=file_risultati_cv,
    file_output=f'{cartella_output}/boxplot_prec_recall_cv.png',
    titolo_principale=f'Distribuzione Precision e Recall per {nome_progetto} (Cross-Validation)'
)

print("\n" + "="*50 + "\n") # Separatore per chiarezza

# Grafico Precision/Recall per la Validazione Temporale
crea_e_salva_boxplot_prec_recall(
    file_input=file_risultati_temporal,
    file_output=f'{cartella_output}/boxplot_prec_recall_temporal.png',
    titolo_principale=f'Distribuzione Precision e Recall per {nome_progetto} (Temporal Validation)'
)

print("\nElaborazione completata.")