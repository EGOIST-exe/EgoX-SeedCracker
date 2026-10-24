import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import { Workbook, SpreadsheetFile } from '@oai/artifact-tool';

const output = new URL('../outputs/egox/', import.meta.url);
await fs.mkdir(output, {recursive:true});
const wb = Workbook.create();
const seeds = wb.worksheets.add('Seeds');
seeds.showGridLines = false;
const headers = ['ID','Zeitpunkt (UTC)','Serveradresse','Dimension','Minecraft','Welt-Seed','Struktur-Seed','Seed-Hash','Prüfung','Biom-Samples','Strukturstarts','Salts (JSON)','Methode','Freigabe'];
seeds.getRange('A1:N1').values = [headers];
seeds.getRange('A1:N25').format.font = {name:'Arial',size:10};
seeds.getRange('A1:N1').format = {fill:'#183527',font:{name:'Arial',size:10,bold:true,color:'#FFFFFF'},horizontalAlignment:'center',verticalAlignment:'center',rowHeight:28};
seeds.getRange('A2:N1000').setNumberFormat('@');
seeds.getRange('A:A').format.columnWidth=18;
seeds.getRange('B:B').format.columnWidth=26;
seeds.getRange('C:D').format.columnWidth=28;
seeds.getRange('E:E').format.columnWidth=14;
seeds.getRange('F:H').format.columnWidth=26;
seeds.getRange('I:I').format.columnWidth=23;
seeds.getRange('J:K').format.columnWidth=18;
seeds.getRange('L:M').format.columnWidth=28;
seeds.getRange('N:N').format.columnWidth=18;
seeds.freezePanes.freezeRows(1);
seeds.freezePanes.freezeColumns(3);
const help = wb.worksheets.add('Hinweise');
help.showGridLines=false;
help.getRange('A2:B2').values=[['EgoX Seed-Datenbank','']];
help.getRange('A2:B2').format.font={name:'Arial',size:14,bold:true};
help.getRange('A4:B11').values=[
 ['Status','Noch keine echten Seeds eingetragen.'],
 ['Automatische Speicherung','Die Mod speichert gefundene Seeds zuerst lokal.'],
 ['Upload','Nach Einrichtung des Apps-Script-Endpunkts werden offene Einträge automatisch übertragen.'],
 ['Prüfung','HASH_VERIFIED bedeutet: Der Seed stimmt mit dem vom Minecraft-Server gesendeten Hash überein.'],
 ['Freigabe','Neue Einsendungen bleiben UNREVIEWED bis zur Prüfung durch die Datenbankverwaltung.'],
 ['Zahlenpräzision','Seeds und Hashes sind Textwerte, damit alle 64 Bits erhalten bleiben.'],
 ['Serveradresse','Gespeichert wird die Adresse des Spielservers, kein Spielername oder Spieler-Login-Token.'],
 ['Community','https://discord.gg/FFuMpJg6yj']
];
help.getRange('A4:B11').format.font={name:'Arial',size:10};
help.getRange('A:A').format.columnWidth=26;
help.getRange('B:B').format.columnWidth=105;
help.getRange('A4:B11').format.rowHeight=24;
wb.recalculate();
console.log((await wb.inspect({kind:'table',range:'Seeds!A1:N3',tableMaxRows:3,tableMaxCols:14,maxChars:1800})).ndjson);
for(const [sheetName,range] of [['Seeds','A1:I5'],['Hinweise','A1:B12']]){
 const png=await wb.render({sheetName,range,scale:1.3,format:'png'});
 await fs.writeFile(new URL(`${sheetName}.png`,output),new Uint8Array(await png.arrayBuffer()));
}
const file=await SpreadsheetFile.exportXlsx(wb);
await file.save(fileURLToPath(new URL('EgoX-Seed-Datenbank.xlsx',output)));
console.log('Database workbook saved');
