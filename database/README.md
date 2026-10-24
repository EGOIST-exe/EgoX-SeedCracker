# EgoX Google-Backend

Die [Community-Tabelle](https://docs.google.com/spreadsheets/d/1y7HEf0x_j0Dg6THsi-lTT3T_u0-PdP0Q8SikVPsVacg/edit) ist als Betrachter-Link freigegeben. Der [Apps-Script-Code](https://script.google.com/home/projects/1TczqzEdvaQc_PnAQNbagAvuVV6KDVSLwgoGrJUEzkpkS8PJMNcOU5G8O/edit) liegt im Google-Konto des Eigentümers. Der öffentliche Endpunkt ist in `EgoXConfig` vorkonfiguriert und antwortet auf GET mit einem Schema-/Gesundheitsstatus.

`Code.gs` enthält eine lesbare Fassung des eingesetzten Backends. Der Upload akzeptiert ausschließlich Hash-bestätigte Welt-Seeds, speichert 64-Bit-Werte als Text, prüft die Record-ID, begrenzt Feldgrößen und behandelt Duplikate unter einem Script-Lock. Ein Grenzwert von 2.000 neuen Einträgen pro Stunde begrenzt reguläre Last. Google-Quoten gelten zusätzlich; dies ist keine umfassende Abwehr gegen absichtlichen Missbrauch eines öffentlichen Dienstes.

Der Hash belegt die Seed/Hash-Konsistenz, nicht die Zugehörigkeit zum behaupteten Server. Die Freigabespalte bleibt deshalb `UNREVIEWED`, bis die Datenbankverwaltung den Eintrag geprüft hat. Die Öffentlichkeit erhält Leserechte an der Tabelle und Schreibzugriff nur über die validierende Web-App.

## Änderungen und eigene Instanz

1. Eigene Google-Tabelle mit denselben 14 Spalten und einem Tab `Seeds` anlegen oder die bestehende Tabelle kopieren.
2. `EGOX_SHEET_ID` in `Code.gs` auf diese Tabelle setzen.
3. Code in ein eigenes Apps-Script-Projekt übernehmen und als Web-App veröffentlichen: Ausführen als Eigentümer, Zugriff `Jeder`.
4. Google-Tabellenzugriff im eigenen Konto autorisieren. Der verwendete `SpreadsheetApp.openById`-Aufruf erfordert den allgemeinen Sheets-OAuth-Scope; der Code öffnet ausschließlich die fest eingetragene Tabellen-ID.
5. Neue `/exec`-URL und Tabellen-URL in der Mod-Konfiguration eintragen. Nach Codeänderungen eine neue Web-App-Version bereitstellen; bloßes Speichern aktualisiert die veröffentlichte Version nicht.

Optional kann die Script-Property `UPLOAD_TOKEN` gesetzt werden. Nutzer müssen denselben Token dann in ihrer lokalen `uploadToken`-Konfiguration eintragen. Ein Token in einem öffentlich verteilten Client ist kein sicherer Identitätsnachweis.

Die lokale Queue löscht einen Upload erst nach einer erfolgreichen JSON-Antwort mit passender Record-ID. HTTP 200, Weiterleitung oder eine Google-Anmeldeseite allein gelten nicht als erfolgreiche Speicherung.

Backend-Prüfungen ohne Netzwerkzugriff:

```text
node database/test-backend.mjs
```

Das Backend basiert auf Googles [Web-App-Schnittstelle](https://developers.google.com/apps-script/guides/web) und [SpreadsheetApp](https://developers.google.com/apps-script/reference/spreadsheet/spreadsheet-app). Zum Synchronisieren von Code und Bereitstellungen ist weiterhin das eigene Google-Konto erforderlich.
