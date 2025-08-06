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
print("Creazione del grafico per Kappa...")

fig, axes = plt.subplots(nrows=1, ncols=3, figsize=(18, 5), sharey=True)
classificatori = ['IBk', 'NaiveBayes', 'RandomForest']
colore = 'lightsalmon'
larghezza_box = 0.5

for i, clf in enumerate(classificatori):
    dati_clf = df[df['Classifier'] == clf]

    sns.boxplot(
        data=dati_clf, x='Tecnica', y='Kappa', ax=axes[i],
        color=colore, order=ordine_tecniche,
        width=larghezza_box
    )
    axes[i].set_title(f'{clf}')
    axes[i].set_xlabel('')
    axes[i].tick_params(axis='x', rotation=45)
    if i == 0:
        axes[i].set_ylabel('Kappa')
    else:
        axes[i].set_ylabel('')

# --- 3. FORMATTAZIONE FINALE ---
fig.suptitle(f'Distribuzione Kappa per {nome_progetto}', fontsize=18, y=0.98)
plt.tight_layout(rect=[0, 0, 1, 0.92])

dest_path = f'{nome_progetto.lower()}/boxplot_kappa.png'
plt.savefig(dest_path, dpi=300)
print(f"Grafico Kappa salvato come: {dest_path}")

# plt.show()
