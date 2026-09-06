// Compile-time checks of the declarations generated for purchaseorder.xsd, composed with the
// @mitre/jsonix runtime typings (3.1.0+, jsonix-CR-001): no casts anywhere.
import { Jsonix } from '@mitre/jsonix';
import { PO } from '../../../target/generated-sources/xjc/PurchaseOrder.std';
import { PO as PO_ESM } from '../../../target/generated-sources/xjc/PurchaseOrder.mjs';
import type { PurchaseOrderElement, RootElement, USAddress, Items, XmlCalendar } from '../../../target/generated-sources/xjc/PurchaseOrder.std';

declare const xml: string;

// Generated mappings are accepted by the runtime's Context (UMD and ES-module outputs alike).
const context = new Jsonix.Context([PO]);
const esmContext = new Jsonix.Context([PO_ESM]);
const unmarshaller = context.createUnmarshaller();
const marshaller = context.createMarshaller();

// Unmarshal results take a generated element type; required properties are non-optional and
// typed as Jsonix produces them.
const element: PurchaseOrderElement = unmarshaller.unmarshalString<PurchaseOrderElement>(xml);
const po = element.value;
const name: string = po.shipTo.name;
const zip: number = po.shipTo.zip;
const items: Items = po.items;
const firstItem: Items.Item | undefined = items.item?.[0];
const partNum: string | undefined = firstItem?.partNum;
const shipDate: XmlCalendar | undefined = firstItem?.shipDate;
const year: number | undefined = shipDate?.year;
const comment: string | undefined = po.comment;
const typeName: 'PO.PurchaseOrderType' | undefined = po.TYPE_NAME;

// The union of global elements is what an unmarshaller returns; the callback forms too.
unmarshaller.unmarshalFile<RootElement>('po.xml', (root) => void root.name.localPart);

// The mapping constant carries its root element type as a phantom parameter (jsonix-CR-001 item 6).
type RootOfPO = NonNullable<typeof PO.__rootElement>;
const sameRoot: RootOfPO = element;

// Generated support types are structurally the runtime's own.
const runtimeCalendar: Jsonix.XML.Calendar | undefined = shipDate;
const runtimeName: Jsonix.XML.QName = element.name;

// Object literals typed with generated types marshal without a cast.
const address: USAddress = { name: 'Alice Smith', street: '123 Maple Street', city: 'Mill Valley', state: 'CA', zip: 90952 };
const outgoing: PurchaseOrderElement = {
  name: { namespaceURI: '', localPart: 'purchaseOrder' },
  value: { shipTo: address, billTo: address, items: { item: [] }, orderDate: { year: 1999, month: 10, day: 20 } },
};
const out: string = marshaller.marshalString(outgoing);

// @ts-expect-error misspelt property
const misspelt = po.shipTo.nme;
// @ts-expect-error wrong type
const wrongType: number = po.shipTo.city;
// @ts-expect-error missing required property
const incomplete: USAddress = { name: 'x' };
// @ts-expect-error a bare record is not an element
marshaller.marshalString({ foo: 'bar' });

export { esmContext, name, zip, partNum, year, comment, typeName, sameRoot, runtimeCalendar, runtimeName, out, misspelt, wrongType, incomplete };
