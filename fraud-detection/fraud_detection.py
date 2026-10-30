"""Partie H : détection de fraude bancaire (classification binaire).

Exécution :  python fraud_detection.py [chemin/vers/bank_transactions.csv]
Si le fichier n'existe pas, un jeu synthétique est généré (voir generate_dataset.py).
"""
import sys
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    average_precision_score,
    classification_report,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

RANDOM_STATE = 42
ID_COLUMNS = ["transaction_id", "account_id"]
TARGET = "is_fraud"
NUMERIC = ["amount", "hour", "transactions_last_24h", "average_amount_last_7d"]
CATEGORICAL = ["country", "merchant_category"]


def load_data(path: Path) -> pd.DataFrame:
    # 1. Charger les données
    if not path.exists():
        from generate_dataset import generate
        print(f"[!] {path} introuvable : jeu de données SYNTHÉTIQUE généré.\n")
        df = generate()
        df.to_csv(path, index=False)
        return df
    return pd.read_csv(path)


def build_pipeline(model) -> Pipeline:
    # 5. Imputation, encodage des catégories et normalisation : ajustés sur l'entraînement seulement
    numeric = Pipeline([("imputer", SimpleImputer(strategy="median")), ("scaler", StandardScaler())])
    categorical = Pipeline([
        ("imputer", SimpleImputer(strategy="most_frequent")),
        ("encoder", OneHotEncoder(handle_unknown="ignore")),
    ])
    preprocessing = ColumnTransformer([("num", numeric, NUMERIC), ("cat", categorical, CATEGORICAL)])
    return Pipeline([("preprocessing", preprocessing), ("model", model)])


def evaluate(name: str, pipeline: Pipeline, x_test, y_test, threshold: float = 0.5) -> dict:
    scores = pipeline.predict_proba(x_test)[:, 1]
    predicted = (scores >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_test, predicted).ravel()
    result = {
        "model": name,
        "precision": precision_score(y_test, predicted, zero_division=0),
        "recall": recall_score(y_test, predicted),
        "f1": f1_score(y_test, predicted),
        "average_precision": average_precision_score(y_test, scores),
        "tn": tn, "fp": fp, "fn": fn, "tp": tp,
    }
    print(f"--- {name} (seuil {threshold}) ---")
    print(f"Précision (fraude) : {result['precision']:.3f}")
    print(f"Rappel (fraude)    : {result['recall']:.3f}")
    print(f"F1-score (fraude)  : {result['f1']:.3f}")
    print(f"Average precision  : {result['average_precision']:.3f}")
    print("Matrice de confusion [lignes = réel, colonnes = prédit]")
    print(f"              prédit 0   prédit 1\n  réel 0   {tn:10d} {fp:10d}\n  réel 1   {fn:10d} {tp:10d}")
    print(classification_report(y_test, predicted, target_names=["légitime", "fraude"], zero_division=0))
    return result


def main() -> None:
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "bank_transactions.csv")
    df = load_data(path)
    print(f"Données : {df.shape[0]} lignes, {df.shape[1]} colonnes\n")

    # 2. Proportion de fraudes et valeurs manquantes
    print("Proportion de fraudes :")
    print(df[TARGET].value_counts(normalize=True).rename({0: "légitime", 1: "fraude"}).map("{:.2%}".format))
    print("\nValeurs manquantes par colonne :")
    print(df.isna().sum().to_string(), "\n")

    # 4. Les identifiants sont exclus des variables explicatives
    x = df.drop(columns=ID_COLUMNS + [TARGET])
    y = df[TARGET]

    # 3. Séparation entraînement/test stratifiée
    x_train, x_test, y_train, y_test = train_test_split(
        x, y, test_size=0.25, stratify=y, random_state=RANDOM_STATE)
    print(f"Entraînement : {len(x_train)} lignes ({y_train.mean():.2%} fraudes) | "
          f"Test : {len(x_test)} lignes ({y_test.mean():.2%} fraudes)\n")

    # 6. Modèles simples. class_weight compense le déséquilibre des classes.
    models = {
        "Régression logistique": LogisticRegression(max_iter=1000, class_weight="balanced"),
        "Random forest": RandomForestClassifier(
            n_estimators=200, min_samples_leaf=5, class_weight="balanced_subsample",
            random_state=RANDOM_STATE, n_jobs=-1),
    }
    results = []
    for name, model in models.items():
        pipeline = build_pipeline(model).fit(x_train, y_train)  # prétraitement ajusté sur train uniquement
        # 7. Évaluation sur le test, avec le seuil par défaut 0.5 (non choisi sur le test)
        results.append(evaluate(name, pipeline, x_test, y_test))

    # Accuracy seule : référence triviale (toujours « légitime »)
    baseline_accuracy = 1 - y_test.mean()
    print(f"Accuracy d'un modèle qui prédit toujours « légitime » : {baseline_accuracy:.2%}")
    print("-> d'où l'insuffisance de l'accuracy seule (question 1).")


if __name__ == "__main__":
    main()
