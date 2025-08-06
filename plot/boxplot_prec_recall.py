import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

# --- CONFIGURAZIONE ---
nome_progetto = 'BOOKKEEPER'
file_risultati = f'../wekaFiles/{nome_progetto.lower()}/classificationResults.csv'


# --- 1. CARICAMENTO E PREPARAZIONE DEI DATI ---
print(f"Caricamento dati dal file: {file_risultati}")
try:
    df = pd.read_csv(file_risultati)
except FileNotFoundError:
    print(f"ERRORE: File '{file_risultati}' non trovato.")
    exit()

def crea_etichetta_tecnica(row):
    fs = row['FeatureSelection']
    samp = row['Sampling']
    cs = row['CostSensitive']

    if fs != 'none' and samp == 'none' and cs == 'none':
        return 'BestFirst'
    if fs == 'none' and samp != 'none' and cs == 'none':
        return 'SMOTE'
    if fs == 'none' and samp == 'none' and cs != 'none':
        return 'Sensitive'
    if fs != 'none' and samp != 'none':
        return 'BestFirst + SMOTE'
    if fs != 'none' and cs != 'none':
        return 'BestFirst + Sensitive'
    return 'none'

df['Tecnica'] = df.apply(crea_etichetta_tecnica, axis=1)
ordine_tecniche = ['none', 'BestFirst', 'SMOTE', 'Sensitive', 'BestFirst + SMOTE', 'BestFirst + Sensitive']

# --- 2. CREAZIONE DEL GRAFICO COMBINATO ---
print("Creazione del grafico combinato per Precision e Recall...")

fig, axes = plt.subplots(nrows=2, ncols=3, figsize=(18, 10), sharex=True, sharey='row')
classificatori = ['IBk', 'NaiveBayes', 'RandomForest']
colori = {'Precision': 'lightskyblue', 'Recall': 'lightgreen'}
larghezza_box = 0.5

for i, clf in enumerate(classificatori):
    dati_clf = df[df['Classifier'] == clf]

    # === GRAFICO F1-SCORE (RIGA SUPERIORE) ===
    sns.boxplot(
        data=dati_clf, x='Tecnica', y='F1-Score', ax=axes[0, i],
        color=colori['Precision'], order=ordine_tecniche,
        width=larghezza_box
    )
    axes[0, i].set_title(f'{clf}')
    axes[0, i].set_xlabel('')
    axes[0, i].set_ylabel('Precision')

    # === GRAFICO AUC (RIGA INFERIORE) ===
    sns.boxplot(
        data=dati_clf, x='Tecnica', y='AUC', ax=axes[1, i],
        color=colori['Recall'], order=ordine_tecniche,
        width=larghezza_box
    )
    axes[1, i].set_ylabel('Recall')

    # --- MODIFICA APPLICATA QUI ---
    axes[1, i].set_xlabel('') # Impostiamo una stringa vuota per rimuovere l'etichetta

    axes[1, i].tick_params(axis='x', rotation=45)

# --- 3. FORMATTAZIONE FINALE ---
fig.suptitle(f'Distribuzione Precision e Recall per {nome_progetto}', fontsize=18, y=0.97)
plt.tight_layout(rect=[0, 0, 1, 0.95])

dest_path = f'{nome_progetto.lower()}/boxplot_prec_recall.png'
plt.savefig(dest_path, dpi=300)
print(f"Grafico combinato salvato come: {dest_path}")

# plt.show()