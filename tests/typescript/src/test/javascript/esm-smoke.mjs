// Runtime check of the ES module output (CR-005, phase 2): the generated .mjs mapping
// must be importable as an ES module next to a named import of the @mitre/jsonix runtime
// (3.1.0+, jsonix-CR-001), and the unmarshalled object must have the shape the generated
// declarations describe.
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { Jsonix } from '@mitre/jsonix';
import { PO } from '../../../target/generated-sources/xjc/PurchaseOrder.mjs';

const context = new Jsonix.Context([PO]);
const unmarshaller = context.createUnmarshaller();
const xml = readFileSync(new URL('../resources/po.xml', import.meta.url), 'utf8');
const element = unmarshaller.unmarshalString(xml);

assert.equal(element.name.localPart, 'purchaseOrder');
const po = element.value;
assert.equal(po.TYPE_NAME, 'PO.PurchaseOrderType');
assert.equal(po.shipTo.TYPE_NAME, 'PO.USAddress');
assert.equal(po.shipTo.name, 'Alice Smith');
assert.equal(typeof po.shipTo.zip, 'number');
assert.equal(po.items.item[0].TYPE_NAME, 'PO.Items.Item');
assert.equal(typeof po.items.item[0].quantity, 'number');
assert.equal(typeof po.items.item[0].usPrice, 'number');
assert.equal(po.orderDate.year, 1999);
const shipped = po.items.item.find((item) => item.shipDate !== undefined);
assert.equal(shipped.shipDate.year, 1999);
assert.equal(shipped.shipDate.month, 5);
console.log('esm-smoke: ES module mapping unmarshals po.xml with the declared shape');
