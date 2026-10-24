import fs from 'node:fs';
import vm from 'node:vm';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
const context = vm.createContext({
  Utilities: {
    DigestAlgorithm:{SHA_256:'sha256'},Charset:{UTF_8:'utf8'},
    computeDigest:(algorithm,value)=>[...crypto.createHash(algorithm).update(typeof value==='string'?value:Buffer.from(value.map(b=>b&255))).digest()].map(b=>b>127?b-256:b)
  },
  ContentService:{MimeType:{JSON:'json'},createTextOutput:value=>({value,setMimeType(){return this;}})}
});
vm.runInContext(fs.readFileSync(new URL('./Code.gs',import.meta.url),'utf8'),context);
let tests=0;
for(const value of ['0','1','-1','9223372036854775807','-9223372036854775808','384905238495082349']){
  const bytes=Buffer.alloc(8);bytes.writeBigInt64LE(BigInt(value));
  assert.deepEqual(Array.from(context.decimalBytes_(value)),[...bytes]);
  assert.equal(context.signedDecimal_([...bytes]),value);
  const record={server:'example.invalid:25565',dimension:'minecraft:overworld',minecraftVersion:'26.3',worldSeed:value,
    structureSeed:(BigInt(value)&((1n<<48n)-1n)).toString(),seedHash:crypto.createHash('sha256').update(bytes).digest().readBigInt64LE().toString(),
    createdAt:'2026-10-23T12:00:00Z',verification:'HASH_VERIFIED',biomeSamples:20,structureSamples:6,salts:{'minecraft:desert_pyramids':'14357617'},source:'fixture'};
  record.id=crypto.createHash('sha256').update(`${record.server}\n${record.dimension}\n${record.worldSeed}\n${record.minecraftVersion}`).digest('hex');
  assert.equal(context.validateRecord_(record).worldSeed,value);
  assert.throws(()=>context.validateRecord_({...record,seedHash:'42'}));
  assert.throws(()=>context.validateRecord_({...record,worldSeed:Number(value)}));
  tests+=5;
}
for(const value of ['9223372036854775808','-9223372036854775809','1.1','1e10','-0','01']){assert.throws(()=>context.long_(value));tests++;}
assert.equal(context.safeCell_('=IMPORTXML("https://example.invalid")'),'\'=IMPORTXML("https://example.invalid")');
assert.equal(context.safeCell_('-9223372036854775808'),'-9223372036854775808');
assert.equal(JSON.parse(context.doGet().value).schema,1);
console.log(`${tests+3} backend checks passed`);
