import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt
import os

# --- CONFIGURAZIONE ---
# Modifica solo questa riga per cambiare progetto
nome_progetto = 'BOOKKEEPER'

# Percorsi dei file di input e cartella di output
file_risultati_cv = f'../finalAcumeFiles/{nome_progetto.lower()}_acume_cv.csv'
file_risultati_temporal = f'../finalAcumeFiles/{nome_progetto.lower()}_acume_temporal.csv'
cartella_output = nome_progetto.lower()

# --- FUNZIONI HELPER ---

def estrai_classifier(filename):
    """Estrae il nome del classificatore dal nome del file."""
    # Ritorna la prima parte del nome del file, che è il classificatore base
    # es. "RandomForest_CostSensitive_run10.csv" -> "RandomForest"
    return filename.split('_')[0]

def estrai_tecnica(filename):
    """Estrae le tecniche usate dal nome del file."""
    # Questa funzione ora deve gestire le combinazioni
    tecnica = []
    if 'BestFirst' in filename:
        tecnica.append('BestFirst')
    if 'SMOTE' in filename:
        tecnica.append('SMOTE')
    if 'Sensitive' in filename or 'CostSensitive' in filename:
        tecnica.append('Sensitive')

    # sorted() assicura che "SMOTE + BestFirst" sia sempre uguale a "BestFirst + SMOTE"
    return ' + '.join(sorted(tecnica)) if tecnica else 'none'

def crea_e_salva_boxplot_npofb20(file_input, file_output, titolo_principale):
    """
    Carica i dati ACUME da un file CSV riassuntivo, crea un boxplot per NPofB20 e lo salva.
    """
    print(f"--- Inizio elaborazione NPofB20 per: {titolo_principale} ---")

    # 1. CARICAMENTO E PREPARAZIONE DEI DATI
    print(f"Caricamento dati dal file: {file_input}")
    try:
        df = pd.read_csv(file_input)
    except FileNotFoundError:
        print(f"ERRORE: File '{file_input}' non trovato. Salto questo grafico.")
        return

    # Applica le funzioni per estrarre le colonne necessarie dalla colonna 'Filename'
    df['Classifier'] = df['Filename'].apply(estrai_classifier)
    df['Tecnica'] = df['Filename'].apply(estrai_tecnica)

    # Definisci l'ordine desiderato per le tecniche sull'asse X
    ordine_tecniche = ['none', 'BestFirst', 'SMOTE', 'Sensitive', 'BestFirst + SMOTE', 'BestFirst + Sensitive']

    # 2. CREAZIONE DEL GRAFICO
    print("Creazione del grafico per NPofB20...")

    fig, axes = plt.subplots(nrows=1, ncols=3, figsize=(18, 6), sharey=False) # sharey=False è cruciale

    classificatori = ['IBk', 'NaiveBayes', 'RandomForest']
    titoli_grafici = ['IBk', 'NaiveBayes', 'RandomForest']
    colore_box = 'lightcoral'
    larghezza_box = 0.5

    for i, clf in enumerate(classificatori):
        dati_clf = df[df['Classifier'] == clf]

        sns.boxplot(
            data=dati_clf, x='Tecnica', y='Npofb20', ax=axes[i],
            color=colore_box, order=ordine_tecniche,
            width=larghezza_box
        )
        axes[i].set_title(f'{titoli_grafici[i]}')
        axes[i].set_xlabel('')
        axes[i].tick_params(axis='x', rotation=45)
        axes[i].set_ylabel('Npofb20')

    # 3. FORMATTAZIONE FINALE
    fig.suptitle(titolo_principale, fontsize=18, y=0.98)
    plt.tight_layout(rect=[0, 0, 1, 0.92])

    os.makedirs(os.path.dirname(file_output), exist_ok=True)
    plt.savefig(file_output, dpi=300)
    print(f"Grafico salvato con successo come: {file_output}")
    plt.close(fig)


# --- ESECUZIONE PRINCIPALE ---
crea_e_salva_boxplot_npofb20(
    file_input=file_risultati_cv,
    file_output=f'{cartella_output}/boxplot_npofb20_cv.png',
    titolo_principale=f'Distribuzione NPofB20 per {nome_progetto} (Cross-Validation)'
)
print("\n" + "="*50 + "\n")
crea_e_salva_boxplot_npofb20(
    file_input=file_risultati_temporal,
    file_output=f'{cartella_output}/boxplot_npofb20_temporal.png',
    titolo_principale=f'Distribuzione NPofB20 per {nome_progetto} (Temporal Validation)'
)
print("\nElaborazione completata.")