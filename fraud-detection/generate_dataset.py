"""Génère un jeu de données SYNTHÉTIQUE au format de bank_transactions.csv.

À utiliser seulement si le fichier officiel de l'épreuve n'est pas disponible.
Les fraudes (~2 %) sont plus fréquentes la nuit, pour des montants élevés, dans certains pays
et catégories, et quand l'activité récente s'écarte de la moyenne du compte.
Les variables historiques sont calculées sans utiliser la transaction courante.
"""
import sys
from pathlib import Path

import numpy as np
import pandas as pd

N_ROWS = 20_000
SEED = 42
COUNTRIES = ["FR", "MA", "ES", "DE", "US", "NG", "RU", "BR"]
CATEGORIES = ["GROCERY", "FUEL", "TRAVEL", "ELECTRONICS", "RESTAURANT", "ONLINE", "JEWELRY"]


def generate(n_rows: int = N_ROWS, seed: int = SEED) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    account_ids = [f"ACC{i:04d}" for i in rng.integers(0, 1500, n_rows)]
    avg_7d = np.round(rng.lognormal(mean=4.0, sigma=0.5, size=n_rows), 2)
    amount = np.round(avg_7d * rng.lognormal(mean=0.0, sigma=0.6, size=n_rows), 2)
    hour = rng.integers(0, 24, n_rows)
    country = rng.choice(COUNTRIES, n_rows, p=[0.35, 0.25, 0.1, 0.1, 0.08, 0.04, 0.04, 0.04])
    category = rng.choice(CATEGORIES, n_rows, p=[0.25, 0.15, 0.1, 0.1, 0.2, 0.15, 0.05])
    tx_24h = rng.poisson(2.0, n_rows)

    # Score de risque latent -> probabilité de fraude (~2 % en moyenne)
    score = (
        -6.0
        + 1.2 * (amount / avg_7d > 3)
        + 0.9 * ((hour <= 5) | (hour >= 23))
        + 1.0 * np.isin(country, ["NG", "RU", "BR"])
        + 0.8 * np.isin(category, ["ELECTRONICS", "JEWELRY", "ONLINE"])
        + 0.5 * (tx_24h >= 6)
        + 0.001 * np.minimum(amount, 3000)
    )
    is_fraud = (rng.random(n_rows) < 1 / (1 + np.exp(-score))).astype(int)

    df = pd.DataFrame({
        "transaction_id": [f"TX{i:06d}" for i in range(n_rows)],
        "account_id": account_ids,
        "amount": amount,
        "hour": hour,
        "country": country,
        "merchant_category": category,
        "transactions_last_24h": tx_24h,
        "average_amount_last_7d": avg_7d,
        "is_fraud": is_fraud,
    })
    # Valeurs manquantes réalistes sur quelques colonnes
    for column, rate in [("average_amount_last_7d", 0.03), ("country", 0.01), ("transactions_last_24h", 0.02)]:
        df.loc[rng.random(n_rows) < rate, column] = np.nan
    return df


if __name__ == "__main__":
    target = Path(sys.argv[1] if len(sys.argv) > 1 else "bank_transactions.csv")
    frame = generate()
    frame.to_csv(target, index=False)
    print(f"{len(frame)} lignes écrites dans {target} (fraudes : {frame['is_fraud'].mean():.2%})")
