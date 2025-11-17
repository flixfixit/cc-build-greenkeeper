# cc-build-greenkeeper (Java CLI)

Ein CLI, um alte, löschbare Builds in SAP Commerce Cloud zu listen und (optional) zu löschen.

## Installation

```bash
mvn -q -DskipTests package
# fat jar
ls target/*jar-with-dependencies.jar
```

## Konfiguration

Setze entweder **Flags** oder **ENV Variablen**:

- `CC_API_BASE` – Basis-URL der Commerce Cloud API (z. B. https://api.eu.commercecloud.sap )
- `CC_API_TOKEN` – Bearer Token
- `CC_SUBSCRIPTION` – Subscription Code
- `CC_BUILDS_PATH` – Pfad zur Build-Collection (Default: `/builds`, wird automatisch unter `/v2/subscriptions/{subscription}` gehangen)
- `CC_DELETE_TEMPLATE` – Pfad-Template zum Löschen (Default: `/builds/%s` – `%s` wird durch buildCode ersetzt; ebenfalls automatisch unter `/v2/subscriptions/{subscription}`)

> Hinweis: Die exakten Endpunkte können je nach Tenant/Region leicht variieren. Passe ggf. `CC_BUILDS_PATH` und `CC_DELETE_TEMPLATE` an deine Dokumentation an.

## Beispiele

### 1) Die 20 ältesten Builds anzeigen, älter als 45 Tage

```bash
java -jar target/cc-build-greenkeeper-0.1.0-jar-with-dependencies.jar \
  list --older-than 45d --limit 20 \
  --base "$CC_API_BASE" --token "$CC_API_TOKEN" --subscription "$CC_SUBSCRIPTION"
```

### 2) Die 10 ältesten löschbaren Builds löschen (Dry‑Run)

```bash
java -jar target/cc-build-greenkeeper-0.1.0-jar-with-dependencies.jar \
  prune --older-than 30d --limit 10 \
  --base "$CC_API_BASE" --token "$CC_API_TOKEN" --subscription "$CC_SUBSCRIPTION"
```

### 3) Tatsächlich löschen (mit Schutz der neuesten 5)

```bash
java -jar target/cc-build-greenkeeper-0.1.0-jar-with-dependencies.jar \
  prune --older-than 30d --limit 10 --keep-latest 5 --execute \
  --base "$CC_API_BASE" --token "$CC_API_TOKEN" --subscription "$CC_SUBSCRIPTION"
```

## Sicherheit & Best Practices

- Standard ist **Dry‑Run**. Setze `--execute`, um wirklich zu löschen.
- Pinned/aktive/benutzte Builds werden standardmäßig **nicht** gelöscht.
- Logs in der Konsole (kannst du leicht in JSON/CSV erweitern).
- Paginierung: `--page-size` (Default 100) und automatische Seitenabfrage.

## Erweiterung zu GUI

- Das CLI ist so geschnitten, dass du eine **JavaFX**‑App leicht darüberlegen kannst (Tabelle, Filter, "Delete"‑Button). Business‑Logik bleibt in `Client`/`Util`.

## Mapping‑Hinweis

- Das Model `Models.Build` nimmt an, dass die API Felder wie `code`, `name`, `status`, `createdAt`, `pinned` liefert. Wenn eure API andere Feldnamen nutzt, kannst du in `Models.Build` die `@JsonProperty`‑Namen anpassen oder in `Client.listBuilds` die Konvertierung justieren.
