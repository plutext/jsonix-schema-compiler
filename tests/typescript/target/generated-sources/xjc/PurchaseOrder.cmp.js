var PurchaseOrder_Module_Factory = function () {
  var PO = {
    n: 'PO',
    tis: [{
        ln: 'Items',
        ps: [{
            n: 'item',
            mno: 0,
            col: true,
            en: {
              lp: 'item'
            },
            ti: '.Items.Item'
          }]
      }, {
        ln: 'Items.Item',
        tn: null,
        ps: [{
            n: 'productName',
            rq: true,
            en: {
              lp: 'productName'
            }
          }, {
            n: 'quantity',
            rq: true,
            en: {
              lp: 'quantity'
            },
            ti: 'Int'
          }, {
            n: 'usPrice',
            rq: true,
            en: {
              lp: 'USPrice'
            },
            ti: 'Decimal'
          }, {
            n: 'comment',
            en: {
              lp: 'comment'
            }
          }, {
            n: 'shipDate',
            en: {
              lp: 'shipDate'
            },
            ti: 'Date'
          }, {
            n: 'partNum',
            rq: true,
            an: {
              lp: 'partNum'
            },
            t: 'a'
          }]
      }, {
        ln: 'PurchaseOrderType',
        ps: [{
            n: 'shipTo',
            rq: true,
            en: {
              lp: 'shipTo'
            },
            ti: '.USAddress'
          }, {
            n: 'billTo',
            rq: true,
            en: {
              lp: 'billTo'
            },
            ti: '.USAddress'
          }, {
            n: 'comment',
            en: {
              lp: 'comment'
            }
          }, {
            n: 'items',
            rq: true,
            en: {
              lp: 'items'
            },
            ti: '.Items'
          }, {
            n: 'orderDate',
            ti: 'Date',
            an: {
              lp: 'orderDate'
            },
            t: 'a'
          }]
      }, {
        ln: 'USAddress',
        ps: [{
            n: 'name',
            rq: true,
            en: {
              lp: 'name'
            }
          }, {
            n: 'street',
            rq: true,
            en: {
              lp: 'street'
            }
          }, {
            n: 'city',
            rq: true,
            en: {
              lp: 'city'
            }
          }, {
            n: 'state',
            rq: true,
            en: {
              lp: 'state'
            }
          }, {
            n: 'zip',
            rq: true,
            en: {
              lp: 'zip'
            },
            ti: 'Decimal'
          }, {
            n: 'country',
            ti: 'NMToken',
            an: {
              lp: 'country'
            },
            t: 'a'
          }]
      }],
    eis: [{
        en: {
          lp: 'comment'
        }
      }, {
        ti: '.PurchaseOrderType',
        en: {
          lp: 'purchaseOrder'
        }
      }]
  };
  return {
    PO: PO
  };
};
if (typeof define === 'function' && define.amd) {
  define([], PurchaseOrder_Module_Factory);
}
else {
  var PurchaseOrder_Module = PurchaseOrder_Module_Factory();
  if (typeof module !== 'undefined' && module.exports) {
    module.exports.PO = PurchaseOrder_Module.PO;
  }
  else {
    var PO = PurchaseOrder_Module.PO;
  }
}