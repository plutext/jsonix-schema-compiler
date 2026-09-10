// Runtime check of the ES module output (CR-005, phase 2): the generated .mjs mapping
// must be importable as an ES module next to a named import of the @docx4j/jsonix runtime
// (3.1.0+, jsonix-CR-001), and the unmarshalled object must have the shape the generated
// declarations describe.
import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { Jsonix } from '@docx4j/jsonix';
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

// CR-006 / jsonix-CR-002: parent pointers and deep copy, once the runtime provides them.
if (typeof Jsonix.Util.deepCopy === 'function') {
  const parented = new Jsonix.Context([PO], { parentPointers: true }).createUnmarshaller().unmarshalString(xml).value;
  assert.equal(parented.shipTo.PARENT, parented);
  assert.equal(parented.items.item[0].PARENT, parented.items);
  assert.equal(Object.prototype.hasOwnProperty.call(parented, 'PARENT'), false);
  assert.deepEqual(Object.keys(parented.shipTo), Object.keys(po.shipTo));
  const copy = Jsonix.Util.deepCopy(parented);
  assert.notEqual(copy, parented);
  assert.equal(copy.shipTo.PARENT, copy);
  assert.equal(copy.items.item[0].PARENT, copy.items);
  assert.ok(Jsonix.Util.Type.isEqual(copy, parented));
  console.log('esm-smoke: parent pointers and deepCopy behave as declared');
} else {
  console.log('esm-smoke: runtime has no Jsonix.Util.deepCopy yet (jsonix-CR-002); parent pointer checks skipped');
}

// CR-010: element factories. The same purchase order built three ways marshals to the same XML.
{
  const { createPurchaseOrderType, createUSAddress, createItems, createItemsItem, createPurchaseOrderElement } = await import('../../../target/generated-sources/xjc/PurchaseOrder.factory.mjs');
  const el = await import('../../../target/generated-sources/xjc/PurchaseOrder.el.mjs');
  const marshaller = context.createMarshaller();
  const address = () => ({ name: 'Alice Smith', street: '123 Maple Street', city: 'Mill Valley', state: 'CA', zip: 90952 });
  const value = () => ({ shipTo: address(), billTo: address(), items: { item: [{ productName: 'Lawnmower', partNum: '872-AA', quantity: 1, usPrice: 148.95 }] }, orderDate: { year: 1999, month: 10, day: 20 } });
  const viaCreators = createPurchaseOrderElement(createPurchaseOrderType({ shipTo: createUSAddress(address()), billTo: address(), items: createItems({ item: [createItemsItem({ productName: 'Lawnmower', partNum: '872-AA', quantity: 1, usPrice: 148.95 })] }), orderDate: { year: 1999, month: 10, day: 20 } }));
  const viaEl = el.purchaseOrder(value());
  const viaLiteral = { name: { namespaceURI: '', localPart: 'purchaseOrder' }, value: value() };
  const expected = marshaller.marshalString(viaLiteral);
  assert.equal(marshaller.marshalString(viaCreators), expected);
  assert.equal(marshaller.marshalString(viaEl), expected);
  assert.equal(viaCreators.value.TYPE_NAME, 'PO.PurchaseOrderType', 'a creator sets TYPE_NAME');
  assert.equal(viaCreators.value.shipTo.TYPE_NAME, 'PO.USAddress');
  assert.equal(viaEl.value.TYPE_NAME, 'PO.PurchaseOrderType', 'a wrapper sets TYPE_NAME on a literal value');
  const tagged = createPurchaseOrderElement({ TYPE_NAME: 'PO.PurchaseOrderType', ...value() });
  assert.equal(tagged.value.TYPE_NAME, 'PO.PurchaseOrderType', 'a wrapper leaves an existing TYPE_NAME alone');
  assert.deepEqual(createUSAddress(), { TYPE_NAME: 'PO.USAddress' });
  console.log('esm-smoke: element factories (CR-010) build the same document as a literal');
}
