// Compile-time checks of the element factories generated for purchaseorder.xsd (CR-010): the same
// purchase order built three ways must type-check, and the wrong value type must not.
import { Jsonix } from '@docx4j/jsonix';
import { PO } from '../../../target/generated-sources/xjc/PurchaseOrder.mjs';
import type { PurchaseOrderElement, USAddress } from '../../../target/generated-sources/xjc/PurchaseOrder.mjs';
import { createPurchaseOrderType, createUSAddress, createItems, createItemsItem, createPurchaseOrderElement, createCommentElement } from '../../../target/generated-sources/xjc/PurchaseOrder.factory.mjs';
import * as el from '../../../target/generated-sources/xjc/PurchaseOrder.el.mjs';

const marshaller = new Jsonix.Context([PO]).createMarshaller();
const address: USAddress = { name: 'Alice Smith', street: '123 Maple Street', city: 'Mill Valley', state: 'CA', zip: 90952 };

// 1. creators plus the global wrapper (docx4j: factory.createPurchaseOrder(factory.createPurchaseOrderType()))
const viaCreators: PurchaseOrderElement = createPurchaseOrderElement(createPurchaseOrderType({
  shipTo: createUSAddress(address),
  billTo: address,
  items: createItems({ item: [createItemsItem({ productName: 'Lawnmower', partNum: '872-AA', quantity: 1, usPrice: 148.95 })] }),
  orderDate: { year: 1999, month: 10, day: 20 },
}));
// 2. the per-name wrapper
const viaEl: PurchaseOrderElement = el.purchaseOrder({ shipTo: address, billTo: address, items: { item: [{ productName: 'Lawnmower', partNum: '872-AA', quantity: 1, usPrice: 148.95 }] }, orderDate: { year: 1999, month: 10, day: 20 } });
// 3. a literal, the stricter form (required properties checked; no TYPE_NAME)
const viaLiteral: PurchaseOrderElement = {
  name: { namespaceURI: '', localPart: 'purchaseOrder' },
  value: { shipTo: address, billTo: address, items: { item: [{ productName: 'Lawnmower', partNum: '872-AA', quantity: 1, usPrice: 148.95 }] }, orderDate: { year: 1999, month: 10, day: 20 } },
};
const xml: [string, string, string] = [marshaller.marshalString(viaCreators), marshaller.marshalString(viaEl), marshaller.marshalString(viaLiteral)];

// A creator sets TYPE_NAME; the wrapper sets it on a literal value.
const typeName: 'PO.PurchaseOrderType' | undefined = viaCreators.value.TYPE_NAME;
const comment = createCommentElement('a string element');
const commentValue: string = comment.value;

// @ts-expect-error the wrong value type for the element
createPurchaseOrderElement(createUSAddress());
// @ts-expect-error the wrong value type for the per-name wrapper
el.comment(createUSAddress());
// @ts-expect-error a creator's init cannot set TYPE_NAME
createUSAddress({ TYPE_NAME: 'PO.USAddress' });
// @ts-expect-error a creator's init is checked property by property
createUSAddress({ name: 42 });

export { xml, typeName, commentValue };
