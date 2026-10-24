// EgoX Community Seed-Datenbank. No player names or Minecraft login tokens are accepted.
const EGOX_SHEET_ID = '1y7HEf0x_j0Dg6THsi-lTT3T_u0-PdP0Q8SikVPsVacg';
const EGOX_HEADERS = ['ID','Zeitpunkt (UTC)','Serveradresse','Dimension','Minecraft','Welt-Seed','Struktur-Seed','Seed-Hash','Prüfung','Biom-Samples','Strukturstarts','Salts (JSON)','Methode','Freigabe'];

function doGet() {
  return json_({ok:true, service:'EgoX Seed Database', schema:1});
}

function doPost(event) {
  let lock;
  try {
    if (!event || !event.postData || event.postData.contents.length > 16384) throw new Error('invalid_body');
    const body = JSON.parse(event.postData.contents);
    const requiredToken = PropertiesService.getScriptProperties().getProperty('UPLOAD_TOKEN') || '';
    if (requiredToken && body.token !== requiredToken) throw new Error('invalid_token');
    const record = validateRecord_(body.record);
    lock = LockService.getScriptLock();
    if (!lock.tryLock(10000)) throw new Error('busy_retry');
    const sheet = SpreadsheetApp.openById(EGOX_SHEET_ID).getSheetByName('Seeds');
    if (!sheet) throw new Error('missing_Seeds_sheet');
    const headers = sheet.getRange(1, 1, 1, EGOX_HEADERS.length).getDisplayValues()[0];
    if (headers.some((value,index) => value !== EGOX_HEADERS[index])) throw new Error('schema_mismatch');
    const rows = sheet.getLastRow();
    if (rows > 1 && sheet.getRange(2,1,rows-1,1).createTextFinder(record.id).matchEntireCell(true).findNext())
      return json_({ok:true,id:record.id,duplicate:true});
    const cache = CacheService.getScriptCache();
    const hour = new Date().toISOString().slice(0,13);
    const rateKey = 'rate:' + hour;
    const count = Number(cache.get(rateKey) || '0');
    if (count >= 2000) throw new Error('hourly_limit_retry');
    const row = [record.id, record.createdAt, record.server, record.dimension, record.minecraftVersion,
      record.worldSeed, record.structureSeed, record.seedHash, record.verification,
      record.biomeSamples, record.structureSamples, JSON.stringify(record.salts), record.source, 'UNREVIEWED'];
    const range = sheet.getRange(rows + 1, 1, 1, row.length);
    range.setNumberFormat('@');
    range.setValues([row.map(safeCell_)]);
    SpreadsheetApp.flush();
    cache.put(rateKey, String(count + 1), 3600);
    return json_({ok:true,id:record.id,duplicate:false});
  } catch (error) {
    return json_({ok:false,error:String(error.message || 'invalid_request').slice(0,100)});
  } finally {
    if (lock && lock.hasLock()) lock.releaseLock();
  }
}

function validateRecord_(r) {
  if (!r || typeof r !== 'object') throw new Error('missing_record');
  if (!/^[a-f0-9]{64}$/.test(r.id)) throw new Error('invalid_id');
  for (const field of ['server','dimension','minecraftVersion','createdAt','worldSeed','structureSeed','seedHash','verification','source'])
    if (typeof r[field] !== 'string' || !r[field].length || r[field].length > 256 || /[\r\n\u0000-\u001f]/.test(r[field])) throw new Error('invalid_' + field);
  if (!['26.3','1.21.11'].includes(r.minecraftVersion)) throw new Error('unsupported_version');
  if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(r.dimension)) throw new Error('invalid_dimension');
  if (!Number.isFinite(Date.parse(r.createdAt))) throw new Error('invalid_date');
  if (r.verification !== 'HASH_VERIFIED') throw new Error('unverified_seed');
  long_(r.worldSeed); long_(r.seedHash);
  const bytes = decimalBytes_(r.worldSeed);
  let lower48 = 0;
  for (let i = 0; i < 6; i++) lower48 += bytes[i] * Math.pow(256, i);
  if (r.structureSeed !== String(lower48)) throw new Error('invalid_structure_seed');
  const digest = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, bytes.map(b => b > 127 ? b - 256 : b));
  if (signedDecimal_(digest.slice(0,8).map(b => b & 255)) !== r.seedHash) throw new Error('seed_hash_mismatch');
  const expectedId = digestHex_(r.server + '\n' + r.dimension + '\n' + r.worldSeed + '\n' + r.minecraftVersion);
  if (r.id !== expectedId) throw new Error('id_mismatch');
  for (const field of ['biomeSamples','structureSamples'])
    if (!Number.isInteger(r[field]) || r[field] < 0 || r[field] > 1000000) throw new Error('invalid_sample_count');
  if (!r.salts || Array.isArray(r.salts) || typeof r.salts !== 'object' || Object.keys(r.salts).length > 128) throw new Error('invalid_salts');
  for (const [key,value] of Object.entries(r.salts)) {
    if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(key) || typeof value !== 'string') throw new Error('invalid_salt');
    long_(value);
    if (Number(value) < -2147483648 || Number(value) > 2147483647) throw new Error('salt_out_of_range');
  }
  return r;
}
function long_(value) {
  if (typeof value !== 'string' || !/^-?(0|[1-9][0-9]*)$/.test(value) || value === '-0') throw new Error('long_must_be_decimal_text');
  const negative = value[0] === '-', digits = negative ? value.slice(1) : value;
  const limit = negative ? '9223372036854775808' : '9223372036854775807';
  if (digits.length > 19 || (digits.length === 19 && digits > limit)) throw new Error('long_out_of_range');
  return value;
}
// Decimal-string conversion preserves all 64 bits without relying on Apps Script BigInt parsing.
function negateBytes_(bytes) {
  let carry = 1;
  return bytes.map(b => { const value = (255 - b) + carry; carry = value > 255 ? 1 : 0; return value & 255; });
}
function decimalBytes_(value) {
  const negative = value[0] === '-';
  let digits = negative ? value.slice(1) : value;
  const bytes = [];
  for (let i = 0; i < 8; i++) {
    let carry = 0, next = '';
    for (let j = 0; j < digits.length; j++) { const n = carry * 10 + Number(digits[j]); next += String(Math.floor(n / 256)); carry = n % 256; }
    bytes.push(carry); digits = next.replace(/^0+/, '') || '0';
  }
  return negative ? negateBytes_(bytes) : bytes;
}
function signedDecimal_(input) {
  const negative = input[7] >= 128, bytes = negative ? negateBytes_(input) : input;
  let digits = '0';
  for (let i = 7; i >= 0; i--) {
    let carry = bytes[i], next = '';
    for (let j = digits.length - 1; j >= 0; j--) { const n = Number(digits[j]) * 256 + carry; next = String(n % 10) + next; carry = Math.floor(n / 10); }
    digits = (carry ? String(carry) : '') + next;
  }
  digits = digits.replace(/^0+/, '') || '0';
  return negative && digits !== '0' ? '-' + digits : digits;
}
function digestHex_(value) {
  return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, value, Utilities.Charset.UTF_8)
    .map(byte => (byte & 255).toString(16).padStart(2,'0')).join('');
}
function safeCell_(value) {
  const text = String(value);
  return /^[=+@\-\t\r]/.test(text) && !/^-?\d+$/.test(text) ? "'" + text : text;
}
function json_(value) {
  return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);
}
