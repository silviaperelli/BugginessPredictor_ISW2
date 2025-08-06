import pandas as pd
import seaborn as sns
import matplotlib.pyplot as plt

# --- CONFIGURAZIONE ---
nome_progetto = 'BOOKKEEPER'
file_risultati = f'../finalAcumeFiles/{nome_progetto.lower()}_acume.csv'

# --- 1. CARICAMENTO E PREPARAZIONE DEI DATI ---
print(f"Caricamento dati dal file: {file_risultati}")
try:
    df = pd.read_csv(file_risultati)
except FileNotFoundError:
    print(f"ERRORE: File '{file_risultati}' non trovato.")
    exit()

def estrai_classifier(filename):
    return filename.split('_')[0]

def estrai_tecnica(filename):
    tecnica = []
    if 'BestFirst' in filename:
        tecnica.append('BestFirst')
    if 'SMOTE' in filename:
        tecnica.append('SMOTE')
    if 'Sensitive' in filename or 'SensitiveThreshold' in filename:
        tecnica.append('Sensitive')
    return ' + '.join(tecnica) if tecnica else 'none'

df['Classifier'] = df['Filename'].apply(estrai_classifier)
df['Tecnica'] = df['Filename'].apply(estrai_tecnica)

ordine_tecniche = ['none', 'BestFirst', 'SMOTE', 'Sensitive', 'BestFirst + SMOTE', 'BestFirst + Sensitive']

# --- 2. CREAZIONE DEL GRAFICO PER NPOFB20 ---
print("Creazione del grafico per NPofB20...")

fig, axes = plt.subplots(nrows=1, ncols=3, figsize=(18, 5), sharey=True)
classificatori = ['IBk', 'NaiveBayes', 'RandomForest']
colore_box = 'lightcoral'
larghezza_box = 0.5

for i, clf in enumerate(classificatori):
    dati_clf = df[df['Classifier'] == clf]

    sns.boxplot(
        data=dati_clf, x='Tecnica', y='Npofb20', ax=axes[i],
        color=colore_box, order=ordine_tecniche,
        width=larghezza_box
    )
    axes[i].set_title(f'{clf}')
    axes[i].set_xlabel('')
    axes[i].tick_params(axis='x', rotation=45)
    if i == 0:
        axes[i].set_ylabel('Npofb20')
    else:
        axes[i].set_ylabel('')

# --- 3. FORMATTAZIONE FINALE ---
fig.suptitle(f'Distribuzione NPofB20 per {nome_progetto}', fontsize=18, y=0.98)
plt.tight_layout(rect=[0, 0, 1, 0.92])

dest_path = f'{nome_progetto.lower()}/boxplot_npofb20.png'
plt.savefig(dest_path, dpi=300)
print(f"Grafico NPofB20 salvato come: {dest_path}")

# plt.show()
